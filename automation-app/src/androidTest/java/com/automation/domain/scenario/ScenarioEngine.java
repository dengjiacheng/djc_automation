package com.automation.domain.scenario;

import android.app.UiAutomation;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.automation.domain.scenario.accessibility.AccessibilitySnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 极简场景引擎，按照注册顺序匹配并执行场景。
 *
 * 设计目标：
 * 1. 通过 {@link SceneSignature} 快速判断当前界面，无需 dump。
 * 2. 场景执行结果驱动流程，支持成功/停止/失败。
 * 3. 每个场景仅执行一次，执行后可按需删除其他场景。
 */
public final class ScenarioEngine {

    private static final String TAG = "ScenarioEngine";

    private final Map<String, Scene> sceneIndex = new LinkedHashMap<>();
    private long pollIntervalMs = 400L;

    public ScenarioEngine addScene(@NonNull Scene scene) {
        Objects.requireNonNull(scene, "scene");
        sceneIndex.put(scene.id(), scene);
        return this;
    }

    public ScenarioEngine addScenes(Collection<Scene> scenes) {
        if (scenes == null) {
            return this;
        }
        for (Scene scene : scenes) {
            addScene(scene);
        }
        return this;
    }

    public ScenarioEngine clear() {
        sceneIndex.clear();
        return this;
    }

    public ScenarioEngine pollInterval(long intervalMs) {
        if (intervalMs > 0) {
            this.pollIntervalMs = intervalMs;
        }
        return this;
    }

    public ScenarioRunResult run(ScenarioContext context, ScenarioEngineOptions options) {
        if (sceneIndex.isEmpty()) {
            log(context, "没有可执行的场景");
            return finish(context, ScenarioRunResult.empty());
        }

        Objects.requireNonNull(options, "options");

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        // 活跃场景维护为有序 Map，方便按照注册顺序遍历并支持按 id 删除
        Map<String, Scene> activeScenes = new LinkedHashMap<>(sceneIndex);

        SceneResult lastResult = SceneResult.CONTINUE;
        String lastSceneId = null;

        long lastMatchTime = SystemClock.elapsedRealtime();
        long timeoutMs = options.noMatchTimeoutMs();

        if (options.initSceneId() != null) {
            Scene initScene = sceneIndex.get(options.initSceneId());
            if (initScene != null && initScene.handler() != null) {
                try {
                    log(context, "执行初始化场景: " + initScene.id());
                    initScene.handler().handle(context);
                } catch (Exception e) {
                    reportError(context, "初始化场景执行异常: " + initScene.id(), e);
                }
            }
            if (initScene != null) {
                pruneScenes(activeScenes, Collections.singletonList(initScene.id()));
            }
            // 初始化场景执行完毕后，重新计时防止耗时影响超时判断
            lastMatchTime = SystemClock.elapsedRealtime();
        }

        while (!activeScenes.isEmpty()) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastMatchTime > timeoutMs) {
                context.getReporter().onTimeout();
                return finish(context, ScenarioRunResult.timeout(lastSceneId));
            }
            Map<String, Object> snapshotData = context.snapshotData();
            LegacyVariableResolver resolver = LegacyVariableResolver.from(snapshotData);

            // 单次循环仅抓取一次快照，后续在内存中匹配所有场景
            AccessibilitySnapshot snapshot = AccessibilitySnapshot.capture(uiAutomation);
            context.updateSnapshot(snapshot);
            if (snapshot.isEmpty()) {
                log(context, "快照为空，等待下一轮");
                SystemClock.sleep(pollIntervalMs);
                continue;
            }

            List<Scene> matched = new ArrayList<>();
            for (Scene scene : activeScenes.values()) {
                if (shouldExecute(scene, snapshot, resolver)) {
                    matched.add(scene);
                }
            }

            if (matched.isEmpty()) {
                SystemClock.sleep(pollIntervalMs);
                continue;
            }

            if (matched.size() > 1) {
                String[] ids = new String[matched.size()];
                for (int i = 0; i < matched.size(); i++) {
                    ids[i] = matched.get(i).id();
                }
                context.getReporter().onSceneConflict(ids);
                return finish(context, ScenarioRunResult.failed(null, new IllegalStateException("scene conflict")));
            }

            Scene scene = matched.get(0);
            lastSceneId = scene.id();

            context.getReporter().onSceneMatched(scene.id(), scene.description());
            pruneScenes(activeScenes, scene.pruneScenes());
            try {
                SceneResult result = scene.handler() != null
                        ? scene.handler().handle(context)
                        : SceneResult.CONTINUE;

                if (result == SceneResult.SUCCESS) {
                    log(context, "流程成功结束，最后场景: " + scene.id());
                    return finish(context, ScenarioRunResult.success(scene.id()));
                }
                if (result == SceneResult.STOP) {
                    log(context, "流程收到 STOP 指令停止: " + scene.id());
                    return finish(context, ScenarioRunResult.stopped(scene.id()));
                }
                if (result == SceneResult.ERROR) {
                    reportError(context, "场景执行返回错误: " + scene.id(), null);
                }
                lastResult = result;
                lastMatchTime = SystemClock.elapsedRealtime();
            } catch (Exception e) {
                reportError(context, "场景执行异常: " + scene.id(), e);
                lastResult = SceneResult.ERROR;
                lastMatchTime = SystemClock.elapsedRealtime();
            }
        }

        ScenarioRunResult finalResult;
        switch (lastResult) {
            case STOP:
                finalResult = ScenarioRunResult.stopped(lastSceneId);
                break;
            case ERROR:
                finalResult = ScenarioRunResult.failed(lastSceneId, null);
                break;
            default:
                finalResult = ScenarioRunResult.success(lastSceneId);
                break;
        }
        return finish(context, finalResult);
    }

    private ScenarioRunResult finish(ScenarioContext context, ScenarioRunResult result) {
        context.updateSnapshot(AccessibilitySnapshot.empty());
        return result;
    }

    private boolean shouldExecute(Scene scene, AccessibilitySnapshot snapshot,
            LegacyVariableResolver resolver) {
        if (scene.isSignatureLess()) {
            return true;
        }
        SceneSignature signature = scene.signature();
        if (signature == null) {
            return false;
        }
        boolean matched = signature.matches(snapshot, resolver);
        if (matched && Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "场景匹配成功: " + scene.id());
        }
        return matched;
    }

    private void log(ScenarioContext context, String message) {
        Log.i(TAG, message);
        context.getReporter().onInfo(message);
    }

    private void reportError(ScenarioContext context, String message, Throwable error) {
        Log.e(TAG, message, error);
        context.getReporter().onError(message, error);
    }

    private void pruneScenes(Map<String, Scene> scenes, List<String> pruneIds) {
        if (pruneIds == null || pruneIds.isEmpty()) {
            return;
        }
        for (String id : pruneIds) {
            if (scenes.remove(id) != null) {
                Log.d(TAG, "移除场景: " + id);
            }
        }
    }
}

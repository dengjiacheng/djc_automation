package com.automation.domain.scenario;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Objects;

/**
 * 负责串联互斥锁与场景执行。
 */
public final class ScenarioRunner {

    private static final String TAG = "ScenarioRunner";

    private final ScriptRunGuard guard;

    public ScenarioRunner(@NonNull ScriptRunGuard guard) {
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    public ScenarioRunResult run(String scriptId,
            Collection<Scene> scenes,
            ScenarioContext context,
            ScenarioEngineOptions options) {
        Objects.requireNonNull(scenes, "scenes");
        Objects.requireNonNull(options, "options");
        Log.d(TAG, "Starting scenario run for scriptId=" + scriptId + " scenes=" + scenes.size());
        if (!guard.tryAcquire(scriptId)) {
            Log.w(TAG, "设备正在执行其他脚本: " + guard.currentScript());
            return ScenarioRunResult.failed(null, new IllegalStateException("device busy"));
        }
        try {
            ScenarioEngine engine = new ScenarioEngine().addScenes(scenes);
            ScenarioRunResult result = engine.run(context, options);
            Log.d(TAG, "Scenario run completed for scriptId=" + scriptId + " status=" + result.status());
            return result;
        } finally {
            guard.release(scriptId);
            context.getVisionToolkit().clearTemplates();
        }
    }
}

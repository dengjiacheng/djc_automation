package com.automation.application.scenario;

import android.os.SystemClock;

import com.automation.domain.scenario.ScenarioReporter;
import com.automation.domain.scenario.ScenarioRunResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 采集场景执行过程中的指标，用于结果输出与监控。
 */
public final class ScenarioTelemetryCollector implements ScenarioReporter {

    private final long startTime = SystemClock.elapsedRealtime();
    private final List<String> matchedScenes = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    private boolean timeout;
    private ScenarioRunResult result;

    @Override
    public void onInfo(String message) {
        // no-op
    }

    @Override
    public void onWarning(String message) {
        warnings.add(message);
    }

    @Override
    public void onError(String message, Throwable error) {
        if (error != null) {
            errors.add(message + " [" + error.getClass().getSimpleName() + "]");
        } else {
            errors.add(message);
        }
    }

    @Override
    public void onSceneMatched(String sceneId, String description) {
        matchedScenes.add(sceneId);
    }

    @Override
    public void onSceneConflict(String[] sceneIds) {
        errors.add("Scene conflict: " + String.join(",", sceneIds));
    }

    @Override
    public void onTimeout() {
        timeout = true;
    }

    public void finalizeResult(ScenarioRunResult result) {
        this.result = result;
    }

    public JSONObject toJson() {
        JSONObject metrics = new JSONObject();
        long duration = SystemClock.elapsedRealtime() - startTime;
        try {
            metrics.put("duration_ms", duration);
            metrics.put("matched_count", matchedScenes.size());
            metrics.put("matched_scenes", new JSONArray(matchedScenes));
            metrics.put("warnings", new JSONArray(warnings));
            metrics.put("errors", new JSONArray(errors));
            metrics.put("timeout", timeout);
            if (result != null) {
                metrics.put("status", result.status().name().toLowerCase());
                if (result.lastSceneId() != null) {
                    metrics.put("last_scene", result.lastSceneId());
                }
            }
        } catch (JSONException ignored) {
        }
        return metrics;
    }
}

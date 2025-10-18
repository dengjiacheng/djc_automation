package com.automation.domain.scenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 组合多个 reporter，简化事件广播。
 */
public final class CompositeScenarioReporter implements ScenarioReporter {

    private final List<ScenarioReporter> delegates;

    public CompositeScenarioReporter(ScenarioReporter... reporters) {
        List<ScenarioReporter> temp = new ArrayList<>();
        if (reporters != null) {
            for (ScenarioReporter reporter : reporters) {
                if (reporter != null) {
                    temp.add(reporter);
                }
            }
        }
        this.delegates = Collections.unmodifiableList(temp);
    }

    @Override
    public void onInfo(String message) {
        for (ScenarioReporter reporter : delegates) {
            reporter.onInfo(message);
        }
    }

    @Override
    public void onWarning(String message) {
        for (ScenarioReporter reporter : delegates) {
            reporter.onWarning(message);
        }
    }

    @Override
    public void onError(String message, Throwable error) {
        for (ScenarioReporter reporter : delegates) {
            reporter.onError(message, error);
        }
    }

    @Override
    public void onSceneMatched(String sceneId, String description) {
        for (ScenarioReporter reporter : delegates) {
            reporter.onSceneMatched(sceneId, description);
        }
    }

    @Override
    public void onSceneConflict(String[] sceneIds) {
        for (ScenarioReporter reporter : delegates) {
            reporter.onSceneConflict(sceneIds);
        }
    }

    @Override
    public void onTimeout() {
        for (ScenarioReporter reporter : delegates) {
            reporter.onTimeout();
        }
    }
}

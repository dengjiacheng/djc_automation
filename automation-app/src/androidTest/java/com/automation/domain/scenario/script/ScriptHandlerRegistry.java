package com.automation.domain.scenario.script;

import com.automation.domain.scenario.SceneHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 统一的 handler 查找器，便于后续扩展多脚本。
 */
public final class ScriptHandlerRegistry {

    private final List<ScriptHandlerProvider> providers = new ArrayList<>();

    public ScriptHandlerRegistry(List<? extends ScriptHandlerProvider> providers) {
        if (providers != null) {
            this.providers.addAll(providers);
        }
    }

    public void register(ScriptHandlerProvider provider) {
        providers.add(Objects.requireNonNull(provider, "provider"));
    }

    public SceneHandler resolve(String scriptName, String handlerName) {
        if (handlerName == null) {
            return null;
        }
        for (ScriptHandlerProvider provider : providers) {
            if (provider.supports(scriptName)) {
                SceneHandler handler = provider.resolve(handlerName);
                if (handler != null) {
                    return handler;
                }
            }
        }
        return null;
    }
}

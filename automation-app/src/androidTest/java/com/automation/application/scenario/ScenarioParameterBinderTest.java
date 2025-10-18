package com.automation.application.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.automation.domain.scenario.script.AssetScriptRepository;
import com.automation.domain.scenario.script.ScriptRepository;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * 基本回归，确保参数绑定逻辑按预期合并默认值与用户配置。
 */
@RunWith(AndroidJUnit4.class)
public final class ScenarioParameterBinderTest {

    private ScenarioParameterBinder binder;

    @Before
    public void setUp() {
        Context instrumentationContext = InstrumentationRegistry.getInstrumentation().getContext();
        ScriptRepository repository = new AssetScriptRepository(instrumentationContext);
        ScenarioCatalog catalog = new ScenarioCatalog(repository);
        binder = new ScenarioParameterBinder(catalog);
    }

    @Test
    public void bind_usesDefaultsWhenConfigMissing() throws Exception {
        JSONObject params = new JSONObject()
                .put("task_name", "dhgate_order_v2");

        ScenarioTaskRequest request = binder.bind(params);

        assertEquals("dhgate_order_v2", request.taskName());
        assertNotNull(request.script());
        assertTrue("默认参数应该被带入上下文", request.contextData().containsKey("task_name"));
    }
}

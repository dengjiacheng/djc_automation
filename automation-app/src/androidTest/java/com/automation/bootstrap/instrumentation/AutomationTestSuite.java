package com.automation.bootstrap.instrumentation;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.automation.application.runtime.AutomationController;
import com.automation.infrastructure.network.AuthService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumentation entry point – delegates to {@link AutomationController}.
 */
@RunWith(AndroidJUnit4.class)
public class AutomationTestSuite {

    private static final String TAG = "AutomationTestSuite";

    private AutomationController controller;
    private String wsUrl;
    private String deviceId;

    @Before
    public void setUp() throws Exception {
        controller = new AutomationController();

        Bundle arguments = InstrumentationRegistry.getArguments();
        wsUrl = arguments.getString("wsUrl", "");
        deviceId = arguments.getString("deviceId", "");
        String serverUrl = arguments.getString("serverUrl", "");

        if (TextUtils.isEmpty(wsUrl)) {
            throw new IllegalStateException("缺少 wsUrl 参数，无法启动自动化服务");
        }

        if (TextUtils.isEmpty(deviceId)) {
            throw new IllegalStateException("缺少 deviceId 参数，无法启动自动化服务");
        }

        if (!TextUtils.isEmpty(serverUrl)) {
            controller.setAuthService(new AuthService(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    serverUrl
            ));
        }

        controller.setDeviceId(deviceId);

        Log.i(TAG, "启动自动化控制器，目标 WebSocket: " + wsUrl);
        controller.start(wsUrl);
    }

    @Test
    public void runAutomationService() throws InterruptedException {
        controller.keepAlive();
    }
}

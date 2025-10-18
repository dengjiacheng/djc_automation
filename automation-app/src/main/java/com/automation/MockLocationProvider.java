package com.automation;

import android.content.Context;

/**
 * 与历史代码兼容的定位帮助器，实际实现位于 location 包。
 */
public class MockLocationProvider extends com.automation.infrastructure.location.MockLocationProvider {

    public MockLocationProvider(String providerName, Context context) {
        super(providerName, context);
    }
}

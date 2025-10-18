package com.automation.domain.scenario;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiDevice;

import com.automation.domain.scenario.accessibility.AccessibilitySnapshot;
import com.automation.domain.scenario.device.DeviceActions;
import com.automation.domain.scenario.vision.VisionToolkit;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 场景共享上下文，记录设备、操作工具与动态数据。
 */
public final class ScenarioContext {

    private final Context appContext;
    private final UiDevice uiDevice;
    private final DeviceActions deviceActions;
    private final VisionToolkit visionToolkit;
    private final Map<String, Object> data;
    private final ScenarioReporter reporter;
    private AccessibilitySnapshot snapshot;

    private ScenarioContext(Builder builder) {
        this.appContext = Objects.requireNonNull(builder.appContext, "appContext");
        this.uiDevice = Objects.requireNonNull(builder.uiDevice, "uiDevice");
        this.deviceActions = Objects.requireNonNull(builder.deviceActions, "deviceActions");
        this.visionToolkit = Objects.requireNonNull(builder.visionToolkit, "visionToolkit");
        this.data = builder.data != null ? builder.data : new HashMap<>();
        this.reporter = builder.reporter != null ? builder.reporter : ScenarioReporter.NO_OP;
        this.snapshot = AccessibilitySnapshot.empty();
    }

    @NonNull
    public Context getAppContext() {
        return appContext;
    }

    @NonNull
    public UiDevice getUiDevice() {
        return uiDevice;
    }

    @NonNull
    public DeviceActions getDeviceActions() {
        return deviceActions;
    }

    @NonNull
    public VisionToolkit getVisionToolkit() {
        return visionToolkit;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        // 约定调用方自行确保类型正确，失败会抛出 ClassCastException。
        return (T) data.get(key);
    }

    public ScenarioContext put(String key, Object value) {
        data.put(key, value);
        return this;
    }

    @NonNull
    public AccessibilitySnapshot getSnapshot() {
        return snapshot != null ? snapshot : AccessibilitySnapshot.empty();
    }

    public ScenarioContext updateSnapshot(AccessibilitySnapshot newSnapshot) {
        this.snapshot = newSnapshot != null ? newSnapshot : AccessibilitySnapshot.empty();
        return this;
    }

    @NonNull
    public ScenarioReporter getReporter() {
        return reporter;
    }

    @NonNull
    public Map<String, Object> snapshotData() {
        // 拷贝一份只读视图，避免外部直接修改内部状态。
        return Collections.unmodifiableMap(new HashMap<>(data));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Context appContext;
        private UiDevice uiDevice;
        private DeviceActions deviceActions;
        private VisionToolkit visionToolkit;
        private Map<String, Object> data;
        private ScenarioReporter reporter;

        private Builder() {
        }

        public Builder appContext(Context context) {
            this.appContext = context;
            return this;
        }

        public Builder uiDevice(UiDevice device) {
            this.uiDevice = device;
            return this;
        }

        public Builder deviceActions(DeviceActions actions) {
            this.deviceActions = actions;
            return this;
        }

        public Builder visionToolkit(VisionToolkit toolkit) {
            this.visionToolkit = toolkit;
            return this;
        }

        public Builder data(Map<String, Object> map) {
            this.data = map;
            return this;
        }

        public Builder reporter(ScenarioReporter reporter) {
            this.reporter = reporter;
            return this;
        }

        public ScenarioContext build() {
            return new ScenarioContext(this);
        }
    }
}

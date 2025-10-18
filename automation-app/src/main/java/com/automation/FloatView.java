package com.automation;

import android.content.Context;

import com.automation.infrastructure.overlay.FloatingOverlayWindow;
import com.automation.presentation.overlay.FloatingIndicatorView;

/**
 * 兼容旧接口的悬浮窗视图，内部委托给 overlay 层的控制器实现。
 */
public class FloatView extends FloatingIndicatorView {

    private final FloatingOverlayWindow overlayWindow;

    public FloatView(Context context) {
        super(context);
        overlayWindow = new FloatingOverlayWindow(context, this);
    }

    public void show() {
        overlayWindow.show();
    }

    public void hide() {
        overlayWindow.hide();
    }
}

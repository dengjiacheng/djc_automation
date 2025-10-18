package com.automation.infrastructure.overlay;

import android.content.Context;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.automation.presentation.overlay.FloatingIndicatorView;

/**
 * 负责悬浮窗的显示、隐藏以及位置更新，隔离系统 WindowManager 操作。
 */
public class FloatingOverlayWindow implements FloatingIndicatorView.GestureCallback {

    private static final String TAG = "FloatingOverlayWindow";

    private final Context context;
    private final WindowManager windowManager;
    private final FloatingIndicatorView indicatorView;
    private final WindowManager.LayoutParams layoutParams;

    private boolean attached;

    public FloatingOverlayWindow(Context context, FloatingIndicatorView indicatorView) {
        this(context, indicatorView, new OverlayLayoutParamsFactory());
    }

    public FloatingOverlayWindow(Context context,
                                 FloatingIndicatorView indicatorView,
                                 OverlayLayoutParamsFactory factory) {
        this.context = context.getApplicationContext();
        this.indicatorView = indicatorView;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.layoutParams = factory.create(context);
        this.indicatorView.setGestureCallback(this);
    }

    public void show() {
        if (windowManager == null || attached) {
            return;
        }
        try {
            windowManager.addView(indicatorView, layoutParams);
            attached = true;
        } catch (IllegalStateException alreadyAdded) {
            Log.i(TAG, "indicatorView already added", alreadyAdded);
        }
    }

    public void hide() {
        if (windowManager == null || !attached) {
            return;
        }
        try {
            windowManager.removeView(indicatorView);
        } catch (IllegalArgumentException ignored) {
            // 忽略未注册视图的异常
        } finally {
            attached = false;
        }
    }

    @Override
    public void onDrag(float xInScreen, float yInScreen, float xInView, float yInView) {
        if (windowManager == null || !attached) {
            return;
        }
        layoutParams.x = (int) (xInScreen - xInView);
        layoutParams.y = (int) (yInScreen - yInView);
        windowManager.updateViewLayout(indicatorView, layoutParams);
    }

    @Override
    public void onTap() {
        Toast.makeText(context, "Float view tapped", Toast.LENGTH_SHORT).show();
    }
}

package com.automation.infrastructure.overlay;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.view.Gravity;
import android.view.WindowManager;

/**
 * 构建悬浮窗所需的 LayoutParams，集中管理布局参数便于后续扩展。
 */
public class OverlayLayoutParamsFactory {

    public WindowManager.LayoutParams create(Context context) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.packageName = context.getPackageName();
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        params.format = PixelFormat.RGBA_8888;
        params.gravity = Gravity.TOP | Gravity.START;
        params.alpha = 0.6f;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
        Point size = new Point();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getSize(size);
            params.x = size.x - dp2px(context, 64);
            params.y = size.y / 3;
        }
        return params;
    }

    private int dp2px(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}

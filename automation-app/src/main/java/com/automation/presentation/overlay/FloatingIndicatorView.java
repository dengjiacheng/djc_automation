package com.automation.presentation.overlay;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.automation.R;

/**
 * 悬浮窗内部的可拖拽视图，负责处理触摸手势并通过回调暴露给控制层。
 */
public class FloatingIndicatorView extends FrameLayout {

    public interface GestureCallback {
        void onDrag(float xInScreen, float yInScreen, float xInView, float yInView);

        void onTap();
    }

    private final float touchSlop;
    private GestureCallback gestureCallback;

    private float xInView;
    private float yInView;
    private float downXInScreen;
    private float downYInScreen;

    public FloatingIndicatorView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        initContent(context);
    }

    private void initContent(Context context) {
        ImageView indicator = new ImageView(context);
        indicator.setImageResource(R.mipmap.ic_launcher);
        addView(indicator);
    }

    public void setGestureCallback(GestureCallback gestureCallback) {
        this.gestureCallback = gestureCallback;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                xInView = event.getX();
                yInView = event.getY();
                downXInScreen = event.getRawX();
                downYInScreen = event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (gestureCallback != null) {
                    gestureCallback.onDrag(event.getRawX(), event.getRawY(), xInView, yInView);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (gestureCallback != null
                        && Math.abs(downXInScreen - event.getRawX()) <= touchSlop
                        && Math.abs(downYInScreen - event.getRawY()) <= touchSlop) {
                    gestureCallback.onTap();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }
}

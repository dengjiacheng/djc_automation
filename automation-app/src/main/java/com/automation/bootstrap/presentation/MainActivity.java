package com.automation.bootstrap.presentation;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.automation.bootstrap.presentation.info.AppInfoProvider;

/**
 * Android Automation - 主应用说明页面
 * 此应用仅作为测试框架的目标包
 */
public class MainActivity extends Activity {

    private final AppInfoProvider infoProvider = new AppInfoProvider();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 创建布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.WHITE);
        layout.setPadding(40, 60, 40, 40);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        // 标题
        TextView titleView = new TextView(this);
        titleView.setText("Android Automation Framework");
        titleView.setTextSize(24);
        titleView.setTextColor(Color.parseColor("#2196F3"));
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, 30);

        // 说明文本
        TextView infoView = new TextView(this);
        infoView.setText(infoProvider.getInfoText());
        infoView.setTextSize(16);
        infoView.setTextColor(Color.parseColor("#424242"));
        infoView.setLineSpacing(8, 1.0f);
        infoView.setMovementMethod(new ScrollingMovementMethod());

        // 添加到布局
        layout.addView(titleView);
        layout.addView(infoView);

        setContentView(layout);
    }
}

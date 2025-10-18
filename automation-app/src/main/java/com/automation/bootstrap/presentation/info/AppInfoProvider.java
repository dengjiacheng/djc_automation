package com.automation.bootstrap.presentation.info;

/**
 * 提供主界面展示的项目信息文本。
 * 单独抽离便于未来接入远端动态内容或多语言支持。
 */
public class AppInfoProvider {

    public String getInfoText() {
        return "📱 项目概览\n\n" +
                "• 主应用 (com.automation)\n" +
                "  → 仅作为目标包存在，便于 instrumentation 运行\n" +
                "  → 当前页面展示架构及部署说明\n\n" +
                "• 测试应用 (androidTest)\n" +
                "  → AutomationController + CommandExecutionEngine 负责执行指令\n" +
                "  → 内置 WebSocket 客户端、截图、应用管理、图像识别能力\n\n" +
                "• 认证应用 (auth-app)\n" +
                "  → 登录服务器获取 wsUrl\n" +
                "  → 一键启动 / 停止 instrumentation\n\n" +
                "🚀 使用方式\n\n" +
                "1. 打开 auth-app 并填写服务器信息\n" +
                "2. 点击“登录&启动”触发 automation-app 的测试包\n" +
                "3. 通过 WebSocket 下发指令，AutomationController 实时执行\n" +
                "4. 需要结束时点击“停止”\n\n" +
                "📂 代码入口\n\n" +
                "automation-app/src/androidTest/java/com/automation/application/runtime\n\n" +
                "版本: 1.0.0\n" +
                "包名: com.automation";
    }
}

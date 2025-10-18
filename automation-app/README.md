+# Automation App
+
+Instrumentation target used together with `auth-app` for remote UI automation.
+
+## 模块划分
+
+```
+automation-app/
+└── src/
+    ├── main/java/com/automation/MainActivity.java          # 说明页
+    └── androidTest/java/com/automation
+        ├── runtime/AutomationController.java               # 生命周期与连接管理
+        ├── runtime/CommandExecutionEngine.java                   # 指令路由 + 执行
+        ├── runtime/net/AutomationWebSocketClient.java      # WebSocket 客户端
+        ├── runtime/net/AutomationMessage.java              # WebSocket 消息
+        ├── runtime/net/AuthService.java                    # 认证信息存储
+        ├── runtime/system/NetworkInspector.java            # 网络工具
+        ├── enhanced/system/*.java                          # 截图、剪贴板、应用管理
+        ├── enhanced/gesture/TouchController.java
+        ├── enhanced/vision/ImageRecognition.java           # OpenCV 模板匹配
+        └── test/AutomationTestSuite.java                   # Instrumentation 入口
+```
+
+## 流程概述
+
+1. `auth-app` 登录服务器，拿到 `wsUrl` 后通过 `am instrument` 启动测试包。
+2. `AutomationTestSuite` 构建 `AutomationController` 并发起 WebSocket 连接。
+3. `AutomationWebSocketClient` 维持心跳、分发消息、处理重连。
+4. `CommandExecutionEngine` 执行指令并通过 WebSocket 回传执行结果。
+
+## 支持指令（节选）
+
+| 指令 | 说明 |
+|------|------|
+| `click` | 点击指定坐标 |
+| `swipe` | 按轨迹滑动 |
+| `screenshot` | 截图 → JPEG → GZip → Base64 |
+| `dump_hierarchy` | 导出当前窗口层级 |
+| `launch_app` / `stop_app` / `clear_app` | 应用生命周期控制 |
+| `set_clipboard` / `get_clipboard` | 剪贴板操作 |
+| `press_key` / `press_back` / `press_home` | 按键事件 |
+| `get_battery` | 查询电量状态 |
+| `find_template` / `compare_images` | OpenCV 图像能力 |
+
+> `screen_rotation`、`input_text` 当前返回占位结果，可按需扩展。
+
+## 构建
+
+```bash
+./gradlew automation-app:assembleDebug
+./gradlew automation-app:assembleDebugAndroidTest
+```
+
+## 启动示例
+
+```bash
+adb install automation-app/build/outputs/apk/debug/automation-app-debug.apk
+adb install automation-app/build/outputs/apk/androidTest/debug/automation-app-debug-androidTest.apk
+adb shell am instrument -w \
+  -e wsUrl "ws://SERVER/device?token=xxx" \
+  com.automation.test/androidx.test.runner.AndroidJUnitRunner
+```
+
+## 依赖
+
+- `androidx.test.uiautomator:uiautomator`
+- `androidx.test.ext:junit`, `androidx.test:runner`, `androidx.test:rules`
+- `com.squareup.okhttp3:okhttp`
+- `:opencv` 模块（提供 `ImageRecognition` 使用的 OpenCV）
+
+## 注意
+
+- 设备需允许 root，以便 `auth-app` 通过 `su` 启动 / 停止测试。
+- `AutomationController` 会缓存最近一次 `wsUrl`，便于调试重新连接。

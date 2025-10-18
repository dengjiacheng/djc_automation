# 测试 APK 仓库说明

服务器会在运行时读取同目录下的 `manifest.json` 来获取需要分发给设备的“主应用 + 测试 APK”组合。示例如 `manifest.example.json`，请按以下步骤准备文件：

1. 将编译好的 **主应用 APK**（例如 `automation-app-debug.apk`）和 **测试 APK**（例如 `automation-app-debug-androidTest.apk`）放入本目录。
2. 复制一份 `manifest.example.json` 为 `manifest.json`，并按如下字段填写：
   - `version`：人类可读的版本号。
   - `version_code`：用于比较大小的整数版本号，越大代表越新版本。
   - `app.file` / `test.file`：分别填写主应用、测试 APK 的文件名。
   - `app.package_name` / `test.package_name`：可选，填入对应包名（便于设备端卸载/安装）。
   - `app.version_code` / `test.version_code` 与 `version_name`：可选，用于记录各自版本信息；服务器在上传时也会自动解析并填充。
   - `app.checksum_sha256` / `test.checksum_sha256`：可选，但推荐写入 SHA-256 校验值，便于完整性验证。
   - `created_at`：可选，记录发布时间（ISO8601 格式）。
3. 如果存在多个版本，可在 `artifacts` 数组中追加条目；服务端会自动选择 `version_code` 最大的作为当前最新版本。

更新套件时，只需替换 APK 文件并更新 `manifest.json` 对应条目。通过后台上传时，服务器会自动读取 APK 的包名与版本号；设备端在认证后也会自动检测版本号，必要时卸载旧包并安装新的主应用与测试 APK。

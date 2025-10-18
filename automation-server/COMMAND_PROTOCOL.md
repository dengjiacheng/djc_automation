# 指令协议规范 v2.0

## 1. WebSocket 消息格式

所有 WebSocket 消息使用统一的 JSON 格式：

```json
{
    "type": "message_type",
    "data": { ... }
}
```

## 2. 登录流程

设备通过 HTTP 调用 `/api/auth/device/login` 获取访问令牌、WebSocket 地址和 `device_id`：

```json
POST /api/auth/device/login
{
  "username": "device_account",
  "password": "******"
}

响应:
{
  "access_token": "...",
  "token_type": "bearer",
  "device_id": "device-uuid",
  "ws_url": "ws://SERVER/ws?token=..."
}
```

测试 APK 需将 `wsUrl` 和 `deviceId` 作为 Instrumentation 参数传入，连接 WebSocket 后首条消息即发送 `device_info`（见 5.1）。

## 3. 指令消息格式

### 服务端 → 客户端（指令）

```json
{
    "type": "command",
    "data": {
        "command_id": "uuid",    // 指令ID（用于响应关联，统一使用command_id）
        "action": "action_name", // 动作名称
        "params": { ... },       // 动作参数（统一使用params）
        "user_id": "uuid",       // 发起指令的用户ID（用于结果路由）
        "device_id": "uuid"      // 目标设备ID
    }
}
```

### 客户端 → 服务端（响应）

```json
{
    "type": "result",
    "data": {
        "command_id": "uuid",    // 对应的指令ID
        "status": "success",     // 执行状态 (success | failed)
        "result": "...",         // 成功时的结果数据（通常是JSON字符串）
        "error_message": null,   // 失败时的错误信息
        "user_id": "uuid",       // 用于路由给对应Web用户
        "device_id": "uuid"      // 执行设备ID
    }
}
```

## 4. 支持的指令列表

### 4.1 点击操作
```json
{
    "action": "click",
    "params": {
        "x": 100,
        "y": 200
    }
}
```

### 4.2 滑动操作
```json
{
    "action": "swipe",
    "params": {
        "startX": 100,
        "startY": 500,
        "endX": 100,
        "endY": 200,
        "steps": 50
    }
}
```

### 4.3 输入文本
```json
{
    "action": "input",
    "params": {
        "text": "hello world"
    }
}
```

### 4.4 按键操作
```json
{
    "action": "press_key",
    "params": {
        "keycode": 3  // Android KeyEvent常量数值
    }
}
```

### 4.5 截图（使用minicap）
```json
{
    "action": "screenshot",
    "params": {
        "quality": 80            // JPEG质量 (1-100)
    }
}
```

**响应格式：**
设备在 `result` 字段中返回如下 JSON 字符串：
```json
{
  "image": "H4sIAAAAAAAA....",      // Base64编码（默认GZIP压缩）
  "original_size": 45678,         // 原始JPEG字节数
  "compressed_size": 12345,       // 压缩后字节数
  "quality": 80,
  "format": "jpeg",
  "gzipped": true
}
```

### 4.6 Dump界面层级
```json
{
    "action": "dump_hierarchy",
    "params": {
        "compress": true  // 是否压缩XML
    }
}
```

**响应格式：**
设备在 `result` 字段中返回如下 JSON 字符串：
```json
{
  "data": "H4sIAAAAAAAA....",      // Base64数据，可能压缩
  "original_size": 32100,
  "compressed_size": 8450,
  "compressed": true
}
```

### 4.7 获取当前Activity
```json
{
    "action": "get_current_activity",
    "params": {}
}
```

**响应格式：**
设备在 `result` 字段中返回如下 JSON 字符串：
```json
{
  "package": "com.example.app",
  "activity": "com.example.app.MainActivity"
}
```

### 4.8 启动应用
```json
{
    "action": "launch_app",
    "params": {
        "package": "com.example.app"
    }
}
```

### 4.9 停止应用
```json
{
    "action": "stop_app",
    "params": {
        "package": "com.example.app"
    }
}
```

### 4.10 执行Shell命令
```json
{
    "action": "shell",
    "params": {
        "command": "input tap 100 200"
    }
}
```

### 4.11 清除应用数据
```json
{
    "action": "clear_app",
    "params": {
        "package": "com.example.app"
    }
}
```

### 4.12 剪贴板操作
```json
{
    "action": "set_clipboard",
    "params": {
        "text": "hello",
        "label": "automation"
    }
}
```

```json
{
    "action": "get_clipboard",
    "params": {}
}
```

### 4.13 模板匹配 / 图片比对
```json
{
    "action": "find_template",
    "params": {
        "screenshot": "/sdcard/screen.png",
        "template": "/sdcard/template.png",
        "threshold": 0.85
    }
}
```

```json
{
    "action": "compare_images",
    "params": {
        "image1": "/sdcard/img1.png",
        "image2": "/sdcard/img2.png"
    }
}
```

### 4.14 启动预置任务
```json
{
    "action": "start_task",
    "params": {
        "task_name": "example"
    }
}
```

## 5. 其他消息类型

### 5.1 设备信息上报
```json
{
    "type": "device_info",
    "data": {
        "device_id": "device-uuid",
        "device_name": "Pixel 5",
        "device_model": "redfin",
        "android_version": "13",
        "local_ip": "192.168.1.100",
        "capabilities": [
            {
                "action": "click",
                "description": "点击指定坐标",
                "params": [
                    {"name": "x", "type": "int", "required": true},
                    {"name": "y", "type": "int", "required": true}
                ]
            }
        ]
    }
}
```

### 5.2 心跳
```json
{
    "type": "heartbeat",
    "data": {
        "battery": 85,
        "network_type": "WiFi",
        "current_task": "idle"
    }
}
```

### 5.3 欢迎消息
```json
{
    "type": "welcome",
    "data": {}
}
```

## 6. 错误处理

客户端执行失败时，应返回 `status: "failed"` 并填写 `error_message`，其余字段同「指令响应」。

## 7. 数据压缩

对于截图和dump等大数据传输，使用 gzip 压缩：
1. 客户端压缩数据
2. Base64编码后传输
3. 服务端解码和解压

压缩标志：`compress: true`

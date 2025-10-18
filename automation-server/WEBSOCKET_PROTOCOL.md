# WebSocket 通信协议规范 v2.0

## 设计原则

1. **统一格式**：所有消息使用 `{type: string, data: object}` 结构
2. **可追踪性**：指令和响应包含 `user_id` 和 `device_id` 用于追踪
3. **可扩展性**：使用 JSON 格式便于添加新字段
4. **向后兼容**：参数使用 `params` 而非 `payload`

## 1. 基础消息格式

所有 WebSocket 消息的通用结构：

```json
{
  "type": "message_type",
  "data": { ... }
}
```

## 2. 认证与连接

### 2.1 HTTP 登录 (POST /api/auth/device/login)

**请求：**
```json
{
  "username": "device_android_001",
  "password": "password123",
  "device_name": "Pixel 5",
  "device_model": "redfin",
  "android_version": "13",
  "local_ip": "192.168.1.100"
}
```

**响应：**
```json
{
  "access_token": "eyJhbGci...",
  "token_type": "bearer",
  "device_id": "",
  "ws_url": "ws://server/ws?token=eyJhbGci..."
}
```

### 2.2 WebSocket 连接

客户端使用 `ws_url` 建立连接，连接后**首条消息必须是 device_info**。

### 2.3 设备信息与能力上报 (客户端 → 服务端)

**类型：** `device_info`

```json
{
  "type": "device_info",
  "data": {
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
      },
      {
        "action": "hd_order",
        "description": "HD 脚本下单",
        "params": [
          {"name": "task_name", "type": "string", "required": true},
          {"name": "config", "type": "object", "required": false}
        ]
      }
    ]
  }
}
```

服务端收到后校验 `device_id`（登录阶段已分配），同步能力列表并更新设备信息。

### 2.4 欢迎消息 (服务端 → 客户端)

**类型：** `welcome`

```json
{
  "type": "welcome",
  "data": {}
}
```

客户端应在登录阶段获取 `device_id`，欢迎消息仅表示握手成功。

## 3. 指令协议

### 3.1 指令消息 (服务端 → 客户端)

**类型：** `command`

**格式：**
```json
{
  "type": "command",
  "data": {
    "command_id": "cmd-uuid-123",
    "action": "action_name",
    "params": { ... },
    "user_id": "admin-uuid",
    "device_id": "device-uuid"
  }
}
```

**字段说明：**
- `command_id`: 指令唯一标识符（UUID），用于结果关联
- `action`: 操作类型（见下方支持的指令列表）
- `params`: 操作参数（统一使用 params）
- `user_id`: 发起指令的用户 ID（用于结果路由）
- `device_id`: 目标设备 ID

### 3.2 指令响应 (客户端 → 服务端)

**类型：** `result`

**格式：**
```json
{
  "type": "result",
  "data": {
    "command_id": "cmd-uuid-123",
    "status": "success",
    "result": "...",
    "error_message": null,
    "user_id": "admin-uuid",
    "device_id": "device-uuid"
  }
}
```

**字段说明：**
- `command_id`: 对应的指令 ID
- `status`: 执行状态 (`success` | `failed`)
- `result`: 成功时的结果数据（可选）
- `error_message`: 失败时的错误信息（可选）
- `user_id`: 原指令发起者 ID（用于路由）
- `device_id`: 执行设备 ID

## 4. 支持的指令

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

### 4.4 截图

```json
{
  "action": "screenshot",
  "params": {
    "quality": 80
  }
}
```

**响应：** 设备在 `result` 字段中返回如下 JSON 字符串：
```json
{
  "image": "H4sIAAAAAAAA....",
  "original_size": 45678,
  "compressed_size": 12345,
  "quality": 80,
  "format": "jpeg",
  "gzipped": true
}
```

### 3.3 指令进度 (客户端 → 服务端)

**类型：** `progress`

```json
{
  "type": "progress",
  "data": {
    "command_id": "cmd-uuid-123",
    "stage": "scene.match",
    "message": "匹配到 search_result 场景",
    "percent": 40,
    "status": "running",
    "extra": {"scene_id": "search_result"},
    "user_id": "admin-uuid",
    "device_id": "device-uuid"
  }
}
```

客户端可以在执行过程中多次发送 `progress` 消息，上层会将其转发给 Web 控制台。执行完成后仍需发送最终的 `result` 消息。

### 4.5 Dump 界面层级

```json
{
  "action": "dump_hierarchy",
  "params": {}
}
```

**响应：** 设备在 `result` 字段中返回如下 JSON 字符串：
```json
{
  "data": "H4sIAAAAAAAA....",
  "original_size": 32100,
  "compressed_size": 8450,
  "compressed": true
}
```

### 4.6 按键操作

```json
{
  "action": "press_key",
  "params": {
    "keycode": 3
  }
}
```

### 4.7 获取当前 Activity

```json
{
  "action": "get_current_activity",
  "params": {}
}
```

**响应：** 设备在 `result` 字段中返回如下 JSON 字符串：
```json
{
  "package": "com.example.app",
  "activity": "MainActivity"
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

### 4.10 清除应用数据

```json
{
  "action": "clear_app",
  "params": {
    "package": "com.example.app"
  }
}
```

### 4.11 剪贴板

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

### 4.12 图片处理

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

### 4.13 任务调度

```json
{
  "action": "start_task",
  "params": {
    "task_name": "example"
  }
}
```

### 4.10 执行 Shell 命令

```json
{
  "action": "shell",
  "params": {
    "command": "input tap 100 200"
  }
}
```

## 5. 心跳协议

### 5.1 心跳消息 (客户端 → 服务端)

**类型：** `heartbeat`

**频率：** 每 30 秒

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

**字段说明：**
- `battery`: 电池电量百分比 (0-100)
- `network_type`: 网络类型 (`WiFi` | `Mobile` | `Offline`)
- `current_task`: 当前任务状态

## 6. 日志上报

### 6.1 日志消息 (客户端 → 服务端)

**类型：** `log`

```json
{
  "type": "log",
  "data": {
    "type": "info",
    "message": "任务执行成功",
    "timestamp": "2025-10-14T00:40:23Z"
  }
}
```

**日志级别：**
- `info`: 信息日志
- `warning`: 警告日志
- `error`: 错误日志

## 7. Ping/Pong

### 7.1 Ping (服务端 → 客户端)

```json
{
  "type": "ping",
  "data": null
}
```

### 7.2 Pong (客户端 → 服务端)

```json
{
  "type": "pong",
  "data": null
}
```

## 8. 错误处理

### 8.1 连接错误

- **1003**: 首条消息不是 device_info
- **1008**: Token 验证失败

### 8.2 指令执行错误

客户端返回 `status: "failed"` 并填写 `error_message`。

## 9. 数据编码

### 9.1 图片数据

- 格式：Base64 编码的 JPEG
- 前缀：`data:image/jpeg;base64,`

### 9.2 XML 数据

- 格式：UTF-8 字符串
- 可选 GZIP 压缩（Base64 编码）

## 10. 版本说明

- **v2.0 (当前版本)**: 统一使用 `params` 字段，不兼容旧版本

## 11. 实现检查清单

### 服务端
- [ ] 指令消息使用 `params` 字段
- [ ] 指令消息包含 `user_id` 和 `device_id`
- [ ] CommandResultUpdate Schema 包含 `user_id` 和 `device_id`
- [ ] 结果转发到正确的 Web 用户

### 客户端
- [x] 连接后首条消息发送 device_info
- [x] 解析指令使用 `params` 字段
- [x] 结果上报包含 `user_id` 和 `device_id`
- [x] 登录响应返回 device_id

## 12. 示例：完整指令流程

### 步骤 1: Web 用户发起截图指令

Web → 服务端 HTTP POST:
```json
POST /api/commands
{
  "device_id": "device-uuid",
  "action": "screenshot",
  "params": {"quality": 80}
}
```

### 步骤 2: 服务端下发指令

服务端 → 设备 WebSocket:
```json
{
  "type": "command",
  "data": {
    "id": "cmd-123",
    "action": "screenshot",
    "params": {"quality": 80},
    "user_id": "web-user-uuid",
    "device_id": "device-uuid"
  }
}
```

### 步骤 3: 设备执行并上报结果

设备 → 服务端 WebSocket:
```json
{
  "type": "result",
  "data": {
    "command_id": "cmd-123",
    "status": "success",
    "result": "data:image/jpeg;base64,/9j/...",
    "error_message": null,
    "user_id": "web-user-uuid",
    "device_id": "device-uuid"
  }
}
```

### 步骤 4: 服务端转发结果给 Web 用户

服务端 → Web WebSocket:
```json
{
  "type": "command_result",
  "data": {
    "command_id": "cmd-123",
    "status": "success",
    "result": "data:image/jpeg;base64,/9j/...",
    "device_id": "device-uuid"
  }
}
```

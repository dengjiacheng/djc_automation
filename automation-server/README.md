# Android Automation Server

Android 设备远程控制服务端 - 基于 FastAPI + WebSocket

## 功能特性

- ✅ 设备登录认证(JWT)
- ✅ WebSocket 实时双向通信
- ✅ 设备在线状态管理
- ✅ 指令下发和结果接收
- ✅ 心跳检测和自动重连
- ✅ RESTful API 管理后台
- ✅ 完整的日志记录

## 技术栈

- **FastAPI** - 现代化 Web 框架
- **WebSocket** - 实时通信
- **SQLAlchemy** - ORM 数据库
- **JWT** - 身份认证
- **SQLite** - 默认数据库(可换为 PostgreSQL)

## 架构设计

```
app/
├── core/                # 配置、认证工具
├── db/                  # 引擎、Session 与 ORM 模型
├── api/
│   └── routers/         # 模块化路由 (auth/devices/commands/admin/websocket)
├── services/            # 业务逻辑分层
├── schemas/             # Pydantic 请求/响应模型
├── websocket/           # WebSocket 连接管理
└── web/                 # 前端模板与静态资源
```

## 快速开始

### 1. 安装依赖

```bash
# 创建虚拟环境
python3 -m venv venv
source venv/bin/activate

# 安装依赖
pip install -r requirements.txt
```

### 2. 配置环境变量

复制 `.env.example` 到 `.env` 并修改配置:

```bash
cp .env.example .env
```

**重要**: 修改 `SECRET_KEY` 为随机密钥:

```bash
python3 -c "import secrets; print(secrets.token_urlsafe(32))"
```

### 3. 启动服务

```bash
# 方式 1: 使用启动脚本
./start.sh

# 方式 2: 直接运行
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

服务启动后访问:
- API 文档: http://localhost:8000/docs
- 健康检查: http://localhost:8000/health

## API 接口说明

### 1. 设备登录

**POST** `/api/auth/device/login`

请求体:
```json
{
  "username": "device001",
  "password": "password123",
  "device_name": "Xiaomi 13",
  "device_model": "2211133C",
  "android_version": "13"
}
```

响应:
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "bearer",
  "device_id": "uuid-here",
  "ws_url": "ws://localhost:8000/ws?token=..."
}
```

### 2. 设备注册

**POST** `/api/auth/register`

请求体:
```json
{
  "username": "device001",
  "password": "password123"
}
```

### 3. 下发指令

**POST** `/api/commands/{device_id}`

请求体:
```json
{
  "action": "click",
  "params": {
    "x": 500,
    "y": 1000
  }
}
```

支持的指令类型:
- `click` - 点击屏幕
- `swipe` - 滑动
- `screenshot` - 截图
- `start_task` - 启动任务
- `input_text` - 输入文本

### 4. 获取设备列表

**GET** `/api/devices?online_only=true`

### 5. WebSocket 连接

**WS** `/ws?token=<JWT_TOKEN>`

消息格式:

**心跳消息** (设备 → 服务器):
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

**指令消息** (服务器 → 设备):
```json
{
  "type": "command",
  "data": {
    "id": "command-uuid",
    "action": "click",
    "payload": {"x": 500, "y": 1000}
  }
}
```

**结果消息** (设备 → 服务器):
```json
{
  "type": "result",
  "data": {
    "command_id": "command-uuid",
    "status": "success",
    "result": "已点击坐标 (500, 1000)"
  }
}
```

## 数据库

默认使用 SQLite,生产环境建议使用 PostgreSQL:

```env
DATABASE_URL=postgresql+asyncpg://user:password@localhost/automation
```

查看数据库结构:
```bash
sqlite3 automation.db ".schema"
```

## 开发调试

### 查看日志

```bash
tail -f logs/server.log
```

### 测试 API

使用内置的 Swagger 文档:
http://localhost:8000/docs

或使用 curl:
```bash
# 注册设备
curl -X POST http://localhost:8000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test001","password":"123456"}'

# 登录
curl -X POST http://localhost:8000/api/auth/device/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test001","password":"123456"}'
```

### 测试 WebSocket

使用 wscat:
```bash
npm install -g wscat
wscat -c "ws://localhost:8000/ws?token=YOUR_TOKEN"
```

## 部署

### Docker 部署(推荐)

```bash
# 构建镜像
docker build -t android-automation-server .

# 运行容器
docker run -d \
  --name automation-server \
  -p 8000:8000 \
  -v $(pwd)/data:/app/data \
  android-automation-server
```

### 生产环境配置

1. **使用 PostgreSQL** 替代 SQLite
2. **设置强密钥** SECRET_KEY
3. **启用 HTTPS** (使用 Nginx 反向代理)
4. **限制 CORS** 允许的域名
5. **配置防火墙** 只开放必要端口

## 故障排查

### 问题 1: WebSocket 连接失败

检查:
- Token 是否有效
- 服务器地址是否正确
- 防火墙是否开放 8000 端口

### 问题 2: 设备离线

检查:
- 网络连接是否正常
- 心跳是否正常发送
- 服务器日志中是否有错误

### 问题 3: 指令无法下发

检查:
- 设备是否在线 (调用 /api/devices 查看)
- WebSocket 连接是否建立
- 日志中是否有错误信息

## 许可证

MIT License

## 联系方式

- Issues: https://github.com/your-repo/issues

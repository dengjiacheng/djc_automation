# 仓库总览与协作手册

本手册集中说明 `djc_automation` 仓库内所有子项目、它们之间的协作关系、脚本能力与模板的生成逻辑，以及日常构建、部署与运维要点。目标是让新成员或后续维护者无需再次通读代码，就能快速理解整体架构并完成常见操作。

## 1. 仓库结构与系统划分

```
djc_automation
├── automation-server        # FastAPI 服务端，REST + WebSocket
├── automation-app           # 自动化测试 APK（Instrumentation 包）
├── auth-app                 # 设备端控制台 APK（登录 + 启停自动化）
├── opencv                   # OpenCV Android 原生依赖子模块
├── scripts                  # CLI 脚本（构建 / 上传 APK 套件）
├── gradle.* / settings.gradle
├── w_build_and_upload.sh    # 封装脚本（代理调用 scripts/build_and_upload.py）
└── PROJECT_OVERVIEW.md      # 当前文档
```

系统由三端协作构成：

1. **Automation Server**：提供账号体系、设备管理、脚本与指令调度、APK 分发、日志采集。核心框架为 FastAPI + SQLAlchemy + WebSocket。
2. **Auth App**：运行在被控 Android 设备上的登录与控制台界面。负责输入服务端地址、账号密码、触发自动化测试包的安装 / 启动、监控运行状态。
3. **Automation App（Instrumentation）**：真正执行自动化指令的测试 APK。与服务器建立 WebSocket 长连接、上报能力描述、执行脚本，并回传进度与结果。

此外，`opencv` 子模块提供了图像识别所需的 JNI 库，`scripts` 目录下的 Python 脚本协助构建并上传最新的 APK 套件。

### 1.1 组件关系图

```
┌────────────────────┐               ┌─────────────────────────┐
│    Automation      │  REST / WS    │     Automation Server    │
│        App         │◀────────────▶│  FastAPI + WebSocket      │
│ (Instrumentation)  │               │  - 账号 / 设备 / 指令        │
└────────▲───────────┘               │  - 脚本模板 / 日志 / APK     │
         │                           └─────────▲────────────────┘
         │ adb instrument 启动                      │ 浏览器 / 运维入口
         │                           ┌──────────────┴──────────────┐
┌────────┴──────────┐               │     Web Console / API Client │
│      Auth App      │──────────────▶│（管理员或普通用户界面，待集成）│
│ 登录 + 启停测试包 │  REST (登录)   └───────────────────────────────┘
└───────────────────┘
```

## 2. 子项目详解

### 2.1 automation-server

- **入口**：`automation-server/main.py` 使用 `uvicorn app.main:app` 启动。
- **配置**：`app/core/config.py` 通过 `pydantic-settings` 读取 `.env`。可配置的模块包括数据库、JWT 密钥、WebSocket 心跳、APK 存储路径等。
- **分层**：
  - `app/api/routers`：REST & WebSocket 路由。主要模块：
    - `auth.py`：设备 / 管理员登录、注册。
    - `admin.py`：管理员查看设备 / 用户、下发指令。
    - `devices.py`：面向设备的查询接口。
    - `commands.py`：普通用户指令。
    - `apk.py`：测试 APK 上传、分发。
    - `websocket.py`：设备长连接、能力上报、指令结果回写。
  - `app/domain`：领域服务（账号、设备、指令、日志等），封装业务规则。
  - `app/infrastructure/database/repositories`：SQLAlchemy 仓储。
  - `app/websocket/manager.py`：维护在线设备与 Web 客户端连接、心跳与能力缓存。
  - `app/services/test_apk.py`：APK 套件的持久化、校验、下载链接生成。
- **数据库**：`app/db/models.py` 定义四张主表：
  - `accounts`：账号（admin / user / super_admin）。
  - `devices`：设备元数据、在线状态、最近心跳。
  - `commands`：指令生命周期（pending/sent/completed）。
  - `device_logs`：运行日志与脚本进度事件。
- **静态资源**：`app/web/templates` 提供 Jinja 模板，可直接渲染登录 / 控制页面；`frontend/` 保留 Vue 3 项目的源码（目前作为后续 SPA 重构的入口）。
- **脚本能力缓存**：`ConnectionManager` 中的 `device_capabilities` 字典按设备 ID 缓存最新的能力描述（由设备上报）。`/api/admin/devices/{id}/capabilities` 提供查询接口。

### 2.2 auth-app

- **主界面**：`AutomationControlActivity`（`auth-app/src/main/java/com/automation/auth/`）提供服务器地址、账号密码输入、日志窗口、启停按钮。
- **关键组件**：
  - `DeviceAuthManager`：基于 OkHttp 的设备登录，支持复用上次保存的 `device_id` 与 `ws_url`。
  - `TestApkClient`：调用服务端 `/api/apk/test/latest`，比较 SHA256，按需下载并安装主 APK / 测试 APK。
  - ADB 命令执行、APK 安装和 Instrumentation 启动逻辑封装在 `automation-app` 工具类中。
- **缓存**：SharedPreferences 保存服务器地址、账号、密码、APK SHA256 以及最近一次的设备 ID。

### 2.3 automation-app（Instrumentation）

- **入口**：`com.automation.bootstrap.instrumentation.AutomationTestSuite` 负责初始化并启动 `AutomationController`。
- **核心类**：
  - `AutomationController`：维持 WebSocket 连接、管理指令执行队列、互斥脚本运行、处理结果回传。
  - `CommandExecutionEngine`：注册所有指令模块（设备交互、文本输入、截图、应用管理、场景脚本等）。
  - `ScenarioTaskService`：组合 `ScenarioCatalog`、`ScenarioParameterBinder`、`ScenarioRunCoordinator`，将脚本元数据转换为 `start_task` 指令与具体的脚本执行。
  - `AutomationWebSocketClient`：基于 OkHttp WebSocket 的客户端，提供断线重连、心跳、消息分发。
  - `ImageRecognition` / `VisionToolkit`：通过 `opencv` 模块实现模板匹配与截图比对。
- **脚本资源**：位于 `automation-app/src/androidTest/assets/scripts/<task_name>/`，例如 `dhgate_order_v2`。`project.yaml` 描述脚本元数据与参数，`scenes.yaml` 描述场景签名及处理器。
- **能力上报**：
  - `CommandRegistry.capabilitiesAsJson()` 生成统一结构：
    ```json
    {
      "action": "start_task",
      "description": "启动脚本任务",
      "params": [...],
      "meta": {
        "scripts": [
          {
            "name": "dhgate_order_v2",
            "version": "3.0.4",
            "description": "...",
            "parameters": [
              {"name": "search_keyword", "type": "string", "required": true, "default": "..."},
              ...
            ]
          }
        ]
      }
    }
    ```
  - 此 JSON 在设备连接 WebSocket 后通过 `session_init` 消息附带到服务器。

### 2.4 opencv

- 提供 OpenCV Android SDK 的拆分模块（`java/` + `native/`）。`automation-app` 的 `VisionCommandModule` / `ImageRecognition` 使用此模块进行模板匹配。
- 构建配置在 `opencv/build.gradle`，默认以 AAR 形式依赖。

### 2.5 scripts

- `build_and_upload.py`：串联 Gradle 编译 + APK 套件上传。
  1. 执行 `automation-app:assembleDebug` 与 `automation-app:assembleDebugAndroidTest`。
  2. 调用 `/api/auth/admin/login` 获取 token。
  3. 上传 APK 至 `/api/apk/test/upload`，校验包名必须分别为 `com.automation` 与 `com.automation.test`。
- `w_build_and_upload.sh`：简单封装，方便在 CI 或终端执行。

## 3. 关键业务流程

### 3.1 账号与 APK 分发

1. 管理员通过 `/api/admin/login` 获取后台 token。
2. 使用 `/api/admin/devices` 创建或查看设备账号。创建时同时在 `accounts` 表生成角色为 `user` 的账号。
3. 构建新版本 APK 后执行 `python scripts/build_and_upload.py --server <host> --username admin --password ***` 上传。服务端会保留最近版本并生成可下载链接。
4. 设备上的 `auth-app` 登录后自动比较 SHA256，若有更新则下载并安装主 APK 与测试 APK。

### 3.2 设备接入与能力上报

1. `auth-app` 调用 `/api/auth/device/login`，服务端校验账号后返回 `access_token`、`device_id` 与 `ws_url`。
2. `auth-app` 通过 `adb shell am instrument` 启动 `AutomationTestSuite` 并传递 `wsUrl`、`deviceId`。
3. `AutomationController` 连接 WebSocket，首条消息发送 `session_init`：
   ```json
   {
     "type": "session_init",
     "data": {
       "device_id": "<uuid>",
       "device_name": "...",
       "device_model": "...",
       "android_version": "...",
       "local_ip": "...",
       "capabilities": [ { "action": "click", "params": [...] }, ... ]
     }
   }
   ```
4. 服务器将设备信息写入数据库、缓存连接、保存能力列表，返回 `session_ready`。
5. 后端日志通过 `device_logs` 记录设备上线、能力上报等事件。

### 3.3 指令下发与结果回传

1. 管理员调用 `/api/admin/commands`（或未来的普通用户接口）下发指令，服务端写入 `commands` 表并通过 WebSocket 推送。
2. 设备执行完成后发送：
   ```json
   {
     "type": "result",
     "data": {
       "command_id": "...",
       "status": "success" | "failed",
       "result": "{...}",
       "error_message": null,
       "user_id": "...",
       "device_id": "...",
       "action": "start_task"
     }
   }
   ```
3. 服务器更新 `commands` 表状态、写入日志，并可通过 `manager.send_to_web()` 推送给在线的网页客户端。
4. 过程中，设备可持续发送 `heartbeat` 和 `progress` 消息，分别刷新在线状态与脚本进度。

### 3.4 脚本模板生成与填参

1. 设备端的 `ScenarioTaskService` 会为每个脚本注册 `start_task:<script_name>` 能力，并在 `meta` 字段附带脚本参数规格。
2. 服务端可通过 `/api/admin/devices/{device_id}/capabilities` 获取最新能力，`meta.scripts` 内含如下字段：
   - `name`、`version`、`description`
   - 每个参数的 `name`、`type`、`required`、`default`、`description`
3. 推荐的模板生成流程：
   - 后端在能力缓存中读取 `start_task` 的 `meta.scripts`。
   - 对于每个脚本，构造一份默认模板：
     ```json
     {
       "task_name": "dhgate_order_v2",
       "config": {
         "search_keyword": "<默认值>",
         "target_product_title": "<默认值>",
         "product_image": { "value": null, "source": "base64", "mime": "image/png" },
         ...
       }
     }
     ```
   - 将模板保存在数据库或 Redis，前端选择设备时即可直接展示可填写的字段，而无需再次解析 APK。
   - 在执行脚本时，将模板合并用户输入后调用 `/api/admin/commands`，指令 `action` 为 `start_task`，`params` 为最终 JSON。
4. 若需支持 **多个设备同时执行**：
   - 当前 REST 接口针对单设备。可在业务层循环调用 `admin_send_command`，或扩展一个批量接口（接收设备 ID 列表，逐个写入 `commands` 表并推送，成功 / 失败分别返回）。
   - 建议在推送前校验所有目标设备是否在线，并复用同一个模板，避免每台设备重新填参。

### 3.5 WebSocket 消息协议

**握手与生命周期**
- Instrumentation 首次连线时发送 `session_init`，携带 `device_id`（可选）、设备信息与 `capabilities`。
- 服务端在 `websocket.py` 校验账号 → 调 `DeviceService.ensure_device_for_connection` 写库 → 回复 `session_ready` 并缓存能力。
- 管理端经 `/api/admin/commands` 下发 `command`；设备执行过程中可以持续发出 `progress`、`log`，完成后发送 `result`。
- 服务端持久化 `result` 后会回送 `command_ack`，设备即可从本地重试队列移除该指令，避免重复上报。
- 客户端每 30 秒发送 `heartbeat`；若超时未收到，服务端将断开连接并标记离线。

**消息类型速查**

| 类型 | 方向 | 关键字段 | 说明 |
| --- | --- | --- | --- |
| `session_init` | 设备 → 服务端 | `device_id?`、`device_name`、`capabilities[]` | 建立会话、上报能力 |
| `session_ready` | 服务端 → 设备 | `device_id` | 会话就绪 / 回写最终设备 ID |
| `command` | 服务端 → 设备 | `command_id`、`action`、`params`、`user_id`、`device_id` | 指令下发，供设备执行 |
| `result` | 设备 → 服务端 | `command_id`、`status`、`result`、`error_message`、`user_id`、`device_id`、`action` | 指令执行结果，`status`= `success`/`failed` |
| `command_ack` | 服务端 → 设备 | `command_id` | 服务端确认已落库，设备清理重试队列 |
| `progress` | 设备 → 服务端 | `command_id`、`stage`、`message`、`percent?`、`extra?` | 场景阶段进度，服务端写入 `device_logs` |
| `log` | 设备 → 服务端 | `type`、`message`、`extra?`、`user_id?` | 非结构化日志 |
| `heartbeat` | 设备 → 服务端 | `battery`、`network_type`、`current_task` | 在线心跳（默认 30s 一次） |
| `error` | 服务端 → 设备 | `reason` | 协议错误 / 鉴权失败时返回 |
| `ping` / `pong` | 双向 | —— | OkHttp / FastAPI 心跳帧 |

**示例载荷**

```json
{
  "type": "session_init",
  "data": {
    "device_id": "cached-dev-id",
    "device_name": "Pixel 7",
    "device_model": "panther",
    "android_version": "14",
    "local_ip": "192.168.1.23",
    "capabilities": [
      {
        "action": "start_task",
        "description": "启动脚本任务",
        "params": [
          {"name": "task_name", "type": "string", "required": true},
          {"name": "config", "type": "object", "required": false, "default": {}}
        ],
        "meta": {
          "scripts": [
            {
              "name": "dhgate_order_v2",
              "version": "3.0.4",
              "parameters": [
                {"name": "search_keyword", "type": "string", "required": true, "default": "..."},
                {"name": "product_image", "type": "file", "required": true}
              ]
            }
          ]
        }
      }
    ]
  }
}
```

```json
{
  "type": "command",
  "data": {
    "command_id": "cmd-a1b2c3",
    "device_id": "dev-uuid",
    "user_id": "admin-uuid",
    "action": "start_task",
    "params": {
      "task_name": "dhgate_order_v2",
      "config": {"search_keyword": "demo keyword"}
    }
  }
}
```

```json
{
  "type": "result",
  "data": {
    "command_id": "cmd-a1b2c3",
    "status": "success",
    "result": "{\"metrics\":{\"duration_ms\":5230}}",
    "error_message": null,
    "user_id": "admin-uuid",
    "device_id": "dev-uuid",
    "action": "start_task"
  }
}
```

```json
{
  "type": "progress",
  "data": {
    "command_id": "cmd-a1b2c3",
    "stage": "scene_match",
    "message": "matched login scene",
    "percent": 40,
    "status": "running",
    "extra": {"scene": "login"},
    "user_id": "admin-uuid",
    "device_id": "dev-uuid"
  }
}
```

收到结果后，服务端会向设备发送：

```json
{
  "type": "command_ack",
  "data": {"command_id": "cmd-a1b2c3"}
}
```

### 3.6 脚本执行栈（Instrumentation）

- `ScenarioCatalog`：列举/加载脚本元数据，生成能力描述。
- `ScenarioParameterBinder`：负责校验 `task_name` 与 `config`，合并 `project.yaml` 中的默认值，输出 `ScenarioTaskRequest`。
- `ScenarioRunCoordinator`：调度 `ScenarioRunner` 与 `ScriptHandlerRegistry`，执行 YAML 场景，并通过 `AutomationController` 的回调发送进度 / 遥测。
- `ScriptRunGuard`：确保同一设备同一时间只运行一个脚本，避免冲突。

## 4. 构建与部署

### 4.1 服务端

- **安装依赖**：
  ```bash
  cd automation-server
  python3 -m venv venv
  source venv/bin/activate
  pip install -r requirements.txt
  ```
- **环境变量**：复制 `deploy.env.example` 或者创建 `.env`，至少配置 `SECURITY__SECRET_KEY`、`DATABASE__URL`（默认 SQLite）。
- **数据库初始化**：
  ```bash
  alembic upgrade head   # migrations/ 目录已经就绪
  ```
- **启动**：
  ```bash
  uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
  ```
- **静态资源**：默认从 `app/web/static` 提供，如果将 `frontend` 构建产物放入 `frontend/dist`，可在 `settings.template_dir` / `static_dir` 指向新的路径或通过 Nginx 托管。

### 4.2 Android 模块

- **全局前提**：已安装 Android SDK，`local.properties` 指向 sdk.dir。
- **常用指令**：
  ```bash
  ./gradlew auth-app:assembleDebug
  ./gradlew automation-app:assembleDebug
  ./gradlew automation-app:assembleDebugAndroidTest
  ```
- **设备部署流程（归纳自旧文档）**：
  1. 确认 ADB 连接：`adb connect <device_ip>:5555`，`adb devices` 查看在线列表。
  2. 安装控制台 APK：`adb -s <serial> install -r auth-app/build/outputs/apk/debug/auth-app-debug.apk`。
  3. 安装主 APK 与测试 APK（可先运行脚本自动安装）：
     ```bash
     adb -s <serial> install -r -t automation-app/build/outputs/apk/debug/automation-app-debug.apk
     adb -s <serial> install -r -t automation-app/build/outputs/apk/androidTest/debug/automation-app-debug-androidTest.apk
     ```
  4. 启动自动化测试：
     ```bash
     adb -s <serial> shell am instrument -w \
       -e wsUrl "ws://SERVER/ws?token=..." \
       -e deviceId "<uuid>" \
       com.automation.test/androidx.test.runner.AndroidJUnitRunner
     ```
  5. 使用 `adb logcat -s AutomationController CommandBus ScenarioTaskService` 观察运行日志。

- **自动化构建 + 上传**：`python scripts/build_and_upload.py --server http://HOST --username admin --password ****`。

### 4.3 前端（待完善）

- `automation-server/frontend` 是一个 Vue 3 + Vite 初始工程（含 `node_modules`）。尚未集成到生产流程，后续可将其产物部署到 Nginx 或替换现有模板页。

## 5. 维护要点与扩展建议

### 5.1 能力与模板缓存

`ConnectionManager` 仅在设备在线时持有 `device_capabilities`。若业务需要离线仍可查询脚本参数，建议在 `_process_session_init` 中额外把 `capabilities` 落库（可新建 `device_capabilities` 表，字段包含 `device_id`、`capability_hash`、`payload`、`updated_at`），并在能力发生变更时刷新缓存，供前端直接渲染模板。

### 5.2 批量指令下发

现有 `/api/admin/commands` 面向单设备。可在 `admin.py` 增加 `/commands/batch` 接口，接收 `{device_ids: [...], action, params}`。实现要点：逐台校验在线状态、在事务中循环创建 `Command`、调用 `manager.send_command`，若某台失败需要记录失败原因并回滚对应记录，最终返回成功 / 失败列表给调用方。

### 5.3 脚本扩展流程

1. **编写资源**：在 `automation-app/src/androidTest/assets/scripts/<task_name>/` 新增 `project.yaml`（元数据、参数定义）和 `scenes.yaml`（场景签名、handler 映射），保持缩进与字段命名规范。
2. **实现处理器**：在 `com.automation.feature.scripts` 下编写脚本处理类（示例 `DhgateOrderV2Handlers`），并在 `ScriptHandlerRegistry` 构造函数中注册。
3. **接入参数校验**：通过 `project.yaml` 中的 `default`、`type` 信息，结合 `ScenarioParameterBinder` 自动生成校验规则；如需自定义参数处理，可扩展 `ScenarioTaskService` 的逻辑或新增 `CommandDescriptor` 元数据。
4. **构建与上传**：重新执行 `./gradlew automation-app:assembleDebugAndroidTest` 并使用 `scripts/build_and_upload.py` 上传，新的 `capabilities` 会随 APK 一并上报。
5. **验证**：在后台调用 `/api/admin/devices/{device_id}/capabilities` 查看脚本是否出现，使用控制台执行一次集成测试，确认 `progress` 与 `result` 行为符合预期。

### 5.4 新指令 / 新能力扩展

1. 在 `automation-app/src/androidTest/java/com/automation/application/runtime/modules/` 新建 `CommandModule`，实现 `register()`，注册新的 `CommandDescriptor` 与 `CommandHandler`。
2. 将模块实例加入 `CommandExecutionEngine.registerModules()`，必要时为其增加拦截器或上下文依赖。
3. 使用 `CommandParameter.required/optional` 定义参数列表，借助 `metadataSupplier` 返回额外配置，使服务端 / 前端可渲染表单。
4. 若服务端需要感知新指令，更新 `automation-server/app/api/routers/commands.py` / `admin.py` 的参数校验或权限逻辑，并确保 `CommandService` 能序列化新字段。
5. 通过真实设备或 WebSocket 模拟工具回归测试，确认 `command_ack` 能正确返回，且新指令在 Web 控制台展示正常。

### 5.5 原生依赖管理

`opencv` 子模块是 OpenCV Android SDK 的本地镜像。如需升级，应下载官方发行版，替换 `java/` 与 `native/` 目录，更新 `build.gradle` 版本号，然后重新构建 `automation-app` 验证 `ImageRecognition` / `VisionCommandModule` 功能。

### 5.6 日志与排障

- 服务端：FastAPI 默认日志 + `device_logs` 表，结合 `log_type`、`command_id` 可还原脚本执行轨迹。
- 设备端：主要关注 `AutomationController`、`CommandBus`、`ScenarioTaskService` 日志，可使用 `adb logcat -s AutomationController CommandBus ScenarioTaskService`。
- 参数错误：若脚本参数缺失或类型不匹配，Instrumentation 会返回 `status=failed` 且在 `error_message` 中附带 `ParameterValidator` 提示，可直接反馈给调用方。

## 6. 常用路径速查

- 服务端入口：`automation-server/main.py`
- REST 路由定义：`automation-server/app/api/routers/`
- WebSocket 管理：`automation-server/app/api/routers/websocket.py`
- 领域服务：`automation-server/app/domain/*`
- 数据模型：`automation-server/app/db/models.py`
- 测试 APK 仓库：`automation-server/storage/test_apk/`（上传后自动生成）
- 控制台 Activity：`auth-app/src/main/java/com/automation/auth/AutomationControlActivity.java`
- 设备登录管理：`auth-app/src/main/java/com/automation/network/http/DeviceAuthManager.java`
- 自动化控制器：`automation-app/src/androidTest/java/com/automation/application/runtime/AutomationController.java`
- 指令引擎：`automation-app/src/androidTest/java/com/automation/application/runtime/CommandExecutionEngine.java`
- 场景服务：`automation-app/src/androidTest/java/com/automation/application/scenario/`
- 脚本资源：`automation-app/src/androidTest/assets/scripts/`
- 构建脚本：`scripts/build_and_upload.py`

---

如需在现有基础上新增业务模块（例如脚本模板管理、执行历史查询、设备分组调度），建议遵循现有分层：在 `domain` 层定义用例与仓储接口，在 `infrastructure` 层提供实现，再在 `api/routers` 暴露 REST 接口，并复用 `ConnectionManager` 进行实时推送。

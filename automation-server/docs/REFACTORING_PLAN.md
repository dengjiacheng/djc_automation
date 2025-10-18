# Android Automation Server 重构蓝图

> 目标：打造一套前后端分离、分层清晰、可持续迭代的架构，支撑日后普通用户管理、更多业务模块与数据库迁移。

## 1. 设计原则
- **分层解耦**：领域模型（Domain）独立于接口层，避免业务逻辑散落在路由与视图中。
- **可替换性**：数据库、认证、存储等基础设施通过接口约束与配置驱动，便于后续切换 MySQL/PostgreSQL。
- **可观测 & 可测试**：每个模块具备清晰输入输出，便于单元/集成测试；提供统一日志与错误处理。
- **渐进重构**：保持现有功能可运行，分批迁移，避免一次性大爆炸式替换。

## 2. 目标架构
```
app/
├── core/               # 全局配置、日志、安全、依赖注入容器
├── api/                # FastAPI 路由层，纯粹处理 IO 与协议
│   ├── deps/           # 依赖项（auth、db session、current user）
│   ├── routers/        # 各模块路由入口
│   └── responses/      # 公共响应模型/异常映射
├── domain/             # 领域层：实体、仓储接口、服务、用例
│   ├── accounts/
│   ├── devices/
│   ├── commands/
│   └── logs/
├── infrastructure/     # 基础设施实现：SQLAlchemy 仓储、Alembic、消息推送等
│   ├── database/
│   ├── security/
│   └── websocket/
├── presentation/       # WebSocket handler、后台任务、调度
├── schemas/            # Pydantic DTO，与领域模型分离
└── main.py             # 应用入口（组装依赖）
```

前端独立为 `frontend/` 目录，使用 **Vue 3 + TypeScript + Vite**，划分模块：
```
frontend/
├── apps/
│   ├── admin/          # 管理端（设备、账户、统计）
│   ├── customer/       # 客户端（设备控制、日志）
│   └── auth/           # 登录、注册、权限守卫
├── shared/             # 组件、composables、API 客户端、样式系统
└── tests/
```

构建产物通过 `npm run build` 生成到 `frontend/dist`，再由 FastAPI 静态服务或 Nginx 代理。

## 3. 后端重构阶段

### 阶段 A：基础设施
1. **配置模块重构**：拆分环境、数据库、JWT、CORS、静态资源配置，支持 `.env` 与系统环境变量。
2. **数据库抽象**：
   - 引入 `SQLAlchemy` declarative base + `AsyncSession` 工厂统一管理。
   - 新增 `app/infrastructure/database/migrations`，集成 Alembic 基础配置。
   - 定义 `Repository` 抽象基类与 CRUD mixins，隔离 ORM 细节。
3. **依赖注入**：使用 FastAPI `Depends` + 自定义 `ServiceFactory` 管理服务实例。
4. **异常与日志**：统一异常处理器、结构化日志、错误码映射。

### 阶段 B：领域模块迁移
1. **账户/认证 (`accounts`)**
   - 领域模型 + 仓储 + 服务（注册、登录、角色管理、普通用户操作）。
   - JWT 与密码加密放置在 `infrastructure/security`。
   - API 路由仅负责请求解析 / 响应封装。
2. **设备 (`devices`)**
   - 设备实体、仓储、服务方法（注册、上线、离线、能力查询）。
   - 添加查询规范与分页/过滤 DTO。
3. **命令 (`commands`) 与 日志 (`logs`)**
   - 统一处理指令生命周期、执行结果、查询历史。
   - 引入事件或回调机制，后期接入消息队列/WebSocket。
4. **WebSocket 管理**
   - 将 `manager` 重构为可注入的领域服务，拆分会话跟踪、消息调度、心跳。

### 阶段 C：API 与用例
- 重新梳理 REST 接口，按领域模块划分 Router，使用响应模型 + 错误码。
- 扩展用户管理接口（例如普通用户列表、角色切换、密码重置）。
- 提供 `/v1/` 命名空间，为未来版本化做好准备。

### 阶段 D：测试与工具
- 添加 `tests/` 目录，使用 `pytest` + `async fixtures` 覆盖关键服务与 API。
- 补充 `pre-commit` 钩子、`ruff`/`black`/`isort` 规范。
- 编写 `Makefile` 或 `tasks.py`（Invoke）统一开发命令。

## 4. 前端规划（Vue 3 + TS）
1. 使用 Vite 创建项目，配置路径别名、环境变量、API 代理。
2. 集成 `Pinia` 做状态管理；`Vue Router` 管理多角色入口。
3. UI 框架建议使用 `Naive UI` 或 `Element Plus`，同时构建基础组件库。
4. 封装 API 客户端（Axios + 拦截器 + Token 刷新），统一响应处理。
5. 页面模块：
   - **Auth**：登录、注册、角色切换。
   - **Dashboard**：
     - 普通用户：设备列表、状态、控制入口。
     - 管理员：用户管理、设备管理、指令下发、统计。
   - **控制/实时**：WebSocket 控制面板，拆为可复用组件。
6. 使用 `Vitest` + `Testing Library` 做基础单元测试。

## 5. 部署策略
- 后端提供 `uvicorn` / `gunicorn` 启动脚本；生产环境可结合 Nginx。
- 前端构建产物放置 `frontend/dist` → Nginx 静态服务或 FastAPI `StaticFiles`。
- 配置 `.env` 模板：开发与生产拆分。

## 6. 实施顺序（当前迭代）
1. **阶段 A**：搭建新版后端骨架与配置/数据库抽象（当前任务）。
2. **阶段 B.1**：迁移认证/账户模块，保证登录流程可用，并补齐普通用户管理接口。
3. **阶段 B.2/B.3**：迁移设备与命令模块，清理旧服务与仓储。
4. **阶段 B.4 + 阶段 C**：重塑 WebSocket 管理与路由契约。
5. **阶段 D**：测试框架、CI/格式化工具。
6. **阶段前端**：初始化 Vue 工程并逐屏迁移现有页面。

每一步结束后，保持旧接口兼容或提供过渡层，确保线上可逐步替换。

## 当前进度
- ✅ 阶段 B.1：账户/认证模块已迁移至领域层，统一使用 `AccountService` + SQL 仓储实现。
- ✅ 阶段 B.2：设备、指令、日志服务完成领域化与基础设施抽象，API/WebSocket 已适配新模型。

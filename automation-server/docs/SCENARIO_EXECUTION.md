# 场景执行与指令架构说明

本文档描述 `automation-app` 中全新的命令执行与脚本场景运行体系，便于后续团队成员理解模块职责、扩展方式以及指标采集方案。

## 整体拓扑

```
AutomationController
 └── CommandExecutionEngine
      ├── CommandBus + CommandRegistry（命令总线）
      ├── CommandLoggingInterceptor（执行拦截）
      ├── 各类 CommandModule（设备/应用/诊断/场景/…）
      └── ScenarioTaskService（脚本调度门面）
             ├── ScenarioCatalog（脚本枚举与元数据）
             ├── ScenarioParameterBinder（参数合并 + 校验）
             └── ScenarioRunCoordinator（执行协调 + 指标采集）
```

## 核心模块

### CommandExecutionEngine

- 统一承载所有命令模块注册，暴露 `execute()` 与 `getCapabilitiesJson()`。
- 依赖 `CommandBus` 完成参数归一化、拦截器调度、执行路由。
- 默认挂载 `CommandLoggingInterceptor`，记录指令耗时并通过 WebSocket 打点。

### CommandBus 及相关抽象

- `CommandDescriptor`：描述单条指令的 action、参数、额外元数据。
- `CommandHandler`：命令处理函数接口，返回 `CommandResult`。
- `CommandInterceptor`：拦截器接口，可实现日志、鉴权、熔断等横切逻辑。
- `CommandResult`：统一封装成功/失败状态、载荷、提示文案，便于上层结构化输出。

### 场景执行三件套

| 组件 | 职责 |
| --- | --- |
| ScenarioCatalog | 读取脚本资源，提供脚本列表、元数据以及生成能力参数 |
| ScenarioParameterBinder | 合并默认参数与调用配置，使用 `ParameterValidator` 校验并构造 `ScenarioTaskRequest` |
| ScenarioRunCoordinator | 调用 `ScenarioRunner` 执行场景，引入 `ScenarioTelemetryCollector` 聚合指标，并与 `CommandScenarioReporter` 组合上报进度 |

`ScenarioTaskService` 仅承担管道调度，负责调用上述组件并向 `CommandRegistry` 注册 `start_task` 相关能力。

### Telemetry 与 Reporter

- `ScenarioTelemetryCollector` 记录耗时、匹配场景、警告/错误、是否超时等信息，最终写入 `CommandResult` 的 `metrics` 字段。
- `CompositeScenarioReporter` 支持同时将场景事件广播给多个 Reporter（如指令进度 Reporter + 指标采集器）。
- `AutomationController` 根据 `CommandResult` 成功或失败状态分别调用 `sendCommandResult`，确保失败时仍返回结构化 summary。

## 扩展指导

1. **新增命令**：实现 `CommandModule` 并注册到 `CommandExecutionEngine`，返回 `CommandResult`。
2. **添加拦截器**：实现 `CommandInterceptor` 并通过 `CommandExecutionEngine#addInterceptor` 注入。
3. **接入新脚本仓库**：扩展/替换 `ScenarioCatalog` 的 `ScriptRepository` 实现即可。
4. **监控接入**：根据 `ScenarioTelemetryCollector#toJson()` 的输出可落地到日志、埋点或上报服务端。

## 指标字段示例

```json
{
  "task_name": "dhgate_order_v2",
  "script": "dhgate_order_v2",
  "status": "success",
  "last_scene": "confirm_order",
  "metrics": {
    "duration_ms": 5230,
    "matched_count": 4,
    "matched_scenes": ["login", "select_product", "checkout", "confirm_order"],
    "warnings": [],
    "errors": [],
    "timeout": false
  }
}
```

## 测试覆盖

新增 `ScenarioParameterBinderTest` 用于校验参数合并逻辑。建议继续补充：

- Runner 层的集成测试（Mock 场景处理器验证执行顺序和剪枝）。
- CommandBus 拦截器链单测（成功/失败路径、异常传播）。

## 版本历史

- **2024-XX-XX**：首次引入命令总线与全新场景执行架构，完成日志/指标接入。

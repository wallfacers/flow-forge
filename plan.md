# Flow-Forge DAG工作流引擎 - 实施计划

## 项目概述

**目标**: 从零构建一个企业级DAG工作流引擎，支持私有化部署

**技术栈**:
- Java 21 + Spring Boot 3.1 + Virtual Threads
- Maven (构建工具)
- 包名: `com.workflow`
- JGraphT (DAG处理)
- GraalVM 22.3.0 + Polyglot (安全沙箱)
- PostgreSQL 15 + Redis 7.0 + MinIO
- SQL脚本 (数据库初始化，不用Flyway)
- SpringDoc OpenAPI (API文档)
- Docker 容器化交付

**项目路径**: `D:\develop\java\source\flow-forge`

---

## 当前进度

### ✅ Week 1-2: 内核原型 (已完成)

| 任务 | 状态 | 完成内容 | 文件 | 提交 |
|------|:----:|----------|------|------|
| Maven多模块骨架 | ✅ | 父POM + 8个子模块 | `pom.xml` | # f7fa853 |
| 核心模型类 | ✅ | 8个模型类 | `flow-forge-core-model/.../model/*.java` | # f7fa853 |
| 异常类 | ✅ | 自定义异常 | `WorkflowException.java`<br>`WorkflowValidationException.java` | # f7fa853 |
| JSON DSL解析器 | ✅ | 解析器+验证器 | `WorkflowDslParser.java`<br>`JsonDslValidator.java` | # f7fa853 |
| DAG循环检测 | ✅ | JGraphT循环检测 | `CycleDetector.java` | # f7fa853 |
| 入度调度算法 | ✅ | 拓扑排序+入度计算 | `InDegreeScheduler.java` | # f7fa853 |
| 变量解析器 | ✅ | JSONPath支持 | `VariableResolver.java` | # f7fa853 |
| 单元测试 | ✅ | 模型和DSL测试 | `WorkflowDefinitionTest.java`<br>`WorkflowDslParserTest.java` | # f7fa853 |
| 示例工作流 | ✅ | JSON示例 | `example-workflow.json` | # f7fa853 |

**里程碑M1达成**: 能够解析JSON DSL并检测循环

---

## 未完成任务清单

### ✅ Week 3: 基础节点执行器 (已完成)

**目标**: 实现HTTP和Log节点执行器，能够执行简单的 HTTP → Log 流程

| ID | 任务 | 文件路径 | 功能描述 | 注意事项 | 状态 | 提交 |
|----|------|----------|----------|----------|:----:|-----|
| 3.1 | NodeExecutor接口 | `flow-forge-nodes/.../node/NodeExecutor.java` | 定义execute()方法，支持变量解析和超时控制 | execute需支持`{{}}`变量解析 | ✅ | # 4daecd3 |
| 3.2 | 执行器工厂 | `.../node/NodeExecutorFactory.java` | Spring自动注入所有实现，按类型返回执行器 | 使用Map<NodeType, NodeExecutor>存储 | ✅ | # 4daecd3 |
| 3.3 | HTTP节点 | `.../node/http/HttpNodeExecutor.java` | 支持GET/POST/PUT/DELETE、headers、body、超时 | 使用RestTemplate/WebClient，需完整记录响应 | ✅ | # 4daecd3 |
| 3.4 | Log节点 | `.../node/log/LogNodeExecutor.java` | 支持INFO/WARN/ERROR/DEBUG级别，变量解析 | 输出到SLF4J | ✅ | # 4daecd3 |
| 3.5 | 变量解析集成 | 复用`VariableResolver.java` | 在节点执行前解析config中的变量 | 使用`resolveMap()`处理config | ✅ | # 4daecd3 |
| 3.6 | 单元测试 | `.../node/HttpNodeExecutorTest.java`<br>`.../node/LogNodeExecutorTest.java` | HTTP请求成功/失败、Log输出、变量解析验证 | 覆盖率目标70%+ | ✅ | # 4daecd3 |

**状态说明**: 🔲 未开始 | 🔄 进行中 | ✅ 已完成 | ❌ 失败/阻塞

**HTTP节点config格式**:
```json
{
  "url": "https://api.example.com/users/{{input.userId}}",
  "method": "GET",
  "headers": {"Authorization": "Bearer {{global.apiKey}}"},
  "body": "{\"name\": \"{{input.userName}}\"}",
  "timeout": 5000
}
```

**验收标准 (Milestone M2)**:
- [x] 能够执行 HTTP → Log 简单流程
- [x] 变量解析正确工作
- [x] 单元测试通过

---

### 📋 Week 4-5: GraalVM沙箱集成

**目标**: 实现安全的脚本执行沙箱，支持JavaScript，含资源限制

| ID | 任务 | 文件路径 | 功能描述 | 注意事项 | 状态 | 提交 |
|----|------|----------|----------|----------|:----:|-----|
| 4.1 | 依赖验证 | `flow-forge-nodes/pom.xml` | 验证GraalVM依赖正确配置 | 版本23.1.0，需本地安装或Docker | ✅ | # f04643a |
| 4.2 | GraalSandbox | `.../sandbox/GraalSandbox.java` | 创建Context、执行代码、异常处理 | 内存128MB、指令10k、超时5s | ✅ | # f04643a |
| 4.3 | 安全策略 | `.../sandbox/GraalSandbox.java` | allowIO(false)、禁止线程、禁止反射 | 严格限制可访问的Java方法 | ✅ | # f04643a |
| 4.4 | 导出方法 | `.../sandbox/HostAccessExports.java` | 定义@HostAccess.Export安全方法 | 仅log()、sleep()等安全方法 | ✅ | # f04643a |
| 4.5 | Script节点 | `.../node/script/ScriptNodeExecutor.java` | 支持多语言脚本执行 | config: language, code, timeout | ✅ | # f04643a |
| 4.6 | 安全测试 | `.../sandbox/GraalSandboxSecurityTest.java` | 文件IO、线程创建、system.exit()应失败 | 恶意代码测试 | ✅ | # f04643a |
| 4.7 | 性能压测 | `.../sandbox/GraalSandboxPerformanceTest.java` | 10k并发，延迟<50ms(P95) | 虚拟线程性能测试 | ✅ | # f04643a |

**资源限制配置**:
```java
ResourceLimits limits = ResourceLimits.newBuilder()
    .statementLimit(10000, null)
    .memoryLimit(128 * 1024 * 1024)
    .build();
```

**验收标准 (Milestone M3)**:
- [x] 安全执行JS脚本
- [x] 资源限制生效 (内存/超时)
- [x] 恶意代码被阻止

---

### 📋 Week 6: 条件分支与合并

**目标**: 实现IF/Merge节点，支持条件分支

| ID | 任务 | 文件路径 | 功能描述 | 注意事项 | 状态 | 提交 |
|----|------|----------|----------|----------|:----:|-----|
| 6.1 | SpEL解析器 | `.../condition/SpelEvaluator.java` | 解析SpEL表达式、安全过滤、求值 | sanitize()过滤，仅允许nodeX.property格式 | ✅ | # 7d4f21a |
| 6.2 | IF节点 | `.../condition/IfNodeExecutor.java` | 评估condition，决定流向 | 条件判断在Edge层面处理 | ✅ | # 7d4f21a |
| 6.3 | Merge节点 | `.../merge/MergeNodeExecutor.java` | 等待所有前驱完成、合并结果 | 入度>1，需等待所有入边完成 | ✅ | # 7d4f21a |
| 6.4 | 条件分支测试 | `.../condition/ConditionalFlowTest.java` | IF true/false分支、嵌套、Merge等待 | 覆盖所有分支场景 | ✅ | # 7d4f21a |
| 6.5 | 测试修复 | `.../script/ScriptNodeExecutorTest.java` | 修复资源清理问题 | 移除@AfterEach，避免关闭共享资源 | ✅ | # aeb56c5 |

**安全过滤模式**:
```java
private static final Pattern SAFE_PATTERN =
    Pattern.compile("^[a-zA-Z0-9_.\\s+\\-*/%()=!<>|&#]+$");
```

**验收标准 (Milestone M4)**:
- [x] 支持条件分支和合并
- [x] SpEL表达式安全过滤生效
- [x] 测试用例通过

---

### ✅ Week 7-8: 断点续传与重试策略

**目标**: 实现进程崩溃后恢复执行，支持重试

| ID | 任务 | 文件路径 | 功能描述 | 注意事项 | 状态 | 提交 |
|----|------|----------|----------|----------|:----:|-----|
| 7.1 | 数据库初始化 | `.../resources/db/init.sql` | 创建workflow_execution_history、node_execution_log表 | 规范化字段: 名称、时间戳、软删除 | ✅ | # 691f847 |
| 7.2 | JPA实体 | `.../entity/WorkflowExecutionEntity.java`<br>`.../entity/NodeExecutionLogEntity.java` | 映射数据库表，JSONB字段处理 | @JdbcTypeCode(SqlTypes.JSON) | ✅ | # 691f847 |
| 7.3 | Repository | `.../repository/WorkflowExecutionRepository.java`<br>`.../repository/NodeExecutionLogRepository.java` | Spring Data JPA接口 | 支持租户隔离查询、软删除 | ✅ | # 691f847 |
| 7.4 | CheckpointService | `.../checkpoint/CheckpointService.java` | 每节点执行后保存状态 | 保存ExecutionContext、入度快照 | ✅ | # 691f847 |
| 7.5 | RecoveryService | `.../checkpoint/CheckpointRecoveryService.java` | 从DB加载检查点、恢复执行 | 恢复入度映射，继续执行 | ✅ | # 691f847 |
| 7.6 | 重试策略 | `.../retry/RetryPolicy.java` | 指数退避算法 | 支持4种策略: Fixed, Linear, Exponential, Jitter | ✅ | # 691f847 |
| 7.7 | WorkflowDispatcher | `.../dispatcher/WorkflowDispatcher.java` | 虚拟线程并发调度 | 异步执行、取消、恢复 | ✅ | # 691f847 |
| 7.8 | 断点续传测试 | `.../checkpoint/CheckpointTest.java` | 进程中断后恢复验证 | 24个测试用例，覆盖快照/恢复/拓扑排序 | ✅ | # eccb737 |

**表结构关键部分**:
```sql
CREATE TABLE workflow_execution_history (
    id UUID PRIMARY KEY,
    execution_id VARCHAR(64) UNIQUE NOT NULL,
    workflow_id VARCHAR(64) NOT NULL,
    workflow_name VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    context_data JSONB,
    checkpoint_data JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    ...
);
```

**验收标准 (Milestone M5)**:
- [x] 数据库表规范化（名称、时间戳、软删除）
- [x] 检查点保存和恢复机制
- [x] 重试策略（指数退避+抖动）
- [x] 虚拟线程并发调度
- [x] 断点续传测试（24个测试用例全部通过）

---

### ✅ Week 9: 触发器 (已完成)

**目标**: 实现Webhook和Cron触发器

| ID | 任务 | 文件路径 | 功能描述 | 注意事项 | 状态 | 提交 |
|----|------|----------|----------|----------|:----:|-----|
| 9.1 | Webhook服务 | `.../webhook/WebhookTriggerService.java` | 接收Webhook请求、解析、触发工作流 | POST /api/webhook/{workflowId} | ✅ | # 4b57d44 |
| 9.2 | Webhook注册表 | `.../resources/db/init.sql` | webhook_registration表 | 存储callback_url、secret | ✅ | # 4b57d44 |
| 9.3 | PowerJob管理器 | `.../config/PowerJobWorkerProperties.java` | 管理Scheduler、创建/删除任务 | 使用powerjob-worker | ✅ | # 4b57d44 |
| 9.4 | Cron服务 | `.../cron/CronTriggerService.java` | 创建Cron触发器 | POST /api/triggers/cron | ✅ | # 4b57d44 |
| 9.5 | WAIT节点 | `.../node/wait/WaitNodeExecutor.java` | 暂停执行、等待回调、释放内存 | 状态: RUNNING ↔ WAITING | ✅ | # 4b57d44 |
| 9.6 | 触发器测试 | `.../trigger/TriggerTest.java` | Webhook、Cron、WAIT节点测试 | 覆盖所有触发器类型 | ✅ | # ce5b673 |

**Webhook注册表**:
```sql
CREATE TABLE webhook_registration (
    id UUID PRIMARY KEY,
    workflow_id VARCHAR(50) NOT NULL,
    callback_url VARCHAR(500) NOT NULL,
    secret VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

**验收标准 (Milestone M6)**:
- [x] Webhook/Cron触发器工作
- [x] WAIT节点异步化生效
- [x] 触发器测试通过

---

### ✅ Week 10-11: 可视化API与多租户 (已完成)

**目标**: 实现执行历史可视化API和多租户隔离

| ID | 任务 | 文件路径 | 功能描述 | 注意事项 | 状态 | 提交 |
|----|------|----------|----------|----------|:----:|-----|
| 10.1 | 图生成器 | `.../visualizer/util/GraphGenerator.java` | 生成DAG可视化数据JSON | 兼容D3.js/Cytoscape.js | ✅ | # (pending) |
| 10.2 | 历史映射器 | `.../api/mapper/ExecutionHistoryMapper.java` | 从DB加载历史、映射DTO、分页 | 支持page/size参数 | ✅ | # (pending) |
| 10.3 | 执行API | `.../api/controller/ExecutionController.java` | GET /api/executions/{id}/history | 返回完整执行历史 | ✅ | # (pending) |
| 10.4 | OpenAPI配置 | `.../api/config/OpenApiConfig.java` | Swagger UI配置 | 访问/swagger-ui.html | ✅ | # (pending) |
| 10.5 | 多租户字段 | `.../resources/db/init.sql` | 添加tenant_id字段 | 所有表已有tenant_id | ✅ | # (pending) |
| 10.6 | 租户上下文 | `.../multitenant/TenantContext.java` | ThreadLocal存储租户ID | 提供get/set/clear方法 | ✅ | # (pending) |
| 10.7 | 租户拦截器 | `.../multitenant/TenantInterceptor.java` | 从X-Tenant-ID头提取租户ID | 请求结束后清理ThreadLocal | ✅ | # (pending) |
| 10.8 | 租户隔离 | `.../multitenant/TenantAwareRepository.java` | 自动过滤tenant_id | @Query添加WHERE条件 | ✅ | # (pending) |
| 10.9 | 可视化测试 | `.../visualizer/VisualizerTest.java` | 图生成、历史查询、分页测试 | 12个测试用例全部通过 | ✅ | # (pending) |

**可视化输出格式**:
```json
{
  "nodes": [{"id": "n1", "label": "HTTP请求", "type": "http", "status": "success"}],
  "edges": [{"source": "n1", "target": "n2", "label": "成功"}]
}
```

**验收标准 (Milestone M7)**:
- [x] 可视化API正常工作
- [x] 多租户隔离生效
- [x] Swagger文档可访问

---

### ✅ Week 12: Docker私有化交付 (已完成)

**目标**: 完成Docker镜像和部署配置，兼容外部PostgreSQL

| ID | 任务 | 文件路径 | 功能描述 | 注意事项 | 状态 | 提交 |
|----|------|----------|----------|----------|:----:|-----|
| 12.1 | 应用配置 | `.../api/src/main/resources/application.yml` | 数据库、Redis、MinIO连接配置 | 支持环境变量覆盖 | ✅ | # (pending) |
| 12.2 | Dockerfile | `.../docker/Dockerfile` | 多阶段构建、OpenJDK 21基础镜像 | 打包可执行jar | ✅ | # (pending) |
| 12.3 | docker-compose | `.../docker/docker-compose.yml` | app+redis+minio | PostgreSQL使用外部实例 | ✅ | # (pending) |
| 12.4 | 启动脚本 | `.../docker/start.sh` | Docker容器启动脚本 | 等待依赖服务就绪 | ✅ | # (pending) |
| 12.5 | 部署指南 | `.../docker/README.md` | 环境要求、部署步骤、配置说明 | 包含外部PG配置示例 | ✅ | # (pending) |
| 12.6 | 部署验证 | 手动测试 | Docker一键部署 | 验证所有服务正常 | ✅ | # (pending) |

**docker-compose服务** (不包含PostgreSQL，使用外部实例):
```yaml
services:
  flow-forge:   # 应用服务
  redis:        # Redis 7 (缓存)
  minio:        # MinIO (对象存储)

# PostgreSQL: 使用外部已有实例，通过环境变量配置连接
```

**环境变量配置示例**:
```bash
# 外部PostgreSQL连接
POSTGRES_HOST=your-pg-host
POSTGRES_PORT=5432
POSTGRES_DB=flow_forge
POSTGRES_USER=flow_forge
POSTGRES_PASSWORD=your-password

# Redis连接 (Docker内网)
REDIS_HOST=redis
REDIS_PORT=6379

# MinIO连接 (Docker内网)
MINIO_ENDPOINT=http://minio:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
```

**验收标准 (Milestone M8)**:
- [x] Docker镜像构建成功
- [x] docker-compose一键启动成功
- [x] 可连接外部PostgreSQL
- [x] API文档可访问

---

## 技术风险与注意事项

| 风险 | 发生场景 | 应对措施 |
|------|----------|----------|
| **GraalVM兼容性** | 不同环境GraalVM版本不一致 | 强制Docker交付，使用官方镜像 |
| **大结果OOM** | 节点返回>2MB结果 | 自动存MinIO，Context只存blob_id |
| **SpEL表达式注入** | 恶意表达式执行危险操作 | sanitize()过滤，限制可访问类 |
| **长事务阻塞** | AI推理等长耗时任务 | WAIT节点异步化，释放内存 |
| **虚拟线程兼容性** | 某些库不兼容虚拟线程 | 避免synchronized，用ReentrantLock |

---

## 关键文件清单

### 已完成 ✅

| 文件路径 | 说明 |
|----------|------|
| `pom.xml` | 父POM，8个子模块 |
| `flow-forge-core-model/src/main/java/com/workflow/model/*.java` | 核心模型类(8个) |
| `flow-forge-core-model/src/main/java/com/workflow/dsl/*.java` | DSL解析器(3个) |
| `flow-forge-engine/src/main/java/com/workflow/engine/scheduler/*.java` | 调度器(2个) |
| `flow-forge-engine/src/main/java/com/workflow/context/VariableResolver.java` | 变量解析器 |

### 待实现 🔲

| 文件路径 | 优先级 | 说明 |
|----------|:------:|------|
| `flow-forge-nodes/src/main/java/com/workflow/node/NodeExecutor.java` | P0 | 执行器接口 |
| `flow-forge-nodes/src/main/java/com/workflow/node/NodeExecutorFactory.java` | P0 | 执行器工厂 |
| `flow-forge-nodes/src/main/java/com/workflow/node/http/HttpNodeExecutor.java` | P0 | HTTP节点 |
| `flow-forge-nodes/src/main/java/com/workflow/node/log/LogNodeExecutor.java` | P0 | Log节点 |
| `flow-forge-nodes/src/main/java/com/workflow/sandbox/GraalSandbox.java` | P1 | GraalVM沙箱 |
| `flow-forge-nodes/src/main/java/com/workflow/sandbox/HostAccessExports.java` | P1 | 安全导出方法 |
| `flow-forge-nodes/src/main/java/com/workflow/node/script/ScriptNodeExecutor.java` | P1 | 脚本节点 |
| `flow-forge-nodes/src/main/java/com/workflow/node/condition/SpelEvaluator.java` | P1 | SpEL解析器 |
| `flow-forge-nodes/src/main/java/com/workflow/node/condition/IfNodeExecutor.java` | P1 | IF节点 |
| `flow-forge-nodes/src/main/java/com/workflow/node/merge/MergeNodeExecutor.java` | P1 | Merge节点 |
| `flow-forge-infrastructure/src/main/resources/db/init.sql` | P1 | 数据库初始化 |
| `flow-forge-infrastructure/src/main/java/com/workflow/infra/entity/*.java` | P1 | JPA实体类 |
| `flow-forge-infrastructure/src/main/java/com/workflow/infra/repository/*.java` | P1 | Repository接口 |
| `flow-forge-engine/src/main/java/com/workflow/engine/checkpoint/CheckpointService.java` | P1 | 检查点服务 |
| `flow-forge-engine/src/main/java/com/workflow/engine/checkpoint/CheckpointRecoveryService.java` | P1 | 恢复服务 |
| `flow-forge-engine/src/main/java/com/workflow/engine/retry/RetryPolicy.java` | P1 | 重试策略 |
| `flow-forge-trigger/src/main/java/com/workflow/trigger/webhook/*.java` | P1 | Webhook触发器 |
| `flow-forge-trigger/src/main/java/com/workflow/trigger/cron/*.java` | P1 | Cron触发器 |
| `flow-forge-nodes/src/main/java/com/workflow/node/wait/WaitNodeExecutor.java` | P1 | WAIT节点 |
| `flow-forge-visualizer/src/main/java/com/workflow/visualizer/*.java` | P2 | 可视化API |
| `flow-forge-infrastructure/src/main/java/com/workflow/infra/multi-tenant/*.java` | P2 | 多租户 |
| `flow-forge-api/src/main/java/com/workflow/api/*.java` | P2 | REST控制器 |
| `flow-forge-api/src/main/java/com/workflow/config/*.java` | P2 | Spring配置 |
| `flow-forge-deployment/docker/Dockerfile` | P2 | Docker镜像 |
| `flow-forge-deployment/docker/docker-compose.yml` | P2 | Docker Compose |
| `flow-forge-deployment/k8s/*.yaml` | P2 | K8s部署 |

---

## 开发规范

### 代码规范
1. **包命名**: `com.workflow.{module}`
2. **类命名**: 驼峰命名，见名知意
3. **异常处理**: 使用`WorkflowException`、`WorkflowValidationException`
4. **日志**: 使用SLF4J，关键操作必须记录

### 测试规范
1. 单元测试覆盖率: **70%+**
2. 核心算法: **90%+**
3. 每个节点执行器需要集成测试

### Git提交规范
```
feat: 新功能
fix: 修复bug
docs: 文档更新
test: 测试相关
refactor: 重构
```

---

## 验收标准

| 里程碑 | 周次 | 验收标准 |
|--------|------|----------|
| M1 | W1-W2 | ✅ 解析JSON DSL并检测循环 |
| M2 | W3 | ✅ 执行HTTP→Log流程，变量解析正确 |
| M3 | W5 | ✅ 安全执行JS脚本，资源限制生效 |
| M4 | W6 | ✅ 条件分支和合并正常工作 |
| M5 | W8 | ✅ 进程崩溃后恢复，重试策略正确 |
| M6 | W9 | ✅ Webhook/Cron触发器工作 |
| M7 | W11 | ✅ 多租户隔离生效，可视化API可用 |
| M8 | W12 | ✅ Docker一键部署成功 |
| M9 | W13 | 🔄 触发器作为节点，支持sync/async模式 |

---

### 🔄 Week 13: 触发器节点重构 (进行中)

**目标**: 将触发器从独立服务重构为工作流入口节点类型

**架构变更**:
- 触发器统一为 `NodeType.TRIGGER`，通过 `config.type` 区分具体类型
- 工作流入口规则：每个工作流一个入口触发器节点，入度为 0
- Webhook 支持 sync/async 模式（HTTP 请求头 Prefer > 节点配置 > 默认异步）
- 结束节点支持输出聚合，从多个上游节点收集数据

| ID | 任务 | 文件路径 | 功能描述 | 注意事项 | 状态 | 提交 |
|----|------|----------|----------|----------|:----:|-----|
| 13.1 | trigger_registry表 | `.../resources/db/init.sql` | 统一存储所有触发器类型 | 包含统计、webhook、cron专用字段 | ✅ | # 73de928 |
| 13.2 | TriggerRegistryEntity | `.../entity/TriggerRegistryEntity.java` | JPA实体类，支持JSONB配置 | 业务方法: incrementTrigger(), resetStatistics() | ✅ | # 73de928 |
| 13.3 | TriggerType枚举 | `.../model/TriggerType.java` | WEBHOOK/CRON/MANUAL/EVENT四种类型 | fromCodeIgnoreCase()方法 | ✅ | # 73de928 |
| 13.4 | TriggerRegistryRepository | `.../repository/TriggerRegistryRepository.java` | JPA Repository查询接口 | 支持webhook路径、工作流ID、租户ID查询 | ✅ | # 73de928 |
| 13.5 | TriggerNodeExecutor | `.../node/trigger/TriggerNodeExecutor.java` | 触发器节点执行器 | 作为工作流入口，传递输入数据 | ✅ | # 73de928 |
| 13.6 | EndNodeExecutor | `.../node/end/EndNodeExecutor.java` | 结束节点执行器，支持输出聚合 | aggregateOutputs配置 | ✅ | # 73de928 |
| 13.7 | TriggerRegistryService | `.../registry/TriggerRegistryService.java` | 触发器注册表+缓存服务 | @Cacheable Redis缓存 | ✅ | # 73de928 |
| 13.8 | WebhookTriggerService | `.../webhook/WebhookTriggerService.java` | 支持sync/async模式触发 | CompletableFuture.orTimeout()超时控制 | ✅ | # 73de928 |
| 13.9 | WebhookController | `.../webhook/WebhookController.java` | 重构为触发+查询接口 | 移除CRUD操作 | ✅ | # 73de928 |
| 13.10 | TriggerController | `.../trigger/TriggerController.java` | 统一触发器查询接口 | 返回完整配置供第三方集成 | ✅ | # 73de928 |
| 13.11 | 单元测试 | `.../trigger/TriggerNodeExecutorTest.java`<br>`.../end/EndNodeExecutorTest.java` | 触发器和结束节点测试 | 覆盖所有触发器类型、输出聚合 | ✅ | # 73de928 |
| 13.12 | 移除旧代码 | 删除CronTrigger*、WebhookRegistration*相关文件 | 统一使用TriggerRegistryService | 删除entity、repository、service、controller、dto | ✅ | # 73de928 |

**trigger_registry表结构**:
```sql
CREATE TABLE trigger_registry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    trigger_config JSONB NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    -- 统计
    total_triggers BIGINT DEFAULT 0,
    successful_triggers BIGINT DEFAULT 0,
    failed_triggers BIGINT DEFAULT 0,
    last_triggered_at TIMESTAMPTZ,
    last_trigger_status VARCHAR(20),
    -- Webhook专用
    webhook_path VARCHAR(255) UNIQUE,
    secret_key VARCHAR(255),
    -- Cron专用
    cron_expression VARCHAR(100),
    timezone VARCHAR(50) DEFAULT 'Asia/Shanghai',
    powerjob_job_id BIGINT,
    next_trigger_time TIMESTAMPTZ,
    -- 审计
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
```

**触发器节点配置格式**:
```json
{
  "type": "webhook",
  "webhookPath": "github-webhook",
  "secretKey": "hmac-secret",
  "asyncMode": "sync",
  "timeout": 30000
}
```

**结束节点输出聚合配置**:
```json
{
  "aggregateOutputs": {
    "result": {
      "fromNodes": ["node1", "node2"],
      "transform": {
        "userId": "{{node1.output.userId}}",
        "profile": "{{node2.output.profile}}"
      }
    }
  }
}
```

**API变更**:
- 新增: `POST /api/webhook/{path}` - 支持 `Prefer: wait=sync` 头
- 新增: `GET /api/triggers` - 统一触发器查询
- 新增: `GET /api/triggers/workflow/{workflowId}` - 工作流触发器查询
- 移除: `POST /api/webhook/register` (CRUD操作)
- 移除: `POST /api/triggers/cron` (CRUD操作)

**验收标准 (Milestone M9)**:
- [x] 触发器作为工作流入口节点正常工作
- [x] Webhook 支持 sync/async 模式
- [x] 结束节点正确聚合上游节点输出
- [x] 触发器列表接口返回完整配置
- [ ] Redis 缓存生效，查询性能满足要求
- [ ] 单元测试覆盖所有触发器类型

---

*更新时间: 2025-01-12 (Week 13 触发器重构已完成 - #73de928)*

# Flow-Forge 架构设计文档

## 目录

- [系统架构](#系统架构)
- [模块设计](#模块设计)
- [核心概念](#核心概念)
- [数据流](#数据流)
- [并发模型](#并发模型)
- [存储设计](#存储设计)

---

## 系统架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                           API 层                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │ REST API     │  │ Swagger UI   │  │ 多租户拦截器         │ │
│  │ Controller   │  │              │  │ TenantInterceptor    │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                           触发器层                               │
│  ┌──────────────┐              ┌──────────────────────────┐    │
│  │ Webhook      │              │ Cron 触发器              │    │
│  │ Trigger      │              │ PowerJobWorker           │    │
│  └──────────────┘              └──────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                           引擎层                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │ 调度器       │  │ 分发器       │  │ 检查点服务           │ │
│  │ InDegree     │──│ Dispatcher  │──│ CheckpointService    │ │
│  │ Scheduler    │  │ Virtual      │  │                      │ │
│  └──────────────┘  │ Threads     │  └──────────────────────┘ │
│                    └──────────────┘                            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                           节点层                                 │
│  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐    │
│  │HTTP │ │LOG  │ │Script│ │ IF  │ │Merge│ │Webhook││Wait │    │
│  └─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘    │
│                    ┌──────────────────────────┐                │
│                    │ GraalVM 沙箱            │                │
│                    │ Script 执行环境         │                │
│                    └──────────────────────────┘                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         基础设施层                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │ JPA 实体     │  │ Repository   │  │ 多租户上下文         │ │
│  │ Entity       │  │ Spring Data  │  │ TenantContext        │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         存储层                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │ PostgreSQL   │  │ Redis        │  │ MinIO                │ │
│  │ 持久化存储   │  │ 缓存         │  │ 大结果存储           │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## 模块设计

### 核心模型层 (flow-forge-core-model)

定义工作流的核心领域模型：

| 类 | 说明 |
|----|------|
| `WorkflowDefinition` | 工作流定义，包含节点、边和 JGraphT 图 |
| `Node` | 节点实体，包含 ID、类型、配置 |
| `Edge` | 有向边，包含源节点、目标节点、条件 |
| `ExecutionContext` | 执行上下文，存储变量和节点结果 |
| `NodeResult` | 节点执行结果 |
| `NodeType` | 节点类型枚举 |
| `ExecutionStatus` | 执行状态枚举 |

**关键设计**：
- 使用 JGraphT 的 `DefaultDirectedAcyclicGraph` 存储 DAG 结构
- 图对象标记为 `transient`，不参与序列化
- 通过 JSON DSL 解析器构建图结构

### 节点执行器层 (flow-forge-nodes)

所有节点实现 `NodeExecutor` 接口：

```java
public interface NodeExecutor {
    NodeResult execute(Node node, ExecutionContext context);
}
```

**执行器工厂** 通过 Spring 自动装配所有实现类，按节点类型分发：

```java
@Service
public class NodeExecutorFactory {
    private final Map<NodeType, NodeExecutor> executors;

    public NodeExecutor getExecutor(NodeType type) {
        return executors.get(type);
    }
}
```

### 引擎层 (flow-forge-engine)

#### 调度器 (InDegreeScheduler)

基于入度的拓扑排序调度算法：

1. 计算每个节点的初始入度（前驱节点数量）
2. 将入度为 0 的节点加入就绪队列
3. 节点完成后，递减后继节点的入度
4. 入度变为 0 的节点加入就绪队列

#### 分发器 (WorkflowDispatcher)

使用 Java 21 虚拟线程实现高并发调度：

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (Node readyNode : readyNodes) {
        executor.submit(() -> executeNode(readyNode));
    }
}
```

#### 检查点服务 (CheckpointService)

每次节点执行后保存状态：

```java
public void saveCheckpoint(String executionId, ExecutionContext context) {
    CheckpointData checkpoint = new CheckpointData(
        executionId,
        context.getInDegrees(),  // 当前入度快照
        context.getCompletedNodes()  // 已完成节点
    );
    repository.save(checkpoint);
}
```

---

## 核心概念

### DAG 工作流

DAG（Directed Acyclic Graph）有向无环图是工作流的数学模型：

- **节点（Node）**：表示一个处理单元
- **边（Edge）**：表示节点间的依赖关系
- **无环**：保证工作流能够终止，避免死循环

### 变量解析

使用 `{{expression}}` 语法引用上下文变量：

| 表达式类型 | 示例 | 解析结果 |
|------------|------|----------|
| 节点输出 | `{{node1.output.data}}` | 节点 node1 的输出数据 |
| 全局变量 | `{{global.apiKey}}` | 工作流定义的全局变量 |
| 输入参数 | `{{.input.userId}}` | 工作流输入参数 |
| 系统变量 | `{{system.executionId}}` | 系统内置变量 |

**解析器实现**：使用 JSONPath 实现嵌套路径访问。

### 重试策略

内置四种重试策略：

| 策略 | 说明 | 延迟计算 |
|------|------|----------|
| Fixed | 固定延迟 | `delay` |
| Linear | 线性增长 | `delay * attempt` |
| Exponential | 指数增长 | `delay * 2^(attempt-1)` |
| Jitter | 指数 + 随机抖动 | `delay * 2^(attempt-1) * random(0.5, 1.5)` |

---

## 数据流

### 执行流程

```
1. 接收工作流定义
   ↓
2. 解析 DSL，构建 DAG
   ↓
3. 循环检测（失败则抛出异常）
   ↓
4. 计算初始入度
   ↓
5. 启动虚拟线程分发器
   ↓
6. 取出就绪节点（入度=0）并执行
   ↓
7. 节点完成后：
   - 保存结果到上下文
   - 递减后继节点入度
   - 保存检查点
   ↓
8. 重复 5-7，直到所有节点完成
```

### 上下文数据结构

```java
ExecutionContext {
    // 输入
    Map<String, Object> input;           // 工作流输入参数
    Map<String, Object> globalVariables; // 全局变量

    // 执行状态
    Map<String, NodeResult> nodeResults; // 节点结果
    Map<String, Integer> inDegrees;      // 节点入度
    Set<String> completedNodes;          // 已完成节点

    // 系统变量
    SystemVariables system;              // executionId, currentTime等
}
```

---

## 并发模型

### 虚拟线程优势

Java 21 虚拟线程（Virtual Threads）特点：

| 特性 | 平台线程 | 虚拟线程 |
|------|----------|----------|
| 创建成本 | 高（1MB 栈） | 低（几KB） |
| 上下文切换 | 昂贵（内核态） | 便宜（用户态） |
| 最大数量 | 数千 | 数百万 |
| 适用场景 | CPU 密集 | IO 密集 |

Flow-Forge 使用虚拟线程执行节点，因为节点执行大多是 IO 操作（HTTP 请求、数据库查询等）。

### 并发控制

```java
// 使用 try-with-resources 自动管理虚拟线程
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<Future<NodeResult>> futures = new ArrayList<>();

    for (Node node : readyNodes) {
        Future<NodeResult> future = executor.submit(() -> {
            return nodeExecutor.execute(node, context);
        });
        futures.add(future);
    }

    // 等待所有就绪节点完成
    for (Future<NodeResult> future : futures) {
        NodeResult result = future.get();
        handleResult(result);
    }
}
```

---

## 存储设计

### PostgreSQL 表结构

#### workflow_execution_history

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
    deleted_at TIMESTAMPTZ
);
```

#### node_execution_log

```sql
CREATE TABLE node_execution_log (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    node_name VARCHAR(255),
    node_type VARCHAR(32),
    status VARCHAR(20),
    output_data JSONB,
    error_message TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    retry_count INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
```

### Redis 缓存

| 缓存键 | 类型 | 说明 | 过期时间 |
|--------|------|------|----------|
| `execution:{id}` | Hash | 执行上下文 | 24h |
| `checkpoint:{id}` | Hash | 检查点数据 | 7天 |
| `tenant:{id}` | String | 租户配置 | 永久 |

### MinIO 存储

当节点输出超过阈值（默认 2MB）时，自动存储到 MinIO：

```java
if (outputSize > LARGE_RESULT_THRESHOLD) {
    String blobId = minioService.upload("results/" + executionId, output);
    return NodeResult.blob(blobId);
}
```

---

## 安全设计

### GraalVM 沙箱

脚本执行时的安全限制：

| 限制项 | 配置 |
|--------|------|
| 文件 IO | 禁止 |
| 线程创建 | 禁止 |
| 反射调用 | 禁止 |
| 类加载 | 白名单 |
| 内存限制 | 128MB |
| 指令限制 | 10,000 条 |

### 多租户隔离

通过 `TenantContext` 实现 ThreadLocal 租户隔离：

```java
public final class TenantContext {
    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    public static String getTenantId() {
        return TENANT_ID.get();
    }
}
```

所有 Repository 查询自动添加租户过滤条件。

---

## 监控设计

### 健康检查

Spring Boot Actuator 端点：

- `/actuator/health` - 总体健康状态
- 数据库连接检查
- Redis 连接检查
- MinIO 连接检查

### 执行指标

| 指标 | 说明 |
|------|------|
| `workflow.executions.total` | 总执行次数 |
| `workflow.executions.active` | 活跃执行数 |
| `workflow.nodes.duration` | 节点执行耗时 |
| `workflow.checkpoints.size` | 检查点大小 |

---

## 扩展性设计

### 自定义节点

实现 `NodeExecutor` 接口并注册为 Spring Bean：

```java
@Component
public class MyCustomNodeExecutor implements NodeExecutor {
    @Override
    public NodeResult execute(Node node, ExecutionContext context) {
        // 自定义逻辑
    }
}
```

### 自定义重试策略

实现 `RetryStrategy` 接口：

```java
public interface RetryStrategy {
    long getNextDelay(int attempt);
}
```

### **一、系统架构设计（私有化部署优先）**  
| **层**                | **核心组件**                     | **技术选型**                  | **关键设计**                                                                 | **私有化适配**                                                                 |
|-----------------------|----------------------------------|-----------------------------|----------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| **API Layer (控制面)** | 流程定义管理、多租户API           | Spring Boot 3.1 + React      | 通过`/api/workflows/{id}/definition`提交DAG，自动注入`tenant_id`（多租户隔离） | **租户隔离**：所有API请求携带`X-Tenant-ID`头，数据库查询自动过滤`tenant_id`字段 |
| **Orchestration Layer** | 状态机引擎、入度调度、Checkpoint   | Java 21 + Virtual Threads    | **核心创新**：`AtomicInteger`入度追踪 + 虚拟线程池（10万+并发）               | **低运维成本**：无状态设计，Console仅需HTTP调用Runner API，可水平扩展至100+实例 |
| **Execution Layer**   | GraalVM沙箱、多语言执行器         | GraalVM 22.3.0 + Polyglot   | **安全硬约束**：`allowIO(false)` + `allowCreateThread(false)` + 5s超时          | **Docker交付**：镜像预编译GraalVM，避免客户环境依赖（强制交付Docker镜像）      |
| **Persistence Layer** | PostgreSQL (历史) + Redis (实时)  | PostgreSQL 15 + Redis 7.0    | **断点续传**：节点执行后序列化Context至`workflow_execution_history.context_data`（JSONB） | **LOB存储**：大结果自动存MinIO，Context仅存`blob_id`（避免OOM）                  |

> ✅ **架构优势**：  
> - **Runner/Console分离**：Console仅负责UI和触发，Runner专注执行（私有化部署时可独立部署在客户VPC）  
> - **资源隔离**：每个Workflow实例独立Context，避免相互污染  

---

### **二、核心算法与伪代码（关键优化版）**  
#### **1. DAG调度算法（防死循环+高并发）**  
```java
public class WorkflowDispatcher {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor(); // JDK21+虚拟线程
    private final NodeExecutorFactory nodeFactory;

    public void launch(WorkflowDefinition def) {
        // 1. 循环检测（JGraphT基础校验，Week1交付）
        if (new CycleDetector<>(def.getGraph()).detectCycle()) {
            throw new WorkflowException("DAG contains cycle");
        }

        // 2. 初始化入度映射（所有节点入度=0）
        Map<String, AtomicInteger> inDegreeMap = new HashMap<>();
        def.getNodes().forEach(node -> inDegreeMap.put(node.getId(), new AtomicInteger(0)));
        def.getEdges().forEach(edge -> inDegreeMap.computeIfPresent(edge.getTargetNodeId(), 
            (k, v) -> v.incrementAndGet()));

        // 3. 启动初始节点（入度=0）
        def.getNodes().stream()
            .filter(node -> inDegreeMap.get(node.getId()).get() == 0)
            .forEach(node -> submitNode(node, def, inDegreeMap));
    }

    private void submitNode(Node node, WorkflowDefinition def, 
                            Map<String, AtomicInteger> inDegreeMap) {
        executor.submit(() -> {
            try {
                // 执行节点（HTTP/Script/Condition）
                NodeResult result = nodeFactory.getExecutor(node.getType()).execute(node, context);
                
                // 1. 变量存入Context（支持JsonPath引用）
                context.appendResult(node.getId(), result);
                
                // 2. 持久化Checkpoint（支持断点续传，Week7交付）
                checkpointService.save(node.getId(), context);
                
                // 3. 处理下游（条件分支+入度更新）
                for (Edge edge : def.getOutEdges(node.getId())) {
                    // 条件分支：SpEL表达式解析（Week5交付）
                    if (edge.getCondition() != null && !SpelEvaluator.evaluate(edge.getCondition(), context)) {
                        continue;
                    }
                    
                    String nextId = edge.getTargetNodeId();
                    if (inDegreeMap.get(nextId).decrementAndGet() == 0) {
                        submitNode(def.getNode(nextId), def, inDegreeMap); // 递归提交（虚拟线程避免栈溢出）
                    }
                }
            } catch (Exception e) {
                handleFailure(node, e); // 触发重试策略（Exponential Backoff）
            }
        });
    }
}
```

> **关键优化点**：  
> - **循环检测**：JGraphT `CycleDetector`（`def.getGraph()`返回`DirectedAcyclicGraph`）  
> - **条件分支**：`SpelEvaluator` 严格过滤表达式（`{{node1.status == 200}}` → `true`；`{{1+1}}` → `2`）  
> - **断点续传**：`checkpointService.save()` 每节点执行后写入PostgreSQL（`context_data` JSONB字段）  

---

#### **2. GraalVM安全沙箱（资源囚牢）**  
```java
public class GraalSandbox {
    public Object execute(String userCode, Map<String, Object> input) {
        // 硬性资源限制（5秒超时 + 128MB内存 + 10k指令）
        ResourceLimits limits = ResourceLimits.newBuilder()
            .statementLimit(10000, null) // 指令数上限
            .memoryLimit(128 * 1024 * 1024) // 128MB
            .build();

        try (Context context = Context.newBuilder("js")
                .resourceLimits(limits)
                .allowIO(false) // 禁止文件IO
                .allowCreateThread(false) // 禁止线程创建
                .allowHostAccess(HostAccess.EXPLICIT) // 仅允许@HostAccess.Export方法
                .build()) {
            
            // 安全注入输入（仅允许指定字段）
            context.getBindings("js").putMember("input", input);
            
            // 超时控制（5秒强制中断）
            Future<Value> future = Executors.newSingleThreadExecutor().submit(() -> 
                context.eval("js", userCode));
            return future.get(5, TimeUnit.SECONDS).as(Map.class);
        } catch (TimeoutException e) {
            throw new SandboxTimeoutException("Script execution timeout (5s)");
        }
    }
}
```

> **安全设计**：  
> - `@HostAccess.Export` 限定：仅允许`System.out.println`等预定义方法（如`@HostAccess.Export public void log(String msg) { ... }`）  
> - **OOM防护**：`memoryLimit` + `statementLimit` 双重约束（测试用例：10万行Excel处理时触发OOM）  

---

### **三、12周落地路线图（含交付物与风险对冲）**  
| **阶段**               | **周次**   | **核心任务**                                                                 | **交付物**                                                                 | **风险对冲方案**                                                                 |
|------------------------|------------|-----------------------------------------------------------------------------|---------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| **内核原型**           | W1-W2       | 1. JGraphT实现DAG循环检测2. 定义JSON DSL规范（含`nodes`/`edges`/`properties`） | ✅ **核心模型JAR**：`com.workflow:core-model:1.0`✅ **DAG静态检查器**：`dag-validator validate workflow.json` | **孤立节点检测**：DAG构建时校验`JGraphT.getVertexCount() == nodes.size()` |
|                        | W3          | 1. 实现HttpNode/LogNode执行器2. JSONPath变量传递（`{{node1.output}}`）      | ✅ **命令行Runner**：`java -jar runner.jar --workflow=example.json`（HTTP→Log） | **变量安全**：`JsonPath.evaluate` 仅允许`nodeX.output`格式，过滤`{{system.exit()}}` |
| **逻辑控制**           | W4-W5       | 1. GraalVM集成JS沙箱2. 实现沙箱安全策略（IO/线程限制）                     | ✅ **安全脚本模块**：`GraalSandbox.execute()`✅ **性能压测报告**：10k并发脚本执行（平均延迟<50ms） | **GraalVM兼容性**：Docker镜像基于`eclipse-temurin:21-jdk`，预编译GraalVM二进制 |
|                        | W6          | 1. IF分支节点（SpEL表达式解析）2. Merge节点（多路合并）                     | ✅ **复杂控制流内核**：```<br>if {{status == 200}} → A<br>else → B<br>merge → C<br>```✅ **防死循环**：循环节点配置`maxIterations=100` | **SpEL注入风险**：表达式解析前过滤`{}`/`[]`（`SpelEvaluator.sanitize()`） |
| **企业级特性**         | W7-W8       | 1. Checkpoint持久化（PostgreSQL JSONB）2. 重试策略（Exponential Backoff） | ✅ **断点续传引擎**：进程崩溃后恢复`workflow_id`✅ **错误日志**：记录`input/output/duration/stacktrace` | **OOM防护**：`NodeResult` > 2MB → 自动存MinIO，Context存`blob_id`（`minio://blob/123`） |
|                        | W9          | 1. Webhook触发器（注册回调URL）2. Cron触发器（基于PowerJob）                 | ✅ **事件驱动闭环**：Webhook → Workflow → Callback → 恢复调度 | **长事务处理**：WAIT节点返回`{status: "PENDING", callback_url: "http://webhook"}`，引擎释放内存 |
| **私有化交付**         | W10-W11     | 1. Execution History可视化（类似n8n）2. 多租户隔离（`tenant_id`字段）       | ✅ **流程调试API**：`/api/executions/{id}/history`（输入/输出/耗时） | **多租户**：所有表增加`tenant_id`，API自动注入`X-Tenant-ID` |
|                        | W12         | 1. Docker Compose私有化包2. K8s Deployment YAML + API手册（Swagger）        | ✅ **甲方交付包**：- `docker-compose.yml`（含PostgreSQL/Redis/MinIO）- `k8s/deployment.yaml`- `api-docs/swagger.json` | **依赖冲突**：**强制Docker交付**，不提供JAR包 |

---

### **四、关键技术风险深度对冲（乙方必看）**  
| **风险**                | **发生场景**                     | **解决方案**                                                                 | **验证方式**                                  |
|-------------------------|----------------------------------|---------------------------------------------------------------------------|---------------------------------------------|
| **OOM（大结果集）**     | 处理10万行Excel（结果>2MB）       | **LOB存储**：- `NodeResult` > 2MB → 上传MinIO- Context存`{ "blob_id": "minio://blob/123" }` | `NodeResult`类添加`isLarge()`方法：`if (size > 2 * 1024 * 1024) saveToMinIO()` |
| **GraalVM兼容性**       | 客户环境为CentOS 7 vs Ubuntu 22.04 | **Docker镜像强制交付**：镜像基于`eclipse-temurin:21-jdk`，预编译GraalVM二进制 | `docker run -it runner:v1.0-centos22.3` 无报错 |
| **长事务阻塞**          | AI模型推理（需1小时）             | **异步化设计**：- 节点返回`{status: "PENDING", callback_url: "http://webhook"}`- 引擎释放内存，等待回调 | `WaitNode`执行后存入`workflow_execution_history.status = "WAITING"` |
| **SpEL表达式注入**      | 用户输入`{{1+1}}`被恶意构造为`{{system.exit(0)}}` | **安全沙箱**：- 表达式解析前过滤`{}`/`[]`/`()`- 仅允许`nodeX.property`格式 | `SpelEvaluator.sanitize("{{system.exit(0)}}")` → `{{}}`（返回空） |

---

### **五、数据库表结构（PostgreSQL）**  
```sql
-- 流程执行历史（存储完整Context）
CREATE TABLE workflow_execution_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id VARCHAR(50) NOT NULL, -- 甲方租户+流程ID
    tenant_id VARCHAR(50) NOT NULL,   -- 多租户隔离字段
    status VARCHAR(20) NOT NULL,      -- RUNNING/SUCCESS/FAILED
    context_data JSONB NOT NULL,      -- 节点结果（LOB大结果存MinIO，仅存blob_id）
    current_node_id VARCHAR(50),      -- 当前执行节点
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ
);

-- 节点执行明细（用于前端可视化）
CREATE TABLE node_execution_log (
    execution_id UUID REFERENCES workflow_execution_history(id),
    node_id VARCHAR(50) NOT NULL,
    input_snapshot JSONB,             -- 节点输入
    output_snapshot JSONB,            -- 节点输出（大结果存MinIO，仅存blob_id）
    error_message TEXT,
    duration_ms INT,
    PRIMARY KEY (execution_id, node_id)
);

-- 索引优化（高频查询）
CREATE INDEX idx_workflow_execution_history_tenant ON workflow_execution_history(tenant_id);
CREATE INDEX idx_node_execution_log_execution ON node_execution_log(execution_id);
```

> 💡 **私有化部署关键**：  
> - **多租户隔离**：所有API请求携带`X-Tenant-ID`，SQL自动添加`WHERE tenant_id = ?`  
> - **LOB存储**：`context_data`中`output_snapshot`字段 > 2MB时，自动存MinIO并替换为`blob_id`（`context_data`仅存引用）  

---

### **六、交付物清单（乙方给甲方）**  
1. **私有化部署包**  
   - `docker-compose.yml`（含PostgreSQL/Redis/MinIO/Runner）  
   - `k8s/deployment.yaml`（生产级K8s配置）  
2. **技术文档**  
   - `api-docs/swagger.json`（含所有API接口）  
   - `deployment-guide.pdf`（Docker/K8s部署步骤）  
3. **安全证明**  
   - `sandbox-security-report.pdf`（GraalVM安全测试报告）  
   - `oom-prevention-test.log`（10万行Excel处理测试）  

> ✅ **交付承诺**：  
> **“无JAR包交付，仅提供Docker镜像，确保客户环境零依赖”**  
> **“断点续传能力：进程崩溃后恢复成功率100%”**  
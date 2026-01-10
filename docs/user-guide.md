# Flow-Forge 用户指南

## 目录

- [快速入门](#快速入门)
- [工作流定义](#工作流定义)
- [节点类型详解](#节点类型详解)
- [变量引用](#变量引用)
- [触发器配置](#触发器配置)
- [执行历史查询](#执行历史查询)
- [最佳实践](#最佳实践)

---

## 快速入门

### 1. 创建简单工作流

最简单的 HTTP → Log 工作流：

```json
{
  "id": "my-first-workflow",
  "name": "我的第一个工作流",
  "tenantId": "default",
  "nodes": [
    {
      "id": "fetch-data",
      "name": "获取数据",
      "type": "http",
      "config": {
        "url": "https://api.github.com/repos/java/java",
        "method": "GET"
      }
    },
    {
      "id": "log-result",
      "name": "记录结果",
      "type": "log",
      "config": {
        "level": "INFO",
        "message": "GitHub Stars: {{fetch-data.output.stargazers_count}}"
      }
    }
  ],
  "edges": [
    {"source": "fetch-data", "target": "log-result"}
  ]
}
```

### 2. 提交工作流

```bash
curl -X POST http://localhost:8080/api/workflows/execute \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: default" \
  -d @my-workflow.json
```

### 3. 查看执行结果

```bash
curl http://localhost:8080/api/executions/{executionId} \
  -H "X-Tenant-ID: default"
```

---

## 工作流定义

### 基本结构

```json
{
  "id": "workflow-id",           // 工作流唯一标识
  "name": "工作流名称",            // 显示名称
  "description": "描述信息",       // 可选描述
  "tenantId": "tenant-001",      // 租户ID
  "globalVariables": {            // 全局变量
    "apiKey": "sk-xxxxx",
    "baseUrl": "https://api.example.com"
  },
  "nodes": [ ... ],                // 节点数组
  "edges": [ ... ]                 // 边数组
}
```

### 节点定义

```json
{
  "id": "unique-node-id",          // 节点唯一ID
  "name": "显示名称",              // 可选
  "type": "http",                  // 节点类型
  "description": "描述",           // 可选
  "config": { ... },               // 节点配置（类型相关）
  "retryCount": 3,                 // 重试次数（可选）
  "timeout": 5000                  // 超时时间ms（可选）
}
```

### 边定义

```json
{
  "sourceNodeId": "node-1",        // 源节点ID
  "targetNodeId": "node-2",        // 目标节点ID
  "condition": "{{node1.output.status}} == 200"  // 可选条件
}
```

---

## 节点类型详解

### HTTP 节点

发起 HTTP 请求，支持 GET/POST/PUT/DELETE 方法。

**配置示例**：

```json
{
  "type": "http",
  "config": {
    "url": "https://api.example.com/users/{{.input.userId}}",
    "method": "POST",
    "headers": {
      "Authorization": "Bearer {{global.apiKey}}",
      "Content-Type": "application/json"
    },
    "body": "{\"name\": \"{{.input.userName}}\"}",
    "timeout": 10000
  }
}
```

**输出结构**：

```json
{
  "status": 200,
  "headers": { "content-type": "application/json" },
  "body": "{...}",
  "data": { ... }      // 解析后的 JSON
}
```

### Log 节点

输出日志到 SLF4J。

**配置示例**：

```json
{
  "type": "log",
  "config": {
    "level": "INFO",
    "message": "用户 {{.input.userId}} 请求处理完成，耗时 {{http-request.output.duration}}ms"
  }
}
```

**支持的日志级别**：`DEBUG`、`INFO`、`WARN`、`ERROR`

### Script 节点

执行 JavaScript/Python 脚本。

**JavaScript 配置**：

```json
{
  "type": "script",
  "config": {
    "language": "js",
    "code": "const data = input['http-request'].output.data; result.count = data.items.length;",
    "timeout": 5000
  }
}
```

**可用变量**：
- `__input` - 所有输入数据的映射
- `__global` - 全局变量
- `__system` - 系统变量
- `log(message)` - 输出日志
- `sleep(ms)` - 暂停执行

### IF 节点

条件分支判断。

**配置示例**：

```json
{
  "type": "if",
  "config": {
    "condition": "{{http-request.output.status}} >= 400"
  }
}
```

**条件表达式**：
- 支持 SpEL 表达式语法
- 可引用节点输出：`{{nodeId.output.field}}`
- 比较运算符：`==`, `!=`, `>`, `<`, `>=`, `<=`
- 逻辑运算符：`&&`, `||`, `!`

### Merge 节点

等待多个前驱节点完成，合并所有输入。

**配置**：无需特殊配置，自动合并所有入边节点的输出。

**输出结构**：

```json
{
  "merged": {
    "node1": { ... },
    "node2": { ... }
  }
}
```

### Webhook 节点

暂停执行，等待外部 Webhook 回调。

**配置示例**：

```json
{
  "type": "webhook",
  "config": {
    "timeout": 3600000  // 1小时超时
  }
}
```

**回调地址**：`POST /api/webhook/{executionId}/{nodeId}`

### WAIT 节点

暂停执行一段时间。

**配置示例**：

```json
{
  "type": "wait",
  "config": {
    "duration": 60000  // 等待60秒
  }
}
```

---

## 变量引用

### 引用语法

变量使用 `{{expression}}` 语法引用。

### 引用类型

| 类型 | 语法 | 示例 | 解析结果 |
|------|------|------|----------|
| 节点输出 | `{{nodeId.output.field}}` | `{{http1.output.data.users}}` | 节点输出中的嵌套字段 |
| 全局变量 | `{{global.varName}}` | `{{global.apiKey}}` | 工作流全局变量 |
| 输入参数 | `{{.input.key}}` | `{{.input.userId}}` | 工作流输入参数 |
| 系统变量 | `{{system.field}}` | `{{system.executionId}}` | 系统内置变量 |

### 系统变量

| 变量 | 说明 |
|------|------|
| `system.executionId` | 当前执行ID |
| `system.workflowId` | 工作流ID |
| `system.tenantId` | 租户ID |
| `system.currentTime` | 当前时间戳 |
| `system.startTime` | 执行开始时间 |
| `system.status` | 当前执行状态 |

### JSONPath 支持

支持 JSONPath 风格的嵌套访问：

```json
{
  "message": "用户 {{http-request.output.data.user.name}} ({{http-request.output.data.user.email}})"
}
```

---

## 触发器配置

### Webhook 触发

注册 Webhook 触发器：

```bash
curl -X POST http://localhost:8080/api/webhooks/register \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: default" \
  -d '{
    "workflowId": "my-workflow",
    "callbackUrl": "https://myapp.com/callback",
    "secret": "my-secret"
  }'
```

触发工作流：

```bash
curl -X POST http://localhost:8080/api/webhook/my-workflow \
  -H "Content-Type: application/json" \
  -d '{"userId": "123", "action": "process"}'
```

### Cron 触发

创建 Cron 触发器：

```bash
curl -X POST http://localhost:8080/api/triggers/cron \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: default" \
  -d '{
    "workflowId": "my-workflow",
    "cronExpression": "0 0 * * * ?",
    "description": "每小时执行"
  }'
```

**Cron 表达式格式**：`秒 分 时 日 月 周`

---

## 执行历史查询

### 获取执行列表

```bash
curl "http://localhost:8080/api/executions?page=0&size=10&workflowId=my-workflow" \
  -H "X-Tenant-ID: default"
```

**响应示例**：

```json
{
  "content": [ ... ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 42,
  "totalPages": 5
}
```

### 获取执行详情

```bash
curl http://localhost:8080/api/executions/{executionId} \
  -H "X-Tenant-ID: default"
```

### 获取可视化图数据

```bash
curl http://localhost:8080/api/executions/{executionId}/graph \
  -H "X-Tenant-ID: default"
```

**响应包含**：节点状态、边连接信息，可用于前端渲染 DAG 图。

---

## 最佳实践

### 1. 节点命名规范

使用有意义的节点 ID，便于调试和日志查看：

```json
{
  "id": "fetch-user-from-db",     // 好
  "id": "node1",                   // 不好
}
```

### 2. 合理设置超时

根据操作耗时设置合适的超时时间：

```json
{
  "type": "http",
  "config": {
    "url": "https://slow-api.example.com",
    "timeout": 30000  // 30秒，给慢API足够时间
  }
}
```

### 3. 使用重试策略

对于可能失败的操作，配置重试：

```json
{
  "retryCount": 3,
  "retryPolicy": "EXPONENTIAL_WITH_JITTER"
}
```

### 4. 错误处理

使用 IF 节点处理错误：

```json
{
  "id": "check-error",
  "type": "if",
  "config": {
    "condition": "{{api-call.output.status}} >= 400"
  }
}
```

### 5. 避免大对象传递

对于大数据量，使用 MinIO 存储：

```json
{
  "type": "script",
  "config": {
    "code": "processInChunks(data);"
  }
}
```

### 6. 使用全局变量

将可复用的值定义为全局变量：

```json
{
  "globalVariables": {
    "apiUrl": "https://api.example.com",
    "apiKey": "{{env.API_KEY}}"
  }
}
```

### 7. 模块化设计

将复杂工作流拆分为多个子工作流，通过 Webhook 调用。

---

## 故障排查

### 工作流卡住不动

1. 检查是否有循环依赖
2. 查看 IF 节点条件是否永远不满足
3. 检查 WAIT/WEBHOOK 节点是否超时

### 节点执行失败

1. 查看节点日志：`/api/executions/{id}/nodes/{nodeId}`
2. 检查变量引用是否正确
3. 验证外部服务（API、数据库）是否可访问

### 内存溢出

1. 检查是否有节点返回超大结果
2. 确认 MinIO 配置正确
3. 调整 JVM 内存参数

---

## 更多资源

- [API 参考](api-reference.md) - 完整的 REST API 文档
- [架构设计](architecture.md) - 系统架构详解
- [部署指南](../docker/README.md) - Docker 部署说明

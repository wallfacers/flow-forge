# Flow-Forge API 参考

## 目录

- [概述](#概述)
- [认证](#认证)
- [工作流执行](#工作流执行)
- [执行历史](#执行历史)
- [触发器](#触发器)
- [错误码](#错误码)

---

## 概述

Flow-Forge 提供 RESTful API，所有端点都需要在请求头中提供租户 ID。

**Base URL**: `http://localhost:8080`

**API 文档**: `http://localhost:8080/swagger-ui.html`

---

## 认证

### 租户 ID

所有 API 请求必须在请求头中包含租户 ID：

```http
X-Tenant-ID: your-tenant-id
```

---

## 工作流执行

### 提交工作流执行

```http
POST /api/workflows/execute
```

**请求头**：
```
Content-Type: application/json
X-Tenant-ID: your-tenant-id
```

**请求体**：

```json
{
  "id": "workflow-001",
  "name": "数据处理工作流",
  "tenantId": "tenant-001",
  "globalVariables": {
    "apiKey": "sk-xxxxx"
  },
  "nodes": [ ... ],
  "edges": [ ... ]
}
```

**响应**：`202 Accepted`

```json
{
  "executionId": "exec-abc123",
  "status": "RUNNING",
  "startedAt": "2025-01-11T10:00:00Z"
}
```

### 获取工作流定义

```http
GET /api/workflows/{workflowId}
```

**响应**：`200 OK`

```json
{
  "id": "workflow-001",
  "name": "数据处理工作流",
  "nodes": [ ... ],
  "edges": [ ... ]
}
```

---

## 执行历史

### 获取执行列表

```http
GET /api/executions?page=0&size=10&workflowId=workflow-001&status=COMPLETED
```

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码（从 0 开始），默认 0 |
| size | int | 否 | 每页大小，默认 10 |
| workflowId | string | 否 | 工作流 ID 筛选 |
| status | string | 否 | 状态筛选 |

**响应**：`200 OK`

```json
{
  "content": [
    {
      "executionId": "exec-abc123",
      "workflowId": "workflow-001",
      "workflowName": "数据处理工作流",
      "status": "COMPLETED",
      "startedAt": "2025-01-11T10:00:00Z",
      "completedAt": "2025-01-11T10:00:15Z",
      "durationMs": 15000,
      "totalNodes": 5,
      "completedNodes": 5,
      "failedNodes": 0,
      "progress": 100
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 42,
  "totalPages": 5,
  "hasNext": true,
  "hasPrevious": false
}
```

### 获取执行详情

```http
GET /api/executions/{executionId}
```

**响应**：`200 OK`

```json
{
  "executionId": "exec-abc123",
  "workflowId": "workflow-001",
  "workflowName": "数据处理工作流",
  "tenantId": "tenant-001",
  "status": "COMPLETED",
  "startedAt": "2025-01-11T10:00:00Z",
  "completedAt": "2025-01-11T10:00:15Z",
  "durationMs": 15000,
  "totalNodes": 5,
  "completedNodes": 5,
  "failedNodes": 0,
  "progress": 100,
  "retryCount": 0,
  "isResumed": false,
  "nodeDetails": [
    {
      "nodeId": "http-request",
      "nodeName": "获取数据",
      "nodeType": "http",
      "status": "success",
      "startedAt": "2025-01-11T10:00:00Z",
      "completedAt": "2025-01-11T10:00:05Z",
      "durationMs": 5000,
      "outputData": "{\"status\":200,\"data\":{...}}"
    }
  ]
}
```

### 获取可视化图数据

```http
GET /api/executions/{executionId}/graph
```

**响应**：`200 OK`

```json
{
  "directed": true,
  "nodes": [
    {
      "id": "http-request",
      "label": "获取数据",
      "type": "http",
      "status": "success",
      "x": 0,
      "y": 0
    }
  ],
  "edges": [
    {
      "source": "http-request",
      "target": "data-process",
      "type": "default"
    }
  ]
}
```

### 获取节点列表

```http
GET /api/executions/{executionId}/nodes
```

**响应**：`200 OK`

```json
[
  {
    "nodeId": "http-request",
    "nodeName": "获取数据",
    "nodeType": "http",
    "status": "success",
    "startedAt": "2025-01-11T10:00:00Z",
    "completedAt": "2025-01-11T10:00:05Z",
    "durationMs": 5000,
    "retryCount": 0,
    "outputData": "{...}"
  }
]
```

### 获取单个节点详情

```http
GET /api/executions/{executionId}/nodes/{nodeId}
```

**响应**：`200 OK` (同上单节点格式)

---

## 触发器

### Webhook 触发（重构版）

#### 触发 Webhook

```http
POST /api/webhook/{webhookPath}
```

**请求头**：
```
Content-Type: application/json
Prefer: wait=sync  // 可选，指定同步/异步模式
X-Signature: <hmac-signature>  // 可选，如果配置了密钥
```

**请求体**：任意 JSON 数据

```json
{
  "userId": "12345",
  "amount": 99.99,
  "status": "PAID"
}
```

**同步模式响应**：`200 OK`

```json
{
  "mode": "sync",
  "executionId": "exec-abc123",
  "success": true,
  "workflowId": "workflow-001",
  "duration": 1523,
  "output": {
    // END 节点的输出
  },
  "triggerNodeId": "webhook-entry",
  "triggerType": "webhook"
}
```

**异步模式响应**：`200 OK`

```json
{
  "mode": "async",
  "executionId": "exec-abc123",
  "success": true,
  "workflowId": "workflow-001"
}
```

**错误响应**：`400 Bad Request`

```json
{
  "mode": "async",
  "success": false,
  "error": "Webhook 未注册"
}
```

#### 同步/异步模式

| 请求头 | 执行模式 | 说明 |
|--------|----------|------|
| `Prefer: wait=sync` | 同步 | 等待工作流完成，返回 END 节点输出 |
| `Prefer: wait=async` | 异步 | 立即返回 executionId |
| (无) | 根据配置 | 使用节点配置的 asyncMode，默认异步 |

#### 查询 Webhook 配置

```http
GET /api/webhook/{webhookPath}/config
```

**响应**：`200 OK`

```json
{
  "id": "uuid-123",
  "workflowId": "workflow-001",
  "nodeId": "webhook-entry",
  "tenantId": "tenant-001",
  "triggerType": "webhook",
  "webhookPath": "github-push",
  "enabled": true,
  "totalTriggers": 1234,
  "successfulTriggers": 1200,
  "failedTriggers": 34,
  "lastTriggeredAt": "2025-01-12T10:00:00Z",
  "lastTriggerStatus": "SUCCESS",
  "config": {
    "type": "webhook",
    "asyncMode": "sync",
    "timeout": 30000
  },
  "createdAt": "2025-01-01T00:00:00Z",
  "updatedAt": "2025-01-12T10:00:00Z"
}
```

#### 查询所有 Webhook

```http
GET /api/webhook?tenantId={tenantId}
```

### 统一触发器查询

#### 查询所有触发器

```http
GET /api/triggers?tenantId={tenantId}&type={type}
```

**查询参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| tenantId | string | 租户 ID 筛选 |
| type | string | 触发器类型：webhook, cron, manual, event |

**响应**：`200 OK`

```json
[
  {
    "id": "uuid-123",
    "workflowId": "workflow-001",
    "tenantId": "tenant-001",
    "nodeId": "webhook-entry",
    "triggerType": "webhook",
    "enabled": true,
    "totalTriggers": 1234,
    "successfulTriggers": 1200,
    "failedTriggers": 34,
    "lastTriggeredAt": "2025-01-12T10:00:00Z",
    "lastTriggerStatus": "SUCCESS",
    "triggerConfig": {
      "type": "webhook",
      "webhookPath": "github-push",
      "asyncMode": "sync"
    },
    "webhookPath": "github-push",
    "hasSecretKey": true,
    "cronExpression": "",
    "timezone": "Asia/Shanghai",
    "powerjobJobId": -1,
    "nextTriggerTime": null,
    "createdAt": "2025-01-01T00:00:00Z",
    "updatedAt": "2025-01-12T10:00:00Z"
  }
]
```

#### 查询工作流触发器

```http
GET /api/triggers/workflow/{workflowId}
```

#### 获取触发器统计

```http
GET /api/triggers/stats?tenantId={tenantId}
```

**响应**：`200 OK`

```json
{
  "totalCount": 25,
  "enabledCount": 20,
  "disabledCount": 5,
  "tenantId": "tenant-001"
}
```

#### 启用/禁用触发器

```http
POST /api/triggers/{id}/enable
POST /api/triggers/{id}/disable
```

#### 重置统计信息

```http
POST /api/triggers/{id}/reset-stats
```

---

## Cron 触发器（已迁移到 TRIGGER 节点）

> **注意**：Cron 触发器现已通过 TRIGGER 节点配置，请使用统一触发器 API。

### 创建 Cron 触发器（旧版，已废弃）

```http
POST /api/triggers/cron  (已废弃)
```

---

## 健康检查

### 健康状态

```http
GET /actuator/health
```

**响应**：`200 OK`

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

---

## 错误码

### HTTP 状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 请求成功 |
| 201 | 创建成功 |
| 202 | 请求已接受（异步执行） |
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

### 错误响应格式

```json
{
  "timestamp": "2025-01-11T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "工作流定义验证失败：存在循环依赖",
  "path": "/api/workflows/execute"
}
```

### 业务错误码

| 错误码 | 说明 |
|--------|------|
| `WORKFLOW_NOT_FOUND` | 工作流不存在 |
| `WORKFLOW_VALIDATION_FAILED` | 工作流定义验证失败 |
| `CYCLE_DETECTED` | 检测到循环依赖 |
| `NODE_EXECUTION_FAILED` | 节点执行失败 |
| `EXECUTION_NOT_FOUND` | 执行记录不存在 |
| `TENANT_NOT_FOUND` | 租户不存在 |
| `TRIGGER_NOT_FOUND` | 触发器不存在 |
| `INVALID_CRON_EXPRESSION` | Cron 表达式无效 |

---

## 分页规范

所有列表 API 遵循统一分页规范：

**请求参数**：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| page | 0 | 页码（从 0 开始） |
| size | 10 | 每页大小（最大 100） |
| sort | createdAt | 排序字段 |
| order | desc | 排序方向（asc/desc） |

**响应格式**：

```json
{
  "content": [ ... ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 100,
  "totalPages": 10,
  "hasNext": true,
  "hasPrevious": false,
  "isFirst": true,
  "isLast": false
}
```

---

## 速率限制

| API | 限制 |
|-----|------|
| 工作流执行 | 100 次/分钟 |
| 执行历史查询 | 1000 次/分钟 |
| Webhook 触发 | 1000 次/分钟 |

超出限制返回 `429 Too Many Requests`。

---

## SDK 使用

### Java SDK

```java
FlowForgeClient client = FlowForgeClient.builder()
    .baseUrl("http://localhost:8080")
    .tenantId("your-tenant-id")
    .build();

// 提交工作流
ExecutionResult result = client.execute(workflowDefinition);

// 查询执行历史
ExecutionHistory history = client.getExecution(executionId);
```

### Python SDK

```python
from flowforge import FlowForgeClient

client = FlowForgeClient(
    base_url="http://localhost:8080",
    tenant_id="your-tenant-id"
)

# 提交工作流
result = client.execute(workflow_definition)

# 查询执行历史
history = client.get_execution(execution_id)
```

---

## 更多资源

- [Swagger UI](http://localhost:8080/swagger-ui.html) - 交互式 API 文档
- [OpenAPI 规范](/api-docs/openapi.yaml) - 下载 OpenAPI 规范文件

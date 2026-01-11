# Flow-Forge 节点参考

本文档详细介绍 Flow-Forge 支持的所有节点类型及其配置。

---

## 目录

- [TRIGGER 节点](#trigger-节点)
- [HTTP 节点](#http-节点)
- [LOG 节点](#log-节点)
- [SCRIPT 节点](#script-节点)
- [IF 节点](#if-节点)
- [MERGE 节点](#merge-节点)
- [WEBHOOK 节点](#webhook-节点)
- [WAIT 节点](#wait-节点)
- [START/END 节点](#startend-节点)

---

## TRIGGER 节点

工作流的入口触发器节点，支持多种触发方式。每个工作流有一个 TRIGGER 节点作为 DAG 的入口点（入度为 0）。

### 支持的触发器类型

| 类型 | 说明 | 配置参数 |
|------|------|----------|
| **webhook** | HTTP Webhook 触发 | `webhookPath`, `secretKey`, `asyncMode`, `timeout` |
| **cron** | Cron 定时触发 | `cronExpression`, `timezone`, `inputData` |
| **manual** | 手动触发 | `allowedRoles` |
| **event** | 事件总线触发 | `eventType`, `filterExpression` |

### Webhook 触发器配置

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| type | string | 是 | - | 触发器类型，固定为 `webhook` |
| webhookPath | string | 是 | - | Webhook 路径，如 `github-webhook` |
| secretKey | string | 否 | - | HMAC 签名密钥 |
| asyncMode | string | 否 | async | 执行模式：`sync`（同步）或 `async`（异步） |
| timeout | int | 否 | 30000 | 同步模式超时时间（毫秒） |
| inputData | object | 否 | {} | 初始输入数据 |

### 同步/异步模式

**优先级**：HTTP 请求头 `Prefer` > 节点配置 `asyncMode` > 默认异步

| 请求头 | 执行模式 | 说明 |
|--------|----------|------|
| `Prefer: wait=sync` | 同步 | 等待工作流完成，返回完整结果 |
| `Prefer: wait=async` | 异步 | 立即返回 `executionId` |
| (无) | 根据配置 | 默认异步 |

### 配置示例

#### Webhook 触发器（同步模式）

```json
{
  "id": "webhook-entry",
  "name": "GitHub Webhook 入口",
  "type": "trigger",
  "config": {
    "type": "webhook",
    "webhookPath": "github-push",
    "secretKey": "hmac-secret-key",
    "asyncMode": "sync",
    "timeout": 30000
  }
}
```

#### Cron 触发器

```json
{
  "id": "cron-entry",
  "name": "每小时执行",
  "type": "trigger",
  "config": {
    "type": "cron",
    "cronExpression": "0 0 * * * ?",
    "timezone": "Asia/Shanghai",
    "inputData": {
      "source": "scheduled"
    }
  }
}
```

#### 手动触发器

```json
{
  "id": "manual-entry",
  "name": "手动触发",
  "type": "trigger",
  "config": {
    "type": "manual",
    "allowedRoles": ["admin", "operator"]
  }
}
```

#### 事件触发器

```json
{
  "id": "event-entry",
  "name": "用户创建事件",
  "type": "trigger",
  "config": {
    "type": "event",
    "eventType": "user.created",
    "filterExpression": "{{.data.priority}} == 'high'"
  }
}
```

### 输出格式

```json
{
  "triggerType": "webhook",
  "triggeredAt": "2025-01-12T10:00:00Z",
  "nodeId": "webhook-entry",
  "input": {
    // 触发器接收到的输入数据
  },
  "httpHeaders": {
    // Webhook 特有：HTTP 请求头
  },
  "asyncMode": "sync"
}
```

### API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/webhook/{webhookPath}` | POST | 触发 Webhook 工作流 |
| `/api/webhook/{webhookPath}/config` | GET | 查询 Webhook 配置 |
| `/api/triggers` | GET | 查询所有触发器 |
| `/api/triggers/workflow/{workflowId}` | GET | 查询工作流触发器 |

---

## HTTP 节点

发起 HTTP 请求，支持 GET/POST/PUT/DELETE/PATCH 方法。

### 配置参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| url | string | 是 | - | 请求 URL，支持变量引用 |
| method | string | 否 | GET | HTTP 方法 |
| headers | object | 否 | {} | 请求头 |
| body | string | 否 | - | 请求体 |
| timeout | int | 否 | 5000 | 超时时间（毫秒） |

### 支持的 HTTP 方法

- `GET`
- `POST`
- `PUT`
- `DELETE`
- `PATCH`
- `HEAD`
- `OPTIONS`

### 配置示例

```json
{
  "id": "call-api",
  "type": "http",
  "config": {
    "url": "https://api.example.com/users/{{.input.userId}}",
    "method": "POST",
    "headers": {
      "Authorization": "Bearer {{global.apiKey}}",
      "Content-Type": "application/json",
      "X-Request-ID": "{{system.executionId}}"
    },
    "body": "{\"name\": \"{{.input.userName}}\", \"email\": \"{{.input.email}}\"}",
    "timeout": 10000
  }
}
```

### 输出格式

```json
{
  "status": 200,
  "headers": {
    "content-type": "application/json",
    "content-length": "1234"
  },
  "body": "{...}",
  "data": {
    // 自动解析的 JSON 响应
  },
  "duration": 523
}
```

### 使用场景

- 调用外部 REST API
- 获取远程数据
- 推送数据到第三方服务
- Webhook 回调

---

## LOG 节点

输出日志到 SLF4J，用于调试和记录信息。

### 配置参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| level | string | 否 | INFO | 日志级别 |
| message | string | 是 | - | 日志内容，支持变量引用 |

### 支持的日志级别

| 级别 | 用途 |
|------|------|
| DEBUG | 调试信息 |
| INFO | 一般信息 |
| WARN | 警告信息 |
| ERROR | 错误信息 |

### 配置示例

```json
{
  "id": "log-start",
  "type": "log",
  "config": {
    "level": "INFO",
    "message": "开始处理用户 {{.input.userId}} 的请求，执行ID: {{system.executionId}}"
  }
}
```

### 输出格式

```json
{
  "level": "INFO",
  "message": "开始处理用户 user123 的请求",
  "loggedAt": "2025-01-11T10:00:00Z"
}
```

---

## SCRIPT 节点

在安全的沙箱环境中执行脚本，支持 JavaScript 和 Python。

### 配置参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| language | string | 是 | - | 脚本语言 (js/py) |
| code | string | 是 | - | 脚本代码 |
| timeout | int | 否 | 5000 | 超时时间（毫秒） |

### JavaScript 示例

```json
{
  "id": "process-data",
  "type": "script",
  "config": {
    "language": "js",
    "code": "const users = input['http-api'].output.data; result.count = users.length; result.names = users.map(u => u.name);"
  }
}
```

### Python 示例

```json
{
  "id": "calculate",
  "type": "script",
  "config": {
    "language": "py",
    "code": "data = input['http-api'].output.data\ntotal = sum(data.values)\nresult = {'total': total}"
  }
}
```

### 可用变量

| 变量 | 类型 | 说明 |
|------|------|------|
| `__input` | object | 所有输入节点的输出 |
| `__global` | object | 全局变量 |
| `__system` | object | 系统变量 |

### 内置函数

| 函数 | 说明 |
|------|------|
| `log(message)` | 输出日志 |
| `sleep(ms)` | 暂停执行 |

### 安全限制

- 禁止文件 I/O
- 禁止创建线程
- 禁止反射调用
- 内存限制：128MB
- 指令限制：10,000 条

---

## IF 节点

条件分支节点，根据条件表达式结果决定是否继续执行后续节点。

### 配置参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| condition | string | 是 | 条件表达式 |

### 条件表达式语法

支持 SpEL 表达式，可以引用节点输出：

```javascript
// 简单比较
{{http-request.output.status}} == 200

// 多条件
{{http-request.output.status}} >= 200 && {{http-request.output.status}} < 300

// 嵌套字段
{{script-node.output.data.count}} > 0

// 字符串匹配
{{http-request.output.data.type}} == 'premium'

// 空值检查
{{script-node.output.result}} != null
```

### 配置示例

```json
{
  "id": "check-status",
  "type": "if",
  "config": {
    "condition": "{{api-call.output.status}} == 200 && {{api-call.output.data.success}} == true"
  }
}
```

### 输出格式

```json
{
  "result": true,    // 条件判断结果
  "evaluated": "{{api-call.output.status}} == 200"
}
```

### 使用模式

**条件分支**：

```
┌─────────┐     ┌─────────┐     ┌─────────┐
│   API   │────▶│    IF   │────▶│  Then   │
└─────────┘     └─────────┘     └─────────┘
                     │ false
                     ▼
                  ┌─────────┐
                  │  Else   │
                  └─────────┘
```

---

## MERGE 节点

合并多个前驱节点的输出，等待所有入边节点完成。

### 配置参数

无需特殊配置，自动合并所有输入。

### 输出格式

```json
{
  "merged": {
    "node1": { /* node1 的输出 */ },
    "node2": { /* node2 的输出 */ },
    "node3": { /* node3 的输出 */ }
  }
}
```

### 使用场景

**并行聚合**：

```
        ┌─────────┐
┌───────┤  node1  │
│       └─────────┘
│                  ┌─────────┐
│       ┌─────────┤ MERGE   │────▶ next
│       │         └─────────┘
│       ┌─────────┐
└───────┤  node2  │
        └─────────┘
```

---

## WEBHOOK 节点

暂停工作流执行，等待外部 Webhook 回调后继续。

### 配置参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| timeout | int | 否 | 3600000 | 等待超时（毫秒），默认1小时 |

### 回调地址

```
POST /api/webhook/{executionId}/{nodeId}
```

### 回调请求头

```
Content-Type: application/json
X-Webhook-Secret: your-secret
```

### 配置示例

```json
{
  "id": "wait-payment",
  "type": "webhook",
  "config": {
    "timeout": 7200000
  }
}
```

### 使用流程

1. 工作流执行到 WEBHOOK 节点时暂停
2. 返回 `webhookUrl` 和 `expectedBy` 时间
3. 外部系统在超时前调用回调地址
4. 工作流恢复执行，回调数据作为节点输出

---

## WAIT 节点

暂停工作流执行指定时间。

### 配置参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| duration | int | 是 | - | 等待时间（毫秒） |

### 配置示例

```json
{
  "id": "wait-30s",
  "type": "wait",
  "config": {
    "duration": 30000
  }
}
```

### 输出格式

```json
{
  "waitedMs": 30000,
  "startedAt": "2025-01-11T10:00:00Z",
  "completedAt": "2025-01-11T10:00:30Z"
}
```

---

## START/END 节点

标记工作流的开始和结束。

### START 节点

工作流的入口点，无需配置。

```json
{
  "id": "start",
  "type": "start"
}
```

### END 节点

工作流的出口点，支持聚合多个上游节点的输出。

### 配置参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| status | string | 否 | SUCCESS | 最终状态标记 |
| aggregateOutputs | object | 否 | - | 输出聚合配置 |

### 输出聚合配置

`aggregateOutputs` 支持从多个上游节点收集并转换输出数据：

| 参数 | 类型 | 说明 |
|------|------|------|
| fromNodes | array/string | 源节点 ID 列表 |
| transform | object | 输出转换映射，支持变量引用 |

### 配置示例

#### 基础配置

```json
{
  "id": "end",
  "type": "end",
  "config": {
    "status": "SUCCESS"
  }
}
```

#### 带输出聚合

```json
{
  "id": "end",
  "type": "end",
  "config": {
    "aggregateOutputs": {
      "result": {
        "fromNodes": ["http-request", "data-process"],
        "transform": {
          "userId": "{{http-request.output.data.userId}}",
          "profile": "{{data-process.output.profile}}",
          "count": "{{data-process.output.count}}"
        }
      },
      "metadata": {
        "fromNodes": ["log-node"],
        "transform": {
          "loggedAt": "{{log-node.output.loggedAt}}"
        }
      }
    }
  }
}
```

### 输出格式

#### 无聚合配置

```json
{
  "http-request": { /* 节点输出 */ },
  "data-process": { /* 节点输出 */ },
  "log-node": { /* 节点输出 */ },
  "_metadata": {
    "endNodeId": "end",
    "workflowId": "workflow-001",
    "totalNodes": 4,
    "successCount": 4,
    "failureCount": 0
  }
}
```

#### 有聚合配置

```json
{
  "result": {
    "userId": "12345",
    "profile": { /* profile 数据 */ },
    "count": 100
  },
  "metadata": {
    "loggedAt": "2025-01-12T10:00:00Z"
  },
  "_metadata": {
    "endNodeId": "end",
    "workflowId": "workflow-001",
    "totalNodes": 4,
    "successCount": 4,
    "failureCount": 0
  }
}
```

---

## 通用参数

所有节点都支持的通用参数：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| id | string | - | 节点唯一标识（必填） |
| name | string | - | 节点显示名称 |
| description | string | - | 节点描述 |
| retryCount | int | 0 | 重试次数 |
| timeout | int | 5000 | 超时时间（毫秒） |

### 重试配置示例

```json
{
  "id": "unstable-api",
  "type": "http",
  "retryCount": 3,
  "retryPolicy": "EXPONENTIAL_WITH_JITTER",
  "config": {
    "url": "https://unstable-api.example.com"
  }
}
```

---

## 节点组合模式

### 1. 串行执行

```
A ──▶ B ──▶ C
```

### 2. 并行执行后合并

```
    ┌──▶ B ──┐
A ─┤          ├──▶ D
    └──▶ C ──┘
```

### 3. 条件分支

```
        ┌──▶ B (true)
A ───▶ IF
        └──▶ C (false)
```

### 4. 循环（通过 WAIT + WEBHOOK）

```
A ──▶ B ──▶ WAIT ──┐
      │             │
      └──── Webhook ┘
```

---

## 最佳实践

1. **合理命名**：使用有意义的节点 ID，如 `fetch-user-data` 而不是 `node1`
2. **设置超时**：为可能耗时的操作设置合适的超时
3. **错误处理**：使用 IF 节点检查错误状态
4. **日志记录**：在关键位置添加 LOG 节点
5. **脚本安全**：脚本代码中避免使用危险操作

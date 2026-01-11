# Flow-Forge DAG 工作流引擎

> 企业级 DAG 工作流引擎，支持私有化部署

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## 简介

Flow-Forge 是一个功能强大的企业级 DAG（有向无环图）工作流引擎，支持复杂业务流程的编排、执行和监控。采用 Java 21 虚拟线程技术，提供高性能的并发执行能力，适合私有化部署。

### 核心特性

| 特性 | 说明 |
|------|------|
| **DAG 编排** | 基于 JGraphT 的 DAG 引擎，支持复杂的依赖关系管理 |
| **多种节点类型** | HTTP、Log、Script、IF、Merge、Webhook、Wait 等节点 |
| **变量解析** | 支持 `{{}}` 语法的上下文变量引用 |
| **脚本执行** | 集成 GraalVM 沙箱，安全执行 JavaScript/Python 脚本 |
| **断点续传** | 支持进程崩溃后从检查点恢复执行 |
| **重试策略** | 内置多种重试策略（固定、线性、指数退避、抖动） |
| **多租户** | 基于 ThreadLocal 的租户隔离机制 |
| **可视化 API** | 提供 DAG 图数据和执行历史的可视化接口 |
| **触发器** | 支持 Webhook 和 Cron 定时触发 |

---

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- PostgreSQL 15+
- Redis 7+ (可选)
- MinIO (可选)

### Docker 部署 (推荐)

```bash
# 克隆项目
git clone https://github.com/your-org/flow-forge.git
cd flow-forge

# 配置环境变量
cat > .env << EOF
POSTGRES_HOST=your-postgres-host
POSTGRES_PORT=5432
POSTGRES_DB=flow_forge
POSTGRES_USER=flow_forge
POSTGRES_PASSWORD=your-password
EOF

# 启动服务
docker-compose -f docker/docker-compose.yml up -d

# 访问 Swagger 文档
open http://localhost:8080/swagger-ui.html
```

### 本地开发

```bash
# 克隆项目
git clone https://github.com/your-org/flow-forge.git
cd flow-forge

# 编译
mvn clean package -DskipTests

# 运行
java -jar flow-forge-api/target/flow-forge-api-1.0.0-SNAPSHOT.jar
```

---

## 项目结构

```
flow-forge/
├── flow-forge-core-model/      # 核心模型层
│   ├── model/                  # 领域模型 (Node, Edge, WorkflowDefinition)
│   ├── dsl/                    # JSON DSL 解析器
│   └── context/                # 变量解析器
├── flow-forge-nodes/           # 节点执行器
│   ├── node/                   # 各类节点实现
│   ├── sandbox/                # GraalVM 沙箱
│   └── condition/              # 条件分支处理
├── flow-forge-engine/          # 执行引擎
│   ├── scheduler/              # 调度器、入度管理
│   ├── dispatcher/             # 虚拟线程分发器
│   ├── checkpoint/             # 检查点服务
│   └── retry/                  # 重试策略
├── flow-forge-infrastructure/  # 基础设施层
│   ├── entity/                 # JPA 实体
│   ├── repository/             # 数据访问层
│   └── multitenant/            # 多租户支持
├── flow-forge-trigger/         # 触发器
│   ├── webhook/                # Webhook 触发
│   └── cron/                   # Cron 定时触发
├── flow-forge-visualizer/      # 可视化
│   ├── util/                   # 图生成器
│   └── dto/                    # 可视化数据模型
├── flow-forge-api/             # REST API 层
│   ├── controller/             # 控制器
│   ├── mapper/                 # 数据映射
│   └── config/                 # Spring 配置
├── docker/                     # Docker 部署
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── README.md
├── docs/                       # 项目文档
│   ├── architecture.md         # 架构设计
│   ├── user-guide.md           # 用户指南
│   └── api-reference.md        # API 参考
└── plan.md                     # 开发计划
```

---

## 工作流定义示例

```json
{
  "id": "workflow-001",
  "name": "数据处理工作流",
  "tenantId": "tenant-001",
  "nodes": [
    {
      "id": "http-request",
      "name": "获取用户数据",
      "type": "http",
      "config": {
        "url": "https://api.example.com/users/{{.input.userId}}",
        "method": "GET",
        "headers": {
          "Authorization": "Bearer {{global.apiKey}}"
        }
      }
    },
    {
      "id": "data-process",
      "name": "数据处理",
      "type": "script",
      "config": {
        "language": "js",
        "code": "const data = input.httpRequest.output; result.count = data.length;"
      }
    },
    {
      "id": "check-condition",
      "name": "条件判断",
      "type": "if",
      "config": {
        "condition": "{{data-process.output.count}} > 0"
      }
    },
    {
      "id": "log-result",
      "name": "记录日志",
      "type": "log",
      "config": {
        "level": "INFO",
        "message": "处理完成，共 {{data-process.output.count}} 条记录"
      }
    }
  ],
  "edges": [
    {"source": "http-request", "target": "data-process"},
    {"source": "data-process", "target": "check-condition"},
    {"source": "check-condition", "target": "log-result", "condition": "{{check-condition.output.result}} == true"}
  ]
}
```

---

## 节点类型

| 节点类型 | 说明 | 配置示例 |
|----------|------|----------|
| **HTTP** | 发起 HTTP 请求 | `url`, `method`, `headers`, `body` |
| **LOG** | 输出日志 | `level`, `message` |
| **SCRIPT** | 执行脚本 | `language`, `code` |
| **IF** | 条件分支 | `condition` |
| **MERGE** | 合并多路输入 | - |
| **WEBHOOK** | Webhook 触发节点 | `timeout` |
| **WAIT** | 等待外部回调 | `timeout` |
| **START** | 工作流开始 | - |
| **END** | 工作流结束 | - |

更多节点详情请参阅 [节点使用指南](docs/nodes.md)。

---

## 变量引用

支持以下变量引用模式：

| 模式 | 说明 | 示例 |
|------|------|------|
| `{{nodeId.output}}` | 引用节点输出 | `{{http-request.output.data}}` |
| `{{global.varName}}` | 引用全局变量 | `{{global.apiKey}}` |
| `{{.input.key}}` | 引用输入参数 | `{{.input.userId}}` |
| `{{system.executionId}}` | 引用系统变量 | `{{system.currentTime}}` |

---

## API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/executions` | GET | 获取执行历史列表 |
| `/api/executions/{id}` | GET | 获取执行详情 |
| `/api/executions/{id}/graph` | GET | 获取可视化图数据 |
| `/api/executions/{id}/nodes` | GET | 获取节点执行详情 |
| `/swagger-ui.html` | - | API 文档 |

完整 API 文档请参阅 [API 参考](docs/api-reference.md)。

---

## 文档

- [架构设计](docs/architecture.md) - 系统架构说明
- [部署指南](docker/README.md) - Docker 部署详细步骤
- [用户指南](docs/user-guide.md) - 使用说明和最佳实践
- [节点参考](docs/nodes.md) - 所有节点类型详解
- [API 参考](docs/api-reference.md) - REST API 完整文档
- [开发指南](docs/development.md) - 本地开发环境搭建

---

## 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 3.1 |
| DAG | JGraphT | 1.5+ |
| 脚本引擎 | GraalVM | 23.1.0 |
| 数据库 | PostgreSQL | 15+ |
| 缓存 | Redis | 7+ |
| 对象存储 | MinIO | Latest |
| API 文档 | SpringDoc OpenAPI | 2.0+ |

---

## 许可证

Apache License 2.0

---
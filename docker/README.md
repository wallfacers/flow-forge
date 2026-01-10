# Flow-Forge DAG 工作流引擎 - Docker 部署指南

## 目录

- [概述](#概述)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [外部 PostgreSQL 配置](#外部-postgresql-配置)
- [服务访问](#服务访问)
- [故障排查](#故障排查)

---

## 概述

Flow-Forge 是一个企业级 DAG 工作流引擎，支持私有化部署。本指南将帮助您使用 Docker Compose 快速部署应用。

**架构说明**：
- **flow-forge**: 应用服务（Docker 容器）
- **redis**: 缓存服务（Docker 容器）
- **minio**: 对象存储（Docker 容器）
- **postgresql**: 数据库（**外部实例，需自行准备**）

---

## 环境要求

### 必需组件

| 组件 | 版本要求 | 说明 |
|------|----------|------|
| Docker | >= 20.10 | 容器运行时 |
| Docker Compose | >= 2.0 | 容器编排 |
| PostgreSQL | >= 15 | 外部数据库实例 |

### 硬件建议

| 资源 | 最低配置 | 推荐配置 |
|------|----------|----------|
| CPU | 2核 | 4核+ |
| 内存 | 4GB | 8GB+ |
| 磁盘 | 20GB | 50GB+ |

---

## 快速开始

### 1. 克隆代码

```bash
git clone https://github.com/your-org/flow-forge.git
cd flow-forge
```

### 2. 创建环境变量文件

创建 `.env` 文件（与 docker-compose.yml 同目录）：

```bash
# ========================================
# 外部 PostgreSQL 连接配置 (必填)
# ========================================
POSTGRES_HOST=your-postgres-host           # PostgreSQL 服务器地址
POSTGRES_PORT=5432                          # PostgreSQL 端口
POSTGRES_DB=flow_forge                      # 数据库名称
POSTGRES_USER=flow_forge                    # 数据库用户
POSTGRES_PASSWORD=your_secure_password      # 数据库密码

# ========================================
# Redis 配置 (可选，使用默认值即可)
# ========================================
REDIS_PASSWORD=                             # Redis 密码 (空表示无密码)
REDIS_EXPOSE_PORT=6379                      # Redis 对外暴露端口

# ========================================
# MinIO 配置 (可选，使用默认值即可)
# ========================================
MINIO_ACCESS_KEY=minioadmin                 # MinIO 访问密钥
MINIO_SECRET_KEY=minioadmin                 # MinIO 秘密密钥
MINIO_BUCKET=flow-forge                     # MinIO 存储桶名称
MINIO_API_PORT=9000                         # MinIO API 端口
MINIO_CONSOLE_PORT=9001                     # MinIO 控制台端口

# ========================================
# 应用配置
# ========================================
SERVER_PORT=8080                            # 应用服务端口
LOG_LEVEL=INFO                              # 日志级别 (DEBUG/INFO/WARN/ERROR)
```

### 3. 初始化数据库

在您的 PostgreSQL 实例中执行初始化脚本：

```bash
# 方式一：使用 psql 命令
psql -h your-postgres-host -U postgres -d flow_forge -f flow-forge-infrastructure/src/main/resources/db/init.sql

# 方式二：使用 Docker
docker run --rm -v $(pwd)/flow-forge-infrastructure/src/main/resources/db:/sql \
  postgres:15 psql -h your-postgres-host -U postgres -d flow_forge -f /sql/init.sql
```

### 4. 启动服务

```bash
# 构建并启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看应用日志
docker-compose logs -f flow-forge
```

### 5. 验证部署

访问健康检查端点：
```bash
curl http://localhost:8080/actuator/health
```

预期输出：
```json
{"status":"UP"}
```

---

## 配置说明

### application.yml 配置项

| 配置项 | 环境变量 | 默认值 | 说明 |
|--------|----------|--------|------|
| `spring.datasource.url` | `POSTGRES_URL` | - | PostgreSQL JDBC URL |
| `spring.datasource.username` | `POSTGRES_USER` | `flow_forge` | 数据库用户名 |
| `spring.datasource.password` | `POSTGRES_PASSWORD` | - | 数据库密码 |
| `spring.data.redis.host` | `REDIS_HOST` | `localhost` | Redis 主机 |
| `spring.data.redis.port` | `REDIS_PORT` | `6379` | Redis 端口 |
| `minio.endpoint` | `MINIO_ENDPOINT` | `http://localhost:9000` | MinIO 服务地址 |
| `minio.access-key` | `MINIO_ACCESS_KEY` | `minioadmin` | MinIO 访问密钥 |
| `minio.secret-key` | `MINIO_SECRET_KEY` | `minioadmin` | MinIO 秘密密钥 |

### JVM 调优参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `-XX:MaxRAMPercentage` | `75.0` | 最大使用容器内存的百分比 |
| `-XX:+UseG1GC` | - | 使用 G1 垃圾收集器 |
| `-XX:+UseStringDeduplication` | - | 启用字符串去重 |

---

## 外部 PostgreSQL 配置

### 云数据库配置示例

**阿里云 RDS PostgreSQL**：
```bash
POSTGRES_HOST=pgm-xxxxxxx.rds.aliyuncs.com
POSTGRES_PORT=3433
POSTGRES_DB=flow_forge
POSTGRES_USER=flow_forge
POSTGRES_PASSWORD=YourPassword123
```

**腾讯云 PostgreSQL**：
```bash
POSTGRES_HOST=pg-xxxxxxx.sql.tencentcdb.com
POSTGRES_PORT=5432
POSTGRES_DB=flow_forge
POSTGRES_USER=flow_forge
POSTGRES_PASSWORD=YourPassword123
```

**自建 PostgreSQL (Docker)**：
```bash
# 启动 PostgreSQL 容器
docker run -d \
  --name postgres \
  -p 5432:5432 \
  -e POSTGRES_DB=flow_forge \
  -e POSTGRES_USER=flow_forge \
  -e POSTGRES_PASSWORD=YourPassword123 \
  -v postgres-data:/var/lib/postgresql/data \
  postgres:15

# 配置 Flow-Forge 连接
POSTGRES_HOST=host.docker.internal    # Docker Desktop
# POSTGRES_HOST=172.17.0.1            # Linux Docker
POSTGRES_PORT=5432
POSTGRES_DB=flow_forge
POSTGRES_USER=flow_forge
POSTGRES_PASSWORD=YourPassword123
```

### 网络连接说明

| 场景 | POSTGRES_HOST 配置 |
|------|-------------------|
| PostgreSQL 与 flow-forge 在同一 Docker 网络 | 容器名称 (如 `postgres`) |
| PostgreSQL 在宿主机 | `host.docker.internal` (Docker Desktop) / `172.17.0.1` (Linux) |
| PostgreSQL 在远程服务器 | 实际 IP 或域名 |

---

## 服务访问

### 应用服务

| 服务 | 地址 | 说明 |
|------|------|------|
| 应用 API | http://localhost:8080 | REST API |
| Swagger 文档 | http://localhost:8080/swagger-ui.html | API 文档 |
| 健康检查 | http://localhost:8080/actuator/health | 健康状态 |

### MinIO 控制台

| 项目 | 地址 | 说明 |
|------|------|------|
| 控制台 | http://localhost:9001 | MinIO 管理界面 |
| 用户名 | minioadmin | 默认访问密钥 |
| 密码 | minioadmin | 默认秘密密钥 |

### API 端点示例

```bash
# 获取执行历史列表
curl -H "X-Tenant-ID: default" http://localhost:8080/api/executions

# 获取单个执行详情
curl -H "X-Tenant-ID: default" http://localhost:8080/api/executions/{executionId}

# 获取可视化图数据
curl -H "X-Tenant-ID: default" http://localhost:8080/api/executions/{executionId}/graph
```

---

## 故障排查

### 查看日志

```bash
# 查看所有服务日志
docker-compose logs

# 查看特定服务日志
docker-compose logs flow-forge
docker-compose logs redis
docker-compose logs minio

# 实时跟踪日志
docker-compose logs -f flow-forge
```

### 常见问题

**1. 应用无法连接 PostgreSQL**

```bash
# 检查网络连通性
docker-compose exec flow-forge ping -c 3 ${POSTGRES_HOST}

# 检查端口监听
docker-compose exec flow-forge nc -zv ${POSTGRES_HOST} ${POSTGRES_PORT}

# 查看应用日志
docker-compose logs flow-forge | grep -i "postgres\|connection"
```

**2. Redis 连接失败**

```bash
# 检查 Redis 状态
docker-compose ps redis

# 进入 Redis 容器测试
docker-compose exec redis redis-cli ping
```

**3. MinIO 连接失败**

```bash
# 检查 MinIO 状态
docker-compose ps minio

# 访问 MinIO 健康检查
curl http://localhost:9000/minio/health/live
```

**4. 容器启动失败**

```bash
# 查看容器详情
docker-compose ps -a

# 查看容器日志
docker-compose logs flow-forge --tail=100

# 重新构建镜像
docker-compose build --no-cache
docker-compose up -d
```

### 完全清理

```bash
# 停止并删除所有服务
docker-compose down

# 删除所有数据卷
docker-compose down -v

# 删除镜像
docker rmi $(docker images 'flow-forge*' -q)
```

---

## 维护操作

### 备份

```bash
# 备份 PostgreSQL 数据 (外部数据库自行备份)
# 备份 Redis 数据
docker-compose exec redis redis-cli SAVE

# 备份 MinIO 数据
docker cp flow-forge-minio:/data ./minio-backup
```

### 更新

```bash
# 拉取最新代码
git pull

# 重新构建并启动
docker-compose up -d --build

# 查看更新状态
docker-compose logs -f
```

### 扩容

```bash
# 扩展应用实例
docker-compose up -d --scale flow-forge=3
```

---

## 技术支持

如有问题，请联系：
- 邮箱: support@workflow.com
- Issues: https://github.com/your-org/flow-forge/issues

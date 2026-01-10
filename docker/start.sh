#!/bin/sh
set -e

# Flow-Forge Docker 容器启动脚本
# 用于等待依赖服务就绪并启动应用

echo "=========================================="
echo "   Flow-Forge DAG Workflow Engine"
echo "=========================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 等待服务就绪的函数
wait_for_service() {
    local host=$1
    local port=$2
    local service_name=$3
    local max_attempts=${4:-30}
    local attempt=1

    log_info "Waiting for ${service_name} at ${host}:${port}..."

    while [ $attempt -le $max_attempts ]; do
        if (echo > "/dev/tcp/${host}/${port}") >/dev/null 2>&1; then
            log_info "${service_name} is ready!"
            return 0
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done

    log_error "${service_name} is not available after ${max_attempts} attempts"
    return 1
}

# 检查环境变量
check_env_vars() {
    log_info "Checking required environment variables..."

    required_vars="POSTGRES_HOST POSTGRES_PORT POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD"
    missing_vars=""

    for var in $required_vars; do
        if [ -z "$(eval echo \${$var})" ]; then
            missing_vars="$missing_vars $var"
        fi
    done

    if [ -n "$missing_vars" ]; then
        log_error "Missing required environment variables:$missing_vars"
        exit 1
    fi

    log_info "All required environment variables are set."
}

# 打印配置信息
print_config() {
    log_info "Configuration:"
    echo "  PostgreSQL: ${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}"
    echo "  Redis: ${REDIS_HOST}:${REDIS_PORT}"
    echo "  MinIO: ${MINIO_ENDPOINT}"
    echo "  Server Port: ${SERVER_PORT:-8080}"
    echo "  Spring Profile: ${SPRING_PROFILES_ACTIVE:-prod}"
}

# 等待依赖服务
wait_for_dependencies() {
    local redis_host=${REDIS_HOST:-localhost}
    local redis_port=${REDIS_PORT:-6379}

    # 如果 Redis 在 Docker 内网中，跳过检测
    if [ "$redis_host" != "localhost" ] && [ "$redis_host" != "127.0.0.1" ]; then
        wait_for_service "$redis_host" "$redis_port" "Redis" 30 || true
    fi

    # PostgreSQL 是外部的，这里只做警告
    log_warn "Make sure external PostgreSQL is accessible at ${POSTGRES_HOST}:${POSTGRES_PORT}"
}

# 主流程
main() {
    log_info "Starting Flow-Forge application..."

    check_env_vars
    print_config
    wait_for_dependencies

    log_info "All dependencies checked. Starting Spring Boot application..."

    # 执行 Java 应用
    exec java \
        -XX:+UseContainerSupport \
        -XX:MaxRAMPercentage=75.0 \
        -XX:+UseG1GC \
        -XX:+UnlockExperimentalVMOptions \
        -XX:+UseStringDeduplication \
        -Djava.security.egd=file:/dev/./urandom \
        -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} \
        -jar /app/flow-forge.jar
}

# 捕获退出信号
trap 'echo "Container stopping..."; exit 0' SIGTERM SIGINT

# 执行主流程
main "$@"

-- Flow-Forge DAG 工作流引擎 - 数据库初始化脚本
-- 适用于 PostgreSQL 15+
-- 执行方式: psql -U postgres -d flow_forge -f init.sql

-- ============================================
-- 1. 工作流执行历史表
-- ============================================
CREATE TABLE IF NOT EXISTS workflow_execution_history (
    -- 主键
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- 业务标识
    execution_id            VARCHAR(64) NOT NULL UNIQUE,
    workflow_id             VARCHAR(64) NOT NULL,
    workflow_name           VARCHAR(255) NOT NULL,
    workflow_definition     JSONB NOT NULL,
    -- 租户隔离
    tenant_id               VARCHAR(64) NOT NULL,
    -- 执行状态: RUNNING, SUCCESS, FAILED, CANCELLED, WAITING
    status                  VARCHAR(20) NOT NULL,
    -- 执行结果
    error_message           TEXT,
    -- 输入输出
    input_data              JSONB,
    output_data             JSONB,
    -- 全局变量
    global_variables        JSONB,
    -- 执行上下文 (包含节点结果、入度快照等)
    context_data            JSONB,
    -- 检查点数据 (入度快照、已完成节点列表)
    checkpoint_data         JSONB,
    -- 统计信息
    total_nodes             INTEGER NOT NULL DEFAULT 0,
    completed_nodes         INTEGER NOT NULL DEFAULT 0,
    failed_nodes            INTEGER NOT NULL DEFAULT 0,
    -- 执行时间
    started_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMPTZ,
    duration_ms             BIGINT,
    -- 重试信息
    retry_count             INTEGER NOT NULL DEFAULT 0,
    max_retry_count         INTEGER NOT NULL DEFAULT 3,
    -- 恢复信息
    is_resumed              BOOLEAN NOT NULL DEFAULT FALSE,
    resumed_from_id         UUID,
    -- 标准字段
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,
    -- 索引
    CONSTRAINT fk_resumed_from FOREIGN KEY (resumed_from_id)
        REFERENCES workflow_execution_history(id) ON DELETE SET NULL
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_weh_execution_id ON workflow_execution_history(execution_id);
CREATE INDEX IF NOT EXISTS idx_weh_workflow_id ON workflow_execution_history(workflow_id);
CREATE INDEX IF NOT EXISTS idx_weh_tenant_id ON workflow_execution_history(tenant_id);
CREATE INDEX IF NOT EXISTS idx_weh_status ON workflow_execution_history(status);
CREATE INDEX IF NOT EXISTS idx_weh_started_at ON workflow_execution_history(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_weh_created_at ON workflow_execution_history(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_weh_tenant_status ON workflow_execution_history(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_weh_deleted_at ON workflow_execution_history(deleted_at) WHERE deleted_at IS NULL;

-- 添加注释
COMMENT ON TABLE workflow_execution_history IS '工作流执行历史表';
COMMENT ON COLUMN workflow_execution_history.id IS '主键ID';
COMMENT ON COLUMN workflow_execution_history.execution_id IS '执行实例ID (业务唯一标识)';
COMMENT ON COLUMN workflow_execution_history.workflow_id IS '工作流定义ID';
COMMENT ON COLUMN workflow_execution_history.workflow_name IS '工作流名称';
COMMENT ON COLUMN workflow_execution_history.workflow_definition IS '工作流定义JSON (包含节点和边)';
COMMENT ON COLUMN workflow_execution_history.tenant_id IS '租户ID (多租户隔离)';
COMMENT ON COLUMN workflow_execution_history.status IS '执行状态: RUNNING, SUCCESS, FAILED, CANCELLED, WAITING';
COMMENT ON COLUMN workflow_execution_history.error_message IS '错误消息 (失败时记录)';
COMMENT ON COLUMN workflow_execution_history.input_data IS '输入数据JSON';
COMMENT ON COLUMN workflow_execution_history.output_data IS '输出数据JSON';
COMMENT ON COLUMN workflow_execution_history.global_variables IS '全局变量JSON';
COMMENT ON COLUMN workflow_execution_history.context_data IS '执行上下文 (节点结果映射)';
COMMENT ON COLUMN workflow_execution_history.checkpoint_data IS '检查点数据 (入度快照、已完成节点)';
COMMENT ON COLUMN workflow_execution_history.total_nodes IS '总节点数';
COMMENT ON COLUMN workflow_execution_history.completed_nodes IS '已完成节点数';
COMMENT ON COLUMN workflow_execution_history.failed_nodes IS '失败节点数';
COMMENT ON COLUMN workflow_execution_history.started_at IS '开始时间';
COMMENT ON COLUMN workflow_execution_history.completed_at IS '完成时间';
COMMENT ON COLUMN workflow_execution_history.duration_ms IS '执行时长(毫秒)';
COMMENT ON COLUMN workflow_execution_history.retry_count IS '当前重试次数';
COMMENT ON COLUMN workflow_execution_history.max_retry_count IS '最大重试次数';
COMMENT ON COLUMN workflow_execution_history.is_resumed IS '是否为恢复执行';
COMMENT ON COLUMN workflow_execution_history.resumed_from_id IS '恢复来源执行ID';
COMMENT ON COLUMN workflow_execution_history.created_at IS '创建时间';
COMMENT ON COLUMN workflow_execution_history.updated_at IS '更新时间';
COMMENT ON COLUMN workflow_execution_history.deleted_at IS '删除时间 (软删除)';

-- ============================================
-- 2. 节点执行日志表
-- ============================================
CREATE TABLE IF NOT EXISTS node_execution_log (
    -- 主键
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- 关联执行历史
    execution_id            UUID NOT NULL,
    execution_id_str        VARCHAR(64) NOT NULL,
    -- 节点信息
    node_id                 VARCHAR(64) NOT NULL,
    node_name               VARCHAR(255) NOT NULL,
    node_type               VARCHAR(20) NOT NULL,
    -- 执行状态: PENDING, RUNNING, SUCCESS, FAILED, SKIPPED, WAITING
    status                  VARCHAR(20) NOT NULL,
    -- 执行结果
    output_data             JSONB,
    error_message           TEXT,
    error_stack_trace       TEXT,
    -- 重试信息
    retry_count             INTEGER NOT NULL DEFAULT 0,
    retry_reason            VARCHAR(255),
    -- 执行时间
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    duration_ms             BIGINT,
    -- 配置快照
    node_config             JSONB,
    -- 输入快照
    input_snapshot          JSONB,
    -- 大结果处理
    large_result_blob_id    VARCHAR(255),
    large_result_size       BIGINT,
    large_result_path       VARCHAR(500),
    -- 节点入度信息 (用于断点续传)
    node_in_degree          INTEGER,
    predecessors_completed  JSONB,
    -- 标准字段
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,
    -- 外键约束
    CONSTRAINT fk_execution_history FOREIGN KEY (execution_id)
        REFERENCES workflow_execution_history(id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_nel_execution_id ON node_execution_log(execution_id);
CREATE INDEX IF NOT EXISTS idx_nel_execution_id_str ON node_execution_log(execution_id_str);
CREATE INDEX IF NOT EXISTS idx_nel_node_id ON node_execution_log(node_id);
CREATE INDEX IF NOT EXISTS idx_nel_status ON node_execution_log(status);
CREATE INDEX IF NOT EXISTS idx_nel_started_at ON node_execution_log(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_nel_node_type ON node_execution_log(node_type);
CREATE INDEX IF NOT EXISTS idx_nel_execution_node ON node_execution_log(execution_id, node_id);
CREATE INDEX IF NOT EXISTS idx_nel_deleted_at ON node_execution_log(deleted_at) WHERE deleted_at IS NULL;

-- 添加注释
COMMENT ON TABLE node_execution_log IS '节点执行日志表';
COMMENT ON COLUMN node_execution_log.id IS '主键ID';
COMMENT ON COLUMN node_execution_log.execution_id IS '关联的执行历史ID (外键)';
COMMENT ON COLUMN node_execution_log.execution_id_str IS '执行实例ID字符串 (冗余字段，便于查询)';
COMMENT ON COLUMN node_execution_log.node_id IS '节点ID';
COMMENT ON COLUMN node_execution_log.node_name IS '节点名称';
COMMENT ON COLUMN node_execution_log.node_type IS '节点类型: http, log, script, if, merge, webhook, wait, start, end';
COMMENT ON COLUMN node_execution_log.status IS '执行状态: PENDING, RUNNING, SUCCESS, FAILED, SKIPPED, WAITING';
COMMENT ON COLUMN node_execution_log.output_data IS '输出数据JSON';
COMMENT ON COLUMN node_execution_log.error_message IS '错误消息';
COMMENT ON COLUMN node_execution_log.error_stack_trace IS '错误堆栈';
COMMENT ON COLUMN node_execution_log.retry_count IS '重试次数';
COMMENT ON COLUMN node_execution_log.retry_reason IS '重试原因';
COMMENT ON COLUMN node_execution_log.started_at IS '开始执行时间';
COMMENT ON COLUMN node_execution_log.completed_at IS '完成时间';
COMMENT ON COLUMN node_execution_log.duration_ms IS '执行时长(毫秒)';
COMMENT ON COLUMN node_execution_log.node_config IS '节点配置快照';
COMMENT ON COLUMN node_execution_log.input_snapshot IS '输入数据快照';
COMMENT ON COLUMN node_execution_log.large_result_blob_id IS '大结果MinIO blob ID';
COMMENT ON COLUMN node_execution_log.large_result_size IS '大结果大小(字节)';
COMMENT ON COLUMN node_execution_log.large_result_path IS '大结果MinIO路径';
COMMENT ON COLUMN node_execution_log.node_in_degree IS '节点入度 (用于断点续传)';
COMMENT ON COLUMN node_execution_log.predecessors_completed IS '已完成的前驱节点列表';

-- ============================================
-- 3. 自动更新 updated_at 触发器
-- ============================================

-- workflow_execution_history 表的更新触发器
CREATE OR REPLACE FUNCTION update_workflow_execution_history_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_weh_updated_at ON workflow_execution_history;
CREATE TRIGGER trigger_update_weh_updated_at
    BEFORE UPDATE ON workflow_execution_history
    FOR EACH ROW
    EXECUTE FUNCTION update_workflow_execution_history_updated_at();

-- node_execution_log 表的更新触发器
CREATE OR REPLACE FUNCTION update_node_execution_log_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_nel_updated_at ON node_execution_log;
CREATE TRIGGER trigger_update_nel_updated_at
    BEFORE UPDATE ON node_execution_log
    FOR EACH ROW
    EXECUTE FUNCTION update_node_execution_log_updated_at();

-- ============================================
-- 4. 创建视图 (用于查询活跃的执行记录)
-- ============================================

-- 活跃执行视图 (未软删除的执行记录)
CREATE OR REPLACE VIEW v_active_workflow_executions AS
SELECT
    id,
    execution_id,
    workflow_id,
    workflow_name,
    tenant_id,
    status,
    error_message,
    total_nodes,
    completed_nodes,
    failed_nodes,
    started_at,
    completed_at,
    duration_ms,
    retry_count,
    is_resumed,
    created_at,
    updated_at
FROM workflow_execution_history
WHERE deleted_at IS NULL;

COMMENT ON VIEW v_active_workflow_executions IS '活跃的工作流执行视图 (未软删除)';

-- 活跃节点日志视图
CREATE OR REPLACE VIEW v_active_node_logs AS
SELECT
    id,
    execution_id,
    execution_id_str,
    node_id,
    node_name,
    node_type,
    status,
    error_message,
    retry_count,
    started_at,
    completed_at,
    duration_ms,
    created_at,
    updated_at
FROM node_execution_log
WHERE deleted_at IS NULL;

COMMENT ON VIEW v_active_node_logs IS '活跃的节点执行日志视图 (未软删除)';

-- ============================================
-- 5. 审计日志表 (可选)
-- ============================================

CREATE TABLE IF NOT EXISTS workflow_audit_log (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(64) NOT NULL,
    execution_id            VARCHAR(64),
    workflow_id             VARCHAR(64),
    -- 操作类型: START, COMPLETE, FAIL, CANCEL, RETRY, RESUME
    action_type             VARCHAR(20) NOT NULL,
    -- 操作详情
    action_detail           JSONB,
    -- 操作人/系统
    performed_by            VARCHAR(128),
    performer_type          VARCHAR(20), -- SYSTEM, USER, API
    -- 时间戳
    occurred_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- 关联IP
    client_ip               INET,
    -- 标准字段
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wal_tenant_id ON workflow_audit_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_wal_execution_id ON workflow_audit_log(execution_id);
CREATE INDEX IF NOT EXISTS idx_wal_workflow_id ON workflow_audit_log(workflow_id);
CREATE INDEX IF NOT EXISTS idx_wal_action_type ON workflow_audit_log(action_type);
CREATE INDEX IF NOT EXISTS idx_wal_occurred_at ON workflow_audit_log(occurred_at DESC);

COMMENT ON TABLE workflow_audit_log IS '工作流审计日志表';
COMMENT ON COLUMN workflow_audit_log.action_type IS '操作类型: START, COMPLETE, FAIL, CANCEL, RETRY, RESUME';
COMMENT ON COLUMN workflow_audit_log.performed_by IS '操作人标识';
COMMENT ON COLUMN workflow_audit_log.performer_type IS '操作人类型: SYSTEM, USER, API';

-- ============================================
-- 6. Webhook 注册表
-- ============================================

CREATE TABLE IF NOT EXISTS webhook_registration (
    -- 主键
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- 租户隔离
    tenant_id               VARCHAR(64) NOT NULL,
    -- 工作流关联
    workflow_id             VARCHAR(64) NOT NULL,
    workflow_name           VARCHAR(255),
    -- Webhook 配置
    webhook_path            VARCHAR(255) NOT NULL UNIQUE,  -- /webhook/{path}
    secret_key              VARCHAR(255),                  -- HMAC 签名密钥
    -- 配置选项
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    -- 请求头映射 (可选: 将请求头映射到工作流输入)
    header_mapping          JSONB,
    -- 统计信息
    total_triggers          BIGINT NOT NULL DEFAULT 0,
    successful_triggers     BIGINT NOT NULL DEFAULT 0,
    failed_triggers         BIGINT NOT NULL DEFAULT 0,
    last_triggered_at       TIMESTAMPTZ,
    last_trigger_status     VARCHAR(20),                  -- SUCCESS, FAILED
    -- 标准字段
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_webhook_tenant_id ON webhook_registration(tenant_id);
CREATE INDEX IF NOT EXISTS idx_webhook_workflow_id ON webhook_registration(workflow_id);
CREATE INDEX IF NOT EXISTS idx_webhook_path ON webhook_registration(webhook_path);
CREATE INDEX IF NOT EXISTS idx_webhook_enabled ON webhook_registration(enabled);
CREATE INDEX IF NOT EXISTS idx_webhook_deleted_at ON webhook_registration(deleted_at) WHERE deleted_at IS NULL;

-- 添加注释
COMMENT ON TABLE webhook_registration IS 'Webhook触发器注册表';
COMMENT ON COLUMN webhook_registration.webhook_path IS 'Webhook路径 (唯一标识)';
COMMENT ON COLUMN webhook_registration.secret_key IS 'HMAC签名密钥 (用于验证请求来源)';
COMMENT ON COLUMN webhook_registration.header_mapping IS '请求头映射配置';

-- webhook 更新触发器
CREATE OR REPLACE FUNCTION update_webhook_registration_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_webhook_updated_at ON webhook_registration;
CREATE TRIGGER trigger_update_webhook_updated_at
    BEFORE UPDATE ON webhook_registration
    FOR EACH ROW
    EXECUTE FUNCTION update_webhook_registration_updated_at();

-- ============================================
-- 7. Cron 触发器配置表
-- ============================================

CREATE TABLE IF NOT EXISTS cron_trigger (
    -- 主键
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- 租户隔离
    tenant_id               VARCHAR(64) NOT NULL,
    -- 工作流关联
    workflow_id             VARCHAR(64) NOT NULL,
    workflow_name           VARCHAR(255),
    -- Cron 表达式配置
    cron_expression         VARCHAR(100) NOT NULL,        -- 标准Cron表达式: "0 0 * * * ?"
    timezone                VARCHAR(50) NOT NULL DEFAULT 'Asia/Shanghai',
    -- PowerJob 任务ID (创建后返回)
    powerjob_job_id         BIGINT,
    -- 执行配置
    input_data              JSONB,                        -- 每次执行的固定输入
    misfire_strategy        VARCHAR(20) NOT NULL DEFAULT 'FIRE',  -- MISFIRE策略: FIRE, SKIP, ONCE
    -- 配置选项
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    description             VARCHAR(500),
    -- 统计信息
    total_triggers          BIGINT NOT NULL DEFAULT 0,
    successful_triggers     BIGINT NOT NULL DEFAULT 0,
    failed_triggers         BIGINT NOT NULL DEFAULT 0,
    last_triggered_at       TIMESTAMPTZ,
    next_trigger_time       TIMESTAMPTZ,
    last_trigger_status     VARCHAR(20),                  -- SUCCESS, FAILED
    -- 标准字段
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,
    -- 唯一约束
    CONSTRAINT uk_cron_workflow UNIQUE (tenant_id, workflow_id, deleted_at)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_cron_tenant_id ON cron_trigger(tenant_id);
CREATE INDEX IF NOT EXISTS idx_cron_workflow_id ON cron_trigger(workflow_id);
CREATE INDEX IF NOT EXISTS idx_cron_enabled ON cron_trigger(enabled);
CREATE INDEX IF NOT EXISTS idx_cron_powerjob_job_id ON cron_trigger(powerjob_job_id);
CREATE INDEX IF NOT EXISTS idx_cron_deleted_at ON cron_trigger(deleted_at) WHERE deleted_at IS NULL;

-- 添加注释
COMMENT ON TABLE cron_trigger IS 'Cron定时触发器配置表';
COMMENT ON COLUMN cron_trigger.cron_expression IS 'Cron表达式 (秒 分 时 日 月 周 年)';
COMMENT ON COLUMN cron_trigger.timezone IS '时区';
COMMENT ON COLUMN cron_trigger.powerjob_job_id IS 'PowerJob任务ID';
COMMENT ON COLUMN cron_trigger.misfire_strategy IS '错失触发策略: FIRE(立即执行) SKIP(跳过) ONCE(执行一次)';
COMMENT ON COLUMN cron_trigger.input_data IS '每次执行的固定输入数据';

-- cron_trigger 更新触发器
CREATE OR REPLACE FUNCTION update_cron_trigger_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_cron_trigger_updated_at ON cron_trigger;
CREATE TRIGGER trigger_update_cron_trigger_updated_at
    BEFORE UPDATE ON cron_trigger
    FOR EACH ROW
    EXECUTE FUNCTION update_cron_trigger_updated_at();

-- ============================================
-- 8. 初始化数据 (可选)
-- ============================================

-- 可以在这里插入一些初始化数据，如示例工作流等

-- ============================================
-- 7. 权限设置 (根据需要调整)
-- ============================================

-- 创建应用用户 (如果不存在)
-- DO $$
-- BEGIN
--     IF NOT EXISTS (SELECT FROM pg_user WHERE usename = 'flow_forge_app') THEN
--         CREATE USER flow_forge_app WITH PASSWORD 'your_secure_password';
--     END IF;
-- END
-- $$;

-- 授权
-- GRANT CONNECT ON DATABASE flow_forge TO flow_forge_app;
-- GRANT USAGE ON SCHEMA public TO flow_forge_app;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO flow_forge_app;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO flow_forge_app;
-- ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flow_forge_app;
-- ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO flow_forge_app;

-- ============================================
-- 8. 数据库版本信息
-- ============================================

CREATE TABLE IF NOT EXISTS schema_version (
    id INTEGER PRIMARY KEY,
    version VARCHAR(20) NOT NULL UNIQUE,
    description VARCHAR(255),
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO schema_version (id, version, description)
VALUES (1, '1.0.0', 'Initial schema: workflow_execution_history, node_execution_log, workflow_audit_log')
ON CONFLICT (id) DO NOTHING;

-- ============================================
-- 9. 触发器注册表 (统一管理所有类型触发器)
-- ============================================

CREATE TABLE IF NOT EXISTS trigger_registry (
    -- 主键
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- 工作流关联
    workflow_id             VARCHAR(64) NOT NULL,
    tenant_id               VARCHAR(64) NOT NULL,
    -- 节点关联
    node_id                 VARCHAR(64) NOT NULL,
    -- 触发器类型: WEBHOOK, CRON, MANUAL, EVENT
    trigger_type            VARCHAR(20) NOT NULL,
    -- 触发器配置 (从 Node.config 冗余存储，便于快速查询)
    trigger_config          JSONB NOT NULL,
    -- 状态
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    -- 统计信息
    total_triggers          BIGINT NOT NULL DEFAULT 0,
    successful_triggers     BIGINT NOT NULL DEFAULT 0,
    failed_triggers         BIGINT NOT NULL DEFAULT 0,
    last_triggered_at       TIMESTAMPTZ,
    last_trigger_status     VARCHAR(20),
    -- Webhook 专用字段
    webhook_path            VARCHAR(255) UNIQUE,
    secret_key              VARCHAR(255),
    -- Cron 专用字段
    cron_expression         VARCHAR(100),
    timezone                VARCHAR(50) DEFAULT 'Asia/Shanghai',
    powerjob_job_id         BIGINT,
    next_trigger_time       TIMESTAMPTZ,
    -- 标准字段
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_trigger_workflow_id ON trigger_registry(workflow_id);
CREATE INDEX IF NOT EXISTS idx_trigger_tenant_id ON trigger_registry(tenant_id);
CREATE INDEX IF NOT EXISTS idx_trigger_type ON trigger_registry(trigger_type);
CREATE INDEX IF NOT EXISTS idx_trigger_node_id ON trigger_registry(node_id);
CREATE INDEX IF NOT EXISTS idx_trigger_webhook_path ON trigger_registry(webhook_path) WHERE webhook_path IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_trigger_enabled ON trigger_registry(enabled);
CREATE INDEX IF NOT EXISTS idx_trigger_deleted_at ON trigger_registry(deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_trigger_tenant_workflow ON trigger_registry(tenant_id, workflow_id);

-- 添加注释
COMMENT ON TABLE trigger_registry IS '触发器注册表 (统一管理所有类型触发器)';
COMMENT ON COLUMN trigger_registry.workflow_id IS '工作流ID';
COMMENT ON COLUMN trigger_registry.node_id IS '触发器节点ID';
COMMENT ON COLUMN trigger_registry.trigger_type IS '触发器类型: WEBHOOK, CRON, MANUAL, EVENT';
COMMENT ON COLUMN trigger_registry.webhook_path IS 'Webhook路径 (仅WEBHOOK类型)';
COMMENT ON COLUMN trigger_registry.secret_key IS 'HMAC签名密钥 (仅WEBHOOK类型)';
COMMENT ON COLUMN trigger_registry.cron_expression IS 'Cron表达式 (仅CRON类型)';
COMMENT ON COLUMN trigger_registry.powerjob_job_id IS 'PowerJob任务ID (仅CRON类型)';

-- trigger_registry 更新触发器
CREATE OR REPLACE FUNCTION update_trigger_registry_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_trigger_registry_updated_at ON trigger_registry;
CREATE TRIGGER trigger_update_trigger_registry_updated_at
    BEFORE UPDATE ON trigger_registry
    FOR EACH ROW
    EXECUTE FUNCTION update_trigger_registry_updated_at();

-- ============================================
-- 脚本结束
-- ============================================

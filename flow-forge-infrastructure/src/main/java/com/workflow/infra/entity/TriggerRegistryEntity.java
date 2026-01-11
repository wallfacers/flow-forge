package com.workflow.infra.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 触发器注册表实体
 * <p>
 * 冗余存储工作流中的触发器配置，便于高性能查询
 * </p>
 * <p>
 * 支持 WEBHOOK、CRON、MANUAL、EVENT 四种触发器类型
 * </p>
 */
@Entity
@Table(name = "trigger_registry", indexes = {
        @Index(name = "idx_trigger_workflow_id", columnList = "workflow_id"),
        @Index(name = "idx_trigger_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_trigger_type", columnList = "trigger_type"),
        @Index(name = "idx_trigger_node_id", columnList = "node_id"),
        @Index(name = "idx_trigger_webhook_path", columnList = "webhook_path"),
        @Index(name = "idx_trigger_enabled", columnList = "enabled"),
        @Index(name = "idx_trigger_deleted_at", columnList = "deleted_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerRegistryEntity extends BaseEntity {

    /**
     * 工作流ID
     */
    @Column(name = "workflow_id", nullable = false, length = 64)
    private String workflowId;

    /**
     * 租户ID
     */
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    /**
     * 节点ID
     */
    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    /**
     * 触发器类型: WEBHOOK, CRON, MANUAL, EVENT
     */
    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType;

    /**
     * 触发器配置 (JSONB)
     */
    @Column(name = "trigger_config", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> triggerConfig;

    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 总触发次数
     */
    @Column(name = "total_triggers", nullable = false)
    @Builder.Default
    private Long totalTriggers = 0L;

    /**
     * 成功触发次数
     */
    @Column(name = "successful_triggers", nullable = false)
    @Builder.Default
    private Long successfulTriggers = 0L;

    /**
     * 失败触发次数
     */
    @Column(name = "failed_triggers", nullable = false)
    @Builder.Default
    private Long failedTriggers = 0L;

    /**
     * 最后触发时间
     */
    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    /**
     * 最后触发状态: SUCCESS, FAILED
     */
    @Column(name = "last_trigger_status", length = 20)
    private String lastTriggerStatus;

    // ====================
    // Webhook 专用字段
    // ====================

    /**
     * Webhook 路径 (仅 WEBHOOK 类型)
     */
    @Column(name = "webhook_path", unique = true, length = 255)
    private String webhookPath;

    /**
     * HMAC 签名密钥 (仅 WEBHOOK 类型)
     */
    @Column(name = "secret_key", length = 255)
    private String secretKey;

    // ====================
    // Cron 专用字段
    // ====================

    /**
     * Cron 表达式 (仅 CRON 类型)
     */
    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    /**
     * 时区
     */
    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "Asia/Shanghai";

    /**
     * PowerJob 任务ID (仅 CRON 类型)
     */
    @Column(name = "powerjob_job_id")
    private Long powerjobJobId;

    /**
     * 下次触发时间
     */
    @Column(name = "next_trigger_time")
    private Instant nextTriggerTime;

    // ====================
    // 业务方法
    // ====================

    /**
     * 增加触发统计
     *
     * @param success 是否成功
     */
    public void incrementTrigger(boolean success) {
        this.totalTriggers++;
        if (success) {
            this.successfulTriggers++;
            this.lastTriggerStatus = "SUCCESS";
        } else {
            this.failedTriggers++;
            this.lastTriggerStatus = "FAILED";
        }
        this.lastTriggeredAt = Instant.now();
    }

    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        this.totalTriggers = 0L;
        this.successfulTriggers = 0L;
        this.failedTriggers = 0L;
    }

    /**
     * 是否为 Webhook 类型
     */
    public boolean isWebhook() {
        return "WEBHOOK".equalsIgnoreCase(triggerType);
    }

    /**
     * 是否为 Cron 类型
     */
    public boolean isCron() {
        return "CRON".equalsIgnoreCase(triggerType);
    }

    /**
     * 是否为 Manual 类型
     */
    public boolean isManual() {
        return "MANUAL".equalsIgnoreCase(triggerType);
    }

    /**
     * 是否为 Event 类型
     */
    public boolean isEvent() {
        return "EVENT".equalsIgnoreCase(triggerType);
    }
}

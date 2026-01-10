package com.workflow.infra.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Map;

/**
 * Webhook 触发器注册实体。
 * <p>
 * 用于存储外部系统触发工作流的 Webhook 配置。
 * </p>
 */
@Entity
@Table(name = "webhook_registration", indexes = {
        @Index(name = "idx_webhook_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_webhook_workflow_id", columnList = "workflow_id"),
        @Index(name = "idx_webhook_path", columnList = "webhook_path"),
        @Index(name = "idx_webhook_enabled", columnList = "enabled"),
        @Index(name = "idx_webhook_deleted_at", columnList = "deleted_at")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookRegistrationEntity extends BaseEntity {

    /**
     * 租户ID
     */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /**
     * 工作流ID
     */
    @Column(name = "workflow_id", nullable = false)
    private String workflowId;

    /**
     * 工作流名称
     */
    @Column(name = "workflow_name")
    private String workflowName;

    /**
     * Webhook 路径 (唯一标识)
     */
    @Column(name = "webhook_path", nullable = false, unique = true, length = 255)
    private String webhookPath;

    /**
     * HMAC 签名密钥 (用于验证请求来源)
     */
    @Column(name = "secret_key", length = 255)
    private String secretKey;

    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 请求头映射配置 (将请求头映射到工作流输入)
     */
    @Column(name = "header_mapping")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> headerMapping;

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
    private java.time.Instant lastTriggeredAt;

    /**
     * 最后触发状态
     */
    @Column(name = "last_trigger_status", length = 20)
    private String lastTriggerStatus;

    /**
     * 增加触发统计
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
        this.lastTriggeredAt = java.time.Instant.now();
    }

    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        this.totalTriggers = 0L;
        this.successfulTriggers = 0L;
        this.failedTriggers = 0L;
        this.lastTriggeredAt = null;
        this.lastTriggerStatus = null;
    }
}

package com.workflow.infra.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Cron 定时触发器配置实体。
 * <p>
 * 用于存储基于 Cron 表达式的定时触发器配置。
 * </p>
 */
@Entity
@Table(name = "cron_trigger", indexes = {
        @Index(name = "idx_cron_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_cron_workflow_id", columnList = "workflow_id"),
        @Index(name = "idx_cron_enabled", columnList = "enabled"),
        @Index(name = "idx_cron_powerjob_job_id", columnList = "powerjob_job_id"),
        @Index(name = "idx_cron_deleted_at", columnList = "deleted_at"),
        @Index(name = "idx_cron_tenant_workflow", columnList = "tenant_id, workflow_id")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CronTriggerEntity extends BaseEntity {

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
     * Cron 表达式 (标准Cron表达式: "0 0 * * * ?")
     */
    @Column(name = "cron_expression", nullable = false, length = 100)
    private String cronExpression;

    /**
     * 时区
     */
    @Column(name = "timezone", nullable = false, length = 50)
    @Builder.Default
    private String timezone = "Asia/Shanghai";

    /**
     * PowerJob 任务ID
     */
    @Column(name = "powerjob_job_id")
    private Long powerjobJobId;

    /**
     * 每次执行的固定输入数据
     */
    @Column(name = "input_data")
    @JdbcTypeCode(SqlTypes.JSON)
    private Object inputData;

    /**
     * 错失触发策略: FIRE(立即执行) SKIP(跳过) ONCE(执行一次)
     */
    @Column(name = "misfire_strategy", nullable = false, length = 20)
    @Builder.Default
    private String misfireStrategy = "FIRE";

    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 描述
     */
    @Column(name = "description", length = 500)
    private String description;

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
     * 下次触发时间
     */
    @Column(name = "next_trigger_time")
    private java.time.Instant nextTriggerTime;

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

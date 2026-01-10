package com.workflow.infra.entity;

import com.workflow.model.ExecutionStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 工作流执行历史实体。
 * <p>
 * 对应数据库表: workflow_execution_history
 * <p>
 * 记录每次工作流执行的完整信息，包括输入、输出、状态、检查点等。
 * 支持断点续传和执行恢复。
 */
@Entity
@Table(name = "workflow_execution_history",
       indexes = {
           @Index(name = "idx_weh_execution_id", columnList = "execution_id"),
           @Index(name = "idx_weh_workflow_id", columnList = "workflow_id"),
           @Index(name = "idx_weh_tenant_id", columnList = "tenant_id"),
           @Index(name = "idx_weh_status", columnList = "status"),
           @Index(name = "idx_weh_started_at", columnList = "started_at"),
           @Index(name = "idx_weh_tenant_status", columnList = "tenant_id, status"),
           @Index(name = "idx_weh_deleted_at", columnList = "deleted_at")
       })
public class WorkflowExecutionEntity extends BaseEntity {

    /**
     * 执行实例ID（业务唯一标识）
     */
    @Column(name = "execution_id", nullable = false, unique = true, length = 64)
    private String executionId;

    /**
     * 工作流定义ID
     */
    @Column(name = "workflow_id", nullable = false, length = 64)
    private String workflowId;

    /**
     * 工作流名称
     */
    @Column(name = "workflow_name", nullable = false)
    private String workflowName;

    /**
     * 工作流定义JSON（包含节点和边）
     */
    @Column(name = "workflow_definition", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> workflowDefinition;

    /**
     * 租户ID（多租户隔离）
     */
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    /**
     * 执行状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExecutionStatus status;

    /**
     * 错误消息（失败时记录）
     */
    @Column(name = "error_message")
    private String errorMessage;

    /**
     * 输入数据JSON
     */
    @Column(name = "input_data")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> inputData;

    /**
     * 输出数据JSON
     */
    @Column(name = "output_data")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> outputData;

    /**
     * 全局变量JSON
     */
    @Column(name = "global_variables")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> globalVariables;

    /**
     * 执行上下文（包含节点结果、入度快照等）
     */
    @Column(name = "context_data")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> contextData;

    /**
     * 检查点数据（入度快照、已完成节点列表）
     */
    @Column(name = "checkpoint_data")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> checkpointData;

    /**
     * 总节点数
     */
    @Column(name = "total_nodes", nullable = false)
    private Integer totalNodes = 0;

    /**
     * 已完成节点数
     */
    @Column(name = "completed_nodes", nullable = false)
    private Integer completedNodes = 0;

    /**
     * 失败节点数
     */
    @Column(name = "failed_nodes", nullable = false)
    private Integer failedNodes = 0;

    /**
     * 开始时间
     */
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /**
     * 完成时间
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * 执行时长（毫秒）
     */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * 当前重试次数
     */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /**
     * 最大重试次数
     */
    @Column(name = "max_retry_count", nullable = false)
    private Integer maxRetryCount = 3;

    /**
     * 是否为恢复执行
     */
    @Column(name = "is_resumed", nullable = false)
    private Boolean isResumed = false;

    /**
     * 恢复来源执行ID
     */
    @Column(name = "resumed_from_id")
    private UUID resumedFromId;

    /**
     * 关联的恢复来源执行实体
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resumed_from_id", insertable = false, updatable = false)
    private WorkflowExecutionEntity resumedFrom;

    // ========== Getters and Setters ==========

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public Map<String, Object> getWorkflowDefinition() {
        return workflowDefinition;
    }

    public void setWorkflowDefinition(Map<String, Object> workflowDefinition) {
        this.workflowDefinition = workflowDefinition;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Map<String, Object> getInputData() {
        return inputData;
    }

    public void setInputData(Map<String, Object> inputData) {
        this.inputData = inputData;
    }

    public Map<String, Object> getOutputData() {
        return outputData;
    }

    public void setOutputData(Map<String, Object> outputData) {
        this.outputData = outputData;
    }

    public Map<String, Object> getGlobalVariables() {
        return globalVariables;
    }

    public void setGlobalVariables(Map<String, Object> globalVariables) {
        this.globalVariables = globalVariables;
    }

    public Map<String, Object> getContextData() {
        return contextData;
    }

    public void setContextData(Map<String, Object> contextData) {
        this.contextData = contextData;
    }

    public Map<String, Object> getCheckpointData() {
        return checkpointData;
    }

    public void setCheckpointData(Map<String, Object> checkpointData) {
        this.checkpointData = checkpointData;
    }

    public Integer getTotalNodes() {
        return totalNodes;
    }

    public void setTotalNodes(Integer totalNodes) {
        this.totalNodes = totalNodes;
    }

    public Integer getCompletedNodes() {
        return completedNodes;
    }

    public void setCompletedNodes(Integer completedNodes) {
        this.completedNodes = completedNodes;
    }

    public Integer getFailedNodes() {
        return failedNodes;
    }

    public void setFailedNodes(Integer failedNodes) {
        this.failedNodes = failedNodes;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(Integer maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public Boolean getIsResumed() {
        return isResumed;
    }

    public void setIsResumed(Boolean isResumed) {
        this.isResumed = isResumed;
    }

    public UUID getResumedFromId() {
        return resumedFromId;
    }

    public void setResumedFromId(UUID resumedFromId) {
        this.resumedFromId = resumedFromId;
    }

    public WorkflowExecutionEntity getResumedFrom() {
        return resumedFrom;
    }

    public void setResumedFrom(WorkflowExecutionEntity resumedFrom) {
        this.resumedFrom = resumedFrom;
        if (resumedFrom != null) {
            this.resumedFromId = resumedFrom.getId();
        }
    }

    // ========== Convenience Methods ==========

    /**
     * 检查是否已完成（成功或失败）
     */
    public boolean isFinished() {
        return status == ExecutionStatus.SUCCESS
                || status == ExecutionStatus.FAILED
                || status == ExecutionStatus.CANCELLED
                || status == ExecutionStatus.TIMEOUT;
    }

    /**
     * 检查是否可以重试
     */
    public boolean canRetry() {
        return status == ExecutionStatus.FAILED
                && retryCount < maxRetryCount;
    }

    /**
     * 检查是否可以恢复
     */
    public boolean canResume() {
        return status == ExecutionStatus.FAILED
                || status == ExecutionStatus.RUNNING
                || status == ExecutionStatus.WAITING;
    }

    /**
     * 增加已完成节点计数
     */
    public void incrementCompletedNodes() {
        this.completedNodes++;
    }

    /**
     * 增加失败节点计数
     */
    public void incrementFailedNodes() {
        this.failedNodes++;
    }

    /**
     * 增加重试计数
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * 计算进度百分比
     */
    public int getProgressPercentage() {
        if (totalNodes == null || totalNodes == 0) {
            return 0;
        }
        return (completedNodes * 100) / totalNodes;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }
}

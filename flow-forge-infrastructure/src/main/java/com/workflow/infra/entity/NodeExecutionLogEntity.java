package com.workflow.infra.entity;

import com.workflow.model.NodeType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 节点执行日志实体。
 * <p>
 * 对应数据库表: node_execution_log
 * <p>
 * 记录每个节点的执行详情，包括输入、输出、错误、重试等信息。
 * 支持断点续传时的节点状态恢复。
 */
@Entity
@Table(name = "node_execution_log",
       indexes = {
           @Index(name = "idx_nel_execution_id", columnList = "execution_id"),
           @Index(name = "idx_nel_execution_id_str", columnList = "execution_id_str"),
           @Index(name = "idx_nel_node_id", columnList = "node_id"),
           @Index(name = "idx_nel_status", columnList = "status"),
           @Index(name = "idx_nel_started_at", columnList = "started_at"),
           @Index(name = "idx_nel_node_type", columnList = "node_type"),
           @Index(name = "idx_nel_execution_node", columnList = "execution_id, node_id"),
           @Index(name = "idx_nel_deleted_at", columnList = "deleted_at")
       })
public class NodeExecutionLogEntity extends BaseEntity {

    /**
     * 关联的执行历史ID（外键）
     */
    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    /**
     * 执行实例ID字符串（冗余字段，便于查询）
     */
    @Column(name = "execution_id_str", nullable = false, length = 64)
    private String executionIdStr;

    /**
     * 节点ID
     */
    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    /**
     * 节点名称
     */
    @Column(name = "node_name", nullable = false)
    private String nodeName;

    /**
     * 节点类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 20)
    private NodeType nodeType;

    /**
     * 执行状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NodeExecutionStatus status;

    /**
     * 输出数据JSON
     */
    @Column(name = "output_data")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> outputData;

    /**
     * 错误消息
     */
    @Column(name = "error_message")
    private String errorMessage;

    /**
     * 错误堆栈
     */
    @Column(name = "error_stack_trace")
    private String errorStackTrace;

    /**
     * 重试次数
     */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /**
     * 重试原因
     */
    @Column(name = "retry_reason", length = 255)
    private String retryReason;

    /**
     * 开始执行时间
     */
    @Column(name = "started_at")
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
     * 节点配置快照
     */
    @Column(name = "node_config")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> nodeConfig;

    /**
     * 输入数据快照
     */
    @Column(name = "input_snapshot")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> inputSnapshot;

    /**
     * 大结果MinIO blob ID
     */
    @Column(name = "large_result_blob_id", length = 255)
    private String largeResultBlobId;

    /**
     * 大结果大小（字节）
     */
    @Column(name = "large_result_size")
    private Long largeResultSize;

    /**
     * 大结果MinIO路径
     */
    @Column(name = "large_result_path", length = 500)
    private String largeResultPath;

    /**
     * 节点入度（用于断点续传）
     */
    @Column(name = "node_in_degree")
    private Integer nodeInDegree;

    /**
     * 已完成的前驱节点列表
     */
    @Column(name = "predecessors_completed")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> predecessorsCompleted;

    /**
     * 关联的执行历史实体
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", insertable = false, updatable = false)
    private WorkflowExecutionEntity workflowExecution;

    /**
     * 节点执行状态枚举
     */
    public enum NodeExecutionStatus {
        /**
         * 等待执行
         */
        PENDING,
        /**
         * 正在执行
         */
        RUNNING,
        /**
         * 执行成功
         */
        SUCCESS,
        /**
         * 执行失败
         */
        FAILED,
        /**
         * 跳过执行
         */
        SKIPPED,
        /**
         * 等待中（用于WAIT节点）
         */
        WAITING
    }

    // ========== Getters and Setters ==========

    public UUID getExecutionId() {
        return executionId;
    }

    public void setExecutionId(UUID executionId) {
        this.executionId = executionId;
    }

    public String getExecutionIdStr() {
        return executionIdStr;
    }

    public void setExecutionIdStr(String executionIdStr) {
        this.executionIdStr = executionIdStr;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public void setNodeType(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    public NodeExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(NodeExecutionStatus status) {
        this.status = status;
    }

    public Map<String, Object> getOutputData() {
        return outputData;
    }

    public void setOutputData(Map<String, Object> outputData) {
        this.outputData = outputData;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorStackTrace() {
        return errorStackTrace;
    }

    public void setErrorStackTrace(String errorStackTrace) {
        this.errorStackTrace = errorStackTrace;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getRetryReason() {
        return retryReason;
    }

    public void setRetryReason(String retryReason) {
        this.retryReason = retryReason;
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

    public Map<String, Object> getNodeConfig() {
        return nodeConfig;
    }

    public void setNodeConfig(Map<String, Object> nodeConfig) {
        this.nodeConfig = nodeConfig;
    }

    public Map<String, Object> getInputSnapshot() {
        return inputSnapshot;
    }

    public void setInputSnapshot(Map<String, Object> inputSnapshot) {
        this.inputSnapshot = inputSnapshot;
    }

    public String getLargeResultBlobId() {
        return largeResultBlobId;
    }

    public void setLargeResultBlobId(String largeResultBlobId) {
        this.largeResultBlobId = largeResultBlobId;
    }

    public Long getLargeResultSize() {
        return largeResultSize;
    }

    public void setLargeResultSize(Long largeResultSize) {
        this.largeResultSize = largeResultSize;
    }

    public String getLargeResultPath() {
        return largeResultPath;
    }

    public void setLargeResultPath(String largeResultPath) {
        this.largeResultPath = largeResultPath;
    }

    public Integer getNodeInDegree() {
        return nodeInDegree;
    }

    public void setNodeInDegree(Integer nodeInDegree) {
        this.nodeInDegree = nodeInDegree;
    }

    public List<String> getPredecessorsCompleted() {
        return predecessorsCompleted;
    }

    public void setPredecessorsCompleted(List<String> predecessorsCompleted) {
        this.predecessorsCompleted = predecessorsCompleted;
    }

    public WorkflowExecutionEntity getWorkflowExecution() {
        return workflowExecution;
    }

    public void setWorkflowExecution(WorkflowExecutionEntity workflowExecution) {
        this.workflowExecution = workflowExecution;
        if (workflowExecution != null) {
            this.executionId = workflowExecution.getId();
            this.executionIdStr = workflowExecution.getExecutionId();
        }
    }

    // ========== Convenience Methods ==========

    /**
     * 检查是否已完成
     */
    public boolean isFinished() {
        return status == NodeExecutionStatus.SUCCESS
                || status == NodeExecutionStatus.FAILED
                || status == NodeExecutionStatus.SKIPPED;
    }

    /**
     * 检查是否可以重试
     */
    public boolean canRetry() {
        return status == NodeExecutionStatus.FAILED;
    }

    /**
     * 检查是否有大结果
     */
    public boolean hasLargeResult() {
        return largeResultBlobId != null && !largeResultBlobId.isEmpty();
    }

    /**
     * 增加重试计数
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * 标记为开始执行
     */
    public void markAsStarted() {
        this.status = NodeExecutionStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    /**
     * 标记为完成
     */
    public void markAsCompleted() {
        this.status = NodeExecutionStatus.SUCCESS;
        this.completedAt = Instant.now();
        if (startedAt != null) {
            this.durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();
        }
    }

    /**
     * 标记为失败
     */
    public void markAsFailed(String errorMessage, String errorStackTrace) {
        this.status = NodeExecutionStatus.FAILED;
        this.completedAt = Instant.now();
        this.errorMessage = errorMessage;
        this.errorStackTrace = errorStackTrace;
        if (startedAt != null) {
            this.durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();
        }
    }

    /**
     * 标记为跳过
     */
    public void markAsSkipped() {
        this.status = NodeExecutionStatus.SKIPPED;
        this.completedAt = Instant.now();
    }

    /**
     * 标记为等待
     */
    public void markAsWaiting() {
        this.status = NodeExecutionStatus.WAITING;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = NodeExecutionStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }
}

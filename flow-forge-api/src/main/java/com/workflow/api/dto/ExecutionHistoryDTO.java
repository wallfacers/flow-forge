package com.workflow.api.dto;

import com.workflow.model.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 执行历史DTO
 * <p>
 * 用于API返回的执行历史数据
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionHistoryDTO {

    /**
     * 执行实例ID
     */
    private String executionId;

    /**
     * 工作流ID
     */
    private String workflowId;

    /**
     * 工作流名称
     */
    private String workflowName;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 执行状态
     */
    private ExecutionStatus status;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 开始时间
     */
    private Instant startedAt;

    /**
     * 完成时间
     */
    private Instant completedAt;

    /**
     * 执行耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 总节点数
     */
    private Integer totalNodes;

    /**
     * 已完成节点数
     */
    private Integer completedNodes;

    /**
     * 失败节点数
     */
    private Integer failedNodes;

    /**
     * 进度百分比
     */
    private Integer progress;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 是否为恢复执行
     */
    private Boolean isResumed;

    /**
     * 恢复来源执行ID
     */
    private String resumedFromId;

    /**
     * 节点执行详情列表
     */
    private List<NodeExecutionDetail> nodeDetails;

    /**
     * 节点执行详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeExecutionDetail {

        /**
         * 节点ID
         */
        private String nodeId;

        /**
         * 节点名称
         */
        private String nodeName;

        /**
         * 节点类型
         */
        private String nodeType;

        /**
         * 执行状态
         */
        private String status;

        /**
         * 开始时间
         */
        private Instant startedAt;

        /**
         * 完成时间
         */
        private Instant completedAt;

        /**
         * 执行耗时（毫秒）
         */
        private Long durationMs;

        /**
         * 重试次数
         */
        private Integer retryCount;

        /**
         * 错误消息
         */
        private String errorMessage;

        /**
         * 输出数据（JSON字符串）
         */
        private String outputData;
    }
}

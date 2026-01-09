package com.workflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;

/**
 * 检查点数据
 * <p>
 * 用于断点续传，保存工作流执行的中间状态
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 执行实例ID
     */
    private String executionId;

    /**
     * 工作流定义ID
     */
    private String workflowId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 执行状态
     */
    private ExecutionStatus status;

    /**
     * 当前执行节点ID
     */
    private String currentNodeId;

    /**
     * 节点执行结果
     */
    private Map<String, NodeResult> nodeResults;

    /**
     * 全局变量
     */
    private Map<String, Object> globalVariables;

    /**
     * 输入参数
     */
    private Map<String, Object> input;

    /**
     * 入度快照
     */
    private Map<String, Integer> inDegreeSnapshot;

    /**
     * 已完成节点集合
     */
    private Set<String> completedNodes;

    /**
     * 检查点创建时间
     */
    @Builder.Default
    private Instant checkpointTime = Instant.now();
}

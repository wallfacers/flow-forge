package com.workflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流边模型
 * <p>
 * 定义DAG中节点之间的连接关系，支持条件分支
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Edge {

    /**
     * 边的唯一标识符（可选）
     */
    private String id;

    /**
     * 源节点ID
     */
    private String sourceNodeId;

    /**
     * 目标节点ID
     */
    private String targetNodeId;

    /**
     * 条件表达式（可选）
     * <p>
     * 使用SpEL表达式，当条件为true时才走这条边
     * 例如: "{{node1.status == 200}}"
     * </p>
     */
    private String condition;

    /**
     * 边的标签（用于可视化）
     */
    private String label;

    /**
     * 边的权重（可选，用于调度优化）
     */
    @Builder.Default
    private int weight = 0;

    /**
     * 创建无条件边
     *
     * @param sourceNodeId 源节点ID
     * @param targetNodeId 目标节点ID
     * @return 边对象
     */
    public static Edge of(String sourceNodeId, String targetNodeId) {
        return Edge.builder()
                .sourceNodeId(sourceNodeId)
                .targetNodeId(targetNodeId)
                .build();
    }

    /**
     * 创建有条件边
     *
     * @param sourceNodeId 源节点ID
     * @param targetNodeId 目标节点ID
     * @param condition    条件表达式
     * @return 边对象
     */
    public static Edge of(String sourceNodeId, String targetNodeId, String condition) {
        return Edge.builder()
                .sourceNodeId(sourceNodeId)
                .targetNodeId(targetNodeId)
                .condition(condition)
                .build();
    }

    /**
     * 验证边配置是否有效
     *
     * @throws WorkflowValidationException 配置无效时抛出
     */
    public void validate() throws WorkflowValidationException {
        if (sourceNodeId == null || sourceNodeId.isBlank()) {
            throw new WorkflowValidationException("Edge source node ID cannot be null or empty");
        }
        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw new WorkflowValidationException("Edge target node ID cannot be null or empty");
        }
        if (sourceNodeId.equals(targetNodeId)) {
            throw new WorkflowValidationException("Edge cannot form a self-loop: " + sourceNodeId);
        }
    }
}

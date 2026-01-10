package com.workflow.visualizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可视化边数据
 * <p>
 * 用于前端渲染 DAG 边，兼容 D3.js/Cytoscape.js 格式
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphEdge {

    /**
     * 边ID（可选，用于唯一标识）
     */
    private String id;

    /**
     * 源节点ID
     */
    private String source;

    /**
     * 目标节点ID
     */
    private String target;

    /**
     * 边标签（显示条件或描述）
     */
    private String label;

    /**
     * 条件表达式
     */
    private String condition;

    /**
     * 边类型（default, conditional, error等）
     */
    private String type;

    /**
     * 是否被激活（条件为true时）
     */
    private Boolean active;

    /**
     * 从Edge模型创建可视化边
     */
    public static GraphEdge from(com.workflow.model.Edge edge) {
        GraphEdgeBuilder builder = GraphEdge.builder()
                .id(edge.getId())
                .source(edge.getSourceNodeId())
                .target(edge.getTargetNodeId())
                .condition(edge.getCondition());

        // 设置标签：优先使用label，否则使用condition的前30个字符
        if (edge.getLabel() != null && !edge.getLabel().isEmpty()) {
            builder.label(edge.getLabel());
        } else if (edge.getCondition() != null && !edge.getCondition().isEmpty()) {
            String cond = edge.getCondition();
            builder.label(cond.length() > 30 ? cond.substring(0, 27) + "..." : cond);
            builder.type("conditional");
        } else {
            builder.type("default");
        }

        return builder.build();
    }

    /**
     * 创建无条件的边
     */
    public static GraphEdge of(String source, String target) {
        return GraphEdge.builder()
                .source(source)
                .target(target)
                .type("default")
                .build();
    }

    /**
     * 创建有条件的边
     */
    public static GraphEdge of(String source, String target, String condition) {
        return GraphEdge.builder()
                .source(source)
                .target(target)
                .condition(condition)
                .label(condition != null && condition.length() > 30
                        ? condition.substring(0, 27) + "..." : condition)
                .type("conditional")
                .build();
    }
}

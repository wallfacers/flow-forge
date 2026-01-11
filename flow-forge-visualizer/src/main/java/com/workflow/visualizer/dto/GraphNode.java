package com.workflow.visualizer.dto;

import com.workflow.model.ExecutionStatus;
import com.workflow.model.NodeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可视化节点数据
 * <p>
 * 用于前端渲染 DAG 图，兼容 D3.js/Cytoscape.js 格式
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphNode {

    /**
     * 节点ID
     */
    private String id;

    /**
     * 节点显示名称
     */
    private String label;

    /**
     * 节点类型（与NodeType枚举一致：http, log, script, if, merge, webhook, wait, start, end）
     */
    private String type;

    /**
     * 执行状态（pending, running, success, failed, waiting, cancelled, timeout）
     */
    private String status;

    /**
     * 节点描述
     */
    private String description;

    /**
     * 开始时间（毫秒时间戳）
     */
    private Long startTime;

    /**
     * 结束时间（毫秒时间戳）
     */
    private Long endTime;

    /**
     * 执行耗时（毫秒）
     */
    private Long duration;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 错误消息（失败时）
     */
    private String errorMessage;

    /**
     * 节点位置X坐标（可选，用于布局）
     */
    private Double x;

    /**
     * 节点位置Y坐标（可选，用于布局）
     */
    private Double y;

    /**
     * 从Node模型创建可视化节点
     */
    public static GraphNode from(com.workflow.model.Node node) {
        return GraphNode.builder()
                .id(node.getId())
                .label(node.getName() != null ? node.getName() : node.getId())
                .type(node.getType() != null ? node.getType().getCode() : "unknown")
                .description(node.getDescription())
                .build();
    }

    /**
     * 创建带执行状态的节点
     */
    public static GraphNode from(com.workflow.model.Node node, ExecutionStatus status) {
        return GraphNode.builder()
                .id(node.getId())
                .label(node.getName() != null ? node.getName() : node.getId())
                .type(node.getType() != null ? node.getType().getCode() : "unknown")
                .status(status.name().toLowerCase())
                .description(node.getDescription())
                .build();
    }

    /**
     * 从NodeType创建显示名称
     */
    public static String getDisplayName(NodeType type) {
        if (type == null) {
            return "Unknown";
        }
        return switch (type) {
            case HTTP -> "HTTP请求";
            case LOG -> "日志输出";
            case SCRIPT -> "脚本执行";
            case IF -> "条件判断";
            case MERGE -> "合并节点";
            case WEBHOOK -> "Webhook触发";
            case WAIT -> "等待回调";
            case START -> "开始";
            case END -> "结束";
            case TRIGGER -> "触发器";
        };
    }
}

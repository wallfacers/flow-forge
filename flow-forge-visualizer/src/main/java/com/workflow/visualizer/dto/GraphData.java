package com.workflow.visualizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 可视化图数据
 * <p>
 * 包含节点和边的完整图结构，兼容 D3.js/Cytoscape.js 格式
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphData {

    /**
     * 节点列表
     */
    @Builder.Default
    private List<GraphNode> nodes = new ArrayList<>();

    /**
     * 边列表
     */
    @Builder.Default
    private List<GraphEdge> edges = new ArrayList<>();

    /**
     * 图的方向（true为有向图，false为无向图）
     */
    @Builder.Default
    private boolean directed = true;

    /**
     * 空图数据
     */
    public static GraphData empty() {
        return new GraphData();
    }

    /**
     * 添加节点
     */
    public GraphData addNode(GraphNode node) {
        if (this.nodes == null) {
            this.nodes = new ArrayList<>();
        }
        this.nodes.add(node);
        return this;
    }

    /**
     * 添加边
     */
    public GraphData addEdge(GraphEdge edge) {
        if (this.edges == null) {
            this.edges = new ArrayList<>();
        }
        this.edges.add(edge);
        return this;
    }

    /**
     * 根据节点ID查找节点
     */
    public GraphNode findNode(String nodeId) {
        if (nodes == null) {
            return null;
        }
        return nodes.stream()
                .filter(n -> nodeId != null && nodeId.equals(n.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 更新节点状态
     */
    public GraphData updateNodeStatus(String nodeId, String status) {
        GraphNode node = findNode(nodeId);
        if (node != null) {
            node.setStatus(status);
        }
        return this;
    }
}

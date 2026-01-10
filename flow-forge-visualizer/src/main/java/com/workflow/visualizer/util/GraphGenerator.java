package com.workflow.visualizer.util;

import com.workflow.model.Edge;
import com.workflow.model.Node;
import com.workflow.model.WorkflowDefinition;
import com.workflow.visualizer.dto.GraphData;
import com.workflow.visualizer.dto.GraphEdge;
import com.workflow.visualizer.dto.GraphNode;
import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * DAG图可视化数据生成器
 * <p>
 * 从 WorkflowDefinition 生成兼容 D3.js/Cytoscape.js 的可视化数据
 * </p>
 */
public class GraphGenerator {

    private static final Logger log = LoggerFactory.getLogger(GraphGenerator.class);

    /**
     * 从工作流定义生成可视化图数据
     *
     * @param workflow 工作流定义
     * @return 可视化图数据
     */
    public static GraphData generate(WorkflowDefinition workflow) {
        if (workflow == null) {
            log.warn("Workflow definition is null, returning empty graph data");
            return GraphData.empty();
        }

        GraphData graphData = new GraphData();

        // 添加节点
        for (Node node : workflow.getNodes()) {
            GraphNode graphNode = GraphNode.from(node);
            // 使用节点的描述名称作为label
            if (node.getName() != null && !node.getName().isEmpty()) {
                graphNode.setLabel(node.getName());
            } else {
                graphNode.setLabel(GraphNode.getDisplayName(node.getType()));
            }
            graphData.addNode(graphNode);
        }

        // 添加边
        for (Edge edge : workflow.getEdges()) {
            GraphEdge graphEdge = GraphEdge.from(edge);
            graphData.addEdge(graphEdge);
        }

        log.debug("Generated graph data with {} nodes and {} edges",
                graphData.getNodes().size(), graphData.getEdges().size());

        return graphData;
    }

    /**
     * 从工作流定义生成可视化图数据（带执行状态）
     *
     * @param workflow 工作流定义
     * @param nodeStatus 节点状态映射（节点ID -> 状态）
     * @return 可视化图数据
     */
    public static GraphData generateWithStatus(WorkflowDefinition workflow,
                                                Map<String, String> nodeStatus) {
        GraphData graphData = generate(workflow);

        // 更新节点状态
        if (nodeStatus != null) {
            for (Map.Entry<String, String> entry : nodeStatus.entrySet()) {
                graphData.updateNodeStatus(entry.getKey(), entry.getValue());
            }
        }

        return graphData;
    }

    /**
     * 从JGraphT图生成可视化图数据
     *
     * @param graph JGraphT图
     * @return 可视化图数据
     */
    public static GraphData generateFromJGraphT(Graph<Node, Edge> graph) {
        if (graph == null) {
            return GraphData.empty();
        }

        GraphData graphData = new GraphData();

        // 添加节点
        for (Node node : graph.vertexSet()) {
            GraphNode graphNode = GraphNode.from(node);
            if (node.getName() != null && !node.getName().isEmpty()) {
                graphNode.setLabel(node.getName());
            } else {
                graphNode.setLabel(GraphNode.getDisplayName(node.getType()));
            }
            graphData.addNode(graphNode);
        }

        // 添加边
        for (Edge edge : graph.edgeSet()) {
            GraphEdge graphEdge = GraphEdge.from(edge);
            graphEdge.setSource(graph.getEdgeSource(edge).getId());
            graphEdge.setTarget(graph.getEdgeTarget(edge).getId());
            graphData.addEdge(graphEdge);
        }

        return graphData;
    }

    /**
     * 计算简单的层次布局坐标（用于可视化）
     * <p>
     * 使用拓扑排序计算节点的X坐标，Y坐标平均分布
     * </p>
     *
     * @param graphData 图数据
     * @return 带布局坐标的图数据
     */
    public static GraphData layout(GraphData graphData) {
        if (graphData == null || graphData.getNodes().isEmpty()) {
            return graphData;
        }

        // 计算每个节点的层级（通过BFS）
        Map<String, Integer> levels = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        // 初始化入度
        for (GraphNode node : graphData.getNodes()) {
            inDegree.put(node.getId(), 0);
            levels.put(node.getId(), 0);
        }

        for (GraphEdge edge : graphData.getEdges()) {
            inDegree.put(edge.getTarget(), inDegree.getOrDefault(edge.getTarget(), 0) + 1);
        }

        // BFS计算层级
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        for (GraphNode node : graphData.getNodes()) {
            if (inDegree.get(node.getId()) == 0) {
                queue.offer(node.getId());
                levels.put(node.getId(), 0);
            }
        }

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            int currentLevel = levels.get(currentId);

            // 找到所有以currentId为源的边
            for (GraphEdge edge : graphData.getEdges()) {
                if (edge.getSource().equals(currentId)) {
                    String targetId = edge.getTarget();
                    inDegree.put(targetId, inDegree.get(targetId) - 1);
                    levels.put(targetId, Math.max(levels.get(targetId), currentLevel + 1));

                    if (inDegree.get(targetId) == 0) {
                        queue.offer(targetId);
                    }
                }
            }
        }

        // 计算每层的节点数
        Map<Integer, Integer> nodesPerLevel = new HashMap<>();
        for (int level : levels.values()) {
            nodesPerLevel.put(level, nodesPerLevel.getOrDefault(level, 0) + 1);
        }

        // 计算每层当前的位置
        Map<Integer, Integer> positionInLevel = new HashMap<>();

        // 设置节点坐标
        final double levelWidth = 200;  // 每层宽度
        final double nodeHeight = 100;  // 节点高度
        final double nodeWidth = 150;   // 节点宽度

        for (GraphNode node : graphData.getNodes()) {
            int level = levels.getOrDefault(node.getId(), 0);
            int pos = positionInLevel.getOrDefault(level, 0);
            positionInLevel.put(level, pos + 1);

            int count = nodesPerLevel.get(level);
            double y = (count * nodeHeight) / 2.0 - pos * nodeHeight;

            node.setX(level * levelWidth);
            node.setY(y);
        }

        return graphData;
    }

    /**
     * 创建子图（只包含指定节点）
     *
     * @param graphData 原图数据
     * @param nodeIds 要包含的节点ID列表
     * @return 子图数据
     */
    public static GraphData subgraph(GraphData graphData, java.util.Set<String> nodeIds) {
        if (graphData == null || nodeIds == null || nodeIds.isEmpty()) {
            return GraphData.empty();
        }

        GraphData result = new GraphData();

        // 添加指定节点
        for (GraphNode node : graphData.getNodes()) {
            if (nodeIds.contains(node.getId())) {
                result.getNodes().add(node);
            }
        }

        // 添加连接指定节点的边
        for (GraphEdge edge : graphData.getEdges()) {
            if (nodeIds.contains(edge.getSource()) && nodeIds.contains(edge.getTarget())) {
                result.getEdges().add(edge);
            }
        }

        return result;
    }
}

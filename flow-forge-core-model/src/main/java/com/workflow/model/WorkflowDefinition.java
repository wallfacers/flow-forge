package com.workflow.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流定义
 * <p>
 * 定义一个DAG工作流的结构，包含节点、边和全局配置
 * </p>
 */
@Data
@Builder
public class WorkflowDefinition implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工作流唯一标识符
     */
    private String id;

    /**
     * 工作流名称
     */
    private String name;

    /**
     * 工作流描述
     */
    private String description;

    /**
     * 工作流版本
     */
    @Builder.Default
    private String version = "1.0.0";

    /**
     * 租户ID（多租户隔离）
     */
    private String tenantId;

    /**
     * 节点列表
     */
    @Builder.Default
    private List<Node> nodes = new ArrayList<>();

    /**
     * 边列表（定义节点间的连接关系）
     */
    @Builder.Default
    private List<Edge> edges = new ArrayList<>();

    /**
     * 全局变量
     */
    @Builder.Default
    private Map<String, Object> globalVariables = new HashMap<>();

    /**
     * 工作流级别配置
     */
    @Builder.Default
    private Map<String, Object> config = new HashMap<>();

    /**
     * 是否启用
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;

    /**
     * 创建者
     */
    private String createdBy;

    /**
     * 更新者
     */
    private String updatedBy;

    /**
     * JGraphT图结构（不序列化）
     */
    private transient org.jgrapht.graph.DefaultDirectedGraph<Node, Edge> graph;

    /**
     * 节点ID到节点的映射（不序列化）
     */
    private transient Map<String, Node> nodeMap;

    /**
     * 验证工作流定义
     *
     * @throws WorkflowValidationException 验证失败时抛出
     */
    public void validate() throws WorkflowValidationException {
        // 基本字段验证
        if (id == null || id.isBlank()) {
            throw new WorkflowValidationException("Workflow ID cannot be null or empty");
        }
        if (name == null || name.isBlank()) {
            throw new WorkflowValidationException("Workflow name cannot be null or empty");
        }

        // 节点验证
        if (nodes == null || nodes.isEmpty()) {
            throw new WorkflowValidationException("Workflow must have at least one node");
        }

        Set<String> nodeIds = new HashSet<>();
        for (Node node : nodes) {
            node.validate();

            // 检查节点ID唯一性
            if (nodeIds.contains(node.getId())) {
                throw new WorkflowValidationException("Duplicate node ID: " + node.getId());
            }
            nodeIds.add(node.getId());
        }

        // 边验证
        if (edges != null) {
            for (Edge edge : edges) {
                edge.validate();

                // 检查边引用的节点是否存在
                if (!nodeIds.contains(edge.getSourceNodeId())) {
                    throw new WorkflowValidationException("Edge references non-existent source node: " + edge.getSourceNodeId());
                }
                if (!nodeIds.contains(edge.getTargetNodeId())) {
                    throw new WorkflowValidationException("Edge references non-existent target node: " + edge.getTargetNodeId());
                }
            }
        }

        // 构建图并检测循环
        buildGraph();

        // 循环检测
        if (hasCycle()) {
            throw new WorkflowValidationException("Workflow contains a cycle");
        }

        // 孤立节点检测
        List<Node> isolatedNodes = findIsolatedNodes();
        if (!isolatedNodes.isEmpty()) {
            String isolatedIds = isolatedNodes.stream()
                    .map(Node::getId)
                    .collect(Collectors.joining(", "));
            throw new WorkflowValidationException("Workflow contains isolated nodes: " + isolatedIds);
        }
    }

    /**
     * 构建JGraphT图结构
     */
    public void buildGraph() {
        // 创建默认有向图
        graph = new org.jgrapht.graph.DefaultDirectedGraph<>(Edge.class);

        // 添加所有节点
        nodeMap = new HashMap<>();
        for (Node node : nodes) {
            graph.addVertex(node);
            nodeMap.put(node.getId(), node);
        }

        // 添加所有边
        if (edges != null) {
            for (Edge edge : edges) {
                Node source = nodeMap.get(edge.getSourceNodeId());
                Node target = nodeMap.get(edge.getTargetNodeId());
                if (source != null && target != null) {
                    graph.addEdge(source, target);
                }
            }
        }
    }

    /**
     * 检测图中是否存在循环
     *
     * @return true表示存在循环
     */
    public boolean hasCycle() {
        if (graph == null) {
            buildGraph();
        }

        org.jgrapht.alg.cycle.CycleDetector<Node, Edge> detector =
                new org.jgrapht.alg.cycle.CycleDetector<>(graph);

        return detector.detectCycles();
    }

    /**
     * 查找循环中的节点
     *
     * @return 循环节点集合
     */
    public Set<Node> findCycleNodes() {
        if (graph == null) {
            buildGraph();
        }

        org.jgrapht.alg.cycle.CycleDetector<Node, Edge> detector =
                new org.jgrapht.alg.cycle.CycleDetector<>(graph);

        return detector.findCycles();
    }

    /**
     * 查找孤立节点（没有入边和出边的节点）
     *
     * @return 孤立节点列表
     */
    public List<Node> findIsolatedNodes() {
        if (graph == null) {
            buildGraph();
        }

        List<Node> isolated = new ArrayList<>();
        for (Node node : nodes) {
            if (graph.inDegreeOf(node) == 0 && graph.outDegreeOf(node) == 0) {
                // 单节点工作流不算孤立
                if (nodes.size() > 1) {
                    isolated.add(node);
                }
            }
        }
        return isolated;
    }

    /**
     * 查找入度为0的起始节点
     *
     * @return 起始节点列表
     */
    public List<Node> getStartNodes() {
        if (graph == null) {
            buildGraph();
        }

        return nodes.stream()
                .filter(node -> graph.inDegreeOf(node) == 0)
                .collect(Collectors.toList());
    }

    /**
     * 查找出度为0的结束节点
     *
     * @return 结束节点列表
     */
    public List<Node> getEndNodes() {
        if (graph == null) {
            buildGraph();
        }

        return nodes.stream()
                .filter(node -> graph.outDegreeOf(node) == 0)
                .collect(Collectors.toList());
    }

    /**
     * 获取节点的所有出边
     *
     * @param nodeId 节点ID
     * @return 出边列表
     */
    public List<Edge> getOutEdges(String nodeId) {
        if (graph == null || nodeMap == null) {
            buildGraph();
        }

        Node node = nodeMap.get(nodeId);
        if (node == null) {
            return Collections.emptyList();
        }

        return new ArrayList<>(graph.outgoingEdgesOf(node));
    }

    /**
     * 获取节点的所有入边
     *
     * @param nodeId 节点ID
     * @return 入边列表
     */
    public List<Edge> getInEdges(String nodeId) {
        if (graph == null || nodeMap == null) {
            buildGraph();
        }

        Node node = nodeMap.get(nodeId);
        if (node == null) {
            return Collections.emptyList();
        }

        return new ArrayList<>(graph.incomingEdgesOf(node));
    }

    /**
     * 根据ID获取节点
     *
     * @param nodeId 节点ID
     * @return 节点对象，不存在返回null
     */
    public Node getNode(String nodeId) {
        if (nodeMap == null) {
            buildGraph();
        }
        return nodeMap.get(nodeId);
    }

    /**
     * 计算节点的入度
     *
     * @param nodeId 节点ID
     * @return 入度值
     */
    public int getInDegree(String nodeId) {
        if (graph == null || nodeMap == null) {
            buildGraph();
        }

        Node node = nodeMap.get(nodeId);
        if (node == null) {
            return 0;
        }

        return graph.inDegreeOf(node);
    }

    /**
     * 初始化所有节点的入度映射
     *
     * @return 节点ID到入度的映射
     */
    public Map<String, Integer> initializeInDegrees() {
        Map<String, Integer> inDegreeMap = new HashMap<>();

        // 初始化所有节点入度为0
        for (Node node : nodes) {
            inDegreeMap.put(node.getId(), 0);
        }

        // 根据边累加入度
        if (edges != null) {
            for (Edge edge : edges) {
                String targetId = edge.getTargetNodeId();
                inDegreeMap.merge(targetId, 1, Integer::sum);
            }
        }

        return inDegreeMap;
    }

    /**
     * 获取配置值
     *
     * @param key 配置键
     * @return 配置值
     */
    public Object getConfigValue(String key) {
        return config != null ? config.get(key) : null;
    }

    /**
     * 获取字符串配置值
     *
     * @param key 配置键
     * @return 配置值
     */
    public String getConfigString(String key) {
        Object value = getConfigValue(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 获取整数配置值
     *
     * @param key 配置键
     * @return 配置值
     */
    public Integer getConfigInt(String key) {
        Object value = getConfigValue(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    /**
     * 设置配置值
     *
     * @param key   配置键
     * @param value 配置值
     */
    public void setConfigValue(String key, Object value) {
        if (config == null) {
            config = new HashMap<>();
        }
        config.put(key, value);
    }

    /**
     * 添加节点
     *
     * @param node 节点
     */
    public void addNode(Node node) {
        if (nodes == null) {
            nodes = new ArrayList<>();
        }
        nodes.add(node);
    }

    /**
     * 添加边
     *
     * @param edge 边
     */
    public void addEdge(Edge edge) {
        if (edges == null) {
            edges = new ArrayList<>();
        }
        edges.add(edge);
    }

    /**
     * 创建创建时间戳
     */
    public void markCreated() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 创建更新时间戳
     */
    public void markUpdated() {
        this.updatedAt = Instant.now();
    }

    /**
     * 获取图结构（用于外部访问）
     *
     * @return JGraphT图对象
     */
    public org.jgrapht.Graph<Node, Edge> getGraph() {
        if (graph == null) {
            buildGraph();
        }
        return graph;
    }
}

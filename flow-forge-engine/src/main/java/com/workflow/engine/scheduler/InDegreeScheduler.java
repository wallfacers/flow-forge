package com.workflow.engine.scheduler;

import com.workflow.model.Edge;
import com.workflow.model.Node;
import com.workflow.model.WorkflowDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 入度调度器
 * <p>
 * 基于节点入度进行DAG拓扑排序和调度
 * <p>
 * 核心算法：
 * 1. 初始化所有节点的入度（入度=前驱节点数量）
 * 2. 找出入度为0的节点作为起始节点
 * 3. 当节点执行完成后，将其后继节点的入度减1
 * 4. 当节点入度变为0时，该节点可以被调度执行
 * </p>
 */
public class InDegreeScheduler {

    private static final Logger logger = LoggerFactory.getLogger(InDegreeScheduler.class);

    /**
     * 计算工作流中所有节点的入度
     *
     * @param definition 工作流定义
     * @return 节点ID到入度的映射
     */
    public Map<String, AtomicInteger> calculateInDegrees(WorkflowDefinition definition) {
        if (definition == null || definition.getNodes() == null) {
            return Collections.emptyMap();
        }

        Map<String, AtomicInteger> inDegreeMap = new ConcurrentHashMap<>();

        // 初始化所有节点入度为0
        for (Node node : definition.getNodes()) {
            inDegreeMap.put(node.getId(), new AtomicInteger(0));
        }

        // 根据边累加入度
        if (definition.getEdges() != null) {
            for (Edge edge : definition.getEdges()) {
                String targetId = edge.getTargetNodeId();
                AtomicInteger inDegree = inDegreeMap.get(targetId);
                if (inDegree != null) {
                    inDegree.incrementAndGet();
                }
            }
        }

        // 打印入度信息
        if (logger.isDebugEnabled()) {
            Map<String, Integer> debugMap = inDegreeMap.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> e.getValue().get()
                    ));
            logger.debug("Calculated in-degrees: {}", debugMap);
        }

        return inDegreeMap;
    }

    /**
     * 查找入度为0的节点（可立即执行的节点）
     *
     * @param inDegreeMap 入度映射
     * @return 可执行节点ID列表
     */
    public List<String> findReadyNodes(Map<String, AtomicInteger> inDegreeMap) {
        if (inDegreeMap == null) {
            return Collections.emptyList();
        }

        return inDegreeMap.entrySet().stream()
                .filter(e -> e.getValue().get() == 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 查找入度为0的节点（使用节点列表）
     *
     * @param nodes      所有节点
     * @param inDegreeMap 入度映射
     * @return 可执行节点列表
     */
    public List<Node> findReadyNodes(List<Node> nodes, Map<String, AtomicInteger> inDegreeMap) {
        if (nodes == null || inDegreeMap == null) {
            return Collections.emptyList();
        }

        return nodes.stream()
                .filter(node -> {
                    AtomicInteger inDegree = inDegreeMap.get(node.getId());
                    return inDegree != null && inDegree.get() == 0;
                })
                .collect(Collectors.toList());
    }

    /**
     * 节点执行完成后，更新其后继节点的入度
     *
     * @param nodeId       已执行完成的节点ID
     * @param outEdges     该节点的出边列表
     * @param inDegreeMap  入度映射
     * @return 新变为可执行的后继节点ID列表
     */
    public List<String> nodeCompleted(String nodeId, List<Edge> outEdges,
                                       Map<String, AtomicInteger> inDegreeMap) {
        if (outEdges == null || inDegreeMap == null) {
            return Collections.emptyList();
        }

        List<String> readyNodes = new ArrayList<>();

        for (Edge edge : outEdges) {
            String targetNodeId = edge.getTargetNodeId();
            AtomicInteger inDegree = inDegreeMap.get(targetNodeId);

            if (inDegree != null) {
                int newInDegree = inDegree.decrementAndGet();
                logger.debug("Node {} completed, {} in-degree: {} -> {}",
                        nodeId, targetNodeId, newInDegree + 1, newInDegree);

                if (newInDegree == 0) {
                    readyNodes.add(targetNodeId);
                }
            }
        }

        return readyNodes;
    }

    /**
     * 执行拓扑排序
     *
     * @param definition 工作流定义
     * @return 拓扑排序后的节点ID列表
     * @throws IllegalArgumentException 如果图中存在循环
     */
    public List<String> topologicalSort(WorkflowDefinition definition) {
        if (definition == null || definition.getNodes() == null) {
            return Collections.emptyList();
        }

        // 克隆入度映射（避免修改原始数据）
        Map<String, AtomicInteger> inDegreeMap = new ConcurrentHashMap<>();
        for (Node node : definition.getNodes()) {
            inDegreeMap.put(node.getId(), new AtomicInteger(0));
        }
        if (definition.getEdges() != null) {
            for (Edge edge : definition.getEdges()) {
                String targetId = edge.getTargetNodeId();
                AtomicInteger inDegree = inDegreeMap.get(targetId);
                if (inDegree != null) {
                    inDegree.incrementAndGet();
                }
            }
        }

        // 拓扑排序结果
        List<String> result = new ArrayList<>(definition.getNodes().size());
        Queue<String> queue = new LinkedList<>();

        // 找出所有入度为0的节点
        for (Map.Entry<String, AtomicInteger> entry : inDegreeMap.entrySet()) {
            if (entry.getValue().get() == 0) {
                queue.offer(entry.getKey());
            }
        }

        // 处理队列
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            result.add(nodeId);

            // 获取该节点的出边
            List<Edge> outEdges = definition.getOutEdges(nodeId);
            for (Edge edge : outEdges) {
                String targetId = edge.getTargetNodeId();
                AtomicInteger inDegree = inDegreeMap.get(targetId);
                if (inDegree != null && inDegree.decrementAndGet() == 0) {
                    queue.offer(targetId);
                }
            }
        }

        // 检查是否处理了所有节点（存在循环则无法处理所有节点）
        if (result.size() != definition.getNodes().size()) {
            throw new IllegalArgumentException(
                    "Graph contains cycle, cannot perform topological sort. " +
                            "Expected " + definition.getNodes().size() + " nodes, but only sorted " + result.size());
        }

        return result;
    }

    /**
     * 验证调度完整性
     * <p>
     * 检查在给定入度映射下是否所有节点最终都能被执行
     * </p>
     *
     * @param definition  工作流定义
     * @param inDegreeMap 入度映射
     * @return true表示所有节点都能被执行
     */
    public boolean validateCompleteness(WorkflowDefinition definition,
                                        Map<String, AtomicInteger> inDegreeMap) {
        if (definition == null || inDegreeMap == null) {
            return false;
        }

        try {
            List<String> sorted = topologicalSort(definition);
            return sorted.size() == definition.getNodes().size();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 获取节点的执行层级
     * <p>
     * 层级定义为从起始节点到该节点的最长路径长度
     * </p>
     *
     * @param definition 工作流定义
     * @return 节点ID到层级的映射
     */
    public Map<String, Integer> calculateLevels(WorkflowDefinition definition) {
        if (definition == null || definition.getNodes() == null) {
            return Collections.emptyMap();
        }

        Map<String, Integer> levels = new HashMap<>();
        Map<String, AtomicInteger> inDegreeMap = calculateInDegrees(definition);

        // 初始化：入度为0的节点层级为0
        for (Map.Entry<String, AtomicInteger> entry : inDegreeMap.entrySet()) {
            if (entry.getValue().get() == 0) {
                levels.put(entry.getKey(), 0);
            }
        }

        // 拓扑排序同时计算层级
        Queue<String> queue = new LinkedList<>(levels.keySet());

        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            int currentLevel = levels.getOrDefault(nodeId, 0);

            List<Edge> outEdges = definition.getOutEdges(nodeId);
            for (Edge edge : outEdges) {
                String targetId = edge.getTargetNodeId();
                AtomicInteger inDegree = inDegreeMap.get(targetId);

                if (inDegree != null) {
                    // 更新后继节点的层级
                    int newLevel = currentLevel + 1;
                    levels.merge(targetId, newLevel, Math::max);

                    if (inDegree.decrementAndGet() == 0) {
                        queue.offer(targetId);
                    }
                }
            }
        }

        return levels;
    }

    /**
     * 创建入度映射的快照（用于断点续传）
     *
     * @param inDegreeMap 原始入度映射（AtomicInteger）
     * @return 不可修改的普通整数映射
     */
    public Map<String, Integer> createSnapshot(Map<String, AtomicInteger> inDegreeMap) {
        if (inDegreeMap == null) {
            return Collections.emptyMap();
        }

        Map<String, Integer> snapshot = new HashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : inDegreeMap.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().get());
        }
        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * 从快照恢复入度映射
     *
     * @param snapshot 快照映射
     * @return 新的入度映射（AtomicInteger）
     */
    public Map<String, AtomicInteger> restoreFromSnapshot(Map<String, Integer> snapshot) {
        if (snapshot == null) {
            return new ConcurrentHashMap<>();
        }

        Map<String, AtomicInteger> inDegreeMap = new ConcurrentHashMap<>();
        for (Map.Entry<String, Integer> entry : snapshot.entrySet()) {
            inDegreeMap.put(entry.getKey(), new AtomicInteger(entry.getValue()));
        }
        return inDegreeMap;
    }
}

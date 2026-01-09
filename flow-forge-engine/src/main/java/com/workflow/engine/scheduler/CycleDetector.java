package com.workflow.engine.scheduler;

import com.workflow.model.Edge;
import com.workflow.model.Node;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.JohnsonSimpleCycles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DAG循环检测器
 * <p>
 * 使用JGraphT库检测有向图中是否存在循环
 * </p>
 */
public class CycleDetector {

    private static final Logger logger = LoggerFactory.getLogger(CycleDetector.class);

    /**
     * 检测图中是否存在循环
     *
     * @param graph JGraphT图对象
     * @param <V>   顶点类型
     * @param <E>   边类型
     * @return true表示存在循环
     */
    public static <V, E> boolean detectCycle(Graph<V, E> graph) {
        if (graph == null) {
            return false;
        }

        org.jgrapht.alg.cycle.CycleDetector<V, E> detector = new org.jgrapht.alg.cycle.CycleDetector<>(graph);
        return detector.detectCycles();
    }

    /**
     * 查找图中所有参与循环的节点
     *
     * @param graph JGraphT图对象
     * @param <V>   顶点类型
     * @param <E>   边类型
     * @return 参与循环的节点集合
     */
    public static <V, E> Set<V> findCycleNodes(Graph<V, E> graph) {
        if (graph == null) {
            return Collections.emptySet();
        }

        org.jgrapht.alg.cycle.CycleDetector<V, E> detector = new org.jgrapht.alg.cycle.CycleDetector<>(graph);
        return detector.findCycles();
    }

    /**
     * 查找图中所有的简单循环
     *
     * @param graph JGraphT图对象
     * @return 循环列表，每个循环是节点ID列表
     */
    public static List<List<String>> findCycles(Graph<Node, Edge> graph) {
        if (graph == null) {
            return Collections.emptyList();
        }

        try {
            JohnsonSimpleCycles<Node, Edge> cycleFinder = new JohnsonSimpleCycles<>(graph);
            List<List<Node>> cycles = cycleFinder.findSimpleCycles();

            List<List<String>> result = new ArrayList<>();
            for (List<Node> cycle : cycles) {
                List<String> cycleIds = cycle.stream()
                        .map(Node::getId)
                        .collect(Collectors.toList());
                result.add(cycleIds);
                logger.debug("Found cycle: {}", cycleIds);
            }

            return result;

        } catch (Exception e) {
            logger.error("Failed to find cycles using Johnson algorithm", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取循环检测的详细信息
     *
     * @param graph JGraphT图对象
     * @return 检测结果
     */
    public static CycleDetectionResult detectDetailed(Graph<Node, Edge> graph) {
        CycleDetectionResult result = new CycleDetectionResult();

        if (graph == null) {
            result.setHasCycle(false);
            return result;
        }

        org.jgrapht.alg.cycle.CycleDetector<Node, Edge> detector = new org.jgrapht.alg.cycle.CycleDetector<>(graph);
        boolean hasCycle = detector.detectCycles();

        result.setHasCycle(hasCycle);

        if (hasCycle) {
            Set<Node> cycleNodes = detector.findCycles();
            result.setCycleNodes(cycleNodes);

            result.setCycles(findCycles(graph));

            Set<Node> nonCycleNodes = new java.util.HashSet<>(graph.vertexSet());
            nonCycleNodes.removeAll(cycleNodes);
            result.setNonCycleNodes(nonCycleNodes);

            logger.warn("Detected {} cycle(s) involving {} nodes",
                    result.getCycles().size(), cycleNodes.size());
        }

        return result;
    }

    /**
     * 检测并抛出异常（如果存在循环）
     *
     * @param graph        工作流图
     * @param workflowId   工作流ID
     * @throws com.workflow.model.WorkflowValidationException 如果存在循环
     */
    public static void validateNoCycle(Graph<Node, Edge> graph, String workflowId)
            throws com.workflow.model.WorkflowValidationException {
        CycleDetectionResult result = detectDetailed(graph);

        if (result.hasCycle()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Workflow '").append(workflowId).append("' contains cycle(s):\n");

            for (List<String> cycle : result.getCycles()) {
                sb.append("  ").append(String.join(" -> ", cycle)).append(" -> ").append(cycle.get(0)).append("\n");
            }

            throw new com.workflow.model.WorkflowValidationException(sb.toString());
        }
    }

    /**
     * 循环检测结果
     */
    public static class CycleDetectionResult {
        private boolean hasCycle;
        private Set<Node> cycleNodes;
        private Set<Node> nonCycleNodes;
        private List<List<String>> cycles;

        public boolean hasCycle() {
            return hasCycle;
        }

        public void setHasCycle(boolean hasCycle) {
            this.hasCycle = hasCycle;
        }

        public Set<Node> getCycleNodes() {
            return cycleNodes;
        }

        public void setCycleNodes(Set<Node> cycleNodes) {
            this.cycleNodes = cycleNodes;
        }

        public Set<Node> getNonCycleNodes() {
            return nonCycleNodes;
        }

        public void setNonCycleNodes(Set<Node> nonCycleNodes) {
            this.nonCycleNodes = nonCycleNodes;
        }

        public List<List<String>> getCycles() {
            return cycles != null ? cycles : Collections.emptyList();
        }

        public void setCycles(List<List<String>> cycles) {
            this.cycles = cycles;
        }

        public int getCycleCount() {
            return getCycles().size();
        }

        public int getCycleNodeCount() {
            return cycleNodes != null ? cycleNodes.size() : 0;
        }

        @Override
        public String toString() {
            if (!hasCycle) {
                return "No cycle detected";
            }
            return String.format("Detected %d cycle(s) involving %d nodes: %s",
                    getCycleCount(), getCycleNodeCount(), getCycles());
        }
    }
}

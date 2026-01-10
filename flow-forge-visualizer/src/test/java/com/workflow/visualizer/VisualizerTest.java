package com.workflow.visualizer;

import com.workflow.model.Edge;
import com.workflow.model.Node;
import com.workflow.model.NodeType;
import com.workflow.model.WorkflowDefinition;
import com.workflow.visualizer.dto.GraphData;
import com.workflow.visualizer.dto.GraphEdge;
import com.workflow.visualizer.dto.GraphNode;
import com.workflow.visualizer.util.GraphGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 可视化模块测试
 * <p>
 * 测试图生成、节点/边转换、布局计算等功能
 * </p>
 */
@DisplayName("可视化模块测试")
class VisualizerTest {

    private WorkflowDefinition testWorkflow;
    private Node httpNode;
    private Node logNode;
    private Node scriptNode;

    @BeforeEach
    void setUp() {
        // 创建测试节点
        httpNode = Node.builder()
                .id("node1")
                .name("HTTP请求")
                .type(NodeType.HTTP)
                .description("调用外部API")
                .build();

        logNode = Node.builder()
                .id("node2")
                .name("日志输出")
                .type(NodeType.LOG)
                .description("记录结果")
                .build();

        scriptNode = Node.builder()
                .id("node3")
                .name("数据处理")
                .type(NodeType.SCRIPT)
                .description("处理返回数据")
                .build();

        // 创建测试工作流
        List<Node> nodes = List.of(httpNode, logNode, scriptNode);
        List<Edge> edges = List.of(
                Edge.of("node1", "node3"),
                Edge.of("node2", "node3")
        );

        testWorkflow = new WorkflowDefinition();
        testWorkflow.setId("test-workflow");
        testWorkflow.setName("测试工作流");
        testWorkflow.setNodes(new ArrayList<>(nodes));
        testWorkflow.setEdges(new ArrayList<>(edges));
    }

    @Test
    @DisplayName("应该生成包含所有节点的图数据")
    void shouldGenerateGraphWithAllNodes() {
        GraphData graphData = GraphGenerator.generate(testWorkflow);

        assertNotNull(graphData);
        assertEquals(3, graphData.getNodes().size());
        assertTrue(graphData.isDirected());
    }

    @Test
    @DisplayName("应该生成包含所有边的图数据")
    void shouldGenerateGraphWithAllEdges() {
        GraphData graphData = GraphGenerator.generate(testWorkflow);

        assertNotNull(graphData);
        assertEquals(2, graphData.getEdges().size());
    }

    @Test
    @DisplayName("节点应该包含正确的属性")
    void nodesShouldHaveCorrectAttributes() {
        GraphData graphData = GraphGenerator.generate(testWorkflow);
        GraphNode node1 = graphData.findNode("node1");

        assertNotNull(node1);
        assertEquals("node1", node1.getId());
        assertEquals("HTTP请求", node1.getLabel());
        assertEquals("http", node1.getType());
        assertEquals("调用外部API", node1.getDescription());
    }

    @Test
    @DisplayName("边应该包含正确的源和目标")
    void edgesShouldHaveCorrectSourceAndTarget() {
        GraphData graphData = GraphGenerator.generate(testWorkflow);

        GraphEdge edge1 = graphData.getEdges().stream()
                .filter(e -> "node1".equals(e.getSource()))
                .findFirst()
                .orElse(null);

        assertNotNull(edge1);
        assertEquals("node3", edge1.getTarget());
    }

    @Test
    @DisplayName("应该支持有条件的边")
    void shouldSupportConditionalEdges() {
        Edge conditionalEdge = Edge.of("node1", "node2", "{{node1.output.status}} == 200");
        testWorkflow.getEdges().add(conditionalEdge);

        GraphData graphData = GraphGenerator.generate(testWorkflow);

        GraphEdge condEdge = graphData.getEdges().stream()
                .filter(e -> e.getCondition() != null)
                .findFirst()
                .orElse(null);

        assertNotNull(condEdge);
        assertEquals("conditional", condEdge.getType());
        assertEquals("{{node1.output.status}} == 200", condEdge.getCondition());
    }

    @Test
    @DisplayName("应该支持带状态的节点")
    void shouldSupportNodesWithStatus() {
        Map<String, String> nodeStatus = new HashMap<>();
        nodeStatus.put("node1", "success");
        nodeStatus.put("node2", "running");
        nodeStatus.put("node3", "pending");

        GraphData graphData = GraphGenerator.generateWithStatus(testWorkflow, nodeStatus);

        GraphNode node1 = graphData.findNode("node1");
        GraphNode node2 = graphData.findNode("node2");
        GraphNode node3 = graphData.findNode("node3");

        assertEquals("success", node1.getStatus());
        assertEquals("running", node2.getStatus());
        assertEquals("pending", node3.getStatus());
    }

    @Test
    @DisplayName("应该支持更新节点状态")
    void shouldUpdateNodeStatus() {
        GraphData graphData = GraphGenerator.generate(testWorkflow);

        graphData.updateNodeStatus("node1", "failed");
        graphData.updateNodeStatus("node2", "success");

        GraphNode node1 = graphData.findNode("node1");
        GraphNode node2 = graphData.findNode("node2");

        assertEquals("failed", node1.getStatus());
        assertEquals("success", node2.getStatus());
    }

    @Test
    @DisplayName("布局应该计算节点坐标")
    void layoutShouldCalculateNodeCoordinates() {
        GraphData graphData = GraphGenerator.generate(testWorkflow);

        GraphGenerator.layout(graphData);

        for (GraphNode node : graphData.getNodes()) {
            assertNotNull(node.getX());
            assertNotNull(node.getY());
        }
    }

    @Test
    @DisplayName("空工作流应该返回空图数据")
    void emptyWorkflowShouldReturnEmptyGraphData() {
        GraphData graphData = GraphGenerator.generate(null);

        assertNotNull(graphData);
        assertTrue(graphData.getNodes().isEmpty());
        assertTrue(graphData.getEdges().isEmpty());
    }

    @Test
    @DisplayName("应该支持创建子图")
    void shouldSupportSubgraph() {
        GraphData graphData = GraphGenerator.generate(testWorkflow);

        GraphData subgraph = GraphGenerator.subgraph(graphData, java.util.Set.of("node1", "node3"));

        assertEquals(2, subgraph.getNodes().size());
        assertEquals(1, subgraph.getEdges().size());
    }

    @Test
    @DisplayName("节点类型应该有正确的显示名称")
    void nodeTypesShouldHaveCorrectDisplayNames() {
        assertEquals("HTTP请求", GraphNode.getDisplayName(NodeType.HTTP));
        assertEquals("日志输出", GraphNode.getDisplayName(NodeType.LOG));
        assertEquals("脚本执行", GraphNode.getDisplayName(NodeType.SCRIPT));
        assertEquals("条件判断", GraphNode.getDisplayName(NodeType.IF));
        assertEquals("合并节点", GraphNode.getDisplayName(NodeType.MERGE));
        assertEquals("Webhook触发", GraphNode.getDisplayName(NodeType.WEBHOOK));
        assertEquals("等待回调", GraphNode.getDisplayName(NodeType.WAIT));
        assertEquals("开始", GraphNode.getDisplayName(NodeType.START));
        assertEquals("结束", GraphNode.getDisplayName(NodeType.END));
    }

    @Test
    @DisplayName("应该支持添加节点和边")
    void shouldSupportAddingNodesAndEdges() {
        GraphData graphData = new GraphData();

        graphData.addNode(GraphNode.from(httpNode));
        graphData.addEdge(GraphEdge.of("node1", "node2"));

        assertEquals(1, graphData.getNodes().size());
        assertEquals(1, graphData.getEdges().size());
    }
}

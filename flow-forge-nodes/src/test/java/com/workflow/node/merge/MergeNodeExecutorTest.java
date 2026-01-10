package com.workflow.node.merge;

import com.workflow.context.VariableResolver;
import com.workflow.model.ExecutionContext;
import com.workflow.model.ExecutionStatus;
import com.workflow.model.Node;
import com.workflow.model.NodeResult;
import com.workflow.model.NodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MergeNodeExecutor}.
 */
@ExtendWith(MockitoExtension.class)
class MergeNodeExecutorTest {

    @Mock
    private VariableResolver variableResolver;

    @Mock
    private ExecutionContext context;

    private MergeNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new MergeNodeExecutor(variableResolver);
    }

    // ===== Basic Tests =====

    @Test
    void getSupportedType_shouldReturnMERGE() {
        assertEquals(NodeType.MERGE, executor.getSupportedType());
    }

    @Test
    void execute_withNullNode_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> executor.execute(null, context));
    }

    @Test
    void execute_withNullContext_shouldThrowException() {
        Node node = Node.builder().id("test-merge").type(NodeType.MERGE).build();
        assertThrows(IllegalArgumentException.class, () -> executor.execute(node, null));
    }

    // ===== Strategy: ALL (default) =====

    @Test
    void execute_withAllStrategy_shouldReturnMergedResults() {
        Node node = Node.builder().id("test-merge").type(NodeType.MERGE).build();
        Map<String, Object> config = new HashMap<>();
        config.put("mergeStrategy", "all");
        node.setConfig(config);

        // Setup context with node results
        Map<String, NodeResult> nodeResults = new HashMap<>();
        nodeResults.put("node1", createNodeResult("result1"));
        nodeResults.put("node2", createNodeResult("result2"));
        when(context.getNodeResults()).thenReturn(nodeResults);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test-merge", result.getNodeId());

        Map<String, Object> output = result.getOutput();
        assertNotNull(output.get("merged"));

        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) output.get("merged");
        assertEquals(2, merged.size());
        // merged values are the output maps from NodeResults
        assertNotNull(merged.get("node1"));
        assertNotNull(merged.get("node2"));

        @SuppressWarnings("unchecked")
        List<String> nodeIds = (List<String>) output.get("nodeIds");
        assertTrue(nodeIds.contains("node1"));
        assertTrue(nodeIds.contains("node2"));

        assertEquals(2, output.get("count"));
    }

    @Test
    void execute_withDefaultStrategy_shouldUseAll() {
        Node node = Node.builder().id("test-merge").type(NodeType.MERGE).build();
        node.setConfig(new HashMap<>());

        Map<String, NodeResult> nodeResults = new HashMap<>();
        nodeResults.put("node1", createNodeResult("result1"));
        when(context.getNodeResults()).thenReturn(nodeResults);

        // Mock the variable resolver call (this is used by AbstractNodeExecutor)
        lenient().when(variableResolver.resolveMap(any(), any())).thenReturn(new HashMap<>());

        NodeResult result = executor.execute(node, context);

        Map<String, Object> output = result.getOutput();
        assertNotNull(output.get("merged"));  // Should use "all" strategy by default
    }

    // ===== Strategy: FIRST =====

    @Test
    void execute_withFirstStrategy_shouldReturnFirstResult() {
        Node node = Node.builder().id("test-merge").type(NodeType.MERGE).build();
        Map<String, Object> config = new HashMap<>();
        config.put("mergeStrategy", "first");
        node.setConfig(config);

        Map<String, NodeResult> nodeResults = new HashMap<>();
        nodeResults.put("node1", createNodeResult("first"));
        nodeResults.put("node2", createNodeResult("second"));
        nodeResults.put("node3", createNodeResult("third"));
        when(context.getNodeResults()).thenReturn(nodeResults);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        Map<String, Object> output = result.getOutput();
        assertNotNull(output.get("nodeId"));
        assertNotNull(output.get("result"));
        assertEquals(1, output.get("count"));
    }

    @Test
    void execute_withFirstStrategyAndEmptyResults_shouldReturnEmpty() {
        Node node = Node.builder().id("test-merge").type(NodeType.MERGE).build();
        Map<String, Object> config = new HashMap<>();
        config.put("mergeStrategy", "first");
        node.setConfig(config);

        when(context.getNodeResults()).thenReturn(new HashMap<>());
        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        Map<String, Object> output = result.getOutput();
        assertNull(output.get("nodeId"));
        assertNull(output.get("result"));
        assertEquals(0, output.get("count"));
    }

    // ===== Strategy: LAST =====

    @Test
    void execute_withLastStrategy_shouldReturnLastResult() {
        Node node = Node.builder().id("test-merge").type(NodeType.MERGE).build();
        Map<String, Object> config = new HashMap<>();
        config.put("mergeStrategy", "last");
        node.setConfig(config);

        Map<String, NodeResult> nodeResults = new HashMap<>();
        nodeResults.put("node1", createNodeResult("first"));
        nodeResults.put("node2", createNodeResult("last"));
        when(context.getNodeResults()).thenReturn(nodeResults);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        Map<String, Object> output = result.getOutput();
        assertNotNull(output.get("nodeId"));
        assertNotNull(output.get("result"));
        assertEquals(1, output.get("count"));
    }

    // ===== Strategy: ARRAY =====

    @Test
    void execute_withArrayStrategy_shouldReturnResultsAsArray() {
        Node node = Node.builder().id("test-merge").type(NodeType.MERGE).build();
        Map<String, Object> config = new HashMap<>();
        config.put("mergeStrategy", "array");
        node.setConfig(config);

        Map<String, NodeResult> nodeResults = new HashMap<>();
        nodeResults.put("node1", createNodeResult("result1"));
        nodeResults.put("node2", createNodeResult("result2"));
        nodeResults.put("node3", createNodeResult("result3"));
        when(context.getNodeResults()).thenReturn(nodeResults);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        Map<String, Object> output = result.getOutput();
        assertNotNull(output.get("results"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) output.get("results");
        assertEquals(3, results.size());
        assertEquals(3, output.get("count"));

        // Verify each result has nodeId and result keys
        for (Map<String, Object> item : results) {
            assertTrue(item.containsKey("nodeId"));
            assertTrue(item.containsKey("result"));
        }
    }

    // ===== Include Node IDs Filter =====

    @Test
    void execute_withIncludeNodeIds_shouldOnlyIncludeSpecifiedNodes() {
        Node node = Node.builder().id("test-merge").type(NodeType.MERGE).build();
        Map<String, Object> config = new HashMap<>();
        config.put("mergeStrategy", "all");
        config.put("includeNodeIds", List.of("node1", "node3"));
        node.setConfig(config);

        Map<String, NodeResult> nodeResults = new HashMap<>();
        nodeResults.put("node1", createNodeResult("result1"));
        nodeResults.put("node2", createNodeResult("result2"));
        nodeResults.put("node3", createNodeResult("result3"));
        when(context.getNodeResults()).thenReturn(nodeResults);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        Map<String, Object> output = result.getOutput();
        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) output.get("merged");

        assertEquals(2, merged.size());
        assertTrue(merged.containsKey("node1"));
        assertTrue(merged.containsKey("node3"));
        assertFalse(merged.containsKey("node2"));
    }

    @Test
    void execute_withEmptyIncludeNodeIds_shouldIncludeAll() {
        Node node = Node.builder().id("test-merge").type(NodeType.MERGE).build();
        Map<String, Object> config = new HashMap<>();
        config.put("mergeStrategy", "all");
        config.put("includeNodeIds", new ArrayList<>());
        node.setConfig(config);

        Map<String, NodeResult> nodeResults = new HashMap<>();
        nodeResults.put("node1", createNodeResult("result1"));
        nodeResults.put("node2", createNodeResult("result2"));
        when(context.getNodeResults()).thenReturn(nodeResults);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        Map<String, Object> output = result.getOutput();
        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) output.get("merged");

        assertEquals(2, merged.size());
    }

    // ===== Exclude Nulls =====

    @Test
    void execute_withExcludeNullsTrue_shouldExcludeNullResults() {
        Node node = Node.builder().id("test-merge").type(NodeType.MERGE).build();
        Map<String, Object> config = new HashMap<>();
        config.put("mergeStrategy", "all");
        config.put("excludeNulls", true);
        node.setConfig(config);

        Map<String, NodeResult> nodeResults = new HashMap<>();
        nodeResults.put("node1", createNodeResult("result1"));
        nodeResults.put("node2", null);
        nodeResults.put("node3", createNodeResult("result3"));
        when(context.getNodeResults()).thenReturn(nodeResults);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        Map<String, Object> output = result.getOutput();
        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) output.get("merged");

        assertEquals(2, merged.size());
        assertTrue(merged.containsKey("node1"));
        assertTrue(merged.containsKey("node3"));
        assertFalse(merged.containsKey("node2"));
    }

    @Test
    void execute_withExcludeNullsFalse_shouldIncludeNullResults() {
        Node node = Node.builder().id("test-merge").type(NodeType.MERGE).build();
        Map<String, Object> config = new HashMap<>();
        config.put("mergeStrategy", "all");
        config.put("excludeNulls", false);
        node.setConfig(config);

        Map<String, NodeResult> nodeResults = new HashMap<>();
        nodeResults.put("node1", createNodeResult("result1"));
        nodeResults.put("node2", null);
        when(context.getNodeResults()).thenReturn(nodeResults);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        Map<String, Object> output = result.getOutput();
        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) output.get("merged");

        assertEquals(2, merged.size());
        assertTrue(merged.containsKey("node1"));
        assertTrue(merged.containsKey("node2"));
        assertNull(merged.get("node2"));
    }

    @Test
    void execute_withDefaultExcludeNulls_shouldExcludeNulls() {
        Node node = Node.builder().id("test-merge").type(NodeType.MERGE).build();
        Map<String, Object> config = new HashMap<>();
        config.put("mergeStrategy", "all");
        // excludeNulls not specified, should default to true
        node.setConfig(config);

        Map<String, NodeResult> nodeResults = new HashMap<>();
        nodeResults.put("node1", createNodeResult("result1"));
        nodeResults.put("node2", null);
        when(context.getNodeResults()).thenReturn(nodeResults);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        Map<String, Object> output = result.getOutput();
        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) output.get("merged");

        assertEquals(1, merged.size());
        assertTrue(merged.containsKey("node1"));
    }

    // ===== Edge Cases =====

    @Test
    void execute_withEmptyNodeResults_shouldReturnEmptyMerged() {
        Node node = Node.builder().id("test-merge").type(NodeType.MERGE).build();
        Map<String, Object> config = new HashMap<>();
        config.put("mergeStrategy", "all");
        node.setConfig(config);

        when(context.getNodeResults()).thenReturn(new HashMap<>());
        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        Map<String, Object> output = result.getOutput();
        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) output.get("merged");

        assertTrue(merged.isEmpty());
        assertEquals(0, output.get("count"));
    }

    @Test
    void execute_withCaseInsensitiveStrategy_shouldHandleCorrectly() {
        Node node = Node.builder().id("test-merge").type(NodeType.MERGE).build();
        Map<String, Object> config = new HashMap<>();
        config.put("mergeStrategy", "FIRST");  // uppercase
        node.setConfig(config);

        Map<String, NodeResult> nodeResults = new HashMap<>();
        nodeResults.put("node1", createNodeResult("result1"));
        when(context.getNodeResults()).thenReturn(nodeResults);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        Map<String, Object> output = result.getOutput();
        assertNotNull(output.get("nodeId"));
        assertEquals(1, output.get("count"));
    }

    // ===== Config Helper Tests =====

    @Test
    void getConfigString_withExistingValue_shouldReturnValue() {
        Map<String, Object> config = new HashMap<>();
        config.put("mergeStrategy", "first");

        String strategy = ReflectionTestUtils.invokeMethod(
                executor, "getConfigString", config, "mergeStrategy", "all");

        assertEquals("first", strategy);
    }

    @Test
    void getConfigString_withMissingValue_shouldReturnDefault() {
        Map<String, Object> config = new HashMap<>();

        String strategy = ReflectionTestUtils.invokeMethod(
                executor, "getConfigString", config, "mergeStrategy", "all");

        assertEquals("all", strategy);
    }

    @Test
    void getConfigBoolean_withTrueValue_shouldReturnTrue() {
        Map<String, Object> config = new HashMap<>();
        config.put("excludeNulls", true);

        boolean excludeNulls = ReflectionTestUtils.invokeMethod(
                executor, "getConfigBoolean", config, "excludeNulls", false);

        assertTrue(excludeNulls);
    }

    @Test
    void getConfigBoolean_withMissingValue_shouldReturnDefault() {
        Map<String, Object> config = new HashMap<>();

        boolean excludeNulls = ReflectionTestUtils.invokeMethod(
                executor, "getConfigBoolean", config, "excludeNulls", true);

        assertTrue(excludeNulls);
    }

    // ===== Helper Methods =====

    private NodeResult createNodeResult(String resultValue) {
        Map<String, Object> output = new HashMap<>();
        output.put("status", 200);
        output.put("data", resultValue);

        return NodeResult.builder()
                .nodeId("test-node")
                .status(ExecutionStatus.SUCCESS)
                .output(output)
                .build();
    }
}

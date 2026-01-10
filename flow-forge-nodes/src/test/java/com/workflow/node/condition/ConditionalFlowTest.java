package com.workflow.node.condition;

import com.workflow.context.VariableResolver;
import com.workflow.model.*;
import com.workflow.node.merge.MergeNodeExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for conditional branching and merging flow.
 * <p>
 * Tests the complete workflow of IF/MERGE nodes including:
 * <ul>
 *   <li>True/false conditional branching</li>
 *   <li>Nested conditional flows</li>
 *   <li>Merge waiting for all branches</li>
 *   <li>Complex expression evaluation</li>
 * </ul>
 */
class ConditionalFlowTest {

    private VariableResolver variableResolver;
    private SpelEvaluator spelEvaluator;
    private IfNodeExecutor ifExecutor;
    private MergeNodeExecutor mergeExecutor;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        variableResolver = new VariableResolver();
        spelEvaluator = new SpelEvaluator();
        ifExecutor = new IfNodeExecutor(variableResolver, spelEvaluator);
        mergeExecutor = new MergeNodeExecutor(variableResolver);

        context = ExecutionContext.builder()
                .executionId("test-execution")
                .workflowId("test-workflow")
                .build();
    }

    // ===== Basic True/False Branching =====

    @Test
    void conditionalFlow_withTrueCondition_shouldFollowTrueBranch() {
        // Setup: node1 produces status=200
        Map<String, Object> node1Output = new HashMap<>();
        node1Output.put("status", 200);

        NodeResult node1Result = NodeResult.builder()
                .nodeId("node1")
                .status(ExecutionStatus.SUCCESS)
                .output(node1Output)
                .build();
        context.getNodeResults().put("node1", node1Result);

        // Create IF node
        Node ifNode = Node.builder()
                .id("if-node")
                .type(NodeType.IF)
                .name("Check Status")
                .build();
        Map<String, Object> ifConfig = new HashMap<>();
        ifConfig.put("condition", "#node1.output.status == 200");
        ifConfig.put("trueValue", "success_branch");
        ifConfig.put("falseValue", "failure_branch");
        ifNode.setConfig(ifConfig);

        // Execute IF node
        NodeResult ifResult = ifExecutor.execute(ifNode, context);

        // Verify
        assertEquals(ExecutionStatus.SUCCESS, ifResult.getStatus());
        assertTrue((Boolean) ifResult.getOutput().get("result"));
        assertEquals("success_branch", ifResult.getOutput().get("selected"));
    }

    @Test
    void conditionalFlow_withFalseCondition_shouldFollowFalseBranch() {
        // Setup: node1 produces status=404
        Map<String, Object> node1Output = new HashMap<>();
        node1Output.put("status", 404);

        NodeResult node1Result = NodeResult.builder()
                .nodeId("node1")
                .status(ExecutionStatus.SUCCESS)
                .output(node1Output)
                .build();
        context.getNodeResults().put("node1", node1Result);

        // Create IF node
        Node ifNode = Node.builder()
                .id("if-node")
                .type(NodeType.IF)
                .name("Check Status")
                .build();
        Map<String, Object> ifConfig = new HashMap<>();
        ifConfig.put("condition", "#node1.output.status == 200");
        ifConfig.put("trueValue", "success_path");
        ifConfig.put("falseValue", "error_path");
        ifNode.setConfig(ifConfig);

        // Execute IF node
        NodeResult ifResult = ifExecutor.execute(ifNode, context);

        // Verify
        assertEquals(ExecutionStatus.SUCCESS, ifResult.getStatus());
        assertFalse((Boolean) ifResult.getOutput().get("result"));
        assertEquals("error_path", ifResult.getOutput().get("selected"));
    }

    // ===== Nested Conditions =====

    @Test
    void conditionalFlow_withNestedConditions_shouldEvaluateCorrectly() {
        // Setup: multiple predecessors
        Map<String, Object> node1Output = new HashMap<>();
        node1Output.put("status", 200);
        node1Output.put("count", 5);

        Map<String, Object> node2Output = new HashMap<>();
        node2Output.put("active", true);

        NodeResult node1Result = NodeResult.builder()
                .nodeId("node1")
                .status(ExecutionStatus.SUCCESS)
                .output(node1Output)
                .build();

        NodeResult node2Result = NodeResult.builder()
                .nodeId("node2")
                .status(ExecutionStatus.SUCCESS)
                .output(node2Output)
                .build();

        context.getNodeResults().put("node1", node1Result);
        context.getNodeResults().put("node2", node2Result);

        // Create IF node with complex condition
        Node ifNode = Node.builder()
                .id("if-nested")
                .type(NodeType.IF)
                .build();
        Map<String, Object> ifConfig = new HashMap<>();
        ifConfig.put("condition", "(#node1.output.status == 200) and (#node1.output.count > 0)");
        ifNode.setConfig(ifConfig);

        // Execute
        NodeResult ifResult = ifExecutor.execute(ifNode, context);

        // Verify - should be true since both conditions are met
        assertEquals(ExecutionStatus.SUCCESS, ifResult.getStatus());
        assertTrue((Boolean) ifResult.getOutput().get("result"));
    }

    @Test
    void conditionalFlow_withOrCondition_shouldEvaluateCorrectly() {
        // Setup: one node succeeds, another fails
        Map<String, Object> node1Output = new HashMap<>();
        node1Output.put("status", 404);

        Map<String, Object> node2Output = new HashMap<>();
        node2Output.put("status", 200);

        NodeResult node1Result = NodeResult.builder()
                .nodeId("node1")
                .status(ExecutionStatus.SUCCESS)
                .output(node1Output)
                .build();

        NodeResult node2Result = NodeResult.builder()
                .nodeId("node2")
                .status(ExecutionStatus.SUCCESS)
                .output(node2Output)
                .build();

        context.getNodeResults().put("node1", node1Result);
        context.getNodeResults().put("node2", node2Result);

        // Create IF node with OR condition
        Node ifNode = Node.builder()
                .id("if-or")
                .type(NodeType.IF)
                .build();
        Map<String, Object> ifConfig = new HashMap<>();
        ifConfig.put("condition", "(#node1.output.status == 200) or (#node2.output.status == 200)");
        ifNode.setConfig(ifConfig);

        // Execute
        NodeResult ifResult = ifExecutor.execute(ifNode, context);

        // Verify - should be true since at least one is 200
        assertEquals(ExecutionStatus.SUCCESS, ifResult.getStatus());
        assertTrue((Boolean) ifResult.getOutput().get("result"));
    }

    // ===== Merge Node Tests =====

    @Test
    void mergeFlow_withMultiplePredecessors_shouldCombineAllResults() {
        // Setup: three branches complete
        Map<String, Object> branch1Output = new HashMap<>();
        branch1Output.put("branch", "A");
        branch1Output.put("value", 100);

        Map<String, Object> branch2Output = new HashMap<>();
        branch2Output.put("branch", "B");
        branch2Output.put("value", 200);

        Map<String, Object> branch3Output = new HashMap<>();
        branch3Output.put("branch", "C");
        branch3Output.put("value", 300);

        context.getNodeResults().put("branch1", NodeResult.builder()
                .nodeId("branch1").status(ExecutionStatus.SUCCESS).output(branch1Output).build());
        context.getNodeResults().put("branch2", NodeResult.builder()
                .nodeId("branch2").status(ExecutionStatus.SUCCESS).output(branch2Output).build());
        context.getNodeResults().put("branch3", NodeResult.builder()
                .nodeId("branch3").status(ExecutionStatus.SUCCESS).output(branch3Output).build());

        // Create MERGE node
        Node mergeNode = Node.builder()
                .id("merge-node")
                .type(NodeType.MERGE)
                .build();
        Map<String, Object> mergeConfig = new HashMap<>();
        mergeConfig.put("mergeStrategy", "all");
        mergeNode.setConfig(mergeConfig);

        // Execute
        NodeResult mergeResult = mergeExecutor.execute(mergeNode, context);

        // Verify
        assertEquals(ExecutionStatus.SUCCESS, mergeResult.getStatus());
        assertEquals(3, mergeResult.getOutput().get("count"));

        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) mergeResult.getOutput().get("merged");
        assertEquals(3, merged.size());
        assertTrue(merged.containsKey("branch1"));
        assertTrue(merged.containsKey("branch2"));
        assertTrue(merged.containsKey("branch3"));
    }

    @Test
    void mergeFlow_withArrayStrategy_shouldReturnArray() {
        // Setup: two branches
        Map<String, Object> branch1Output = new HashMap<>();
        branch1Output.put("result", "first");

        Map<String, Object> branch2Output = new HashMap<>();
        branch2Output.put("result", "second");

        context.getNodeResults().put("branch1", NodeResult.builder()
                .nodeId("branch1").status(ExecutionStatus.SUCCESS).output(branch1Output).build());
        context.getNodeResults().put("branch2", NodeResult.builder()
                .nodeId("branch2").status(ExecutionStatus.SUCCESS).output(branch2Output).build());

        // Create MERGE node with array strategy
        Node mergeNode = Node.builder()
                .id("merge-array")
                .type(NodeType.MERGE)
                .build();
        Map<String, Object> mergeConfig = new HashMap<>();
        mergeConfig.put("mergeStrategy", "array");
        mergeNode.setConfig(mergeConfig);

        // Execute
        NodeResult mergeResult = mergeExecutor.execute(mergeNode, context);

        // Verify
        assertEquals(ExecutionStatus.SUCCESS, mergeResult.getStatus());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) mergeResult.getOutput().get("results");
        assertEquals(2, results.size());
        assertEquals(2, mergeResult.getOutput().get("count"));
    }

    @Test
    void mergeFlow_withIncludeFilter_shouldOnlyIncludeSpecifiedNodes() {
        // Setup: multiple results
        Map<String, Object> output1 = Map.of("value", "a");
        Map<String, Object> output2 = Map.of("value", "b");
        Map<String, Object> output3 = Map.of("value", "c");

        context.getNodeResults().put("node1", NodeResult.builder()
                .nodeId("node1").status(ExecutionStatus.SUCCESS).output(output1).build());
        context.getNodeResults().put("node2", NodeResult.builder()
                .nodeId("node2").status(ExecutionStatus.SUCCESS).output(output2).build());
        context.getNodeResults().put("node3", NodeResult.builder()
                .nodeId("node3").status(ExecutionStatus.SUCCESS).output(output3).build());

        // Create MERGE node with include filter
        Node mergeNode = Node.builder()
                .id("merge-filtered")
                .type(NodeType.MERGE)
                .build();
        Map<String, Object> mergeConfig = new HashMap<>();
        mergeConfig.put("mergeStrategy", "all");
        mergeConfig.put("includeNodeIds", Arrays.asList("node1", "node3"));
        mergeNode.setConfig(mergeConfig);

        // Execute
        NodeResult mergeResult = mergeExecutor.execute(mergeNode, context);

        // Verify
        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) mergeResult.getOutput().get("merged");
        assertEquals(2, merged.size());
        assertTrue(merged.containsKey("node1"));
        assertTrue(merged.containsKey("node3"));
        assertFalse(merged.containsKey("node2"));
    }

    // ===== Complete Conditional Flow =====

    @Test
    void completeConditionalFlow_ifThenElseMerge_shouldWorkEndToEnd() {
        // This simulates a complete workflow:
        // node1 (data source) -> ifNode (condition) -> branch1/branch2 -> mergeNode

        // Step 1: Initial data
        Map<String, Object> initialData = new HashMap<>();
        initialData.put("userType", "premium");
        initialData.put("amount", 150);

        NodeResult sourceResult = NodeResult.builder()
                .nodeId("source")
                .status(ExecutionStatus.SUCCESS)
                .output(initialData)
                .build();
        context.getNodeResults().put("source", sourceResult);

        // Step 2: IF node evaluates userType
        Node ifNode = Node.builder()
                .id("check-user-type")
                .type(NodeType.IF)
                .build();
        Map<String, Object> ifConfig = new HashMap<>();
        ifConfig.put("condition", "#source.output.userType == 'premium'");
        ifConfig.put("trueValue", "premium_branch");
        ifConfig.put("falseValue", "standard_branch");
        ifNode.setConfig(ifConfig);

        NodeResult ifResult = ifExecutor.execute(ifNode, context);
        assertEquals("premium_branch", ifResult.getOutput().get("selected"));

        // Step 3: Simulate branch execution (add branch results)
        Map<String, Object> premiumOutput = new HashMap<>();
        premiumOutput.put("discount", 0.2);
        premiumOutput.put("branch", "premium");

        NodeResult premiumResult = NodeResult.builder()
                .nodeId("premium_branch")
                .status(ExecutionStatus.SUCCESS)
                .output(premiumOutput)
                .build();
        context.getNodeResults().put("premium_branch", premiumResult);

        // Step 4: Merge node combines results
        Node mergeNode = Node.builder()
                .id("merge-results")
                .type(NodeType.MERGE)
                .build();
        Map<String, Object> mergeConfig = new HashMap<>();
        mergeConfig.put("mergeStrategy", "all");
        mergeConfig.put("includeNodeIds", Arrays.asList("source", "premium_branch"));
        mergeNode.setConfig(mergeConfig);

        NodeResult mergeResult = mergeExecutor.execute(mergeNode, context);

        // Verify complete flow
        assertEquals(ExecutionStatus.SUCCESS, mergeResult.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) mergeResult.getOutput().get("merged");
        assertEquals(2, merged.size());
        assertTrue(merged.containsKey("source"));
        assertTrue(merged.containsKey("premium_branch"));
    }

    // ===== Edge Conditions =====

    @Test
    void conditionalFlow_withNullValues_shouldHandleGracefully() {
        // Setup: result with null nested value
        Map<String, Object> nodeOutput = new HashMap<>();
        nodeOutput.put("data", null);

        NodeResult nodeResult = NodeResult.builder()
                .nodeId("node1")
                .status(ExecutionStatus.SUCCESS)
                .output(nodeOutput)
                .build();
        context.getNodeResults().put("node1", nodeResult);

        // Test: condition should handle null
        Node ifNode = Node.builder()
                .id("if-null")
                .type(NodeType.IF)
                .build();
        Map<String, Object> ifConfig = new HashMap<>();
        ifConfig.put("condition", "#node1.output.data == null");
        ifNode.setConfig(ifConfig);

        NodeResult ifResult = ifExecutor.execute(ifNode, context);

        assertEquals(ExecutionStatus.SUCCESS, ifResult.getStatus());
        assertTrue((Boolean) ifResult.getOutput().get("result"));
    }

    @Test
    void conditionalFlow_withNumericComparison_shouldEvaluateCorrectly() {
        // Setup: numeric values
        Map<String, Object> nodeOutput = new HashMap<>();
        nodeOutput.put("count", 10);
        nodeOutput.put("threshold", 5);

        NodeResult nodeResult = NodeResult.builder()
                .nodeId("counter")
                .status(ExecutionStatus.SUCCESS)
                .output(nodeOutput)
                .build();
        context.getNodeResults().put("counter", nodeResult);

        // Test various comparisons
        String[] conditions = {
                "#counter.output.count > #counter.output.threshold",
                "#counter.output.count >= 10",
                "#counter.output.count < 20",
                "#counter.output.count <= 10",
                "#counter.output.count != 5"
        };

        for (String condition : conditions) {
            Node ifNode = Node.builder()
                    .id("if-compare")
                    .type(NodeType.IF)
                    .build();
            Map<String, Object> ifConfig = new HashMap<>();
            ifConfig.put("condition", condition);
            ifNode.setConfig(ifConfig);

            NodeResult ifResult = ifExecutor.execute(ifNode, context);
            assertEquals(ExecutionStatus.SUCCESS, ifResult.getStatus(),
                    "Condition should be valid: " + condition);
            assertTrue((Boolean) ifResult.getOutput().get("result"),
                    "Condition should be true: " + condition);
        }
    }

    @Test
    void mergeFlow_withEmptyResults_shouldReturnEmpty() {
        // No predecessors completed yet
        Node mergeNode = Node.builder()
                .id("merge-empty")
                .type(NodeType.MERGE)
                .build();
        Map<String, Object> mergeConfig = new HashMap<>();
        mergeConfig.put("mergeStrategy", "all");
        mergeNode.setConfig(mergeConfig);

        NodeResult mergeResult = mergeExecutor.execute(mergeNode, context);

        assertEquals(ExecutionStatus.SUCCESS, mergeResult.getStatus());
        assertEquals(0, mergeResult.getOutput().get("count"));

        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) mergeResult.getOutput().get("merged");
        assertTrue(merged.isEmpty());
    }

    // ===== Variable Resolution Integration =====

    @Test
    void conditionalFlow_withVariableResolution_shouldResolveVariables() {
        // Setup: global variables
        Map<String, Object> globals = new HashMap<>();
        globals.put("ENVIRONMENT", "production");
        context.setGlobalVariables(globals);

        // Setup: input variables
        Map<String, Object> input = new HashMap<>();
        input.put("featureFlag", "true");
        context.setInput(input);

        // Create IF node with variable expression
        Node ifNode = Node.builder()
                .id("if-vars")
                .type(NodeType.IF)
                .build();
        Map<String, Object> ifConfig = new HashMap<>();
        ifConfig.put("condition", "#node1.output.data == 'test_value'");
        ifConfig.put("trueValue", "enabled");
        ifConfig.put("falseValue", "disabled");
        ifNode.setConfig(ifConfig);

        // Setup node result with data
        Map<String, Object> nodeOutput = new HashMap<>();
        nodeOutput.put("data", "test_value");

        NodeResult node1Result = NodeResult.builder()
                .nodeId("node1")
                .status(ExecutionStatus.SUCCESS)
                .output(nodeOutput)
                .build();
        context.getNodeResults().put("node1", node1Result);

        // Execute
        NodeResult ifResult = ifExecutor.execute(ifNode, context);

        assertEquals(ExecutionStatus.SUCCESS, ifResult.getStatus());
        assertTrue((Boolean) ifResult.getOutput().get("result"));
        assertEquals("enabled", ifResult.getOutput().get("selected"));
    }

    // ===== Security Tests =====

    @Test
    void conditionalFlow_withMaliciousExpression_shouldBlock() {
        // Create IF node with malicious condition
        Node ifNode = Node.builder()
                .id("if-malicious")
                .type(NodeType.IF)
                .build();
        Map<String, Object> ifConfig = new HashMap<>();
        ifConfig.put("condition", "T(java.lang.Runtime).getRuntime().exec('rm -rf /')");
        ifNode.setConfig(ifConfig);

        // Execute - should fail due to security check
        assertThrows(WorkflowException.class, () -> ifExecutor.execute(ifNode, context));
    }

    @Test
    void conditionalFlow_withSystemExitAttempt_shouldBlock() {
        Node ifNode = Node.builder()
                .id("if-exit")
                .type(NodeType.IF)
                .build();
        Map<String, Object> ifConfig = new HashMap<>();
        ifConfig.put("condition", "System.exit(0)");
        ifNode.setConfig(ifConfig);

        // Execute - should fail due to security check
        assertThrows(WorkflowException.class, () -> ifExecutor.execute(ifNode, context));
    }
}

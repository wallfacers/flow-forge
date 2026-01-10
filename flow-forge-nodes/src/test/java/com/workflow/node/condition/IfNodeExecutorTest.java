package com.workflow.node.condition;

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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link IfNodeExecutor}.
 */
@ExtendWith(MockitoExtension.class)
class IfNodeExecutorTest {

    @Mock
    private VariableResolver variableResolver;

    @Mock
    private SpelEvaluator spelEvaluator;

    @Mock
    private ExecutionContext context;

    private IfNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new IfNodeExecutor(variableResolver, spelEvaluator);
    }

    // ===== Basic Tests =====

    @Test
    void getSupportedType_shouldReturnIF() {
        assertEquals(NodeType.IF, executor.getSupportedType());
    }

    @Test
    void execute_withNullNode_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> executor.execute(null, context));
    }

    @Test
    void execute_withNullContext_shouldThrowException() {
        Node node = Node.builder().id("test-if").type(NodeType.IF).build();
        assertThrows(IllegalArgumentException.class, () -> executor.execute(node, null));
    }

    // ===== No Condition Tests =====

    @Test
    void execute_withNoCondition_shouldReturnTrue() {
        Node node = Node.builder().id("test-if").type(NodeType.IF).build();
        node.setConfig(new HashMap<>());

        lenient().when(variableResolver.resolveMap(any(), any())).thenReturn(new HashMap<>());

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test-if", result.getNodeId());

        Map<String, Object> output = result.getOutput();
        assertTrue((Boolean) output.get("result"));
        assertNull(output.get("condition"));
        assertNull(output.get("selected"));
    }

    @Test
    void execute_withEmptyCondition_shouldReturnTrue() {
        Node node = Node.builder().id("test-if").type(NodeType.IF).build();
        Map<String, Object> config = new HashMap<>();
        config.put("condition", "");
        node.setConfig(config);

        lenient().when(variableResolver.resolveMap(any(), any())).thenReturn(new HashMap<>());

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertTrue((Boolean) result.getOutput().get("result"));
        assertNull(result.getOutput().get("condition"));
        assertNull(result.getOutput().get("selected"));
    }

    // ===== Successful Condition Evaluation =====

    @Test
    void execute_withTrueCondition_shouldReturnTrue() {
        Node node = Node.builder().id("test-if").type(NodeType.IF).build();
        Map<String, Object> config = new HashMap<>();
        config.put("condition", "status == 200");
        node.setConfig(config);

        Map<String, Object> resolvedConfig = new HashMap<>();
        resolvedConfig.put("condition", "status == 200");

        when(variableResolver.resolveMap(any(), any())).thenReturn(resolvedConfig);
        when(spelEvaluator.evaluate("status == 200", context)).thenReturn(true);

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test-if", result.getNodeId());

        Map<String, Object> output = result.getOutput();
        assertTrue((Boolean) output.get("result"));
        assertEquals("status == 200", output.get("condition"));
        assertEquals("true", output.get("selected"));
    }

    @Test
    void execute_withFalseCondition_shouldReturnFalse() {
        Node node = Node.builder().id("test-if").type(NodeType.IF).build();
        Map<String, Object> config = new HashMap<>();
        config.put("condition", "status == 404");
        node.setConfig(config);

        Map<String, Object> resolvedConfig = new HashMap<>();
        resolvedConfig.put("condition", "status == 404");

        when(variableResolver.resolveMap(any(), any())).thenReturn(resolvedConfig);
        when(spelEvaluator.evaluate("status == 404", context)).thenReturn(false);

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());

        Map<String, Object> output = result.getOutput();
        assertFalse((Boolean) output.get("result"));
        assertEquals("false", output.get("selected"));
    }

    // ===== Custom True/False Values =====

    @Test
    void execute_withCustomValues_shouldReturnSelectedValue() {
        Node node = Node.builder().id("test-if").type(NodeType.IF).build();
        Map<String, Object> config = new HashMap<>();
        config.put("condition", "count > 0");
        config.put("trueValue", "success");
        config.put("falseValue", "failure");
        node.setConfig(config);

        Map<String, Object> resolvedConfig = new HashMap<>();
        resolvedConfig.put("condition", "count > 0");
        resolvedConfig.put("trueValue", "success");
        resolvedConfig.put("falseValue", "failure");

        when(variableResolver.resolveMap(any(), any())).thenReturn(resolvedConfig);
        when(spelEvaluator.evaluate("count > 0", context)).thenReturn(true);

        NodeResult result = executor.execute(node, context);

        Map<String, Object> output = result.getOutput();
        assertTrue((Boolean) output.get("result"));
        assertEquals("success", output.get("trueValue"));
        assertEquals("failure", output.get("falseValue"));
        assertEquals("success", output.get("selected"));
    }

    @Test
    void execute_withFalseCondition_shouldReturnFalseValue() {
        Node node = Node.builder().id("test-if").type(NodeType.IF).build();
        Map<String, Object> config = new HashMap<>();
        config.put("condition", "count > 100");
        config.put("trueValue", "proceed");
        config.put("falseValue", "stop");
        node.setConfig(config);

        Map<String, Object> resolvedConfig = new HashMap<>();
        resolvedConfig.put("condition", "count > 100");
        resolvedConfig.put("trueValue", "proceed");
        resolvedConfig.put("falseValue", "stop");

        when(variableResolver.resolveMap(any(), any())).thenReturn(resolvedConfig);
        when(spelEvaluator.evaluate("count > 100", context)).thenReturn(false);

        NodeResult result = executor.execute(node, context);

        Map<String, Object> output = result.getOutput();
        assertFalse((Boolean) output.get("result"));
        assertEquals("stop", output.get("selected"));
    }

    // ===== Condition Evaluation Failures =====

    @Test
    void execute_withParseException_shouldReturnFailure() {
        Node node = Node.builder().id("test-if").type(NodeType.IF).build();
        Map<String, Object> config = new HashMap<>();
        config.put("condition", "invalid syntax here(");
        node.setConfig(config);

        Map<String, Object> resolvedConfig = new HashMap<>();
        resolvedConfig.put("condition", "invalid syntax here(");

        when(variableResolver.resolveMap(any(), any())).thenReturn(resolvedConfig);
        when(spelEvaluator.evaluate("invalid syntax here(", context))
                .thenThrow(new com.workflow.model.WorkflowException("Parse error"));

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.FAILED, result.getStatus());
        assertTrue(result.getErrorMessage().contains("Condition evaluation failed"));
    }

    @Test
    void execute_withEvaluationException_shouldReturnFailure() {
        Node node = Node.builder().id("test-if").type(NodeType.IF).build();
        Map<String, Object> config = new HashMap<>();
        config.put("condition", "undefined.property == true");
        node.setConfig(config);

        Map<String, Object> resolvedConfig = new HashMap<>();
        resolvedConfig.put("condition", "undefined.property == true");

        when(variableResolver.resolveMap(any(), any())).thenReturn(resolvedConfig);
        when(spelEvaluator.evaluate("undefined.property == true", context))
                .thenThrow(new RuntimeException("Property not found"));

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.FAILED, result.getStatus());
        assertTrue(result.getErrorMessage().contains("Condition evaluation failed"));
    }

    // ===== Config Helper Tests =====

    @Test
    void getConfigString_withExistingValue_shouldReturnValue() {
        Map<String, Object> config = new HashMap<>();
        config.put("condition", "status == 200");

        String condition = ReflectionTestUtils.invokeMethod(
                executor, "getConfigString", config, "condition", null);

        assertEquals("status == 200", condition);
    }

    @Test
    void getConfigString_withMissingValue_shouldReturnDefault() {
        Map<String, Object> config = new HashMap<>();

        String condition = ReflectionTestUtils.invokeMethod(
                executor, "getConfigString", config, "condition", "default");

        assertEquals("default", condition);
    }

    @Test
    void getConfigString_withNullValue_shouldReturnDefault() {
        Map<String, Object> config = new HashMap<>();
        config.put("condition", null);

        String condition = ReflectionTestUtils.invokeMethod(
                executor, "getConfigString", config, "condition", "true");

        assertEquals("true", condition);
    }

    // ===== Variable Resolution Integration =====

    @Test
    void execute_withVariableInCondition_shouldResolveFirst() {
        Node node = Node.builder().id("test-if").type(NodeType.IF).build();
        Map<String, Object> config = new HashMap<>();
        config.put("condition", "{{node1.output.status}} == 200");
        node.setConfig(config);

        Map<String, Object> resolvedConfig = new HashMap<>();
        resolvedConfig.put("condition", "200 == 200");

        when(variableResolver.resolveMap(any(), any())).thenReturn(resolvedConfig);
        when(spelEvaluator.evaluate("200 == 200", context)).thenReturn(true);

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertTrue((Boolean) result.getOutput().get("result"));

        verify(variableResolver).resolveMap(any(), any());
        verify(spelEvaluator).evaluate("200 == 200", context);
    }

    // ===== Edge Cases =====

    @Test
    void execute_withComplexExpression_shouldHandleCorrectly() {
        Node node = Node.builder().id("test-if").type(NodeType.IF).build();
        Map<String, Object> config = new HashMap<>();
        config.put("condition", "(status == 200) and (count > 0)");
        config.put("trueValue", "all_good");
        config.put("falseValue", "check_failed");
        node.setConfig(config);

        Map<String, Object> resolvedConfig = new HashMap<>();
        resolvedConfig.put("condition", "(status == 200) and (count > 0)");
        resolvedConfig.put("trueValue", "all_good");
        resolvedConfig.put("falseValue", "check_failed");

        when(variableResolver.resolveMap(any(), any())).thenReturn(resolvedConfig);
        when(spelEvaluator.evaluate("(status == 200) and (count > 0)", context)).thenReturn(true);

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("all_good", result.getOutput().get("selected"));
    }

    @Test
    void execute_withOnlyTrueValue_shouldDefaultFalseToFalse() {
        Node node = Node.builder().id("test-if").type(NodeType.IF).build();
        Map<String, Object> config = new HashMap<>();
        config.put("condition", "active == true");
        config.put("trueValue", "yes");
        node.setConfig(config);

        Map<String, Object> resolvedConfig = new HashMap<>();
        resolvedConfig.put("condition", "active == true");
        resolvedConfig.put("trueValue", "yes");

        when(variableResolver.resolveMap(any(), any())).thenReturn(resolvedConfig);
        when(spelEvaluator.evaluate("active == true", context)).thenReturn(false);

        NodeResult result = executor.execute(node, context);

        Map<String, Object> output = result.getOutput();
        assertEquals("yes", output.get("trueValue"));
        assertNull(output.get("falseValue"));
        assertEquals("false", output.get("selected"));  // default
    }

    @Test
    void execute_withOnlyFalseValue_shouldDefaultTrueToTrue() {
        Node node = Node.builder().id("test-if").type(NodeType.IF).build();
        Map<String, Object> config = new HashMap<>();
        config.put("condition", "active == true");
        config.put("falseValue", "no");
        node.setConfig(config);

        Map<String, Object> resolvedConfig = new HashMap<>();
        resolvedConfig.put("condition", "active == true");
        resolvedConfig.put("falseValue", "no");

        when(variableResolver.resolveMap(any(), any())).thenReturn(resolvedConfig);
        when(spelEvaluator.evaluate("active == true", context)).thenReturn(true);

        NodeResult result = executor.execute(node, context);

        Map<String, Object> output = result.getOutput();
        assertNull(output.get("trueValue"));
        assertEquals("no", output.get("falseValue"));
        assertEquals("true", output.get("selected"));  // default
    }
}

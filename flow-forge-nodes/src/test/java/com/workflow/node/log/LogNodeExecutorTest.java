package com.workflow.node.log;

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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LogNodeExecutor}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogNodeExecutorTest {

    @Mock
    private VariableResolver variableResolver;

    @Mock
    private ExecutionContext context;

    private LogNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new LogNodeExecutor(variableResolver);
    }

    @Test
    void getSupportedType_shouldReturnLOG() {
        assertEquals(NodeType.LOG, executor.getSupportedType());
    }

    @Test
    void execute_withNullNode_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> executor.execute(null, context));
    }

    @Test
    void execute_withNullContext_shouldThrowException() {
        Node node = Node.builder().id("test-node").type(NodeType.LOG).build();
        assertThrows(IllegalArgumentException.class, () -> executor.execute(node, null));
    }

    @Test
    void execute_withInfoLevel_shouldReturnSuccess() {
        Node node = Node.builder().id("test-node").type(NodeType.LOG).build();
        Map<String, Object> config = new HashMap<>();
        config.put("level", "INFO");
        config.put("message", "Test log message");
        node.setConfig(config);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test-node", result.getNodeId());
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().isEmpty());
    }

    @Test
    void execute_withDebugLevel_shouldReturnSuccess() {
        Node node = Node.builder().id("test-node").type(NodeType.LOG).build();
        Map<String, Object> config = new HashMap<>();
        config.put("level", "DEBUG");
        config.put("message", "Debug message");
        node.setConfig(config);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test-node", result.getNodeId());
    }

    @Test
    void execute_withWarnLevel_shouldReturnSuccess() {
        Node node = Node.builder().id("test-node").type(NodeType.LOG).build();
        Map<String, Object> config = new HashMap<>();
        config.put("level", "WARN");
        config.put("message", "Warning message");
        node.setConfig(config);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test-node", result.getNodeId());
    }

    @Test
    void execute_withErrorLevel_shouldReturnSuccess() {
        Node node = Node.builder().id("test-node").type(NodeType.LOG).build();
        Map<String, Object> config = new HashMap<>();
        config.put("level", "ERROR");
        config.put("message", "Error message");
        node.setConfig(config);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test-node", result.getNodeId());
    }

    @Test
    void execute_withDefaultLevel_shouldReturnSuccess() {
        Node node = Node.builder().id("test-node").type(NodeType.LOG).build();
        Map<String, Object> config = new HashMap<>();
        config.put("message", "Test message without level");
        node.setConfig(config);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test-node", result.getNodeId());
    }

    @Test
    void execute_withUnknownLevel_shouldUseInfoAndReturnSuccess() {
        Node node = Node.builder().id("test-node").type(NodeType.LOG).build();
        Map<String, Object> config = new HashMap<>();
        config.put("level", "UNKNOWN");
        config.put("message", "Test message with unknown level");
        node.setConfig(config);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test-node", result.getNodeId());
    }

    @Test
    void execute_withWarningLevelAlias_shouldReturnSuccess() {
        Node node = Node.builder().id("test-node").type(NodeType.LOG).build();
        Map<String, Object> config = new HashMap<>();
        config.put("level", "WARNING");
        config.put("message", "Warning message");
        node.setConfig(config);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
    }

    @Test
    void execute_withEmptyMessage_shouldReturnSuccess() {
        Node node = Node.builder().id("test-node").type(NodeType.LOG).build();
        Map<String, Object> config = new HashMap<>();
        config.put("level", "INFO");
        node.setConfig(config);

        when(variableResolver.resolveMap(any(), any())).thenReturn(config);

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
    }

    @Test
    void execute_withVariableInMessage_shouldResolveCorrectly() {
        Node node = Node.builder().id("test-node").type(NodeType.LOG).build();
        Map<String, Object> config = new HashMap<>();
        config.put("level", "INFO");
        config.put("message", "Processing user: {{input.userId}}");
        node.setConfig(config);

        when(variableResolver.resolveMap(any(), any())).thenReturn(new HashMap<>(config));
        when(variableResolver.resolve(any(), any())).thenReturn("Processing user: user-123");

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test-node", result.getNodeId());
    }

    @Test
    void execute_withMultipleVariablesInMessage_shouldResolveAll() {
        Node node = Node.builder().id("test-node").type(NodeType.LOG).build();
        Map<String, Object> config = new HashMap<>();
        config.put("level", "INFO");
        config.put("message", "User: {{input.userId}}, Status: {{node1.output.status}}");
        node.setConfig(config);

        when(variableResolver.resolveMap(any(), any())).thenReturn(new HashMap<>(config));
        when(variableResolver.resolve(any(), any())).thenReturn("User: user-123, Status: 200");

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test-node", result.getNodeId());
    }

    @Test
    void resolveConfig_withMessageVariable_shouldResolveCorrectly() {
        Node node = Node.builder().id("test-node").type(NodeType.LOG).build();
        Map<String, Object> config = new HashMap<>();
        config.put("level", "INFO");
        config.put("message", "Test: {{input.value}}");
        node.setConfig(config);

        when(variableResolver.resolveMap(any(), any())).thenAnswer(invocation -> {
            Map<String, Object> original = invocation.getArgument(0);
            return new HashMap<>(original);
        });

        when(variableResolver.resolve(any(), any())).thenReturn("Test: resolved-value");

        Map<String, Object> resolved = ReflectionTestUtils.invokeMethod(
                executor, "resolveConfig", node, context);

        assertNotNull(resolved);
        assertEquals("Test: resolved-value", resolved.get("message"));
    }

    @Test
    void execute_withEmptyConfig_shouldReturnSuccess() {
        Node node = Node.builder().id("test-node").type(NodeType.LOG).build();
        node.setConfig(new HashMap<>());

        when(variableResolver.resolveMap(any(), any())).thenReturn(new HashMap<>());

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test-node", result.getNodeId());
    }

    @Test
    void execute_withNullConfig_shouldReturnSuccess() {
        Node node = Node.builder().id("test-node").type(NodeType.LOG).build();
        node.setConfig(null);

        when(variableResolver.resolveMap(any(), any())).thenReturn(null);

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test-node", result.getNodeId());
    }

    @Test
    void execute_withSystemVariable_shouldResolveCorrectly() {
        Node node = Node.builder().id("test-node").type(NodeType.LOG).build();
        Map<String, Object> config = new HashMap<>();
        config.put("message", "Execution ID: {{system.executionId}}");
        node.setConfig(config);

        when(variableResolver.resolveMap(any(), any())).thenReturn(new HashMap<>(config));
        when(variableResolver.resolve(any(), any())).thenReturn("Execution ID: exec-456");

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test-node", result.getNodeId());
    }

    @Test
    void execute_withGlobalVariable_shouldResolveCorrectly() {
        Node node = Node.builder().id("test-node").type(NodeType.LOG).build();
        Map<String, Object> config = new HashMap<>();
        config.put("message", "API Key: {{global.apiKey}}");
        node.setConfig(config);

        when(variableResolver.resolveMap(any(), any())).thenReturn(new HashMap<>(config));
        when(variableResolver.resolve(any(), any())).thenReturn("API Key: ***hidden***");

        NodeResult result = executor.execute(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test-node", result.getNodeId());
    }
}

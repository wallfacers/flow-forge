package com.workflow.node;

import com.workflow.model.ExecutionContext;
import com.workflow.model.ExecutionStatus;
import com.workflow.model.Node;
import com.workflow.model.NodeResult;
import com.workflow.model.NodeType;
import com.workflow.model.WorkflowException;
import com.workflow.node.http.HttpNodeExecutor;
import com.workflow.node.log.LogNodeExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NodeExecutorFactory}.
 */
class NodeExecutorFactoryTest {

    private NodeExecutorFactory factory;

    @BeforeEach
    void setUp() {
        // Create mock executors for testing
        NodeExecutor mockHttpExecutor = new NodeExecutor() {
            @Override
            public NodeResult execute(Node node, ExecutionContext context) {
                return NodeResult.success(node.getId(), java.util.Map.of("type", "http"));
            }

            @Override
            public NodeType getSupportedType() {
                return NodeType.HTTP;
            }
        };

        NodeExecutor mockLogExecutor = new NodeExecutor() {
            @Override
            public NodeResult execute(Node node, ExecutionContext context) {
                return NodeResult.success(node.getId(), java.util.Map.of("type", "log"));
            }

            @Override
            public NodeType getSupportedType() {
                return NodeType.LOG;
            }
        };

        factory = new NodeExecutorFactory(List.of(mockHttpExecutor, mockLogExecutor));
    }

    @Test
    void constructor_withExecutors_shouldRegisterAll() {
        assertEquals(2, factory.getExecutorCount());
        assertTrue(factory.hasExecutor(NodeType.HTTP));
        assertTrue(factory.hasExecutor(NodeType.LOG));
    }

    @Test
    void constructor_withDuplicateExecutors_shouldThrowException() {
        NodeExecutor http1 = new NodeExecutor() {
            @Override
            public NodeResult execute(Node node, ExecutionContext context) {
                return NodeResult.success(node.getId(), java.util.Map.of());
            }

            @Override
            public NodeType getSupportedType() {
                return NodeType.HTTP;
            }
        };

        NodeExecutor http2 = new NodeExecutor() {
            @Override
            public NodeResult execute(Node node, ExecutionContext context) {
                return NodeResult.success(node.getId(), java.util.Map.of());
            }

            @Override
            public NodeType getSupportedType() {
                return NodeType.HTTP;
            }
        };

        assertThrows(IllegalArgumentException.class,
                () -> new NodeExecutorFactory(List.of(http1, http2)));
    }

    @Test
    void getExecutor_withHttpType_shouldReturnHttpExecutor() {
        NodeExecutor executor = factory.getExecutor(NodeType.HTTP);
        assertNotNull(executor);
        assertEquals(NodeType.HTTP, executor.getSupportedType());
    }

    @Test
    void getExecutor_withLogType_shouldReturnLogExecutor() {
        NodeExecutor executor = factory.getExecutor(NodeType.LOG);
        assertNotNull(executor);
        assertEquals(NodeType.LOG, executor.getSupportedType());
    }

    @Test
    void getExecutor_withUnsupportedType_shouldThrowException() {
        assertThrows(WorkflowException.class, () -> factory.getExecutor(NodeType.SCRIPT));
    }

    @Test
    void hasExecutor_withSupportedType_shouldReturnTrue() {
        assertTrue(factory.hasExecutor(NodeType.HTTP));
        assertTrue(factory.hasExecutor(NodeType.LOG));
    }

    @Test
    void hasExecutor_withUnsupportedType_shouldReturnFalse() {
        assertFalse(factory.hasExecutor(NodeType.SCRIPT));
        assertFalse(factory.hasExecutor(NodeType.IF));
        assertFalse(factory.hasExecutor(NodeType.MERGE));
    }

    @Test
    void getSupportedTypes_shouldReturnImmutableSet() {
        Set<NodeType> types = factory.getSupportedTypes();

        assertEquals(2, types.size());
        assertTrue(types.contains(NodeType.HTTP));
        assertTrue(types.contains(NodeType.LOG));

        // Verify immutability
        assertThrows(UnsupportedOperationException.class, () -> types.add(NodeType.SCRIPT));
    }

    @Test
    void getExecutorCount_shouldReturnCorrectCount() {
        assertEquals(2, factory.getExecutorCount());
    }

    @Test
    void executeWithTimeout_withHttpNode_shouldReturnSuccess() {
        Node node = Node.builder().id("test-http").type(NodeType.HTTP).build();
        node.setTimeout(5000);
        ExecutionContext context = ExecutionContext.builder().build();

        NodeResult result = factory.executeWithTimeout(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test-http", result.getNodeId());
        assertEquals("http", result.getOutput().get("type"));
    }

    @Test
    void executeWithTimeout_withLogNode_shouldReturnSuccess() {
        Node node = Node.builder().id("test-log").type(NodeType.LOG).build();
        node.setTimeout(5000);
        ExecutionContext context = ExecutionContext.builder().build();

        NodeResult result = factory.executeWithTimeout(node, context);

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test-log", result.getNodeId());
        assertEquals("log", result.getOutput().get("type"));
    }

    @Test
    void executeWithTimeout_withUnsupportedType_shouldThrowException() {
        Node node = Node.builder().id("test-script").type(NodeType.SCRIPT).build();
        ExecutionContext context = ExecutionContext.builder().build();

        assertThrows(WorkflowException.class,
                () -> factory.executeWithTimeout(node, context));
    }

    @Test
    void determineTimeout_withNodeTimeout_shouldUseNodeTimeout() {
        Node node = Node.builder().id("test").type(NodeType.HTTP).timeout(10000L).build();

        long timeout = ReflectionTestUtils.invokeMethod(factory, "determineTimeout", node);

        assertEquals(10000L, timeout);
    }

    @Test
    void determineTimeout_withConfigTimeout_shouldUseConfigTimeout() {
        Node node = Node.builder().id("test").type(NodeType.HTTP).config(java.util.Map.of("timeout", 8000)).build();

        long timeout = ReflectionTestUtils.invokeMethod(factory, "determineTimeout", node);

        assertEquals(8000L, timeout);
    }

    @Test
    void determineTimeout_withConfigTimeoutOverride_shouldPreferConfig() {
        Node node = Node.builder().id("test").type(NodeType.HTTP).timeout(10000L).build();
        node.setConfig(java.util.Map.of("timeout", 3000));

        long timeout = ReflectionTestUtils.invokeMethod(factory, "determineTimeout", node);

        assertEquals(3000L, timeout);
    }

    @Test
    void determineTimeout_withInvalidConfigTimeout_shouldUseNodeTimeout() {
        // Build node and then use setter due to Lombok @Builder.Default quirk
        Node node = Node.builder().id("test").type(NodeType.HTTP).build();
        node.setTimeout(5000L);
        node.setConfig(java.util.Map.of("timeout", "invalid"));

        long timeout = ReflectionTestUtils.invokeMethod(factory, "determineTimeout", node);

        assertEquals(5000L, timeout);
    }

    @Test
    void determineTimeout_withNoTimeout_shouldUseDefault() {
        Node node = Node.builder().id("test").type(NodeType.HTTP).build();

        long timeout = ReflectionTestUtils.invokeMethod(factory, "determineTimeout", node);

        assertEquals(30000L, timeout); // Default from Node class
    }

    @Test
    void executeWithTimeout_withExecutorThrowing_shouldWrapException() {
        // Create executor that throws exception
        NodeExecutor failingExecutor = new NodeExecutor() {
            @Override
            public NodeResult execute(Node node, ExecutionContext context) {
                throw new RuntimeException("Execution failed");
            }

            @Override
            public NodeType getSupportedType() {
                return NodeType.WEBHOOK;
            }
        };

        NodeExecutorFactory factoryWithFailing = new NodeExecutorFactory(
                List.of(failingExecutor));

        Node node = Node.builder().id("test-failing").type(NodeType.WEBHOOK).build();
        ExecutionContext context = ExecutionContext.builder().build();

        assertThrows(WorkflowException.class,
                () -> factoryWithFailing.executeWithTimeout(node, context));
    }

    @Test
    void executeWithTimeout_withTimeout_shouldReturnFailureResult() {
        // Create executor that never completes
        NodeExecutor slowExecutor = new NodeExecutor() {
            @Override
            public NodeResult execute(Node node, ExecutionContext context) {
                try {
                    Thread.sleep(10000); // Sleep longer than timeout
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return NodeResult.success(node.getId(), java.util.Map.of());
            }

            @Override
            public NodeType getSupportedType() {
                return NodeType.WAIT;
            }
        };

        NodeExecutorFactory factoryWithSlow = new NodeExecutorFactory(
                List.of(slowExecutor));

        Node node = Node.builder().id("test-slow").type(NodeType.WAIT).build();
        node.setConfig(java.util.Map.of("timeout", 100)); // 100ms timeout
        ExecutionContext context = ExecutionContext.builder().build();

        NodeResult result = factoryWithSlow.executeWithTimeout(node, context);

        assertEquals(ExecutionStatus.FAILED, result.getStatus());
        assertTrue(result.getErrorMessage().contains("timed out"));
    }
}

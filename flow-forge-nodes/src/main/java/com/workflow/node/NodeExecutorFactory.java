package com.workflow.node;

import com.workflow.model.WorkflowException;
import com.workflow.model.ExecutionContext;
import com.workflow.model.Node;
import com.workflow.model.NodeResult;
import com.workflow.model.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Factory for node executors with timeout handling.
 * <p>
 * This factory:
 * <ul>
 *   <li>Auto-discovers all {@link NodeExecutor} implementations via Spring</li>
 *   <li>Maps executors by their supported {@link NodeType}</li>
 *   <li>Provides unified timeout control via {@link #executeWithTimeout(Node, ExecutionContext)}</li>
 * </ul>
 * <p>
 * Timeout is implemented using {@link CompletableFuture#orTimeout(long, TimeUnit)}
 * to ensure compatibility with Java 21 virtual threads.
 *
 * @see NodeExecutor
 * @see NodeType
 */
@Component
public class NodeExecutorFactory {

    private static final Logger logger = LoggerFactory.getLogger(NodeExecutorFactory.class);

    /**
     * Registry of executors by node type.
     * Uses ConcurrentHashMap for thread safety with virtual threads.
     */
    private final Map<NodeType, NodeExecutor> executors;

    /**
     * Constructor with Spring auto-wiring of all NodeExecutor implementations.
     *
     * @param executors list of all discovered NodeExecutor beans
     * @throws IllegalArgumentException if multiple executors support the same type
     */
    public NodeExecutorFactory(List<NodeExecutor> executors) {
        this.executors = new ConcurrentHashMap<>();

        for (NodeExecutor executor : executors) {
            NodeType type = executor.getSupportedType();
            if (this.executors.containsKey(type)) {
                throw new IllegalArgumentException(
                        "Duplicate executor for type " + type + ": " +
                                this.executors.get(type).getClass().getSimpleName() +
                                " and " + executor.getClass().getSimpleName());
            }
            this.executors.put(type, executor);
            logger.debug("Registered executor: {} -> {}",
                    type, executor.getClass().getSimpleName());
        }

        logger.info("Initialized NodeExecutorFactory with {} executors: {}",
                this.executors.size(), this.executors.keySet());
    }

    /**
     * Get executor for the given node type.
     *
     * @param type the node type
     * @return the executor instance
     * @throws WorkflowException if no executor found for the type
     */
    public NodeExecutor getExecutor(NodeType type) {
        NodeExecutor executor = executors.get(type);
        if (executor == null) {
            throw new WorkflowException("No executor found for node type: " + type);
        }
        return executor;
    }

    /**
     * Check if an executor exists for the given type.
     *
     * @param type the node type
     * @return true if an executor is registered
     */
    public boolean hasExecutor(NodeType type) {
        return executors.containsKey(type);
    }

    /**
     * Execute a node with timeout control.
     * <p>
     * This method wraps the executor execution in a CompletableFuture and applies
     * timeout using {@link CompletableFuture#orTimeout(long, TimeUnit)}.
     * <p>
     * The timeout is sourced from:
     * <ol>
     *   <li>Node's {@code timeout} config value (in milliseconds)</li>
     *   <li>Fallback to {@link Node#getTimeout()}</li>
     *   <li>Default of 30 seconds if not specified</li>
     * </ol>
     *
     * @param node    the node to execute
     * @param context the execution context
     * @return the execution result
     * @throws WorkflowException if timeout occurs or execution fails
     */
    public NodeResult executeWithTimeout(Node node, ExecutionContext context) {
        NodeExecutor executor = getExecutor(node.getType());

        // Determine timeout
        long timeoutMs = determineTimeout(node);

        logger.debug("Executing node {} with timeout {} ms", node.getId(), timeoutMs);

        // Execute with timeout using CompletableFuture
        try {
            CompletableFuture<NodeResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return executor.execute(node, context);
                } catch (Exception e) {
                    logger.error("Error executing node {}", node.getId(), e);
                    throw new WorkflowException("Node execution failed: " + node.getId(), e);
                }
            });

            // Apply timeout
            NodeResult result = future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join();

            logger.debug("Node {} completed with status {}", node.getId(), result.getStatus());
            return result;

        } catch (CompletionException e) {
            // Check if timeout occurred
            if (e.getCause() instanceof TimeoutException) {
                logger.warn("Node {} execution timed out after {} ms", node.getId(), timeoutMs);
                return NodeResult.failure(node.getId(),
                        "Node execution timed out after " + timeoutMs + " ms");
            }
            // Check if WorkflowException was thrown
            if (e.getCause() instanceof WorkflowException) {
                throw (WorkflowException) e.getCause();
            }
            throw new WorkflowException("Failed to execute node: " + node.getId(), e.getCause());
        } catch (Exception e) {
            throw new WorkflowException("Failed to execute node: " + node.getId(), e);
        }
    }

    /**
     * Determine the timeout for a node.
     * <p>
     * Priority order:
     * <ol>
     *   <li>Config {@code timeout} value</li>
     *   <li>Node's {@link Node#getTimeout()}</li>
     *   <li>Default 30000 ms (30 seconds)</li>
     * </ol>
     *
     * @param node the node
     * @return timeout in milliseconds
     */
    private long determineTimeout(Node node) {
        // Check config first
        Object configTimeout = node.getConfigValue("timeout");
        if (configTimeout instanceof Number) {
            return ((Number) configTimeout).longValue();
        }
        if (configTimeout != null) {
            try {
                return Long.parseLong(configTimeout.toString());
            } catch (NumberFormatException e) {
                logger.warn("Invalid timeout config value: {}", configTimeout);
            }
        }

        // Use node's default timeout
        return node.getTimeout();
    }

    /**
     * Get all registered executor types.
     *
     * @return set of supported node types
     */
    public java.util.Set<NodeType> getSupportedTypes() {
        return java.util.Collections.unmodifiableSet(executors.keySet());
    }

    /**
     * Get the number of registered executors.
     *
     * @return executor count
     */
    public int getExecutorCount() {
        return executors.size();
    }
}

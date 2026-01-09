package com.workflow.node;

import com.workflow.model.ExecutionContext;
import com.workflow.model.Node;
import com.workflow.model.NodeResult;

/**
 * Node executor interface.
 * <p>
 * All node types must implement this interface to provide execution logic.
 * Implementations should be Spring {@code @Component} for auto-discovery.
 *
 * @see com.workflow.model.NodeType
 * @see com.workflow.model.NodeResult
 */
public interface NodeExecutor {

    /**
     * Execute a node with the given execution context.
     *
     * @param node    the node configuration containing id, type, config, etc.
     * @param context the execution context containing variable bindings and results
     * @return the execution result with status, output, and metadata
     * @throws com.workflow.exception.WorkflowException if execution fails
     */
    NodeResult execute(Node node, ExecutionContext context);

    /**
     * Get the node type this executor supports.
     * <p>
     * Used by {@link NodeExecutorFactory} to map executors to node types.
     *
     * @return the node type enum value
     */
    com.workflow.model.NodeType getSupportedType();
}

package com.workflow.node;

import com.workflow.context.VariableResolver;
import com.workflow.model.ExecutionContext;
import com.workflow.model.Node;
import com.workflow.model.NodeResult;
import com.workflow.model.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for node executors.
 * <p>
 * Provides common functionality for all node executor implementations:
 * <ul>
 *   <li>Variable resolution via {@link VariableResolver}</li>
 *   <li>Config pre-processing</li>
 *   <li>Common error handling</li>
 *   <li>Logging support</li>
 * </ul>
 *
 * @see NodeExecutor
 * @see VariableResolver
 */
public abstract class AbstractNodeExecutor implements NodeExecutor {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final VariableResolver variableResolver;

    protected AbstractNodeExecutor(VariableResolver variableResolver) {
        this.variableResolver = variableResolver;
    }

    /**
     * Execute the node with config variable resolution.
     * <p>
     * This method pre-processes the node config by resolving all variables
     * before delegating to {@link #doExecute(Node, ExecutionContext, java.util.Map)}.
     *
     * @param node    the node configuration
     * @param context the execution context
     * @return the execution result
     */
    @Override
    public NodeResult execute(Node node, ExecutionContext context) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("ExecutionContext cannot be null");
        }

        logger.debug("Executing node: id={}, type={}, name={}",
                node.getId(), node.getType(), node.getName());

        // Resolve variables in config
        java.util.Map<String, Object> resolvedConfig = resolveConfig(node, context);

        // Delegate to subclass implementation
        return doExecute(node, context, resolvedConfig);
    }

    /**
     * Perform the actual node execution with resolved config.
     * <p>
     * Subclasses must implement this method to provide node-specific logic.
     *
     * @param node           the original node configuration
     * @param context        the execution context
     * @param resolvedConfig the config with all variables resolved
     * @return the execution result
     */
    protected abstract NodeResult doExecute(Node node,
                                            ExecutionContext context,
                                            java.util.Map<String, Object> resolvedConfig);

    /**
     * Resolve all variables in the node config.
     * <p>
     * Uses {@link VariableResolver#resolveMap(java.util.Map, ExecutionContext)}
     * to recursively resolve all {{expression}} patterns.
     *
     * @param node    the node configuration
     * @param context the execution context
     * @return a new map with all variables resolved
     */
    protected java.util.Map<String, Object> resolveConfig(Node node, ExecutionContext context) {
        java.util.Map<String, Object> config = node.getConfig();
        if (config == null || config.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return variableResolver.resolveMap(config, context);
    }

    /**
     * Get a string value from config with default.
     *
     * @param config         the config map
     * @param key            the config key
     * @param defaultValue   the default value if key not found
     * @return the string value or default
     */
    protected String getConfigString(java.util.Map<String, Object> config,
                                     String key,
                                     String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Get an integer value from config with default.
     *
     * @param config         the config map
     * @param key            the config key
     * @param defaultValue   the default value if key not found or invalid
     * @return the integer value or default
     */
    protected int getConfigInt(java.util.Map<String, Object> config,
                               String key,
                               int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer value for config key '{}': {}", key, value);
            }
        }
        return defaultValue;
    }

    /**
     * Get a boolean value from config with default.
     *
     * @param config         the config map
     * @param key            the config key
     * @param defaultValue   the default value if key not found
     * @return the boolean value or default
     */
    protected boolean getConfigBoolean(java.util.Map<String, Object> config,
                                       String key,
                                       boolean defaultValue) {
        Object value = config.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return defaultValue;
    }
}

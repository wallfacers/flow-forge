package com.workflow.node.log;

import com.workflow.context.VariableResolver;
import com.workflow.model.ExecutionContext;
import com.workflow.model.Node;
import com.workflow.model.NodeResult;
import com.workflow.model.NodeType;
import com.workflow.node.AbstractNodeExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Log node executor.
 * <p>
 * Outputs log messages at various levels (INFO, WARN, ERROR, DEBUG).
 * Supports variable resolution in the message.
 * <p>
 * Config format:
 * <pre>
 * {
 *   "level": "INFO",
 *   "message": "Processing user: {{input.userId}}, status: {{node1.output.status}}"
 * }
 * </pre>
 * <p>
 * The log node always returns success with an empty output map.
 *
 * @see AbstractNodeExecutor
 */
@Component
public class LogNodeExecutor extends AbstractNodeExecutor {

    private static final String DEFAULT_LEVEL = "INFO";
    private static final String DEFAULT_MESSAGE = "";

    public LogNodeExecutor(VariableResolver variableResolver) {
        super(variableResolver);
    }

    @Override
    public NodeType getSupportedType() {
        return NodeType.LOG;
    }

    @Override
    protected NodeResult doExecute(Node node,
                                   ExecutionContext context,
                                   Map<String, Object> resolvedConfig) {
        String nodeId = node.getId();

        // Extract config values
        String level = getConfigString(resolvedConfig, "level", DEFAULT_LEVEL).toUpperCase();
        String message = getConfigString(resolvedConfig, "message", DEFAULT_MESSAGE);

        // Log the message at the specified level
        switch (level) {
            case "DEBUG" -> logger.debug("[Node: {}] {}", nodeId, message);
            case "WARN", "WARNING" -> logger.warn("[Node: {}] {}", nodeId, message);
            case "ERROR" -> logger.error("[Node: {}] {}", nodeId, message);
            case "INFO", "DEFAULT" -> logger.info("[Node: {}] {}", nodeId, message);
            default -> {
                logger.debug("[Node: {}] Unknown log level '{}', using INFO: {}",
                        nodeId, level, message);
                logger.info("[Node: {}] {}", nodeId, message);
            }
        }

        // Log node always succeeds with empty output
        return NodeResult.success(nodeId, Map.of());
    }

    /**
     * Override resolveConfig to handle message as a string that needs variable resolution.
     * <p>
     * The message may contain {{}} expressions that need to be resolved.
     *
     * @param node    the node configuration
     * @param context the execution context
     * @return resolved config map
     */
    @Override
    protected Map<String, Object> resolveConfig(Node node, ExecutionContext context) {
        Map<String, Object> resolved = super.resolveConfig(node, context);

        // Resolve message string separately
        Object messageObj = resolved.get("message");
        if (messageObj instanceof String messageStr) {
            String resolvedMessage = variableResolver.resolve(messageStr, context);
            resolved.put("message", resolvedMessage);
        }

        return resolved;
    }
}

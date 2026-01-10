package com.workflow.node.condition;

import com.workflow.context.VariableResolver;
import com.workflow.model.ExecutionContext;
import com.workflow.model.Node;
import com.workflow.model.NodeResult;
import com.workflow.model.NodeType;
import com.workflow.model.WorkflowException;
import com.workflow.node.AbstractNodeExecutor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * IF node executor for conditional branching.
 * <p>
 * The IF node evaluates a condition expression and outputs the result.
 * The actual branching logic is handled by the Edge condition evaluation
 * in the scheduler - this node simply evaluates and outputs the condition result.
 * <p>
 * Config format:
 * <pre>
 * {
 *   "condition": "{{node1.output.status}} == 200",
 *   "trueValue": "success",
 *   "falseValue": "failure"
 * }
 * </pre>
 * <p>
 * Output format:
 * <pre>
 * {
 *   "result": true,
 *   "condition": "{{node1.output.status}} == 200",
 *   "trueValue": "success",
 *   "falseValue": "failure",
 *   "selected": "success"
 * }
 * </pre>
 * <p>
 * Note: The actual flow branching is determined by Edge conditions in the
 * WorkflowDefinition. Each outgoing edge from an IF node should have its
 * own condition expression. This node serves as a logical marker and can
 * optionally output the evaluation result for downstream nodes to use.
 */
@Component
public class IfNodeExecutor extends AbstractNodeExecutor {

    private final SpelEvaluator spelEvaluator;

    public IfNodeExecutor(VariableResolver variableResolver, SpelEvaluator spelEvaluator) {
        super(variableResolver);
        this.spelEvaluator = spelEvaluator;
    }

    @Override
    public NodeType getSupportedType() {
        return NodeType.IF;
    }

    @Override
    protected NodeResult doExecute(Node node,
                                   ExecutionContext context,
                                   Map<String, Object> resolvedConfig) {
        String nodeId = node.getId();

        // Extract condition from config
        String condition = getConfigString(resolvedConfig, "condition", null);

        // If no condition specified, default to true
        if (condition == null || condition.isEmpty()) {
            logger.debug("No condition specified for IF node {}, defaulting to true", nodeId);
            return NodeResult.success(nodeId, buildOutput(true, null, null, condition));
        }

        // Evaluate the condition
        Boolean result;
        try {
            result = spelEvaluator.evaluate(condition, context);
        } catch (WorkflowException e) {
            // Check if it's a security violation (should propagate)
            String message = e.getMessage();
            if (message != null && (message.contains("security") ||
                message.contains("not allowed") ||
                message.contains("unsafe") ||
                message.contains("dangerous"))) {
                throw e;  // Security violations propagate
            }
            // Other WorkflowExceptions (parse errors) return failure result
            logger.error("Failed to evaluate condition in IF node {}: {}", nodeId, condition, e);
            return NodeResult.failure(nodeId, "Condition evaluation failed: " + e.getMessage());
        } catch (Exception e) {
            // Other errors (runtime errors) return failure result
            logger.error("Failed to evaluate condition in IF node {}: {}", nodeId, condition, e);
            return NodeResult.failure(nodeId, "Condition evaluation failed: " + e.getMessage());
        }

        // Get optional true/false values
        // If specified, use the provided value
        // If not specified, null (will be defaulted in buildOutput for selected value)
        String trueValue = resolvedConfig.containsKey("trueValue")
                ? getConfigString(resolvedConfig, "trueValue", null)
                : null;
        String falseValue = resolvedConfig.containsKey("falseValue")
                ? getConfigString(resolvedConfig, "falseValue", null)
                : null;

        // Build output
        Map<String, Object> output = buildOutput(result, trueValue, falseValue, condition);

        logger.debug("IF node {} evaluated condition '{}' to: {}", nodeId, condition, result);
        return NodeResult.success(nodeId, output);
    }

    /**
     * Build the output map for the IF node.
     *
     * @param result     the condition evaluation result
     * @param trueValue  the value to output when condition is true
     * @param falseValue the value to output when condition is false
     * @param condition  the original condition expression (null if no condition)
     * @return the output map
     */
    private Map<String, Object> buildOutput(Boolean result,
                                             String trueValue,
                                             String falseValue,
                                             String condition) {
        Map<String, Object> output = new HashMap<>();
        output.put("result", result);

        // Only include condition if it was specified
        if (condition != null) {
            output.put("condition", condition);
        }

        if (trueValue != null) {
            output.put("trueValue", trueValue);
        }
        if (falseValue != null) {
            output.put("falseValue", falseValue);
        }

        // Add the selected value based on result (only if condition was specified)
        // Use default values if the specific value wasn't provided
        if (condition != null) {
            String selectedValue = result ?
                    (trueValue != null ? trueValue : "true") :
                    (falseValue != null ? falseValue : "false");
            output.put("selected", selectedValue);
        }

        return output;
    }
}

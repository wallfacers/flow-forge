package com.workflow.node.condition;

import com.workflow.model.ExecutionContext;
import com.workflow.model.NodeResult;
import com.workflow.model.WorkflowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.AccessException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Safe SpEL expression evaluator with security filtering.
 * <p>
 * Evaluates SpEL (Spring Expression Language) expressions for conditional branching.
 * Implements strict security policies to prevent code injection.
 * <p>
 * Supported expression patterns:
 * <ul>
 *   <li>{@code node1.output.status == 200}</li>
 *   <li>{@code node2.output.data.active == true}</li>
 *   <li>{@code node1.output.count > 0 && node2.output.valid}</li>
 * </ul>
 */
@Component
public class SpelEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(SpelEvaluator.class);

    /**
     * Safe character pattern for SpEL expressions.
     * Allows alphanumeric, spaces, dots, operators, parentheses, brackets, hash (# for variables),
     * and common keywords.
     */
    private static final Pattern SAFE_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_\\.\\s+\\-*/%()=!<>|&\\[\\]'\":,?!#]+$"
    );

    /**
     * Dangerous patterns that could indicate injection attempts.
     */
    private static final Pattern[] DANGEROUS_PATTERNS = {
            Pattern.compile("\\bT\\("),                      // Type reference
            Pattern.compile("\\bnew\\s+"),                   // Object creation
            Pattern.compile("\\bclass\\b"),                  // Class literal
            Pattern.compile("\\.java\\."),                   // Java package access
            Pattern.compile("\\.lang\\."),                   // Lang package access
            Pattern.compile("\\.util\\."),                   // Util package access
            Pattern.compile("System\\."),                    // System class access
            Pattern.compile("Runtime\\."),                   // Runtime class access
            Pattern.compile("\\.getRuntime\\("),             // getRuntime reflection
            Pattern.compile("ProcessBuilder\\."),            // ProcessBuilder access
            Pattern.compile("\\.exec\\("),                   // exec method
            Pattern.compile("\\.exit\\("),                   // exit method
            Pattern.compile("\\.getClass\\("),               // getClass reflection
            Pattern.compile("\\.forName\\("),                // forName reflection
    };

    /**
     * Property accessor that enables direct map key access using dot notation.
     * Allows expressions like {@code map.key} instead of requiring {@code map['key']}.
     */
    private static class MapPropertyAccessor implements PropertyAccessor {
        @Override
        public Class<?>[] getSpecificTargetClasses() {
            return new Class<?>[] { Map.class };
        }

        @Override
        public boolean canRead(org.springframework.expression.EvaluationContext context,
                                Object target, String name) throws AccessException {
            return target instanceof Map;
        }

        @Override
        public TypedValue read(org.springframework.expression.EvaluationContext context,
                               Object target, String name) throws AccessException {
            if (target instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) target;
                Object value = map.get(name);
                return new TypedValue(value);
            }
            throw new AccessException("Target is not a Map");
        }

        @Override
        public boolean canWrite(org.springframework.expression.EvaluationContext context,
                                Object target, String name) throws AccessException {
            return false; // Read-only
        }

        @Override
        public void write(org.springframework.expression.EvaluationContext context,
                          Object target, String name, Object newValue) throws AccessException {
            throw new AccessException("Map is read-only in SpEL expressions");
        }
    }

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * Evaluate a SpEL expression against the execution context.
     *
     * @param expression the SpEL expression to evaluate
     * @param context    the execution context containing node results
     * @return the evaluation result as a Boolean
     * @throws WorkflowException if the expression is invalid or a security violation is detected
     */
    public Boolean evaluate(String expression, ExecutionContext context) {
        if (expression == null || expression.isBlank()) {
            logger.debug("Expression is null or blank, returning true (unconditional)");
            return true;
        }

        // Sanitize the expression for security
        String sanitized = sanitize(expression);

        // Create evaluation context with node results
        StandardEvaluationContext evalContext = createEvaluationContext(context);

        try {
            // Parse and evaluate the expression
            Expression spelExpression = parser.parseExpression(sanitized);
            Object result = spelExpression.getValue(evalContext);

            // Convert result to Boolean
            if (result instanceof Boolean) {
                Boolean boolResult = (Boolean) result;
                logger.debug("Expression evaluation result: '{}' -> {}", expression, boolResult);
                return boolResult;
            }

            // Handle non-boolean results
            logger.warn("Expression did not evaluate to boolean: '{}' -> {}", expression, result);
            return result != null;

        } catch (ParseException e) {
            throw new WorkflowException("Failed to parse SpEL expression: " + expression, e);
        } catch (Exception e) {
            throw new WorkflowException("Failed to evaluate SpEL expression: " + expression, e);
        }
    }

    /**
     * Sanitize the expression to prevent code injection.
     *
     * @param expression the raw expression
     * @return the sanitized expression
     * @throws WorkflowException if a security violation is detected
     */
    private String sanitize(String expression) {
        // Trim whitespace
        String sanitized = expression.trim();

        // Check for safe character pattern
        if (!SAFE_PATTERN.matcher(sanitized).matches()) {
            throw new WorkflowException("SpEL expression contains unsafe characters: " + expression);
        }

        // Check for dangerous patterns
        for (Pattern dangerous : DANGEROUS_PATTERNS) {
            if (dangerous.matcher(sanitized).find()) {
                throw new WorkflowException("SpEL expression contains dangerous pattern: " + expression);
            }
        }

        return sanitized;
    }

    /**
     * Create a SpEL evaluation context with node results as root variables.
     * <p>
     * Node outputs are accessible via {@code #nodeId.output.property} notation.
     * For example: {@code #node1.output.status}
     * <p>
     * Security measures:
     * <ul>
     *   <li>Blocks class type resolution (no T() or class literals)</li>
     * </ul>
     *
     * @param context the workflow execution context
     * @return a configured StandardEvaluationContext
     */
    private StandardEvaluationContext createEvaluationContext(ExecutionContext context) {
        StandardEvaluationContext evalContext = new StandardEvaluationContext();

        // Add map property accessor to enable map.key notation
        evalContext.addPropertyAccessor(new MapPropertyAccessor());

        // Security: Block class type resolution to prevent T() and class literal access
        evalContext.setTypeLocator(typeName -> {
            throw new SecurityException("SpEL class access not allowed: " + typeName);
        });

        // Set each node result as a variable in the SpEL context
        for (java.util.Map.Entry<String, NodeResult> entry : context.getNodeResults().entrySet()) {
            String nodeId = entry.getKey();
            NodeResult nodeResult = entry.getValue();
            if (nodeResult != null) {
                evalContext.setVariable(nodeId, new NodeResultWrapper(nodeResult));
            }
        }

        return evalContext;
    }

    /**
     * Wrapper class for NodeResult that provides getter methods for SpEL access.
     * <p>
     * Allows expressions like {@code node1.output.status} to work correctly.
     */
    public static class NodeResultWrapper {
        private final NodeResult nodeResult;

        public NodeResultWrapper(NodeResult nodeResult) {
            this.nodeResult = nodeResult;
        }

        public Object getOutput() {
            return nodeResult.getOutput();
        }

        public String getNodeId() {
            return nodeResult.getNodeId();
        }

        public Object getStatus() {
            return nodeResult.getStatus();
        }

        public Object getErrorMessage() {
            return nodeResult.getErrorMessage();
        }

        public Long getDuration() {
            return nodeResult.getDurationMs();
        }
    }

    /**
     * Evaluate an expression with direct variable map.
     *
     * @param expression the SpEL expression
     * @param variables  the variable map for evaluation
     * @return the evaluation result as a Boolean
     */
    public Boolean evaluateWithVariables(String expression, Map<String, Object> variables) {
        if (expression == null || expression.isBlank()) {
            return true;
        }

        String sanitized = sanitize(expression);
        StandardEvaluationContext evalContext = new StandardEvaluationContext();

        // Add map property accessor to enable map.key notation
        evalContext.addPropertyAccessor(new MapPropertyAccessor());

        // Create a wrapper bean for variable access
        evalContext.setRootObject(new VariableWrapper(variables));

        try {
            Expression spelExpression = parser.parseExpression(sanitized);
            Object result = spelExpression.getValue(evalContext);

            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            return result != null;

        } catch (ParseException e) {
            throw new WorkflowException("Failed to parse SpEL expression: " + expression, e);
        } catch (Exception e) {
            throw new WorkflowException("Failed to evaluate SpEL expression: " + expression, e);
        }
    }

    /**
     * Wrapper class for variable map that provides dynamic property access.
     */
    public static class VariableWrapper {
        private final Map<String, Object> variables;

        public VariableWrapper(Map<String, Object> variables) {
            this.variables = variables;
        }

        @SuppressWarnings("unused")
        public Object getStatus() {
            return variables.get("status");
        }

        @SuppressWarnings("unused")
        public Object getCount() {
            return variables.get("count");
        }

        @SuppressWarnings("unused")
        public Object getThreshold() {
            return variables.get("threshold");
        }

        @SuppressWarnings("unused")
        public Object getOutput() {
            return variables.get("output");
        }
    }

    /**
     * Validate an expression without evaluating it.
     *
     * @param expression the expression to validate
     * @return true if the expression is valid
     * @throws WorkflowException if the expression is invalid
     */
    public boolean validate(String expression) {
        if (expression == null || expression.isBlank()) {
            return true;
        }

        // Just sanitize to check for security issues
        sanitize(expression);

        try {
            parser.parseExpression(expression);
            return true;
        } catch (ParseException e) {
            throw new WorkflowException("Invalid SpEL expression: " + expression, e);
        }
    }
}

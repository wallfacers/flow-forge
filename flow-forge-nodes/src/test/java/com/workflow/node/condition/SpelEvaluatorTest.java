package com.workflow.node.condition;

import com.workflow.model.ExecutionContext;
import com.workflow.model.NodeResult;
import com.workflow.model.WorkflowException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SpelEvaluator}.
 */
class SpelEvaluatorTest {

    private SpelEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SpelEvaluator();
    }

    // ===== Basic Expression Evaluation =====

    @Test
    void evaluate_withNullExpression_shouldReturnTrue() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate(null, context));
    }

    @Test
    void evaluate_withBlankExpression_shouldReturnTrue() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("   ", context));
    }

    @Test
    void evaluate_withSimpleTrueCondition_shouldReturnTrue() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("true", context));
    }

    @Test
    void evaluate_withSimpleFalseCondition_shouldReturnFalse() {
        ExecutionContext context = createContext();
        assertFalse(evaluator.evaluate("false", context));
    }

    // ===== Comparison Operators =====

    @Test
    void evaluate_withEqualsOperator_shouldReturnCorrectResult() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("1 == 1", context));
        assertFalse(evaluator.evaluate("1 == 2", context));
    }

    @Test
    void evaluate_withNotEqualsOperator_shouldReturnCorrectResult() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("1 != 2", context));
        assertFalse(evaluator.evaluate("1 != 1", context));
    }

    @Test
    void evaluate_withGreaterThanOperator_shouldReturnCorrectResult() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("5 > 3", context));
        assertFalse(evaluator.evaluate("3 > 5", context));
    }

    @Test
    void evaluate_withLessThanOperator_shouldReturnCorrectResult() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("3 < 5", context));
        assertFalse(evaluator.evaluate("5 < 3", context));
    }

    @Test
    void evaluate_withGreaterThanOrEqualOperator_shouldReturnCorrectResult() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("5 >= 5", context));
        assertTrue(evaluator.evaluate("5 >= 3", context));
        assertFalse(evaluator.evaluate("3 >= 5", context));
    }

    @Test
    void evaluate_withLessThanOrEqualOperator_shouldReturnCorrectResult() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("3 <= 3", context));
        assertTrue(evaluator.evaluate("3 <= 5", context));
        assertFalse(evaluator.evaluate("5 <= 3", context));
    }

    // ===== Logical Operators =====

    @Test
    void evaluate_withAndOperator_shouldReturnCorrectResult() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("true and true", context));
        assertFalse(evaluator.evaluate("true and false", context));
        assertFalse(evaluator.evaluate("false and true", context));
        assertFalse(evaluator.evaluate("false and false", context));
    }

    @Test
    void evaluate_withOrOperator_shouldReturnCorrectResult() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("true or true", context));
        assertTrue(evaluator.evaluate("true or false", context));
        assertTrue(evaluator.evaluate("false or true", context));
        assertFalse(evaluator.evaluate("false or false", context));
    }

    @Test
    void evaluate_withNotOperator_shouldReturnCorrectResult() {
        ExecutionContext context = createContext();
        assertFalse(evaluator.evaluate("not true", context));
        assertTrue(evaluator.evaluate("not false", context));
    }

    // ===== Complex Expressions =====

    @Test
    void evaluate_withComplexExpression_shouldReturnCorrectResult() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("(5 > 3) and (10 < 20)", context));
        assertFalse(evaluator.evaluate("(5 > 3) and (10 > 20)", context));
    }

    @Test
    void evaluate_withArithmeticOperators_shouldReturnCorrectResult() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("2 + 3 == 5", context));
        assertTrue(evaluator.evaluate("10 - 4 == 6", context));
        assertTrue(evaluator.evaluate("3 * 4 == 12", context));
        assertTrue(evaluator.evaluate("20 / 4 == 5", context));
        assertTrue(evaluator.evaluate("10 % 3 == 1", context));
    }

    // ===== Security Tests =====

    @Test
    void evaluate_withTypeReference_shouldThrowException() {
        ExecutionContext context = createContext();
        assertThrows(WorkflowException.class, () ->
                evaluator.evaluate("T(java.lang.Runtime)", context));
    }

    @Test
    void evaluate_withNewKeyword_shouldThrowException() {
        ExecutionContext context = createContext();
        assertThrows(WorkflowException.class, () ->
                evaluator.evaluate("new java.lang.String('malicious')", context));
    }

    @Test
    void evaluate_withClassLiteral_shouldThrowException() {
        ExecutionContext context = createContext();
        assertThrows(WorkflowException.class, () ->
                evaluator.evaluate("String.class", context));
    }

    @Test
    void evaluate_withSystemAccess_shouldThrowException() {
        ExecutionContext context = createContext();
        assertThrows(WorkflowException.class, () ->
                evaluator.evaluate("System.exit(0)", context));
    }

    @Test
    void evaluate_withExecMethod_shouldThrowException() {
        ExecutionContext context = createContext();
        assertThrows(WorkflowException.class, () ->
                evaluator.evaluate("Runtime.getRuntime().exec('ls')", context));
    }

    @Test
    void evaluate_withForNameReflection_shouldThrowException() {
        ExecutionContext context = createContext();
        assertThrows(WorkflowException.class, () ->
                evaluator.evaluate("Class.forName('java.lang.Runtime')", context));
    }

    @Test
    void evaluate_withUnsafeCharacters_shouldThrowException() {
        ExecutionContext context = createContext();
        assertThrows(WorkflowException.class, () ->
                evaluator.evaluate("result; drop table users", context));
    }

    // ===== String Operations =====

    @Test
    void evaluate_withStringEquality_shouldReturnCorrectResult() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("'hello' == 'hello'", context));
        assertFalse(evaluator.evaluate("'hello' == 'world'", context));
    }

    @Test
    void evaluate_withStringComparison_shouldReturnCorrectResult() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("'apple' < 'banana'", context));
    }

    // ===== Null Handling =====

    @Test
    void evaluate_withNullKeyword_shouldReturnCorrectResult() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("null == null", context));
        assertFalse(evaluator.evaluate("null == 'value'", context));
    }

    // ===== evaluateWithVariables Tests =====

    @Test
    void evaluateWithVariables_withSimpleVariable_shouldReturnCorrectResult() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("status", 200);

        assertTrue(evaluator.evaluateWithVariables("status == 200", variables));
        assertFalse(evaluator.evaluateWithVariables("status == 404", variables));
    }

    @Test
    void evaluateWithVariables_withMultipleVariables_shouldReturnCorrectResult() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("count", 5);
        variables.put("threshold", 10);

        assertTrue(evaluator.evaluateWithVariables("count < threshold", variables));
        assertFalse(evaluator.evaluateWithVariables("count > threshold", variables));
    }

    @Test
    void evaluateWithVariables_withNestedProperty_shouldReturnCorrectResult() {
        Map<String, Object> variables = new HashMap<>();
        Map<String, Object> output = new HashMap<>();
        output.put("status", 200);
        variables.put("output", output);

        assertTrue(evaluator.evaluateWithVariables("output.status == 200", variables));
    }

    // ===== validate Tests =====

    @Test
    void validate_withValidExpression_shouldReturnTrue() {
        assertTrue(evaluator.validate("status == 200"));
    }

    @Test
    void validate_withNullExpression_shouldReturnTrue() {
        assertTrue(evaluator.validate(null));
    }

    @Test
    void validate_withBlankExpression_shouldReturnTrue() {
        assertTrue(evaluator.validate(""));
    }

    @Test
    void validate_withInvalidExpression_shouldThrowException() {
        assertThrows(WorkflowException.class, () ->
                evaluator.validate("status == "));
    }

    @Test
    void validate_withDangerousExpression_shouldThrowException() {
        assertThrows(WorkflowException.class, () ->
                evaluator.validate("T(java.lang.Runtime)"));
    }

    // ===== Edge Cases =====

    @Test
    void evaluate_withWhitespace_shouldHandleCorrectly() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("  5  >  3  ", context));
    }

    @Test
    void evaluate_withParentheses_shouldHandleCorrectly() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("((5 > 3) and (10 > 5)) or false", context));
    }

    @Test
    void evaluate_withModuloOperator_shouldReturnCorrectResult() {
        ExecutionContext context = createContext();
        assertTrue(evaluator.evaluate("10 % 3 == 1", context));
        assertTrue(evaluator.evaluate("10 % 5 == 0", context));
    }

    // ===== Helper Methods =====

    private ExecutionContext createContext() {
        ExecutionContext context = ExecutionContext.builder()
                .executionId("test-execution")
                .workflowId("test-workflow")
                .build();

        // Add some sample node results
        Map<String, Object> node1Output = new HashMap<>();
        node1Output.put("status", 200);
        node1Output.put("data", "success");

        NodeResult node1Result = NodeResult.builder()
                .nodeId("node1")
                .status(com.workflow.model.ExecutionStatus.SUCCESS)
                .output(node1Output)
                .build();

        context.getNodeResults().put("node1", node1Result);

        return context;
    }
}

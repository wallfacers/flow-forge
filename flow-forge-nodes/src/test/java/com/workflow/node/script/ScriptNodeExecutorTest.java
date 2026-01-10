package com.workflow.node.script;

import com.workflow.context.VariableResolver;
import com.workflow.model.ExecutionContext;
import com.workflow.model.ExecutionStatus;
import com.workflow.model.Node;
import com.workflow.model.NodeResult;
import com.workflow.model.NodeType;
import com.workflow.node.script.ScriptNodeExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Script节点执行器测试。
 * <p>
 * 测试脚本节点的各种执行场景：
 * <ul>
 *   <li>简单脚本执行</li>
 *   <li>变量绑定和访问</li>
 *   <li>错误处理</li>
 *   <li>超时控制</li>
 * </ul>
 * <p>
 * 注意：这些测试需要本地安装 GraalVM JDK 21+。
 * 通过系统属性 {@code graalvm.enabled=true} 启用测试。
 */
@DisplayName("Script节点执行器测试")
@EnabledIfSystemProperty(named = "graalvm.enabled", matches = "true")
class ScriptNodeExecutorTest {

    private ScriptNodeExecutor executor;
    private VariableResolver variableResolver;

    @BeforeEach
    void setUp() {
        variableResolver = new VariableResolver();
        executor = new ScriptNodeExecutor(variableResolver);
    }

    @AfterEach
    void tearDown() {
        ScriptNodeExecutor.cleanupAll();
    }

    @Test
    @DisplayName("应该返回SCRIPT类型")
    void shouldReturnScriptType() {
        assertEquals(NodeType.SCRIPT, executor.getSupportedType());
    }

    @Test
    @DisplayName("应该执行简单脚本")
    void shouldExecuteSimpleScript() {
        Node node = Node.builder()
                .id("script1")
                .type(NodeType.SCRIPT)
                .name("Test Script")
                .config(Map.of(
                        "language", "js",
                        "code", "return 42;"
                ))
                .timeout(5000)
                .build();

        ExecutionContext context = ExecutionContext.builder()
                .executionId("exec-1")
                .workflowId("workflow-1")
                .tenantId("tenant-1")
                .status(ExecutionStatus.RUNNING)
                .input(new HashMap<>())
                .build();

        NodeResult result = executor.execute(node, context);

        assertTrue(result.isSuccess(), "Script should execute successfully");
        assertNotNull(result.getOutput(), "Output should not be null");

        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        assertEquals(42, output.get("returnValue"));
    }

    @Test
    @DisplayName("应该支持变量绑定")
    void shouldSupportVariableBindings() {
        Node node = Node.builder()
                .id("script2")
                .type(NodeType.SCRIPT)
                .name("Script with Input")
                .config(Map.of(
                        "language", "js",
                        "code", "return __input.x + __input.y;"
                ))
                .timeout(5000)
                .build();

        Map<String, Object> input = new HashMap<>();
        input.put("x", 10);
        input.put("y", 32);

        ExecutionContext context = ExecutionContext.builder()
                .executionId("exec-1")
                .workflowId("workflow-1")
                .tenantId("tenant-1")
                .status(ExecutionStatus.RUNNING)
                .input(input)
                .build();

        NodeResult result = executor.execute(node, context);

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        assertEquals(42, output.get("returnValue"));
    }

    @Test
    @DisplayName("应该支持访问系统变量")
    void shouldSupportSystemVariables() {
        Node node = Node.builder()
                .id("script3")
                .type(NodeType.SCRIPT)
                .name("Script with System Vars")
                .config(Map.of(
                        "language", "js",
                        "code", "return __system.executionId;"
                ))
                .timeout(5000)
                .build();

        ExecutionContext context = ExecutionContext.builder()
                .executionId("exec-123")
                .workflowId("workflow-1")
                .tenantId("tenant-1")
                .status(ExecutionStatus.RUNNING)
                .input(new HashMap<>())
                .build();

        NodeResult result = executor.execute(node, context);

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        assertEquals("exec-123", output.get("returnValue"));
    }

    @Test
    @DisplayName("应该支持访问全局变量")
    void shouldSupportGlobalVariables() {
        Node node = Node.builder()
                .id("script4")
                .type(NodeType.SCRIPT)
                .name("Script with Global Vars")
                .config(Map.of(
                        "language", "js",
                        "code", "return __global.apiKey;"
                ))
                .timeout(5000)
                .build();

        Map<String, Object> globalVars = new HashMap<>();
        globalVars.put("apiKey", "secret-key-123");

        ExecutionContext context = ExecutionContext.builder()
                .executionId("exec-1")
                .workflowId("workflow-1")
                .tenantId("tenant-1")
                .status(ExecutionStatus.RUNNING)
                .input(new HashMap<>())
                .globalVariables(globalVars)
                .build();

        NodeResult result = executor.execute(node, context);

        assertTrue(result.isSuccess());
        Map<String, Object> output = result.getOutput();
        assertEquals("secret-key-123", output.get("returnValue"));
    }

    @Test
    @DisplayName("应该支持使用导出的log方法")
    void shouldSupportLogMethod() {
        Node node = Node.builder()
                .id("script5")
                .type(NodeType.SCRIPT)
                .name("Script with Log")
                .config(Map.of(
                        "language", "js",
                        "code", """
                                __host.log("Test message from script");
                                return "done";
                                """
                ))
                .timeout(5000)
                .build();

        ExecutionContext context = ExecutionContext.builder()
                .executionId("exec-1")
                .workflowId("workflow-1")
                .tenantId("tenant-1")
                .status(ExecutionStatus.RUNNING)
                .input(new HashMap<>())
                .build();

        NodeResult result = executor.execute(node, context);

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        assertEquals("done", output.get("returnValue"));
        assertTrue(((String) output.get("output")).contains("Test message from script"));
    }

    @Test
    @DisplayName("应该支持使用JSON工具")
    void shouldSupportJsonTools() {
        Node node = Node.builder()
                .id("script6")
                .type(NodeType.SCRIPT)
                .name("Script with JSON")
                .config(Map.of(
                        "language", "js",
                        "code", """
                                const encoded = __host.jsonStringify({name: 'test', value: 123});
                                return encoded;
                                """
                ))
                .timeout(5000)
                .build();

        ExecutionContext context = ExecutionContext.builder()
                .executionId("exec-1")
                .workflowId("workflow-1")
                .tenantId("tenant-1")
                .status(ExecutionStatus.RUNNING)
                .input(new HashMap<>())
                .build();

        NodeResult result = executor.execute(node, context);

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        assertNotNull(output.get("returnValue"));
    }

    @Test
    @DisplayName("应该支持使用Base64工具")
    void shouldSupportBase64Tools() {
        Node node = Node.builder()
                .id("script7")
                .type(NodeType.SCRIPT)
                .name("Script with Base64")
                .config(Map.of(
                        "language", "js",
                        "code", """
                                const encoded = __host.base64Encode('Hello World');
                                return encoded;
                                """
                ))
                .timeout(5000)
                .build();

        ExecutionContext context = ExecutionContext.builder()
                .executionId("exec-1")
                .workflowId("workflow-1")
                .tenantId("tenant-1")
                .status(ExecutionStatus.RUNNING)
                .input(new HashMap<>())
                .build();

        NodeResult result = executor.execute(node, context);

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        assertEquals("SGVsbG8gV29ybGQ=", output.get("returnValue"));
    }

    @Test
    @DisplayName("应该处理空代码")
    void shouldHandleEmptyCode() {
        Node node = Node.builder()
                .id("script8")
                .type(NodeType.SCRIPT)
                .name("Empty Script")
                .config(Map.of(
                        "language", "js",
                        "code", ""
                ))
                .timeout(5000)
                .build();

        ExecutionContext context = ExecutionContext.builder()
                .executionId("exec-1")
                .workflowId("workflow-1")
                .tenantId("tenant-1")
                .status(ExecutionStatus.RUNNING)
                .input(new HashMap<>())
                .build();

        NodeResult result = executor.execute(node, context);

        assertFalse(result.isSuccess(), "Empty code should fail");
        assertTrue(result.getErrorMessage().contains("required"));
    }

    @Test
    @DisplayName("应该处理语法错误")
    void shouldHandleSyntaxError() {
        Node node = Node.builder()
                .id("script9")
                .type(NodeType.SCRIPT)
                .name("Invalid Script")
                .config(Map.of(
                        "language", "js",
                        "code", "return x + ; // invalid syntax - missing operand"
                ))
                .timeout(5000)
                .build();

        ExecutionContext context = ExecutionContext.builder()
                .executionId("exec-1")
                .workflowId("workflow-1")
                .tenantId("tenant-1")
                .status(ExecutionStatus.RUNNING)
                .input(new HashMap<>())
                .build();

        NodeResult result = executor.execute(node, context);

        assertFalse(result.isSuccess(), "Syntax error should cause failure");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("应该处理运行时错误")
    void shouldHandleRuntimeError() {
        Node node = Node.builder()
                .id("script10")
                .type(NodeType.SCRIPT)
                .name("Runtime Error Script")
                .config(Map.of(
                        "language", "js",
                        "code", """
                                throw new Error('Custom error message');
                                """
                ))
                .timeout(5000)
                .build();

        ExecutionContext context = ExecutionContext.builder()
                .executionId("exec-1")
                .workflowId("workflow-1")
                .tenantId("tenant-1")
                .status(ExecutionStatus.RUNNING)
                .input(new HashMap<>())
                .build();

        NodeResult result = executor.execute(node, context);

        assertFalse(result.isSuccess(), "Runtime error should cause failure");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("应该拒绝不支持的语言")
    void shouldRejectUnsupportedLanguage() {
        Node node = Node.builder()
                .id("script11")
                .type(NodeType.SCRIPT)
                .name("Unsupported Language")
                .config(Map.of(
                        "language", "python",
                        "code", "print('hello')"
                ))
                .timeout(5000)
                .build();

        ExecutionContext context = ExecutionContext.builder()
                .executionId("exec-1")
                .workflowId("workflow-1")
                .tenantId("tenant-1")
                .status(ExecutionStatus.RUNNING)
                .input(new HashMap<>())
                .build();

        NodeResult result = executor.execute(node, context);

        assertFalse(result.isSuccess(), "Unsupported language should fail");
        assertTrue(result.getErrorMessage().contains("Unsupported"));
    }

    @Test
    @DisplayName("应该支持变量解析")
    void shouldSupportVariableResolution() {
        Node node = Node.builder()
                .id("script12")
                .type(NodeType.SCRIPT)
                .name("Script with Variables")
                .config(Map.of(
                        "language", "js",
                        "code", "{{input.scriptCode}}"  // 变量占位符
                ))
                .timeout(5000)
                .build();

        Map<String, Object> input = new HashMap<>();
        input.put("scriptCode", "return 100;");

        ExecutionContext context = ExecutionContext.builder()
                .executionId("exec-1")
                .workflowId("workflow-1")
                .tenantId("tenant-1")
                .status(ExecutionStatus.RUNNING)
                .input(input)
                .build();

        NodeResult result = executor.execute(node, context);

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        assertEquals(100, output.get("returnValue"));
    }

    @Test
    @DisplayName("应该支持复杂脚本")
    void shouldSupportComplexScript() {
        String complexScript = """
                // 计算斐波那契数列
                function fibonacci(n) {
                    if (n <= 1) return n;
                    return fibonacci(n - 1) + fibonacci(n - 2);
                }
                return fibonacci(10);
                """;

        Node node = Node.builder()
                .id("script13")
                .type(NodeType.SCRIPT)
                .name("Complex Script")
                .config(Map.of(
                        "language", "js",
                        "code", complexScript
                ))
                .timeout(5000)
                .build();

        ExecutionContext context = ExecutionContext.builder()
                .executionId("exec-1")
                .workflowId("workflow-1")
                .tenantId("tenant-1")
                .status(ExecutionStatus.RUNNING)
                .input(new HashMap<>())
                .build();

        NodeResult result = executor.execute(node, context);

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        assertEquals(55, output.get("returnValue")); // fibonacci(10) = 55
    }
}

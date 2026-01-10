package com.workflow.sandbox;

import com.workflow.model.WorkflowException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GraalVM沙箱安全测试。
 * <p>
 * 验证沙箱能够阻止恶意代码执行危险操作：
 * <ul>
 *   <li>文件系统访问</li>
 *   <li>线程创建</li>
 *   <li>进程创建</li>
 *   <li>系统属性访问</li>
 *   <li>网络访问</li>
 *   <li>反射访问</li>
 * </ul>
 * <p>
 * 注意：这些测试需要本地安装 GraalVM JDK 21+。
 * 通过系统属性 {@code graalvm.enabled=true} 启用测试。
 */
@DisplayName("GraalVM沙箱安全测试")
@EnabledIfSystemProperty(named = "graalvm.enabled", matches = "true")
class GraalSandboxSecurityTest {

    private static final Logger logger = LoggerFactory.getLogger(GraalSandboxSecurityTest.class);

    private GraalSandbox sandbox;

    @BeforeEach
    void setUp() {
        sandbox = new GraalSandbox();
    }

    @AfterEach
    void tearDown() {
        if (sandbox != null) {
            sandbox.close();
        }
        GraalSandbox.clearCache();
    }

    @Test
    @DisplayName("应该允许执行简单脚本")
    void shouldAllowSimpleScript() {
        GraalSandbox.SandboxResult result = sandbox.execute("return 42;", null);

        assertTrue(result.success(), "Script should execute successfully");
        assertEquals(42, result.returnValue(), "Return value should be 42");
    }

    @Test
    @DisplayName("应该允许使用导出的安全方法")
    void shouldAllowExportedMethods() {
        String code = """
                __host.log("Test message");
                __host.sleep(10);
                return "ok";
                """;

        GraalSandbox.SandboxResult result = sandbox.execute(code, null);

        assertTrue(result.success(), "Script with exported methods should execute");
        assertEquals("ok", result.returnValue());
        assertTrue(result.output().contains("[LOG] Test message"),
                "Output should contain log message");
    }

    @Test
    @DisplayName("应该允许使用JSON工具")
    void shouldAllowJsonTools() {
        String code = """
                const str = __host.jsonStringify({test: 123});
                return str;
                """;

        GraalSandbox.SandboxResult result = sandbox.execute(code, null);

        assertTrue(result.success(), "JSON stringify should work");
    }

    @Test
    @DisplayName("应该允许使用Base64工具")
    void shouldAllowBase64Tools() {
        String code = """
                const encoded = __host.base64Encode("Hello");
                return encoded;
                """;

        GraalSandbox.SandboxResult result = sandbox.execute(code, null);

        assertTrue(result.success(), "Base64 encode should work");
        assertEquals("SGVsbG8=", result.returnValue());
    }

    @Test
    @DisplayName("应该允许访问绑定变量")
    void shouldAllowBindingsAccess() {
        String code = "return __input.x + __input.y;";
        Map<String, Object> bindings = Map.of("x", 10, "y", 32);

        GraalSandbox.SandboxResult result = sandbox.execute(code, bindings);

        assertTrue(result.success(), "Should access bindings");
        assertEquals(42, result.returnValue());
    }

    @Test
    @DisplayName("应该拒绝文件系统写入")
    void shouldRejectFileSystemWrite() {
        // 尝试使用Java的File API（应该被阻止）
        String code = """
                try {
                    Java.type('java.io.FileWriter');
                    return 'should fail';
                } catch (e) {
                    return 'blocked';
                }
                """;

        GraalSandbox.SandboxResult result = sandbox.execute(code, null);

        // 由于 allowHostClassLookup(false)，Java.type 不可用
        assertTrue(result.success(), "Script should execute without throwing exception");
        assertEquals("blocked", result.returnValue(),
                "Java.type should be blocked");
    }

    @Test
    @DisplayName("应该拒绝线程创建")
    void shouldRejectThreadCreation() {
        String code = """
                try {
                    new Thread(() => {}).start();
                    return 'should fail';
                } catch (e) {
                    return 'blocked: ' + e.message;
                }
                """;

        GraalSandbox.SandboxResult result = sandbox.execute(code, null);

        assertTrue(result.success(), "Script should complete");
        // Thread构造可能被阻止或执行被阻止
        String returnValue = String.valueOf(result.returnValue());
        assertTrue(returnValue.contains("blocked") || returnValue.contains("Thread"),
                "Thread creation should be blocked or restricted");
    }

    @Test
    @DisplayName("应该拒绝进程创建")
    void shouldRejectProcessCreation() {
        String code = """
                try {
                    Java.type('java.lang.ProcessBuilder');
                    return 'should fail';
                } catch (e) {
                    return 'blocked';
                }
                """;

        GraalSandbox.SandboxResult result = sandbox.execute(code, null);

        assertTrue(result.success(), "Script should execute");
        assertEquals("blocked", result.returnValue(),
                "ProcessBuilder access should be blocked");
    }

    @Test
    @DisplayName("应该拒绝System.exit调用")
    void shouldRejectSystemExit() {
        String code = """
                try {
                    Java.type('java.lang.System').exit(0);
                    return 'should fail';
                } catch (e) {
                    return 'blocked';
                }
                """;

        GraalSandbox.SandboxResult result = sandbox.execute(code, null);

        assertTrue(result.success(), "Script should execute without exiting JVM");
        assertEquals("blocked", result.returnValue(),
                "System.exit should be blocked");
    }

    @Test
    @DisplayName("应该拒绝系统属性访问")
    void shouldRejectSystemPropertyAccess() {
        String code = """
                try {
                    Java.type('java.lang.System').getProperty('user.home');
                    return 'should fail';
                } catch (e) {
                    return 'blocked';
                }
                """;

        GraalSandbox.SandboxResult result = sandbox.execute(code, null);

        assertTrue(result.success(), "Script should execute");
        assertEquals("blocked", result.returnValue(),
                "System.getProperty should be blocked");
    }

    @Test
    @DisplayName("应该拒绝网络访问")
    void shouldRejectNetworkAccess() {
        String code = """
                try {
                    Java.type('java.net.URL');
                    return 'should fail';
                } catch (e) {
                    return 'blocked';
                }
                """;

        GraalSandbox.SandboxResult result = sandbox.execute(code, null);

        assertTrue(result.success(), "Script should execute");
        assertEquals("blocked", result.returnValue(),
                "Network access should be blocked");
    }

    @Test
    @DisplayName("应该拒绝反射访问")
    void shouldRejectReflectionAccess() {
        String code = """
                try {
                    Java.type('java.lang.reflect.Method');
                    return 'should fail';
                } catch (e) {
                    return 'blocked';
                }
                """;

        GraalSandbox.SandboxResult result = sandbox.execute(code, null);

        assertTrue(result.success(), "Script should execute");
        assertEquals("blocked", result.returnValue(),
                "Reflection should be blocked");
    }

    @Test
    @DisplayName("应该拒绝Runtime.exec调用")
    void shouldRejectRuntimeExec() {
        String code = """
                try {
                    Java.type('java.lang.Runtime').getRuntime().exec('calc.exe');
                    return 'should fail';
                } catch (e) {
                    return 'blocked';
                }
                """;

        GraalSandbox.SandboxResult result = sandbox.execute(code, null);

        assertTrue(result.success(), "Script should execute without executing command");
        assertEquals("blocked", result.returnValue(),
                "Runtime.exec should be blocked");
    }

    @Test
    @DisplayName("应该拒绝文件系统读取")
    void shouldRejectFileSystemRead() {
        String code = """
                try {
                    Java.type('java.io.FileReader');
                    return 'should fail';
                } catch (e) {
                    return 'blocked';
                }
                """;

        GraalSandbox.SandboxResult result = sandbox.execute(code, null);

        assertTrue(result.success(), "Script should execute");
        assertEquals("blocked", result.returnValue(),
                "FileReader should be blocked");
    }

    @Test
    @DisplayName("应该拒绝ClassLoader访问")
    void shouldRejectClassLoaderAccess() {
        String code = """
                try {
                    Java.type('java.lang.ClassLoader');
                    return 'should fail';
                } catch (e) {
                    return 'blocked';
                }
                """;

        GraalSandbox.SandboxResult result = sandbox.execute(code, null);

        assertTrue(result.success(), "Script should execute");
        assertEquals("blocked", result.returnValue(),
                "ClassLoader should be blocked");
    }

    @Test
    @DisplayName("应该拒绝SecurityManager访问")
    void shouldRejectSecurityManagerAccess() {
        String code = """
                try {
                    Java.type('java.lang.SecurityManager');
                    return 'should fail';
                } catch (e) {
                    return 'blocked';
                }
                """;

        GraalSandbox.SandboxResult result = sandbox.execute(code, null);

        assertTrue(result.success(), "Script should execute");
        assertEquals("blocked", result.returnValue(),
                "SecurityManager should be blocked");
    }

    @Test
    @DisplayName("应该检测超时")
    void shouldDetectTimeout() {
        // 创建超时时间为100ms的沙箱
        GraalSandbox shortTimeoutSandbox = new GraalSandbox(100);
        try {
            // 执行一个会超时的无限循环
            String code = """
                    while (true) {
                        // 无限循环
                    }
                    """;

            assertThrows(WorkflowException.class, () -> {
                shortTimeoutSandbox.execute(code, null);
            }, "Should timeout on infinite loop");

        } finally {
            shortTimeoutSandbox.close();
        }
    }

    @Test
    @DisplayName("应该检测内存限制")
    void shouldDetectMemoryLimit() {
        // 创建一个会消耗大量内存的脚本
        String code = """
                const arr = [];
                for (let i = 0; i < 10000000; i++) {
                    arr.push(new Array(1000).fill('x'));
                }
                return arr.length;
                """;

        assertThrows(WorkflowException.class, () -> {
            sandbox.execute(code, null);
        }, "Should hit memory limit");
    }

    @Test
    @DisplayName("应该正确处理语法错误")
    void shouldHandleSyntaxError() {
        String code = "return x + ; // invalid syntax - missing operand";

        GraalSandbox.SandboxResult result = sandbox.execute(code, null);

        // 语法错误应该被捕获
        assertFalse(result.success(), "Syntax error should be caught");
        assertNotNull(result.output(), "Error message should be available");
    }

    @Test
    @DisplayName("应该正确处理运行时错误")
    void shouldHandleRuntimeError() {
        String code = """
                throw new Error('Test error');
                """;

        GraalSandbox.SandboxResult result = sandbox.execute(code, null);

        // 运行时错误应该被捕获
        assertFalse(result.success(), "Runtime error should be caught");
        assertTrue(result.output().contains("Test error") ||
                   result.output().contains("Error"),
                "Error message should be in output");
    }

    @Test
    @DisplayName("应该拒绝polyglot访问")
    void shouldRejectPolyglotAccess() {
        String code = """
                try {
                    Polyglot.import('java');
                    return 'should fail';
                } catch (e) {
                    return 'blocked';
                }
                """;

        GraalSandbox.SandboxResult result = sandbox.execute(code, null);

        assertTrue(result.success(), "Script should execute");
        // polyglot访问应该被阻止
        String returnValue = String.valueOf(result.returnValue());
        assertTrue(returnValue.contains("blocked") || returnValue.contains("not"),
                "Polyglot access should be blocked");
    }
}

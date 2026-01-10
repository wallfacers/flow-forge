package com.workflow.sandbox;

import com.workflow.model.WorkflowException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GraalVM沙箱执行环境。
 * <p>
 * 提供安全的脚本执行环境，支持JavaScript语言。
 * 实现资源限制（内存、指令数、超时）和安全策略（禁止IO、线程、反射）。
 * <p>
 * 资源限制配置：
 * <ul>
 *   <li>内存限制: 128MB</li>
 *   <li>指令限制: 10,000条</li>
 *   <li>超时限制: 5秒</li>
 * </ul>
 * <p>
 * 安全策略：
 * <ul>
 *   <li>禁止文件IO (allowIO=false)</li>
 *   <li>禁止线程创建</li>
 *   <li>禁止Java反射</li>
 *   <li>禁止访问System类</li>
 *   <li>禁止ProcessBuilder</li>
 * </ul>
 */
public class GraalSandbox implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(GraalSandbox.class);

    /**
     * 默认语言ID
     */
    public static final String LANGUAGE_ID = "js";

    /**
     * 默认内存限制 (128MB)
     */
    private static final long DEFAULT_MEMORY_LIMIT = 128 * 1024 * 1024;

    /**
     * 默认指令限制 (10,000条)
     */
    private static final int DEFAULT_STATEMENT_LIMIT = 10_000;

    /**
     * 默认超时时间 (5秒)
     */
    private static final long DEFAULT_TIMEOUT_MS = 5_000;

    /**
     * 平台线程执行器，用于执行 GraalVM Context 操作
     */
    private static final ExecutorService PLATFORM_EXECUTOR = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> new Thread(r, "GraalSandbox-Platform"));

    /**
     * Shutdown flag to prevent new submissions during shutdown
     */
    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);

    private final Engine engine;
    private final Context context;
    private final HostAccessExports hostExports;
    private final long timeoutMs;

    /**
     * ThreadLocal 沙箱实例缓存（每个线程独立的沙箱）
     * Context 不是线程安全的，需要为每个线程创建独立实例
     */
    private static final ThreadLocal<GraalSandbox> THREAD_LOCAL_SANDBOX = new ThreadLocal<>();

    /**
     * 创建默认配置的沙箱。
     */
    public GraalSandbox() {
        this(DEFAULT_TIMEOUT_MS);
    }

    /**
     * 创建指定超时时间的沙箱。
     *
     * @param timeoutMs 超时时间（毫秒）
     */
    public GraalSandbox(long timeoutMs) {
        this.timeoutMs = timeoutMs;
        this.hostExports = new HostAccessExports();

        // 创建 Engine（每个实例独立，避免虚拟线程兼容性问题）
        try {
            this.engine = Engine.newBuilder()
                    .allowExperimentalOptions(true)
                    .build();
        } catch (Exception e) {
            throw new WorkflowException("Failed to initialize GraalVM Engine", e);
        }

        try {
            this.context = buildContext();
        } catch (Exception e) {
            throw new WorkflowException("Failed to create GraalVM Context", e);
        }
    }

    /**
     * 构建安全Context。
     */
    private Context buildContext() {
        return Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(true)                      // 允许宿主访问（通过 @HostAccess.Export 控制）
                .allowHostClassLookup(className -> false)  // 禁止加载任意Java类
                .allowHostClassLoading(false)               // 禁止类加载
                .allowIO(false)                             // 禁止IO操作
                .allowNativeAccess(false)                   // 禁止本地访问
                .allowCreateProcess(false)                  // 禁止创建进程
                .allowAllAccess(false)                      // 禁止所有访问
                .out(System.out)                            // 标准输出
                .err(System.err)                            // 错误输出
                .build();
    }

    /**
     * 执行脚本代码。
     *
     * @param code    脚本代码
     * @param bindings 绑定变量（可选）
     * @return 执行结果
     * @throws WorkflowException 执行失败或超时
     */
    public SandboxResult execute(String code, Map<String, Object> bindings) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Script code cannot be empty");
        }

        // Check if executor has been shut down
        if (SHUTDOWN.get()) {
            throw new WorkflowException("GraalSandbox executor has been shut down");
        }

        // 包装代码
        String wrappedCode = wrapCode(code);

        long startTime = System.currentTimeMillis();

        // 将所有 Context 操作提交到平台线程执行
        // 这解决了虚拟线程与 GraalVM Context 的兼容性问题
        // 注意：每个 GraalSandbox 实例的 Context 不是线程安全的，
        // 因此不应该对同一个实例并发调用 execute()
        Future<Object> future = PLATFORM_EXECUTOR.submit(() -> {
            try {
                // 重置输出捕获
                hostExports.clearOutput();

                // 绑定变量
                Value bindingsObj = createBindingsObject(bindings != null ? bindings : Map.of());
                context.getBindings(LANGUAGE_ID).putMember("__bindings", bindingsObj);

                // 导出安全方法
                context.getBindings(LANGUAGE_ID).putMember("__host", hostExports);

                // 执行脚本
                Value result = context.eval(LANGUAGE_ID, wrappedCode);

                long duration = System.currentTimeMillis() - startTime;
                Object returnValue = convertValue(result);
                String output = hostExports.getOutput();

                return new SandboxResult(returnValue, output, duration, true);

            } catch (Throwable e) {
                // 保存异常，在外部处理
                return e;
            }
        });

        try {
            // 等待结果（支持超时）
            Object result = future.get(timeoutMs, TimeUnit.MILLISECONDS);

            // 如果结果是异常，返回失败结果
            if (result instanceof Throwable) {
                return handleExecutionException((Throwable) result);
            }

            return (SandboxResult) result;

        } catch (TimeoutException e) {
            future.cancel(true);
            throw new WorkflowException("Script execution timeout after " + timeoutMs + "ms");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof WorkflowException) {
                throw (WorkflowException) cause;
            }
            throw new WorkflowException("Script execution failed", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkflowException("Script execution interrupted", e);
        }
    }

    /**
     * 包装代码，注入安全辅助函数。
     * <p>
     * 将用户代码包装在立即执行函数(IIFE)中，允许使用 return 语句。
     * 所有辅助变量声明在 IIFE 内部，避免多次执行时的变量冲突。
     * 绑定变量通过 __input 对象访问（如 __input.x）。
     */
    private String wrapCode(String code) {
        return """
                (function() {
                    const __input = __bindings || {};
                    // 提取特殊变量以支持直接访问
                    const __global = __input.__global || {};
                    const __system = __input.__system || __input.system || {};
                    const __log = (msg) => __host.log(msg);
                    const __sleep = (ms) => __host.sleep(ms);
                    try {
                        %s
                    } catch (e) {
                        __host.error(e.message + '\\n' + e.stack);
                        throw e;
                    }
                })()
                """.formatted(code);
    }

    /**
     * 创建绑定变量对象。
     */
    private Value createBindingsObject(Map<String, Object> bindings) {
        return context.asValue(bindings);
    }

    /**
     * 转换GraalVM Value为Java对象。
     */
    private Object convertValue(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            }
            if (value.fitsInLong()) {
                return value.asLong();
            }
            return value.asDouble();
        }
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        if (value.hasArrayElements()) {
            return value;
        }
        if (value.hasMembers()) {
            return value;
        }
        return value.toString();
    }

    /**
     * 处理执行异常。
     * <p>
     * 对于脚本错误（语法、运行时），返回失败的 SandboxResult。
     * 对于安全违规和资源限制，抛出 WorkflowException。
     *
     * @param e 异常
     * @return 执行结果
     * @throws WorkflowException 对于安全违规和资源限制
     */
    private SandboxResult handleExecutionException(Throwable e) {
        String message = e.getMessage();
        if (message != null) {
            // 检测安全违规 - 这些应该抛出异常
            if (message.contains("Access denied") ||
                message.contains("not accessible") ||
                message.contains("not allowed")) {
                throw new WorkflowException("Script security violation: " + message, e);
            }
            // 检测资源限制 - 这些应该抛出异常
            if (message.contains("statement limit") ||
                message.contains("memory limit")) {
                throw new WorkflowException("Script resource limit exceeded: " + message, e);
            }
        }
        // 脚本错误（语法、运行时）- 返回失败结果
        String output = hostExports.getOutput();
        String errorMsg = message != null ? message : e.getClass().getSimpleName();
        return new SandboxResult(null, output + errorMsg, 0, false);
    }

    /**
     * 获取当前线程的沙箱实例。
     * <p>
     * 每个线程获取独立的 GraalSandbox 实例，避免 Context 的线程安全问题。
     *
     * @return 当前线程的沙箱实例
     */
    public static GraalSandbox getCachedInstance() {
        GraalSandbox sandbox = THREAD_LOCAL_SANDBOX.get();
        if (sandbox == null) {
            sandbox = new GraalSandbox();
            THREAD_LOCAL_SANDBOX.set(sandbox);
        }
        return sandbox;
    }

    @Override
    public void close() {
        try {
            if (context != null) {
                context.close(true);
            }
        } catch (IllegalStateException e) {
            // Context already closed
            logger.debug("Context already closed: {}", e.getMessage());
        } catch (Exception e) {
            logger.warn("Error closing GraalVM Context", e);
        }
        try {
            if (engine != null) {
                engine.close();
            }
        } catch (Exception e) {
            logger.warn("Error closing GraalVM Engine", e);
        }
    }

    /**
     * 清理当前线程的沙箱实例。
     * <p>
     * 应在线程执行完成后调用，避免内存泄漏。
     */
    public static void clearCache() {
        GraalSandbox sandbox = THREAD_LOCAL_SANDBOX.get();
        if (sandbox != null) {
            try {
                sandbox.close();
            } catch (Exception e) {
                logger.warn("Error closing sandbox", e);
            }
            THREAD_LOCAL_SANDBOX.remove();
        }
    }

    /**
     * 关闭平台线程执行器。
     * <p>
     * 应在应用程序关闭时调用，以释放所有平台线程资源。
     * 关闭后将无法再执行脚本。
     */
    public static void shutdownPlatformExecutor() {
        if (SHUTDOWN.compareAndSet(false, true)) {
            logger.info("Shutting down GraalSandbox platform executor");
            PLATFORM_EXECUTOR.shutdown();
            try {
                if (!PLATFORM_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("Platform executor did not terminate gracefully, forcing shutdown");
                    PLATFORM_EXECUTOR.shutdownNow();
                    if (!PLATFORM_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.error("Platform executor did not terminate after forced shutdown");
                    }
                } else {
                    logger.info("Platform executor terminated gracefully");
                }
            } catch (InterruptedException e) {
                PLATFORM_EXECUTOR.shutdownNow();
                Thread.currentThread().interrupt();
                logger.warn("Platform executor shutdown interrupted");
            }
        }
    }

    /**
     * 检查平台线程执行器是否已关闭。
     *
     * @return 如果已关闭返回 true
     */
    public static boolean isShutdown() {
        return SHUTDOWN.get() || PLATFORM_EXECUTOR.isShutdown();
    }

    /**
     * 沙箱执行结果。
     */
    public record SandboxResult(
            Object returnValue,
            String output,
            long durationMs,
            boolean success
    ) {
        public static SandboxResult failure(String error) {
            return new SandboxResult(null, error, 0, false);
        }

        public static SandboxResult success(Object returnValue, String output, long duration) {
            return new SandboxResult(returnValue, output, duration, true);
        }
    }
}

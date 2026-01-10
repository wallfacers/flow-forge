package com.workflow.node.script;

import com.workflow.context.VariableResolver;
import com.workflow.model.ExecutionContext;
import com.workflow.model.Node;
import com.workflow.model.NodeResult;
import com.workflow.model.NodeType;
import com.workflow.model.WorkflowException;
import com.workflow.node.AbstractNodeExecutor;
import com.workflow.sandbox.GraalSandbox;
import com.workflow.sandbox.GraalSandboxPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Script节点执行器。
 * <p>
 * 使用GraalVM沙箱安全执行JavaScript代码。
 * 支持多语言脚本执行，资源限制和安全策略。
 * <p>
 * Config格式:
 * <pre>
 * {
 *   "language": "js",           // 脚本语言，默认 "js"
 *   "code": "return x + y;",    // 脚本代码
 *   "timeout": 5000              // 超时时间（毫秒），默认 5000
 * }
 * </pre>
 * <p>
 * 输入变量通过 {@code input} 传递，输出作为 returnValue 返回。
 * <p>
 * 输出格式:
 * <pre>
 * {
 *   "returnValue": 42,          // 脚本返回值
 *   "output": "[LOG] Message"   // 捕获的日志输出
 * }
 * </pre>
 */
@Component
public class ScriptNodeExecutor extends AbstractNodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ScriptNodeExecutor.class);

    private static final String DEFAULT_LANGUAGE = "js";
    private static final String DEFAULT_CODE = "";
    private static final int DEFAULT_TIMEOUT = 5000;

    public ScriptNodeExecutor(VariableResolver variableResolver) {
        super(variableResolver);
    }

    @Override
    public NodeType getSupportedType() {
        return NodeType.SCRIPT;
    }

    @Override
    protected NodeResult doExecute(Node node,
                                   ExecutionContext context,
                                   Map<String, Object> resolvedConfig) {
        String nodeId = node.getId();

        // Extract config values
        String language = getConfigString(resolvedConfig, "language", DEFAULT_LANGUAGE);
        String code = getConfigString(resolvedConfig, "code", DEFAULT_CODE);
        int timeout = getConfigInt(resolvedConfig, "timeout", DEFAULT_TIMEOUT);

        // Validate required config
        if (code == null || code.trim().isEmpty()) {
            return NodeResult.failure(nodeId, "Script code is required for script node");
        }

        // Validate language
        if (!GraalSandbox.LANGUAGE_ID.equals(language)) {
            return NodeResult.failure(nodeId,
                    "Unsupported script language: " + language + ". Only 'js' is supported.");
        }

        logger.debug("Executing script node: id={}, language={}, codeLength={}",
                nodeId, language, code.length());

        try {
            // 准备绑定变量
            Map<String, Object> bindings = prepareBindings(node, context);

            // 从池中获取沙箱实例并执行（使用 try-with-resources 自动归还）
            try (GraalSandboxPool.SandboxLease lease = GraalSandboxPool.getInstance().acquire()) {
                GraalSandbox sandbox = lease.get();

                // 执行脚本
                GraalSandbox.SandboxResult result = sandbox.execute(code, bindings);

                if (!result.success()) {
                    logger.warn("Script execution failed: nodeId={}, error={}", nodeId, result.output());
                    return NodeResult.failure(nodeId, result.output());
                }

                // 构建输出
                Map<String, Object> output = new HashMap<>();
                output.put("returnValue", result.returnValue());
                output.put("output", result.output());
                output.put("duration", result.durationMs());

                logger.debug("Script execution completed: nodeId={}, duration={}ms",
                        nodeId, result.durationMs());
                return NodeResult.success(nodeId, output);
            }

        } catch (WorkflowException e) {
            logger.error("Script execution failed: nodeId={}", nodeId, e);
            return NodeResult.failure(nodeId, e.getMessage());
        } catch (Exception e) {
            logger.error("Script execution error: nodeId={}", nodeId, e);
            return NodeResult.failure(nodeId, "Script execution error: " + e.getMessage());
        }
    }

    /**
     * 准备脚本绑定变量。
     * <p>
     * 提供以下变量给脚本（通过 {@code __input.xxx} 访问）：
     * <ul>
     *   <li>{@code x, y, ...} - 节点输入参数（直接展开）</li>
     *   <li>{@code nodeId} - 当前节点ID</li>
     *   <li>{@code workflowId} - 工作流ID</li>
     *   <li>{@code executionId} - 执行ID</li>
     *   <li>{@code __global} - 全局变量（作为独立对象）</li>
     *   <li>{@code __system} - 系统变量（作为独立对象）</li>
     *   <li>{@code nodes} - 已完成节点的结果</li>
     * </ul>
     * <p>
     * 在脚本中通过 {@code __input.x} 访问输入变量 {@code x}，
     * 通过 {@code __input.__global.xxx} 或 {@code __global.xxx} 访问全局变量。
     *
     * @param node    当前节点
     * @param context 执行上下文
     * @return 绑定变量映射
     */
    private Map<String, Object> prepareBindings(Node node, ExecutionContext context) {
        Map<String, Object> bindings = new HashMap<>();

        // 输入参数（直接展开到顶层，支持 __input.x 访问）
        if (context.getInput() != null) {
            bindings.putAll(context.getInput());
        }

        // 节点信息
        bindings.put("nodeId", node.getId());
        bindings.put("nodeName", node.getName());

        // 上下文信息
        bindings.put("workflowId", context.getWorkflowId());
        bindings.put("executionId", context.getExecutionId());

        // 全局变量（作为 __global 对象）
        if (context.getGlobalVariables() != null) {
            bindings.put("__global", context.getGlobalVariables());
            // 同时展开到顶层（保持向后兼容）
            bindings.putAll(context.getGlobalVariables());
        }

        // 系统变量（作为 __system 对象）
        Map<String, Object> system = new HashMap<>();
        system.put("currentTime", System.currentTimeMillis());
        system.put("executionId", context.getExecutionId());
        system.put("workflowId", context.getWorkflowId());
        system.put("tenantId", context.getTenantId());
        system.put("status", context.getStatus().toString());
        bindings.put("__system", system);
        bindings.put("system", system); // 同时提供不带 __ 前缀的版本

        // 已完成节点的结果
        Map<String, Object> nodes = new HashMap<>();
        for (Map.Entry<String, NodeResult> entry : context.getNodeResults().entrySet()) {
            nodes.put(entry.getKey(), entry.getValue().getOutput());
        }
        bindings.put("nodes", nodes);

        return bindings;
    }

    /**
     * 清理沙箱池和平台线程执行器。
     * <p>
     * 应在应用程序关闭时调用，以释放所有资源。
     */
    public static void cleanupAll() {
        GraalSandboxPool.getInstance().shutdown();
        GraalSandbox.shutdownPlatformExecutor();
    }

    /**
     * 获取当前池大小（用于监控）。
     *
     * @return 当前可用的沙箱实例数量
     */
    public static int getPoolSize() {
        return GraalSandboxPool.getInstance().getPoolSize();
    }
}

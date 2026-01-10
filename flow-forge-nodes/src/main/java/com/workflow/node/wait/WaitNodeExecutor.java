package com.workflow.node.wait;

import com.workflow.context.VariableResolver;
import com.workflow.model.ExecutionContext;
import com.workflow.model.ExecutionStatus;
import com.workflow.model.Node;
import com.workflow.model.NodeResult;
import com.workflow.model.NodeType;
import com.workflow.node.NodeExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WAIT 节点执行器。
 * <p>
 * 暂停工作流执行，等待外部回调恢复。
 * 节点执行后工作流状态变为 WAITING，释放内存资源。
 * </p>
 * <p>
 * 配置选项：
 * <ul>
 *   <li>timeout - 超时时间（毫秒），默认 3600000 (1小时)</li>
 *   <li>callbackUrl - 回调 URL（可选）</li>
 *   <li>callbackData - 需要传递给回调的数据</li>
 * </ul>
 * </p>
 */
@Component
public class WaitNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(WaitNodeExecutor.class);

    private static final long DEFAULT_TIMEOUT_MS = 3600000; // 1 hour

    @Override
    public NodeResult execute(Node node, ExecutionContext context) {
        String nodeId = node.getId();
        log.info("Executing WAIT node: nodeId={}, executionId={}", nodeId, context.getExecutionId());

        try {
            // 获取配置
            Map<String, Object> config = node.getConfig();
            long timeoutMs = getTimeout(config);
            String callbackUrl = getCallbackUrl(config);
            Map<String, Object> callbackData = getCallbackData(config);

            // 创建等待票据
            String waitTicket = generateWaitTicket(context.getExecutionId(), nodeId);

            // 构建输出
            Map<String, Object> output = new HashMap<>();
            output.put("status", "WAITING");
            output.put("waitTicket", waitTicket);
            output.put("nodeId", nodeId);
            output.put("timeoutAt", Instant.now().plusMillis(timeoutMs).toString());
            if (callbackUrl != null) {
                output.put("callbackUrl", callbackUrl);
            }
            if (callbackData != null) {
                output.put("callbackData", callbackData);
            }

            // 创建 WAITING 状态的结果
            Instant now = Instant.now();
            NodeResult result = new NodeResult();
            result.setNodeId(nodeId);
            result.setStatus(ExecutionStatus.WAITING);
            result.setOutput(output);
            result.setStartTime(now);
            result.setEndTime(now);

            // 存储节点结果到上下文
            context.getNodeResults().put(nodeId, result);

            log.info("WAIT node executed successfully: nodeId={}, waitTicket={}, timeoutMs={}",
                    nodeId, waitTicket, timeoutMs);

            return result;

        } catch (Exception e) {
            log.error("Failed to execute WAIT node: nodeId={}", nodeId, e);
            Instant now = Instant.now();
            NodeResult result = new NodeResult();
            result.setNodeId(nodeId);
            result.setStatus(ExecutionStatus.FAILED);
            result.setErrorMessage("WAIT 节点执行失败: " + e.getMessage());
            result.setStackTrace(e.getClass().getName() + ": " + e.getMessage());
            result.setStartTime(now);
            result.setEndTime(now);
            return result;
        }
    }

    @Override
    public NodeType getSupportedType() {
        return NodeType.WAIT;
    }

    /**
     * 获取超时时间（毫秒）
     */
    private long getTimeout(Map<String, Object> config) {
        if (config == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        Object timeoutObj = config.get("timeout");
        if (timeoutObj == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        if (timeoutObj instanceof Number) {
            return ((Number) timeoutObj).longValue();
        }
        try {
            return Long.parseLong(timeoutObj.toString());
        } catch (NumberFormatException e) {
            log.warn("Invalid timeout value: {}, using default", timeoutObj);
            return DEFAULT_TIMEOUT_MS;
        }
    }

    /**
     * 获取回调 URL
     */
    private String getCallbackUrl(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        Object callbackUrl = config.get("callbackUrl");
        return callbackUrl != null ? callbackUrl.toString() : null;
    }

    /**
     * 获取回调数据
     */
    private Map<String, Object> getCallbackData(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        Object callbackData = config.get("callbackData");
        if (callbackData instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) callbackData;
            return map;
        }
        return null;
    }

    /**
     * 生成等待票据
     */
    private String generateWaitTicket(String executionId, String nodeId) {
        return "WAIT-" + executionId + "-" + nodeId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}

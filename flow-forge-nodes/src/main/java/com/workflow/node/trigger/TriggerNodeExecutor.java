package com.workflow.node.trigger;

import com.workflow.context.VariableResolver;
import com.workflow.model.ExecutionContext;
import com.workflow.model.Node;
import com.workflow.model.NodeResult;
import com.workflow.model.NodeType;
import com.workflow.node.AbstractNodeExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 触发器节点执行器
 * <p>
 * 作为工作流的入口节点，负责接收外部触发输入并传递给下游节点。
 * 支持四种触发器类型：
 * <ul>
 *   <li>{@code webhook} - HTTP Webhook 触发器</li>
 *   <li>{@code cron} - Cron 定时触发器</li>
 *   <li>{@code manual} - 手动触发器</li>
 *   <li>{@code event} - 事件触发器</li>
 * </ul>
 * <p>
 * 节点配置格式：
 * <pre>
 * {
 *   "type": "webhook",          // 触发器类型
 *   "webhookPath": "github",    // webhook 路径（webhook 类型）
 *   "asyncMode": "sync",        // 同步/异步模式（webhook 类型）
 *   "cronExpression": "0 * * * * ?",  // cron 表达式（cron 类型）
 *   "inputData": {...}          // 初始输入数据
 * }
 * </pre>
 * <p>
 * 输出格式：
 * <pre>
 * {
 *   "triggerType": "webhook",
 *   "triggeredAt": "2024-01-01T00:00:00Z",
 *   "nodeId": "trigger-node-1",
 *   "input": {...}              // 触发器接收到的输入数据
 * }
 * </pre>
 *
 * @see AbstractNodeExecutor
 * @see com.workflow.model.TriggerType
 */
@Component
public class TriggerNodeExecutor extends AbstractNodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(TriggerNodeExecutor.class);

    private static final String DEFAULT_TYPE = "manual";

    public TriggerNodeExecutor(VariableResolver variableResolver) {
        super(variableResolver);
    }

    @Override
    public NodeType getSupportedType() {
        return NodeType.TRIGGER;
    }

    @Override
    protected NodeResult doExecute(Node node,
                                   ExecutionContext context,
                                   Map<String, Object> resolvedConfig) {

        String nodeId = node.getId();

        // 获取触发器类型
        String triggerType = getConfigString(resolvedConfig, "type", DEFAULT_TYPE);

        log.info("Trigger node executed: nodeId={}, type={}, workflowId={}",
                nodeId, triggerType, context.getWorkflowId());

        // 构建输出数据
        Map<String, Object> output = new HashMap<>();
        output.put("triggerType", triggerType);
        output.put("triggeredAt", Instant.now().toString());
        output.put("nodeId", nodeId);

        // 将触发器输入数据传递到输出
        // 这些数据来自外部触发源（webhook payload, cron inputData, manual input, event data）
        Object inputData = resolvedConfig.get("inputData");
        if (inputData != null) {
            output.put("input", inputData);
        }

        // 对于 webhook 类型，添加额外的元数据
        if ("webhook".equalsIgnoreCase(triggerType)) {
            enhanceWebhookOutput(output, resolvedConfig, context);
        }

        // 对于 cron 类型，添加调度元数据
        if ("cron".equalsIgnoreCase(triggerType)) {
            enhanceCronOutput(output, resolvedConfig);
        }

        // 对于 event 类型，添加事件元数据
        if ("event".equalsIgnoreCase(triggerType)) {
            enhanceEventOutput(output, resolvedConfig);
        }

        // 将触发器输出设置到上下文中，供下游节点使用
        // 下游节点可以通过 {{triggerNodeId.output.input}} 访问触发器输入
        context.getNodeResults().put(nodeId, NodeResult.success(nodeId, output));

        return NodeResult.success(nodeId, output);
    }

    /**
     * 增强 Webhook 触发器输出元数据
     */
    private void enhanceWebhookOutput(Map<String, Object> output,
                                      Map<String, Object> config,
                                      ExecutionContext context) {
        // Webhook 路径
        String webhookPath = getConfigString(config, "webhookPath", "");
        if (!webhookPath.isEmpty()) {
            output.put("webhookPath", webhookPath);
        }

        // 从上下文中提取 HTTP 请求信息（由 WebhookTriggerController 设置）
        Optional.ofNullable(context.getInput())
                .map(input -> input.get("httpHeaders"))
                .ifPresent(headers -> output.put("httpHeaders", headers));

        Optional.ofNullable(context.getInput())
                .map(input -> input.get("httpMethod"))
                .ifPresent(method -> output.put("httpMethod", method));

        Optional.ofNullable(context.getInput())
                .map(input -> input.get("queryString"))
                .ifPresent(query -> output.put("queryString", query));

        Optional.ofNullable(context.getInput())
                .map(input -> input.get("clientIp"))
                .ifPresent(ip -> output.put("clientIp", ip));

        // 异步模式
        String asyncMode = getConfigString(config, "asyncMode", "async");
        output.put("asyncMode", asyncMode);
    }

    /**
     * 增强 Cron 触发器输出元数据
     */
    private void enhanceCronOutput(Map<String, Object> output,
                                   Map<String, Object> config) {
        String cronExpression = getConfigString(config, "cronExpression", "");
        if (!cronExpression.isEmpty()) {
            output.put("cronExpression", cronExpression);
        }

        String timezone = getConfigString(config, "timezone", "Asia/Shanghai");
        output.put("timezone", timezone);

        String scheduledFireTime = getConfigString(config, "scheduledFireTime", "");
        if (!scheduledFireTime.isEmpty()) {
            output.put("scheduledFireTime", scheduledFireTime);
        }

        String previousFireTime = getConfigString(config, "previousFireTime", "");
        if (!previousFireTime.isEmpty()) {
            output.put("previousFireTime", previousFireTime);
        }
    }

    /**
     * 增强事件触发器输出元数据
     */
    private void enhanceEventOutput(Map<String, Object> output,
                                    Map<String, Object> config) {
        String eventType = getConfigString(config, "eventType", "");
        if (!eventType.isEmpty()) {
            output.put("eventType", eventType);
        }

        String eventId = getConfigString(config, "eventId", "");
        if (!eventId.isEmpty()) {
            output.put("eventId", eventId);
        }

        String eventSource = getConfigString(config, "eventSource", "");
        if (!eventSource.isEmpty()) {
            output.put("eventSource", eventSource);
        }

        Object eventData = config.get("eventData");
        if (eventData != null) {
            output.put("eventData", eventData);
        }
    }

    /**
     * 获取触发器类型的中文描述
     */
    public static String getTriggerTypeDescription(String triggerType) {
        return switch (triggerType.toLowerCase()) {
            case "webhook" -> "HTTP Webhook 触发器";
            case "cron" -> "Cron 定时触发器";
            case "manual" -> "手动触发器";
            case "event" -> "事件触发器";
            default -> "未知触发器类型";
        };
    }
}

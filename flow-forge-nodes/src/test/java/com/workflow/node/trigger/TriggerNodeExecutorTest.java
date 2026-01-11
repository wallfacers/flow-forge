package com.workflow.node.trigger;

import com.workflow.context.VariableResolver;
import com.workflow.model.ExecutionContext;
import com.workflow.model.ExecutionStatus;
import com.workflow.model.Node;
import com.workflow.model.NodeType;
import com.workflow.model.NodeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * TriggerNodeExecutor 测试类
 * <p>
 * 测试工作流入口触发器节点的执行逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TRIGGER 节点执行器测试")
public class TriggerNodeExecutorTest {

    private static final String TENANT_ID = "test-tenant";
    private static final String WORKFLOW_ID = "test-workflow";
    private static final String EXECUTION_ID = "exec-123";

    @Mock
    private VariableResolver variableResolver;

    private TriggerNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new TriggerNodeExecutor(variableResolver);
        when(variableResolver.resolve(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("基本功能测试")
    class BasicTests {

        @Test
        @DisplayName("支持的节点类型应为 TRIGGER")
        void testGetSupportedType_IsTrigger() {
            assertThat(executor.getSupportedType()).isEqualTo(NodeType.TRIGGER);
        }

        @Test
        @DisplayName("应返回 TRIGGER 类型描述")
        void testGetTriggerTypeDescription_ReturnsCorrectDescription() {
            assertThat(TriggerNodeExecutor.getTriggerTypeDescription("webhook"))
                    .isEqualTo("HTTP Webhook 触发器");
            assertThat(TriggerNodeExecutor.getTriggerTypeDescription("cron"))
                    .isEqualTo("Cron 定时触发器");
            assertThat(TriggerNodeExecutor.getTriggerTypeDescription("manual"))
                    .isEqualTo("手动触发器");
            assertThat(TriggerNodeExecutor.getTriggerTypeDescription("event"))
                    .isEqualTo("事件触发器");
            assertThat(TriggerNodeExecutor.getTriggerTypeDescription("unknown"))
                    .isEqualTo("未知触发器类型");
        }
    }

    @Nested
    @DisplayName("Webhook 触发器测试")
    class WebhookTriggerTests {

        @Test
        @DisplayName("执行 Webhook 触发器应成功")
        void testExecuteWebhook_Success() {
            // Given
            Map<String, Object> config = new HashMap<>();
            config.put("type", "webhook");
            config.put("webhookPath", "github-push");
            config.put("asyncMode", "sync");

            Node node = new Node();
            node.setId("webhook-entry");
            node.setType(NodeType.TRIGGER);
            node.setName("GitHub Webhook");
            node.setConfig(config);

            Map<String, Object> input = new HashMap<>();
            input.put("data", Map.of("userId", "12345"));

            ExecutionContext context = ExecutionContext.builder()
                    .executionId(EXECUTION_ID)
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .input(input)
                    .build();

            // When
            NodeResult result = executor.execute(node, context);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(result.getNodeId()).isEqualTo("webhook-entry");

            Map<String, Object> output = result.getOutput();
            assertThat(output.get("triggerType")).isEqualTo("webhook");
            assertThat(output.get("nodeId")).isEqualTo("webhook-entry");
            assertThat(output.get("triggeredAt")).isNotNull();
            assertThat(output.get("asyncMode")).isEqualTo("sync");
        }

        @Test
        @DisplayName("Webhook 触发器应包含 HTTP 元数据")
        void testExecuteWebhook_WithHttpMetadata_IncludesHttpHeaders() {
            // Given
            Map<String, Object> config = new HashMap<>();
            config.put("type", "webhook");
            config.put("webhookPath", "test-path");

            Node node = new Node();
            node.setId("webhook-1");
            node.setType(NodeType.TRIGGER);
            node.setConfig(config);

            Map<String, Object> input = new HashMap<>();
            input.put("httpHeaders", Map.of(
                    "Content-Type", "application/json",
                    "X-GitHub-Event", "push"
            ));
            input.put("httpMethod", "POST");
            input.put("queryString", "key=value");
            input.put("clientIp", "192.168.1.1");

            ExecutionContext context = ExecutionContext.builder()
                    .executionId(EXECUTION_ID)
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .input(input)
                    .build();

            // When
            NodeResult result = executor.execute(node, context);

            // Then
            Map<String, Object> output = result.getOutput();
            assertThat(output.get("httpHeaders")).isNotNull();
            assertThat(output.get("httpMethod")).isEqualTo("POST");
            assertThat(output.get("queryString")).isEqualTo("key=value");
            assertThat(output.get("clientIp")).isEqualTo("192.168.1.1");
        }
    }

    @Nested
    @DisplayName("Cron 触发器测试")
    class CronTriggerTests {

        @Test
        @DisplayName("执行 Cron 触发器应成功")
        void testExecuteCron_Success() {
            // Given
            Map<String, Object> config = new HashMap<>();
            config.put("type", "cron");
            config.put("cronExpression", "0 0 * * * ?");
            config.put("timezone", "Asia/Shanghai");
            config.put("inputData", Map.of("source", "scheduled"));

            Node node = new Node();
            node.setId("cron-entry");
            node.setType(NodeType.TRIGGER);
            node.setName("Hourly Cron");
            node.setConfig(config);

            Map<String, Object> input = new HashMap<>();
            input.put("scheduledFireTime", "2025-01-12T10:00:00Z");
            input.put("previousFireTime", "2025-01-12T09:00:00Z");

            ExecutionContext context = ExecutionContext.builder()
                    .executionId(EXECUTION_ID)
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .input(input)
                    .build();

            // When
            NodeResult result = executor.execute(node, context);

            // Then
            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
            Map<String, Object> output = result.getOutput();
            assertThat(output.get("triggerType")).isEqualTo("cron");
            assertThat(output.get("cronExpression")).isEqualTo("0 0 * * * ?");
            assertThat(output.get("timezone")).isEqualTo("Asia/Shanghai");
            assertThat(output.get("scheduledFireTime")).isEqualTo("2025-01-12T10:00:00Z");
            assertThat(output.get("previousFireTime")).isEqualTo("2025-01-12T09:00:00Z");
        }

        @Test
        @DisplayName("Cron 触发器应包含 inputData")
        void testExecuteCron_WithInputData_IncludesInputData() {
            // Given
            Map<String, Object> config = new HashMap<>();
            config.put("type", "cron");
            config.put("cronExpression", "0 */30 * * * ?");
            config.put("inputData", Map.of("source", "cron", "interval", "30m"));

            Node node = new Node();
            node.setId("cron-1");
            node.setType(NodeType.TRIGGER);
            node.setConfig(config);

            ExecutionContext context = ExecutionContext.builder()
                    .executionId(EXECUTION_ID)
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .build();

            // When
            NodeResult result = executor.execute(node, context);

            // Then
            Map<String, Object> output = result.getOutput();
            @SuppressWarnings("unchecked")
            Map<String, Object> inputData = (Map<String, Object>) output.get("input");
            assertThat(inputData).isNotNull();
            assertThat(inputData.get("source")).isEqualTo("cron");
            assertThat(inputData.get("interval")).isEqualTo("30m");
        }
    }

    @Nested
    @DisplayName("手动触发器测试")
    class ManualTriggerTests {

        @Test
        @DisplayName("执行手动触发器应成功")
        void testExecuteManual_Success() {
            // Given
            Map<String, Object> config = new HashMap<>();
            config.put("type", "manual");
            config.put("allowedRoles", List.of("admin", "operator"));

            Node node = new Node();
            node.setId("manual-entry");
            node.setType(NodeType.TRIGGER);
            node.setName("手动触发");
            node.setConfig(config);

            ExecutionContext context = ExecutionContext.builder()
                    .executionId(EXECUTION_ID)
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .input(Map.of("userId", "admin"))
                    .build();

            // When
            NodeResult result = executor.execute(node, context);

            // Then
            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
            Map<String, Object> output = result.getOutput();
            assertThat(output.get("triggerType")).isEqualTo("manual");
        }

        @Test
        @DisplayName("手动触发器默认类型应为 manual")
        void testExecuteManual_NoType_DefaultsToManual() {
            // Given
            Map<String, Object> config = new HashMap<>();
            // 未指定 type

            Node node = new Node();
            node.setId("manual-1");
            node.setType(NodeType.TRIGGER);
            node.setConfig(config);

            ExecutionContext context = ExecutionContext.builder()
                    .executionId(EXECUTION_ID)
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .build();

            // When
            NodeResult result = executor.execute(node, context);

            // Then
            Map<String, Object> output = result.getOutput();
            assertThat(output.get("triggerType")).isEqualTo("manual");
        }
    }

    @Nested
    @DisplayName("事件触发器测试")
    class EventTriggerTests {

        @Test
        @DisplayName("执行事件触发器应成功")
        void testExecuteEvent_Success() {
            // Given
            Map<String, Object> config = new HashMap<>();
            config.put("type", "event");
            config.put("eventType", "user.created");
            config.put("filterExpression", "{{.data.priority}} == 'high'");

            Node node = new Node();
            node.setId("event-entry");
            node.setType(NodeType.TRIGGER);
            node.setName("用户创建事件");
            node.setConfig(config);

            Map<String, Object> input = new HashMap<>();
            input.put("eventId", "evt-123");
            input.put("eventSource", "user-service");
            input.put("eventData", Map.of("userId", "12345", "priority", "high"));

            ExecutionContext context = ExecutionContext.builder()
                    .executionId(EXECUTION_ID)
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .input(input)
                    .build();

            // When
            NodeResult result = executor.execute(node, context);

            // Then
            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
            Map<String, Object> output = result.getOutput();
            assertThat(output.get("triggerType")).isEqualTo("event");
            assertThat(output.get("eventType")).isEqualTo("user.created");
            assertThat(output.get("eventId")).isEqualTo("evt-123");
            assertThat(output.get("eventSource")).isEqualTo("user-service");
            assertThat(output.get("eventData")).isNotNull();
        }
    }
}

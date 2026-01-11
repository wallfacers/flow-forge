package com.workflow.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.infra.entity.CronTriggerEntity;
import com.workflow.infra.entity.WebhookRegistrationEntity;
import com.workflow.infra.repository.CronTriggerRepository;
import com.workflow.infra.repository.WebhookRegistrationRepository;
import com.workflow.model.ExecutionContext;
import com.workflow.model.ExecutionStatus;
import com.workflow.model.Node;
import com.workflow.model.NodeType;
import com.workflow.node.NodeExecutor;
import com.workflow.node.NodeExecutorFactory;
import com.workflow.node.wait.WaitNodeExecutor;
import com.workflow.trigger.config.PowerJobConfig;
import com.workflow.trigger.cron.CronTriggerService;
import com.workflow.trigger.dto.CronTriggerRequest;
import com.workflow.trigger.dto.WebhookRegistrationRequest;
import com.workflow.trigger.dto.WebhookRequest;
import com.workflow.trigger.dto.WebhookTriggerResponse;
import com.workflow.trigger.webhook.WebhookTriggerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 触发器测试类
 * <p>
 * 测试 Webhook、Cron 触发器和 WAIT 节点的功能
 * </p>
 */
@SpringBootTest(classes = {
        TriggerTest.TestConfiguration.class,
        WebhookTriggerService.class,
        CronTriggerService.class,
        WaitNodeExecutor.class,
        NodeExecutorFactory.class
})
@ActiveProfiles("test")
@DisplayName("触发器测试")
public class TriggerTest {

    @Configuration
    static class TestConfiguration {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public PowerJobConfig powerJobConfig() {
            PowerJobConfig config = new PowerJobConfig();
            config.setEnabled(false);
            return config;
        }
    }

    private static final String TENANT_ID = "test-tenant";
    private static final String WORKFLOW_ID = "test-workflow";
    private static final String WEBHOOK_PATH = "test-webhook";

    @MockBean
    private WebhookRegistrationRepository webhookRepository;

    @MockBean
    private CronTriggerRepository cronTriggerRepository;

    @Autowired
    private WebhookTriggerService webhookTriggerService;

    @Autowired
    private CronTriggerService cronTriggerService;

    @Autowired
    private WaitNodeExecutor waitNodeExecutor;

    @Autowired
    private NodeExecutorFactory nodeExecutorFactory;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Webhook 触发器测试")
    class WebhookTriggerTests {

        @Test
        @DisplayName("注册 Webhook 应成功")
        void testRegisterWebhook_Success() {
            // Given
            WebhookRegistrationRequest request = new WebhookRegistrationRequest();
            request.setWorkflowId(WORKFLOW_ID);
            request.setWorkflowName("Test Workflow");
            request.setWebhookPath(WEBHOOK_PATH);
            request.setSecretKey("test-secret");
            request.setEnabled(true);

            when(webhookRepository.existsByWebhookPathAndDeletedAtIsNull(anyString()))
                    .thenReturn(false);
            when(webhookRepository.existsByTenantIdAndWorkflowIdAndDeletedAtIsNull(anyString(), anyString()))
                    .thenReturn(false);
            when(webhookRepository.save(any(WebhookRegistrationEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            var response = webhookTriggerService.registerWebhook(TENANT_ID, request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getWorkflowId()).isEqualTo(WORKFLOW_ID);
            assertThat(response.getWebhookPath()).isEqualTo(WEBHOOK_PATH);
            assertThat(response.getEnabled()).isTrue();

            verify(webhookRepository).save(any(WebhookRegistrationEntity.class));
        }

        @Test
        @DisplayName("注册重复路径的 Webhook 应失败")
        void testRegisterWebhook_DuplicatePath_Fails() {
            // Given
            WebhookRegistrationRequest request = new WebhookRegistrationRequest();
            request.setWorkflowId(WORKFLOW_ID);
            request.setWebhookPath(WEBHOOK_PATH);

            when(webhookRepository.existsByWebhookPathAndDeletedAtIsNull(anyString()))
                    .thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> webhookTriggerService.registerWebhook(TENANT_ID, request))
                    .hasMessageContaining("Webhook路径已存在");
        }

        @Test
        @DisplayName("处理 Webhook 请求应成功")
        void testHandleWebhook_Success() {
            // Given
            Map<String, Object> data = new HashMap<>();
            data.put("userId", "12345");
            data.put("action", "trigger");

            WebhookRequest request = new WebhookRequest();
            request.setData(data);
            request.setSignature(""); // 无签名

            WebhookRegistrationEntity webhook = new WebhookRegistrationEntity();
            webhook.setId(UUID.randomUUID());
            webhook.setTenantId(TENANT_ID);
            webhook.setWorkflowId(WORKFLOW_ID);
            webhook.setWebhookPath(WEBHOOK_PATH);
            webhook.setEnabled(true);
            webhook.setSecretKey(null); // 无密钥
            webhook.setTotalTriggers(0L);
            webhook.setSuccessfulTriggers(0L);
            webhook.setFailedTriggers(0L);

            when(webhookRepository.findByWebhookPathAndDeletedAtIsNull(anyString()))
                    .thenReturn(Optional.of(webhook));
            when(webhookRepository.save(any(WebhookRegistrationEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            WebhookTriggerResponse response = webhookTriggerService.handleWebhook(WEBHOOK_PATH, request, Map.of());

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getSuccess()).isTrue();
            assertThat(response.getExecutionId()).isNotNull();
            assertThat(response.getWorkflowId()).isEqualTo(WORKFLOW_ID);

            ArgumentCaptor<WebhookRegistrationEntity> captor =
                    ArgumentCaptor.forClass(WebhookRegistrationEntity.class);
            verify(webhookRepository).save(captor.capture());
            assertThat(captor.getValue().getTotalTriggers()).isEqualTo(1);
            assertThat(captor.getValue().getSuccessfulTriggers()).isEqualTo(1);
        }

        @Test
        @DisplayName("处理未注册的 Webhook 应失败")
        void testHandleWebhook_NotRegistered_Fails() {
            // Given
            when(webhookRepository.findByWebhookPathAndDeletedAtIsNull(anyString()))
                    .thenReturn(Optional.empty());

            // When
            WebhookTriggerResponse response = webhookTriggerService.handleWebhook(WEBHOOK_PATH, new WebhookRequest(), Map.of());

            // Then
            assertThat(response.getSuccess()).isFalse();
            assertThat(response.getError()).contains("Webhook未注册");
        }

        @Test
        @DisplayName("禁用的 Webhook 不应触发")
        void testHandleWebhook_Disabled_Fails() {
            // Given
            WebhookRegistrationEntity webhook = new WebhookRegistrationEntity();
            webhook.setEnabled(false);

            when(webhookRepository.findByWebhookPathAndDeletedAtIsNull(anyString()))
                    .thenReturn(Optional.of(webhook));

            // When
            WebhookTriggerResponse response = webhookTriggerService.handleWebhook(WEBHOOK_PATH, new WebhookRequest(), Map.of());

            // Then
            assertThat(response.getSuccess()).isFalse();
            assertThat(response.getError()).contains("Webhook已禁用");
        }

        @Test
        @DisplayName("获取租户的所有 Webhook 应成功")
        void testListWebhooks_Success() {
            // Given
            WebhookRegistrationEntity entity = new WebhookRegistrationEntity();
            entity.setId(UUID.randomUUID());
            entity.setTenantId(TENANT_ID);
            entity.setWorkflowId(WORKFLOW_ID);
            entity.setWebhookPath(WEBHOOK_PATH);

            when(webhookRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(anyString()))
                    .thenReturn(List.of(entity));

            // When
            var webhooks = webhookTriggerService.listWebhooks(TENANT_ID);

            // Then
            assertThat(webhooks).hasSize(1);
            assertThat(webhooks.get(0).getWorkflowId()).isEqualTo(WORKFLOW_ID);
        }

        @Test
        @DisplayName("删除 Webhook 应成功")
        void testDeleteWebhook_Success() {
            // Given
            UUID id = UUID.randomUUID();
            WebhookRegistrationEntity entity = new WebhookRegistrationEntity();
            entity.setId(id);
            entity.setTenantId(TENANT_ID);

            when(webhookRepository.findById(id)).thenReturn(Optional.of(entity));
            when(webhookRepository.save(any(WebhookRegistrationEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            webhookTriggerService.deleteWebhook(TENANT_ID, id);

            // Then
            verify(webhookRepository).save(entity);
            assertThat(entity.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("重置统计信息应成功")
        void testResetStatistics_Success() {
            // Given
            UUID id = UUID.randomUUID();
            WebhookRegistrationEntity entity = new WebhookRegistrationEntity();
            entity.setId(id);
            entity.setTenantId(TENANT_ID);
            entity.setTotalTriggers(100L);
            entity.setSuccessfulTriggers(80L);
            entity.setFailedTriggers(20L);

            when(webhookRepository.findById(id)).thenReturn(Optional.of(entity));
            when(webhookRepository.save(any(WebhookRegistrationEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            webhookTriggerService.resetStatistics(TENANT_ID, id);

            // Then
            assertThat(entity.getTotalTriggers()).isZero();
            assertThat(entity.getSuccessfulTriggers()).isZero();
            assertThat(entity.getFailedTriggers()).isZero();
        }
    }

    @Nested
    @DisplayName("Cron 触发器测试")
    class CronTriggerTests {

        @Test
        @DisplayName("创建 Cron 触发器应成功")
        void testCreateCronTrigger_Success() {
            // Given
            CronTriggerRequest request = new CronTriggerRequest();
            request.setWorkflowId(WORKFLOW_ID);
            request.setWorkflowName("Test Workflow");
            request.setCronExpression("0 0 * * * ?");
            request.setTimezone("Asia/Shanghai");
            request.setEnabled(true);

            when(cronTriggerRepository.existsByTenantIdAndWorkflowIdAndDeletedAtIsNull(anyString(), anyString()))
                    .thenReturn(false);
            when(cronTriggerRepository.save(any(CronTriggerEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            var response = cronTriggerService.createCronTrigger(TENANT_ID, request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getWorkflowId()).isEqualTo(WORKFLOW_ID);
            assertThat(response.getCronExpression()).isEqualTo("0 0 * * * ?");
            assertThat(response.getEnabled()).isTrue();

            verify(cronTriggerRepository).save(any(CronTriggerEntity.class));
        }

        @Test
        @DisplayName("创建无效 Cron 表达式的触发器应失败")
        void testCreateCronTrigger_InvalidCron_Fails() {
            // Given
            CronTriggerRequest request = new CronTriggerRequest();
            request.setWorkflowId(WORKFLOW_ID);
            request.setCronExpression("invalid-cron");

            // When & Then
            assertThatThrownBy(() -> cronTriggerService.createCronTrigger(TENANT_ID, request))
                    .hasMessageContaining("无效的Cron表达式");
        }

        @Test
        @DisplayName("更新 Cron 触发器应成功")
        void testUpdateCronTrigger_Success() {
            // Given
            UUID id = UUID.randomUUID();
            CronTriggerRequest request = new CronTriggerRequest();
            request.setWorkflowId(WORKFLOW_ID);
            request.setCronExpression("0 30 * * * ?");
            request.setEnabled(true);

            CronTriggerEntity entity = new CronTriggerEntity();
            entity.setId(id);
            entity.setTenantId(TENANT_ID);
            entity.setWorkflowId(WORKFLOW_ID);
            entity.setCronExpression("0 0 * * * ?");
            entity.setTimezone("Asia/Shanghai");

            when(cronTriggerRepository.findById(id)).thenReturn(Optional.of(entity));
            when(cronTriggerRepository.save(any(CronTriggerEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            var response = cronTriggerService.updateCronTrigger(TENANT_ID, id, request);

            // Then
            assertThat(response.getCronExpression()).isEqualTo("0 30 * * * ?");
        }

        @Test
        @DisplayName("删除 Cron 触发器应成功")
        void testDeleteCronTrigger_Success() {
            // Given
            UUID id = UUID.randomUUID();
            CronTriggerEntity entity = new CronTriggerEntity();
            entity.setId(id);
            entity.setTenantId(TENANT_ID);

            when(cronTriggerRepository.findById(id)).thenReturn(Optional.of(entity));
            when(cronTriggerRepository.save(any(CronTriggerEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            cronTriggerService.deleteCronTrigger(TENANT_ID, id);

            // Then
            verify(cronTriggerRepository).save(entity);
            assertThat(entity.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("获取租户的所有 Cron 触发器应成功")
        void testListCronTriggers_Success() {
            // Given
            CronTriggerEntity entity = new CronTriggerEntity();
            entity.setId(UUID.randomUUID());
            entity.setTenantId(TENANT_ID);
            entity.setWorkflowId(WORKFLOW_ID);
            entity.setCronExpression("0 0 * * * ?");

            when(cronTriggerRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(anyString()))
                    .thenReturn(List.of(entity));

            // When
            var triggers = cronTriggerService.listCronTriggers(TENANT_ID);

            // Then
            assertThat(triggers).hasSize(1);
            assertThat(triggers.get(0).getWorkflowId()).isEqualTo(WORKFLOW_ID);
        }

        @Test
        @DisplayName("启用/禁用 Cron 触发器应成功")
        void testToggleCronTrigger_Success() {
            // Given
            UUID id = UUID.randomUUID();
            CronTriggerEntity entity = new CronTriggerEntity();
            entity.setId(id);
            entity.setTenantId(TENANT_ID);
            entity.setEnabled(true);

            when(cronTriggerRepository.findById(id)).thenReturn(Optional.of(entity));
            when(cronTriggerRepository.save(any(CronTriggerEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            cronTriggerService.toggleCronTrigger(TENANT_ID, id, false);

            // Then
            assertThat(entity.getEnabled()).isFalse();
            verify(cronTriggerRepository).save(entity);
        }

        @Test
        @DisplayName("处理 Cron 触发应成功")
        void testHandleCronTrigger_Success() {
            // Given
            Long powerjobJobId = 12345L;
            CronTriggerEntity entity = new CronTriggerEntity();
            entity.setId(UUID.randomUUID());
            entity.setTenantId(TENANT_ID);
            entity.setWorkflowId(WORKFLOW_ID);
            entity.setEnabled(true);
            entity.setTotalTriggers(0L);
            entity.setSuccessfulTriggers(0L);
            entity.setFailedTriggers(0L);

            when(cronTriggerRepository.findByPowerjobJobIdAndDeletedAtIsNull(powerjobJobId))
                    .thenReturn(Optional.of(entity));
            when(cronTriggerRepository.save(any(CronTriggerEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            cronTriggerService.handleCronTrigger(powerjobJobId);

            // Then
            assertThat(entity.getTotalTriggers()).isEqualTo(1);
            assertThat(entity.getSuccessfulTriggers()).isEqualTo(1);
            assertThat(entity.getLastTriggeredAt()).isNotNull();
        }

        @Test
        @DisplayName("禁用的 Cron 触发器不应执行")
        void testHandleCronTrigger_Disabled_Skips() {
            // Given
            Long powerjobJobId = 12345L;
            CronTriggerEntity entity = new CronTriggerEntity();
            entity.setEnabled(false);

            when(cronTriggerRepository.findByPowerjobJobIdAndDeletedAtIsNull(powerjobJobId))
                    .thenReturn(Optional.of(entity));

            // When
            cronTriggerService.handleCronTrigger(powerjobJobId);

            // Then
            verify(cronTriggerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("WAIT 节点测试")
    class WaitNodeTests {

        @Test
        @DisplayName("执行 WAIT 节点应返回 WAITING 状态")
        void testWaitNode_Executes_ReturnsWaitingStatus() {
            // Given
            Node node = new Node();
            node.setId("wait-1");
            node.setType(NodeType.WAIT);
            node.setName("Test Wait");
            node.setConfig(new HashMap<>());

            ExecutionContext context = ExecutionContext.builder()
                    .executionId("exec-123")
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .build();

            // When
            var result = waitNodeExecutor.execute(node, context);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getNodeId()).isEqualTo("wait-1");
            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.WAITING);

            Map<String, Object> output = result.getOutput();
            assertThat(output).isNotNull();
            assertThat(output.get("status")).isEqualTo("WAITING");
            assertThat(output.get("waitTicket")).isNotNull();
            assertThat(output.get("timeoutAt")).isNotNull();
        }

        @Test
        @DisplayName("WAIT 节点应使用配置的超时时间")
        void testWaitNode_WithCustomTimeout_UsesConfiguredTimeout() {
            // Given
            Map<String, Object> config = new HashMap<>();
            config.put("timeout", 60000L); // 1 minute

            Node node = new Node();
            node.setId("wait-2");
            node.setType(NodeType.WAIT);
            node.setConfig(config);

            ExecutionContext context = ExecutionContext.builder()
                    .executionId("exec-456")
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .build();

            // When
            var result = waitNodeExecutor.execute(node, context);

            // Then
            Map<String, Object> output = result.getOutput();
            String timeoutAt = (String) output.get("timeoutAt");

            // 验证超时时间大约在1分钟后
            Instant expectedTimeout = Instant.now().plusMillis(60000);
            Instant actualTimeout = Instant.parse(timeoutAt);

            assertThat(actualTimeout).isAfter(Instant.now().minusSeconds(5));
            assertThat(actualTimeout).isBefore(expectedTimeout.plusSeconds(5));
        }

        @Test
        @DisplayName("WAIT 节点应包含回调 URL")
        void testWaitNode_WithCallbackUrl_IncludesCallbackInOutput() {
            // Given
            Map<String, Object> config = new HashMap<>();
            config.put("callbackUrl", "https://example.com/callback");
            config.put("callbackData", Map.of("key", "value"));

            Node node = new Node();
            node.setId("wait-3");
            node.setType(NodeType.WAIT);
            node.setConfig(config);

            ExecutionContext context = ExecutionContext.builder()
                    .executionId("exec-789")
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .build();

            // When
            var result = waitNodeExecutor.execute(node, context);

            // Then
            Map<String, Object> output = result.getOutput();
            assertThat(output.get("callbackUrl")).isEqualTo("https://example.com/callback");
            assertThat(output.get("callbackData")).isNotNull();
        }

        @Test
        @DisplayName("WAIT 节点应将结果存储到上下文")
        void testWaitNode_StoresResultInContext() {
            // Given
            Node node = new Node();
            node.setId("wait-4");
            node.setType(NodeType.WAIT);

            ExecutionContext context = ExecutionContext.builder()
                    .executionId("exec-abc")
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .build();

            // When
            waitNodeExecutor.execute(node, context);

            // Then
            assertThat(context.getNodeResults()).containsKey("wait-4");
            var storedResult = context.getNodeResults().get("wait-4");
            assertThat(storedResult.getStatus()).isEqualTo(ExecutionStatus.WAITING);
        }

        @Test
        @DisplayName("WAIT 节点支持类型应为 WAIT")
        void testWaitNode_SupportedType_IsWait() {
            // When & Then
            assertThat(waitNodeExecutor.getSupportedType()).isEqualTo(NodeType.WAIT);
        }

        @Test
        @DisplayName("NodeExecutorFactory 应返回 WaitNodeExecutor")
        void testNodeExecutorFactory_ReturnsWaitNodeExecutor() {
            // When
            NodeExecutor executor = nodeExecutorFactory.getExecutor(NodeType.WAIT);

            // Then
            assertThat(executor).isNotNull();
            assertThat(executor).isInstanceOf(WaitNodeExecutor.class);
            assertThat(executor.getSupportedType()).isEqualTo(NodeType.WAIT);
        }

        @Test
        @DisplayName("WAIT 节点应生成唯一的等待票据")
        void testWaitNode_GeneratesUniqueWaitTicket() {
            // Given
            Node node = new Node();
            node.setId("wait-5");
            node.setType(NodeType.WAIT);

            ExecutionContext context = ExecutionContext.builder()
                    .executionId("exec-xyz")
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .build();

            // When
            var result1 = waitNodeExecutor.execute(node, context);
            var result2 = waitNodeExecutor.execute(node, context);

            // Then
            String ticket1 = (String) result1.getOutput().get("waitTicket");
            String ticket2 = (String) result2.getOutput().get("waitTicket");

            assertThat(ticket1).isNotNull();
            assertThat(ticket2).isNotNull();
            assertThat(ticket1).isNotEqualTo(ticket2); // 每次执行应生成不同的票据
        }
    }
}

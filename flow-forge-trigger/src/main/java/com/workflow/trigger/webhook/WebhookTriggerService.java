package com.workflow.trigger.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.model.WorkflowValidationException;
import com.workflow.infra.entity.WebhookRegistrationEntity;
import com.workflow.infra.repository.WebhookRegistrationRepository;
import com.workflow.trigger.dto.WebhookRegistrationRequest;
import com.workflow.trigger.dto.WebhookRegistrationResponse;
import com.workflow.trigger.dto.WebhookRequest;
import com.workflow.trigger.dto.WebhookTriggerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Webhook 触发器服务。
 * <p>
 * 负责 Webhook 触发器的注册管理和请求处理。
 */
@Service
public class WebhookTriggerService {

    private static final Logger log = LoggerFactory.getLogger(WebhookTriggerService.class);

    private final WebhookRegistrationRepository webhookRepository;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public WebhookTriggerService(WebhookRegistrationRepository webhookRepository,
                                  ObjectMapper objectMapper,
                                  @Value("${webhook.base-url:/api/webhook}") String baseUrl) {
        this.webhookRepository = webhookRepository;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    /**
     * 注册 Webhook 触发器
     */
    @Transactional
    public WebhookRegistrationResponse registerWebhook(String tenantId, WebhookRegistrationRequest request) {
        // 验证 Webhook 路径
        validateWebhookPath(request.getWebhookPath());

        // 检查路径是否已存在
        if (webhookRepository.existsByWebhookPathAndDeletedAtIsNull(request.getWebhookPath())) {
            throw new WorkflowValidationException("Webhook路径已存在: " + request.getWebhookPath());
        }

        // 检查工作流是否已注册
        if (webhookRepository.existsByTenantIdAndWorkflowIdAndDeletedAtIsNull(tenantId, request.getWorkflowId())) {
            throw new WorkflowValidationException("工作流已注册Webhook: " + request.getWorkflowId());
        }

        WebhookRegistrationEntity entity = new WebhookRegistrationEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setWorkflowId(request.getWorkflowId());
        entity.setWorkflowName(request.getWorkflowName());
        entity.setWebhookPath(request.getWebhookPath());
        entity.setSecretKey(request.getSecretKey());
        entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        entity.setHeaderMapping(request.getHeaderMapping());
        entity.setTotalTriggers(0L);
        entity.setSuccessfulTriggers(0L);
        entity.setFailedTriggers(0L);

        entity = webhookRepository.save(entity);
        log.info("Registered webhook: tenantId={}, workflowId={}, path={}",
                tenantId, request.getWorkflowId(), request.getWebhookPath());

        return WebhookRegistrationResponse.fromEntity(entity, baseUrl);
    }

    /**
     * 更新 Webhook 触发器
     */
    @Transactional
    public WebhookRegistrationResponse updateWebhook(String tenantId, UUID id, WebhookRegistrationRequest request) {
        WebhookRegistrationEntity entity = webhookRepository.findById(id)
                .orElseThrow(() -> new WorkflowValidationException("Webhook不存在: " + id));

        // 验证租户
        if (!tenantId.equals(entity.getTenantId())) {
            throw new WorkflowValidationException("无权操作此Webhook");
        }

        // 验证 Webhook 路径（如果修改了路径）
        if (!Objects.equals(entity.getWebhookPath(), request.getWebhookPath())) {
            validateWebhookPath(request.getWebhookPath());
        }

        // 检查路径是否被其他记录占用
        if (!Objects.equals(entity.getWebhookPath(), request.getWebhookPath())) {
            if (webhookRepository.existsByWebhookPathAndDeletedAtIsNull(request.getWebhookPath())) {
                throw new WorkflowValidationException("Webhook路径已存在: " + request.getWebhookPath());
            }
            entity.setWebhookPath(request.getWebhookPath());
        }

        entity.setWorkflowId(request.getWorkflowId());
        entity.setWorkflowName(request.getWorkflowName());
        entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        entity.setHeaderMapping(request.getHeaderMapping());
        if (request.getSecretKey() != null) {
            entity.setSecretKey(request.getSecretKey());
        }

        entity = webhookRepository.save(entity);
        log.info("Updated webhook: id={}, tenantId={}, workflowId={}",
                id, tenantId, request.getWorkflowId());

        return WebhookRegistrationResponse.fromEntity(entity, baseUrl);
    }

    /**
     * 删除 Webhook 触发器（软删除）
     */
    @Transactional
    public void deleteWebhook(String tenantId, UUID id) {
        WebhookRegistrationEntity entity = webhookRepository.findById(id)
                .orElseThrow(() -> new WorkflowValidationException("Webhook不存在: " + id));

        if (!tenantId.equals(entity.getTenantId())) {
            throw new WorkflowValidationException("无权操作此Webhook");
        }

        entity.markAsDeleted();
        webhookRepository.save(entity);
        log.info("Deleted webhook: id={}, tenantId={}", id, tenantId);
    }

    /**
     * 获取 Webhook 触发器
     */
    public WebhookRegistrationResponse getWebhook(String tenantId, UUID id) {
        WebhookRegistrationEntity entity = webhookRepository.findById(id)
                .orElseThrow(() -> new WorkflowValidationException("Webhook不存在: " + id));

        if (!tenantId.equals(entity.getTenantId())) {
            throw new WorkflowValidationException("无权访问此Webhook");
        }

        return WebhookRegistrationResponse.fromEntity(entity, baseUrl);
    }

    /**
     * 根据 Webhook 路径获取注册信息
     */
    public Optional<WebhookRegistrationEntity> getWebhookByPath(String webhookPath) {
        return webhookRepository.findByWebhookPathAndDeletedAtIsNull(webhookPath);
    }

    /**
     * 获取租户的所有 Webhook
     */
    public java.util.List<WebhookRegistrationResponse> listWebhooks(String tenantId) {
        return webhookRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(entity -> WebhookRegistrationResponse.fromEntity(entity, baseUrl))
                .toList();
    }

    /**
     * 处理 Webhook 触发请求
     */
    @Transactional
    public WebhookTriggerResponse handleWebhook(String webhookPath, WebhookRequest request, Map<String, String> headers) {
        // 查找 Webhook 注册
        WebhookRegistrationEntity webhook = webhookRepository.findByWebhookPathAndDeletedAtIsNull(webhookPath)
                .orElse(null);

        if (webhook == null) {
            log.warn("Webhook not found: {}", webhookPath);
            return WebhookTriggerResponse.failure("Webhook未注册");
        }

        if (!webhook.getEnabled()) {
            log.warn("Webhook is disabled: {}", webhookPath);
            return WebhookTriggerResponse.failure("Webhook已禁用");
        }

        // 验证签名（如果配置了密钥）
        if (webhook.getSecretKey() != null && !webhook.getSecretKey().isEmpty()) {
            String expectedSignature = calculateSignature(request, webhook.getSecretKey());
            if (!constantTimeEquals(expectedSignature, request.getSignature())) {
                log.warn("Invalid signature for webhook: {}", webhookPath);
                webhook.incrementTrigger(false);
                webhookRepository.save(webhook);
                return WebhookTriggerResponse.failure("签名验证失败");
            }
        }

        try {
            // TODO: 实际触发工作流执行
            // 这里需要调用 WorkflowDispatcher 来执行工作流
            String executionId = generateExecutionId();

            webhook.incrementTrigger(true);
            webhookRepository.save(webhook);

            log.info("Webhook triggered: path={}, workflowId={}, executionId={}",
                    webhookPath, webhook.getWorkflowId(), executionId);

            return WebhookTriggerResponse.success(executionId, webhook.getWorkflowId());

        } catch (Exception e) {
            log.error("Failed to trigger webhook: {}", webhookPath, e);
            webhook.incrementTrigger(false);
            webhookRepository.save(webhook);
            return WebhookTriggerResponse.failure("触发失败: " + e.getMessage());
        }
    }

    /**
     * 计算请求签名
     */
    private String calculateSignature(WebhookRequest request, String secret) {
        try {
            // HMAC-SHA256 签名
            String payload = objectMapper.writeValueAsString(request.getData());
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes());
            // 使用 Base64 编码（Java 8+）
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to calculate signature", e);
            return "";
        }
    }

    /**
     * 生成执行ID
     */
    private String generateExecutionId() {
        return "EXEC-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 启用/禁用 Webhook
     */
    @Transactional
    public void toggleWebhook(String tenantId, UUID id, boolean enabled) {
        WebhookRegistrationEntity entity = webhookRepository.findById(id)
                .orElseThrow(() -> new WorkflowValidationException("Webhook不存在: " + id));

        if (!tenantId.equals(entity.getTenantId())) {
            throw new WorkflowValidationException("无权操作此Webhook");
        }

        entity.setEnabled(enabled);
        webhookRepository.save(entity);
        log.info("Webhook {}: id={}, tenantId={}", enabled ? "enabled" : "disabled", id, tenantId);
    }

    /**
     * 重置 Webhook 统计信息
     */
    @Transactional
    public void resetStatistics(String tenantId, UUID id) {
        WebhookRegistrationEntity entity = webhookRepository.findById(id)
                .orElseThrow(() -> new WorkflowValidationException("Webhook不存在: " + id));

        if (!tenantId.equals(entity.getTenantId())) {
            throw new WorkflowValidationException("无权操作此Webhook");
        }

        entity.resetStatistics();
        webhookRepository.save(entity);
        log.info("Reset statistics for webhook: id={}, tenantId={}", id, tenantId);
    }

    /**
     * 验证 Webhook 路径
     */
    private void validateWebhookPath(String path) {
        if (path == null || path.isEmpty()) {
            throw new WorkflowValidationException("Webhook路径不能为空");
        }
        // 防止路径遍历攻击
        if (path.contains("..") || path.contains("/") || path.contains("\\")) {
            throw new WorkflowValidationException("Webhook路径包含非法字符");
        }
        // 只允许字母、数字、下划线、连字符
        if (!path.matches("^[a-zA-Z0-9_-]+$")) {
            throw new WorkflowValidationException("Webhook路径只能包含字母、数字、下划线和连字符");
        }
        if (path.length() > 100) {
            throw new WorkflowValidationException("Webhook路径长度不能超过100");
        }
    }

    /**
     * 常量时间比较，防止时序攻击
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        byte[] aBytes = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }
}

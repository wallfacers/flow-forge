package com.workflow.trigger.webhook;

import com.workflow.trigger.dto.WebhookRegistrationRequest;
import com.workflow.trigger.dto.WebhookRegistrationResponse;
import com.workflow.trigger.dto.WebhookRequest;
import com.workflow.trigger.dto.WebhookTriggerResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Webhook 触发器 REST 控制器。
 * <p>
 * 提供 Webhook 触发器的管理接口和触发接口。
 */
@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookTriggerService webhookTriggerService;

    public WebhookController(WebhookTriggerService webhookTriggerService) {
        this.webhookTriggerService = webhookTriggerService;
    }

    /**
     * 注册 Webhook 触发器
     * POST /api/webhook/register
     */
    @PostMapping("/register")
    public ResponseEntity<WebhookRegistrationResponse> registerWebhook(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @Valid @RequestBody WebhookRegistrationRequest request) {
        try {
            WebhookRegistrationResponse response = webhookTriggerService.registerWebhook(tenantId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("Failed to register webhook: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 更新 Webhook 触发器
     * PUT /api/webhook/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<WebhookRegistrationResponse> updateWebhook(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody WebhookRegistrationRequest request) {
        try {
            WebhookRegistrationResponse response = webhookTriggerService.updateWebhook(tenantId, id, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Failed to update webhook: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 删除 Webhook 触发器
     * DELETE /api/webhook/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWebhook(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        try {
            webhookTriggerService.deleteWebhook(tenantId, id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.error("Failed to delete webhook: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 获取 Webhook 触发器详情
     * GET /api/webhook/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<WebhookRegistrationResponse> getWebhook(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        try {
            WebhookRegistrationResponse response = webhookTriggerService.getWebhook(tenantId, id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Failed to get webhook: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 获取租户的所有 Webhook 触发器
     * GET /api/webhook
     */
    @GetMapping
    public ResponseEntity<List<WebhookRegistrationResponse>> listWebhooks(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<WebhookRegistrationResponse> webhooks = webhookTriggerService.listWebhooks(tenantId);
        return ResponseEntity.ok(webhooks);
    }

    /**
     * 启用 Webhook 触发器
     * POST /api/webhook/{id}/enable
     */
    @PostMapping("/{id}/enable")
    public ResponseEntity<Void> enableWebhook(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        try {
            webhookTriggerService.toggleWebhook(tenantId, id, true);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 禁用 Webhook 触发器
     * POST /api/webhook/{id}/disable
     */
    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disableWebhook(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        try {
            webhookTriggerService.toggleWebhook(tenantId, id, false);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 重置 Webhook 统计信息
     * POST /api/webhook/{id}/reset-stats
     */
    @PostMapping("/{id}/reset-stats")
    public ResponseEntity<Void> resetStatistics(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        try {
            webhookTriggerService.resetStatistics(tenantId, id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Webhook 触发接口（外部调用）
     * POST /api/webhook/{path}
     */
    @PostMapping("/{path}")
    public ResponseEntity<WebhookTriggerResponse> triggerWebhook(
            @PathVariable String path,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader Map<String, String> headers) {
        try {
            WebhookRequest request = new WebhookRequest();
            request.setData(body);
            request.setTimestamp(System.currentTimeMillis());
            request.setSignature(headers.get("X-Signature"));

            WebhookTriggerResponse response = webhookTriggerService.handleWebhook(path, request, headers);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to handle webhook: {}", path, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(WebhookTriggerResponse.failure("处理失败: " + e.getMessage()));
        }
    }
}

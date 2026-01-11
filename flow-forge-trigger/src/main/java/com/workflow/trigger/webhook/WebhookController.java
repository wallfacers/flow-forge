package com.workflow.trigger.webhook;

import com.workflow.infra.entity.TriggerRegistryEntity;
import com.workflow.trigger.dto.WebhookExecutionResponse;
import com.workflow.trigger.registry.TriggerRegistryService;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Webhook 触发器 REST 控制器（重构版）
 * <p>
 * 提供以下功能：
 * <ul>
 *   <li>POST /api/webhook/{path} - 触发工作流（支持 sync/async）</li>
 *   <li>GET /api/webhook/{path}/config - 查询 Webhook 配置</li>
 *   <li>GET /api/webhook - 查询所有 Webhook 触发器</li>
 * </ul>
 * <p>
 * 同步/异步模式控制：
 * <ul>
 *   <li>请求头 {@code Prefer: wait=sync} - 同步模式，等待执行完成返回结果</li>
 *   <li>请求头 {@code Prefer: wait=async} - 异步模式，立即返回 executionId</li>
 *   <li>无请求头 - 根据节点配置 {@code asyncMode} 决定，默认异步</li>
 * </ul>
 *
 * @see WebhookTriggerService
 * @see TriggerRegistryService
 */
@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookTriggerService webhookTriggerService;
    private final TriggerRegistryService registryService;

    public WebhookController(WebhookTriggerService webhookTriggerService,
                             TriggerRegistryService registryService) {
        this.webhookTriggerService = webhookTriggerService;
        this.registryService = registryService;
    }

    /**
     * Webhook 触发接口（外部调用）
     * <p>
     * POST /api/webhook/{path}
     * <p>
     * 执行模式由请求头 Prefer 和节点配置决定：
     * <ul>
     *   <li>同步模式返回完整执行结果</li>
     *   <li>异步模式返回 executionId</li>
     * </ul>
     *
     * @param path        webhook 路径
     * @param body        请求体数据
     * @param contentType Content-Type 请求头
     * @param headers     所有 HTTP 请求头
     * @return 执行响应
     */
    @PostMapping("/{path}")
    public ResponseEntity<WebhookExecutionResponse> triggerWebhook(
            @PathVariable String path,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader Map<String, String> headers) {

        log.debug("Webhook triggered: path={}, contentType={}", path, contentType);

        // 验证 Content-Type
        if (body != null && !body.isEmpty()) {
            if (contentType == null || !contentType.contains("application/json")) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                        .body(WebhookExecutionResponse.error("仅支持 application/json 格式"));
            }
        }

        try {
            WebhookExecutionResponse response = webhookTriggerService.handleWebhook(path, body, headers);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        } catch (Exception e) {
            log.error("Failed to handle webhook: path={}", path, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(WebhookExecutionResponse.error("处理失败: " + e.getMessage()));
        }
    }

    /**
     * 查询 Webhook 配置
     * <p>
     * GET /api/webhook/{path}/config
     *
     * @param path webhook 路径
     * @return Webhook 配置信息
     */
    @GetMapping("/{path}/config")
    public ResponseEntity<Map<String, Object>> getWebhookConfig(@PathVariable String path) {
        return webhookTriggerService.getWebhookByPath(path)
                .map(entity -> ResponseEntity.ok(toConfigMap(entity)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 查询所有 Webhook 触发器
     * <p>
     * GET /api/webhook
     *
     * @return Webhook 触发器列表
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listWebhooks(
            @RequestParam(required = false) String tenantId) {

        List<TriggerRegistryEntity> triggers;
        if (tenantId != null && !tenantId.isEmpty()) {
            triggers = registryService.findByTenantId(tenantId);
        } else {
            // 返回所有 WEBHOOK 类型的触发器
            triggers = registryService.findEnabled().stream()
                    .filter(t -> "WEBHOOK".equalsIgnoreCase(t.getTriggerType()))
                    .toList();
        }

        List<Map<String, Object>> result = triggers.stream()
                .map(this::toConfigMap)
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * 启用 Webhook 触发器
     * <p>
     * POST /api/webhook/by-id/{id}/enable
     *
     * @param id 触发器 ID
     * @return 200 成功
     */
    @PostMapping("/by-id/{id}/enable")
    public ResponseEntity<Void> enableWebhook(@PathVariable UUID id) {
        try {
            registryService.toggleEnabled(id, true);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 禁用 Webhook 触发器
     * <p>
     * POST /api/webhook/by-id/{id}/disable
     *
     * @param id 触发器 ID
     * @return 200 成功
     */
    @PostMapping("/by-id/{id}/disable")
    public ResponseEntity<Void> disableWebhook(@PathVariable UUID id) {
        try {
            registryService.toggleEnabled(id, false);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 重置统计信息
     * <p>
     * POST /api/webhook/by-id/{id}/reset-stats
     *
     * @param id 触发器 ID
     * @return 200 成功
     */
    @PostMapping("/by-id/{id}/reset-stats")
    public ResponseEntity<Void> resetStatistics(@PathVariable UUID id) {
        try {
            registryService.resetStatistics(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 将 TriggerRegistryEntity 转换为配置 Map
     */
    private Map<String, Object> toConfigMap(TriggerRegistryEntity entity) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", entity.getId().toString());
        result.put("workflowId", entity.getWorkflowId());
        result.put("nodeId", entity.getNodeId());
        result.put("tenantId", entity.getTenantId());
        result.put("triggerType", entity.getTriggerType());
        result.put("webhookPath", entity.getWebhookPath() != null ? entity.getWebhookPath() : "");
        result.put("enabled", entity.getEnabled());
        result.put("totalTriggers", entity.getTotalTriggers());
        result.put("successfulTriggers", entity.getSuccessfulTriggers());
        result.put("failedTriggers", entity.getFailedTriggers());
        result.put("lastTriggeredAt", entity.getLastTriggeredAt() != null ? entity.getLastTriggeredAt().toString() : null);
        result.put("lastTriggerStatus", entity.getLastTriggerStatus() != null ? entity.getLastTriggerStatus() : "");
        result.put("config", entity.getTriggerConfig());
        result.put("createdAt", entity.getCreatedAt().toString());
        result.put("updatedAt", entity.getUpdatedAt().toString());
        return result;
    }
}

package com.workflow.trigger;

import com.workflow.infra.entity.TriggerRegistryEntity;
import com.workflow.trigger.registry.TriggerRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 统一触发器 REST 控制器
 * <p>
 * 提供所有类型触发器（WEBHOOK、CRON、MANUAL、EVENT）的查询接口。
 * <p>
 * API 接口：
 * <ul>
 *   <li>GET /api/triggers - 查询所有触发器（支持租户、类型过滤）</li>
 *   <li>GET /api/triggers/{id} - 查询单个触发器详情</li>
 *   <li>GET /api/triggers/workflow/{workflowId} - 查询工作流的所有触发器</li>
 *   <li>POST /api/triggers/{id}/enable - 启用触发器</li>
 *   <li>POST /api/triggers/{id}/disable - 禁用触发器</li>
 *   <li>POST /api/triggers/{id}/reset-stats - 重置统计信息</li>
 * </ul>
 *
 * @see TriggerRegistryService
 */
@RestController
@RequestMapping("/api/triggers")
public class TriggerController {

    private static final Logger log = LoggerFactory.getLogger(TriggerController.class);

    private final TriggerRegistryService registryService;

    public TriggerController(TriggerRegistryService registryService) {
        this.registryService = registryService;
    }

    /**
     * 查询所有触发器
     * <p>
     * GET /api/triggers?tenantId={tenantId}&type={type}
     *
     * @param tenantId 租户 ID（可选）
     * @param type     触发器类型（可选）：webhook, cron, manual, event
     * @return 触发器列表
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listTriggers(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String type) {

        log.debug("List triggers: tenantId={}, type={}", tenantId, type);

        List<TriggerRegistryEntity> triggers;

        if (tenantId != null && !tenantId.isEmpty()) {
            triggers = registryService.findByTenantId(tenantId);
        } else {
            triggers = registryService.findEnabled();
        }

        // 按类型过滤
        if (type != null && !type.isEmpty()) {
            triggers = triggers.stream()
                    .filter(t -> type.equalsIgnoreCase(t.getTriggerType()))
                    .toList();
        }

        List<Map<String, Object>> result = triggers.stream()
                .map(this::toDetailMap)
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * 查询单个触发器详情
     * <p>
     * GET /api/triggers/{id}
     *
     * @param id 触发器 ID
     * @return 触发器详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTrigger(@PathVariable UUID id) {
        return registryService.findByWorkflowId(id.toString())
                .stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .map(entity -> ResponseEntity.ok(toDetailMap(entity)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 查询工作流的所有触发器
     * <p>
     * GET /api/triggers/workflow/{workflowId}
     *
     * @param workflowId 工作流 ID
     * @return 触发器列表
     */
    @GetMapping("/workflow/{workflowId}")
    public ResponseEntity<List<Map<String, Object>>> getWorkflowTriggers(@PathVariable String workflowId) {
        List<TriggerRegistryEntity> triggers = registryService.findByWorkflowId(workflowId);

        List<Map<String, Object>> result = triggers.stream()
                .map(this::toDetailMap)
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * 启用触发器
     * <p>
     * POST /api/triggers/{id}/enable
     *
     * @param id 触发器 ID
     * @return 200 成功
     */
    @PostMapping("/{id}/enable")
    public ResponseEntity<Void> enableTrigger(@PathVariable UUID id) {
        try {
            registryService.toggleEnabled(id, true);
            log.info("Trigger enabled: id={}", id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("Failed to enable trigger: id={}, error={}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 禁用触发器
     * <p>
     * POST /api/triggers/{id}/disable
     *
     * @param id 触发器 ID
     * @return 200 成功
     */
    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disableTrigger(@PathVariable UUID id) {
        try {
            registryService.toggleEnabled(id, false);
            log.info("Trigger disabled: id={}", id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("Failed to disable trigger: id={}, error={}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 重置统计信息
     * <p>
     * POST /api/triggers/{id}/reset-stats
     *
     * @param id 触发器 ID
     * @return 200 成功
     */
    @PostMapping("/{id}/reset-stats")
    public ResponseEntity<Void> resetStatistics(@PathVariable UUID id) {
        try {
            registryService.resetStatistics(id);
            log.info("Statistics reset for trigger: id={}", id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("Failed to reset statistics: id={}, error={}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 获取触发器统计信息
     * <p>
     * GET /api/triggers/stats?tenantId={tenantId}
     *
     * @param tenantId 租户 ID（可选）
     * @return 统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics(
            @RequestParam(required = false) String tenantId) {

        long totalCount;
        long enabledCount;

        if (tenantId != null && !tenantId.isEmpty()) {
            List<TriggerRegistryEntity> triggers = registryService.findByTenantId(tenantId);
            totalCount = triggers.size();
            enabledCount = triggers.stream().filter(TriggerRegistryEntity::getEnabled).count();
        } else {
            List<TriggerRegistryEntity> allTriggers = registryService.findEnabled();
            totalCount = allTriggers.size();
            enabledCount = allTriggers.stream().filter(TriggerRegistryEntity::getEnabled).count();
        }

        Map<String, Object> stats = Map.of(
                "totalCount", totalCount,
                "enabledCount", enabledCount,
                "disabledCount", totalCount - enabledCount,
                "tenantId", tenantId != null ? tenantId : "all"
        );

        return ResponseEntity.ok(stats);
    }

    /**
     * 将 TriggerRegistryEntity 转换为详情 Map（包含完整配置）
     * <p>
     * 此接口返回完整的 trigger_config，供第三方集成使用
     */
    private Map<String, Object> toDetailMap(TriggerRegistryEntity entity) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", entity.getId().toString());
        result.put("workflowId", entity.getWorkflowId());
        result.put("tenantId", entity.getTenantId());
        result.put("nodeId", entity.getNodeId());
        result.put("triggerType", entity.getTriggerType());
        result.put("enabled", entity.getEnabled());
        result.put("totalTriggers", entity.getTotalTriggers());
        result.put("successfulTriggers", entity.getSuccessfulTriggers());
        result.put("failedTriggers", entity.getFailedTriggers());
        result.put("lastTriggeredAt", entity.getLastTriggeredAt() != null ? entity.getLastTriggeredAt().toString() : null);
        result.put("lastTriggerStatus", entity.getLastTriggerStatus() != null ? entity.getLastTriggerStatus() : "");
        result.put("triggerConfig", entity.getTriggerConfig());
        // Webhook 专用字段
        result.put("webhookPath", entity.getWebhookPath() != null ? entity.getWebhookPath() : "");
        result.put("hasSecretKey", entity.getSecretKey() != null && !entity.getSecretKey().isEmpty());
        // Cron 专用字段
        result.put("cronExpression", entity.getCronExpression() != null ? entity.getCronExpression() : "");
        result.put("timezone", entity.getTimezone() != null ? entity.getTimezone() : "Asia/Shanghai");
        result.put("powerjobJobId", entity.getPowerjobJobId() != null ? entity.getPowerjobJobId() : -1L);
        result.put("nextTriggerTime", entity.getNextTriggerTime() != null ? entity.getNextTriggerTime().toString() : null);
        // 审计字段
        result.put("createdAt", entity.getCreatedAt().toString());
        result.put("updatedAt", entity.getUpdatedAt().toString());
        return result;
    }
}

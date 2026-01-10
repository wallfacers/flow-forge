package com.workflow.trigger.cron;

import com.workflow.exception.WorkflowException;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.trigger.dto.CronTriggerRequest;
import com.workflow.trigger.dto.CronTriggerResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Cron 触发器 REST 控制器。
 * <p>
 * 提供 Cron 触发器的管理接口。
 */
@RestController
@RequestMapping("/api/triggers/cron")
public class CronController {

    private static final Logger log = LoggerFactory.getLogger(CronController.class);

    private final CronTriggerService cronTriggerService;

    public CronController(CronTriggerService cronTriggerService) {
        this.cronTriggerService = cronTriggerService;
    }

    /**
     * 创建 Cron 触发器
     * POST /api/triggers/cron
     */
    @PostMapping
    public ResponseEntity<CronTriggerResponse> createCronTrigger(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @Valid @RequestBody CronTriggerRequest request) {
        try {
            CronTriggerResponse response = cronTriggerService.createCronTrigger(tenantId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (WorkflowValidationException e) {
            log.warn("Validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (WorkflowException e) {
            log.error("Failed to create cron trigger: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 更新 Cron 触发器
     * PUT /api/triggers/cron/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<CronTriggerResponse> updateCronTrigger(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody CronTriggerRequest request) {
        try {
            CronTriggerResponse response = cronTriggerService.updateCronTrigger(tenantId, id, request);
            return ResponseEntity.ok(response);
        } catch (WorkflowValidationException e) {
            log.warn("Validation failed: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 删除 Cron 触发器
     * DELETE /api/triggers/cron/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCronTrigger(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        try {
            cronTriggerService.deleteCronTrigger(tenantId, id);
            return ResponseEntity.noContent().build();
        } catch (WorkflowValidationException e) {
            log.warn("Validation failed: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 获取 Cron 触发器详情
     * GET /api/triggers/cron/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<CronTriggerResponse> getCronTrigger(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        try {
            CronTriggerResponse response = cronTriggerService.getCronTrigger(tenantId, id);
            return ResponseEntity.ok(response);
        } catch (WorkflowValidationException e) {
            log.warn("Validation failed: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 获取租户的所有 Cron 触发器
     * GET /api/triggers/cron
     */
    @GetMapping
    public ResponseEntity<List<CronTriggerResponse>> listCronTriggers(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<CronTriggerResponse> triggers = cronTriggerService.listCronTriggers(tenantId);
        return ResponseEntity.ok(triggers);
    }

    /**
     * 启用 Cron 触发器
     * POST /api/triggers/cron/{id}/enable
     */
    @PostMapping("/{id}/enable")
    public ResponseEntity<Void> enableCronTrigger(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        try {
            cronTriggerService.toggleCronTrigger(tenantId, id, true);
            return ResponseEntity.ok().build();
        } catch (WorkflowValidationException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 禁用 Cron 触发器
     * POST /api/triggers/cron/{id}/disable
     */
    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disableCronTrigger(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        try {
            cronTriggerService.toggleCronTrigger(tenantId, id, false);
            return ResponseEntity.ok().build();
        } catch (WorkflowValidationException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 重置统计信息
     * POST /api/triggers/cron/{id}/reset-stats
     */
    @PostMapping("/{id}/reset-stats")
    public ResponseEntity<Void> resetStatistics(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        try {
            cronTriggerService.resetStatistics(tenantId, id);
            return ResponseEntity.ok().build();
        } catch (WorkflowValidationException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

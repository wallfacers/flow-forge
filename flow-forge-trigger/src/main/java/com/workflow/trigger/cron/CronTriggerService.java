package com.workflow.trigger.cron;

import com.workflow.model.WorkflowException;
import com.workflow.model.WorkflowValidationException;
import com.workflow.infra.entity.CronTriggerEntity;
import com.workflow.infra.repository.CronTriggerRepository;
import com.workflow.trigger.config.PowerJobConfig;
import com.workflow.trigger.dto.CronTriggerRequest;
import com.workflow.trigger.dto.CronTriggerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cron 触发器服务。
 * <p>
 * 负责管理基于 Cron 表达式的定时触发器。
 */
@Service
public class CronTriggerService {

    private static final Logger log = LoggerFactory.getLogger(CronTriggerService.class);

    private final CronTriggerRepository cronTriggerRepository;
    private final PowerJobConfig powerJobConfig;

    public CronTriggerService(CronTriggerRepository cronTriggerRepository,
                              PowerJobConfig powerJobConfig) {
        this.cronTriggerRepository = cronTriggerRepository;
        this.powerJobConfig = powerJobConfig;
    }

    /**
     * 创建 Cron 触发器
     */
    @Transactional
    public CronTriggerResponse createCronTrigger(String tenantId, CronTriggerRequest request) {
        // 验证 Cron 表达式
        validateCronExpression(request.getCronExpression());

        // 检查工作流是否已存在 Cron 触发器
        if (cronTriggerRepository.existsByTenantIdAndWorkflowIdAndDeletedAtIsNull(tenantId, request.getWorkflowId())) {
            throw new WorkflowValidationException("工作流已存在Cron触发器: " + request.getWorkflowId());
        }

        CronTriggerEntity entity = new CronTriggerEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setWorkflowId(request.getWorkflowId());
        entity.setWorkflowName(request.getWorkflowName());
        entity.setCronExpression(request.getCronExpression());
        entity.setTimezone(request.getTimezone() != null ? request.getTimezone() : "Asia/Shanghai");
        entity.setInputData(request.getInputData());
        entity.setMisfireStrategy(request.getMisfireStrategy() != null ? request.getMisfireStrategy() : "FIRE");
        entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        entity.setDescription(request.getDescription());
        entity.setTotalTriggers(0L);
        entity.setSuccessfulTriggers(0L);
        entity.setFailedTriggers(0L);

        entity = cronTriggerRepository.save(entity);
        log.info("Created cron trigger: tenantId={}, workflowId={}, cron={}",
                tenantId, request.getWorkflowId(), request.getCronExpression());

        // 如果启用 PowerJob，调度任务
        if (entity.getEnabled() && powerJobConfig.isEnabled()) {
            scheduleToPowerJob(entity);
        }

        return CronTriggerResponse.fromEntity(entity);
    }

    /**
     * 更新 Cron 触发器
     */
    @Transactional
    public CronTriggerResponse updateCronTrigger(String tenantId, UUID id, CronTriggerRequest request) {
        // 验证 Cron 表达式
        validateCronExpression(request.getCronExpression());

        CronTriggerEntity entity = cronTriggerRepository.findById(id)
                .orElseThrow(() -> new WorkflowValidationException("Cron触发器不存在: " + id));

        if (!entity.getTenantId().equals(tenantId)) {
            throw new WorkflowValidationException("无权操作此Cron触发器");
        }

        // 如果 Cron 表达式变化，需要重新调度
        boolean needsReschedule = !entity.getCronExpression().equals(request.getCronExpression())
                || !entity.getTimezone().equals(request.getTimezone());

        entity.setWorkflowId(request.getWorkflowId());
        entity.setWorkflowName(request.getWorkflowName());
        entity.setCronExpression(request.getCronExpression());
        entity.setTimezone(request.getTimezone() != null ? request.getTimezone() : "Asia/Shanghai");
        entity.setInputData(request.getInputData());
        entity.setMisfireStrategy(request.getMisfireStrategy() != null ? request.getMisfireStrategy() : "FIRE");
        entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        entity.setDescription(request.getDescription());

        entity = cronTriggerRepository.save(entity);

        // 重新调度
        if (needsReschedule && powerJobConfig.isEnabled()) {
            rescheduleOnPowerJob(entity);
        }

        log.info("Updated cron trigger: id={}, tenantId={}, workflowId={}",
                id, tenantId, request.getWorkflowId());

        return CronTriggerResponse.fromEntity(entity);
    }

    /**
     * 删除 Cron 触发器（软删除）
     */
    @Transactional
    public void deleteCronTrigger(String tenantId, UUID id) {
        CronTriggerEntity entity = cronTriggerRepository.findById(id)
                .orElseThrow(() -> new WorkflowValidationException("Cron触发器不存在: " + id));

        if (!entity.getTenantId().equals(tenantId)) {
            throw new WorkflowValidationException("无权操作此Cron触发器");
        }

        // 从 PowerJob 删除
        if (entity.getPowerjobJobId() != null && powerJobConfig.isEnabled()) {
            deleteFromPowerJob(entity.getPowerjobJobId());
        }

        entity.markAsDeleted();
        cronTriggerRepository.save(entity);
        log.info("Deleted cron trigger: id={}, tenantId={}", id, tenantId);
    }

    /**
     * 获取 Cron 触发器
     */
    public CronTriggerResponse getCronTrigger(String tenantId, UUID id) {
        CronTriggerEntity entity = cronTriggerRepository.findById(id)
                .orElseThrow(() -> new WorkflowValidationException("Cron触发器不存在: " + id));

        if (!entity.getTenantId().equals(tenantId)) {
            throw new WorkflowValidationException("无权访问此Cron触发器");
        }

        return CronTriggerResponse.fromEntity(entity);
    }

    /**
     * 获取租户的所有 Cron 触发器
     */
    public List<CronTriggerResponse> listCronTriggers(String tenantId) {
        return cronTriggerRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(CronTriggerResponse::fromEntity)
                .toList();
    }

    /**
     * 启用/禁用 Cron 触发器
     */
    @Transactional
    public void toggleCronTrigger(String tenantId, UUID id, boolean enabled) {
        CronTriggerEntity entity = cronTriggerRepository.findById(id)
                .orElseThrow(() -> new WorkflowValidationException("Cron触发器不存在: " + id));

        if (!entity.getTenantId().equals(tenantId)) {
            throw new WorkflowValidationException("无权操作此Cron触发器");
        }

        entity.setEnabled(enabled);
        cronTriggerRepository.save(entity);

        if (enabled && powerJobConfig.isEnabled()) {
            scheduleToPowerJob(entity);
        } else if (entity.getPowerjobJobId() != null && powerJobConfig.isEnabled()) {
            // 禁用时停止任务
            stopOnPowerJob(entity.getPowerjobJobId());
        }

        log.info("Cron trigger {}: id={}, tenantId={}", enabled ? "enabled" : "disabled", id, tenantId);
    }

    /**
     * 重置统计信息
     */
    @Transactional
    public void resetStatistics(String tenantId, UUID id) {
        CronTriggerEntity entity = cronTriggerRepository.findById(id)
                .orElseThrow(() -> new WorkflowValidationException("Cron触发器不存在: " + id));

        if (!entity.getTenantId().equals(tenantId)) {
            throw new WorkflowValidationException("无权操作此Cron触发器");
        }

        entity.resetStatistics();
        cronTriggerRepository.save(entity);
        log.info("Reset statistics for cron trigger: id={}, tenantId={}", id, tenantId);
    }

    /**
     * 处理 Cron 触发（由 PowerJob 调用）
     */
    @Transactional
    public void handleCronTrigger(Long powerjobJobId) {
        Optional<CronTriggerEntity> triggerOpt = cronTriggerRepository.findByPowerjobJobIdAndDeletedAtIsNull(powerjobJobId);
        if (triggerOpt.isEmpty()) {
            log.warn("Cron trigger not found for PowerJob job: {}", powerjobJobId);
            return;
        }

        CronTriggerEntity trigger = triggerOpt.get();

        if (!trigger.getEnabled()) {
            log.info("Cron trigger is disabled: {}", trigger.getId());
            return;
        }

        try {
            // TODO: 实际触发工作流执行
            // 这里需要调用 WorkflowDispatcher 来执行工作流
            String executionId = "EXEC-" + System.currentTimeMillis();

            trigger.incrementTrigger(true);
            trigger.setLastTriggeredAt(Instant.now());

            log.info("Cron trigger executed: workflowId={}, executionId={}, powerjobJobId={}",
                    trigger.getWorkflowId(), executionId, powerjobJobId);

        } catch (Exception e) {
            log.error("Failed to execute cron trigger: workflowId={}", trigger.getWorkflowId(), e);
            trigger.incrementTrigger(false);
        }

        cronTriggerRepository.save(trigger);
    }

    /**
     * 调度到 PowerJob
     */
    private void scheduleToPowerJob(CronTriggerEntity entity) {
        // TODO: 实际的 PowerJob 调度逻辑
        // 需要调用 PowerJob Client API 创建定时任务
        log.info("Scheduling cron trigger to PowerJob: workflowId={}, cron={}",
                entity.getWorkflowId(), entity.getCronExpression());

        // 模拟返回的 Job ID
        // Long powerJobId = powerJobClient.createJob(...);
        // entity.setPowerjobJobId(powerJobId);
        // cronTriggerRepository.save(entity);
    }

    /**
     * 在 PowerJob 上重新调度
     */
    private void rescheduleOnPowerJob(CronTriggerEntity entity) {
        if (entity.getPowerjobJobId() != null) {
            deleteFromPowerJob(entity.getPowerjobJobId());
        }
        scheduleToPowerJob(entity);
    }

    /**
     * 从 PowerJob 删除任务
     */
    private void deleteFromPowerJob(Long powerjobJobId) {
        log.info("Deleting cron trigger from PowerJob: powerjobJobId={}", powerjobJobId);
        // TODO: 实际的 PowerJob 删除逻辑
        // powerJobClient.deleteJob(powerjobJobId);
    }

    /**
     * 在 PowerJob 上停止任务
     */
    private void stopOnPowerJob(Long powerjobJobId) {
        log.info("Stopping cron trigger on PowerJob: powerjobJobId={}", powerjobJobId);
        // TODO: 实际的 PowerJob 停止逻辑
        // powerJobClient.disableJob(powerjobJobId);
    }

    /**
     * 验证 Cron 表达式
     */
    private void validateCronExpression(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            throw new WorkflowValidationException("Cron表达式不能为空");
        }
        try {
            // 使用 Spring 的 CronExpression 验证
            org.springframework.scheduling.support.CronExpression.parse(cronExpression);
        } catch (IllegalArgumentException e) {
            throw new WorkflowValidationException("无效的Cron表达式: " + cronExpression, e);
        }
    }
}

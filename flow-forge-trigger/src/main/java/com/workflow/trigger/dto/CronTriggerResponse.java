package com.workflow.trigger.dto;

import java.time.Instant;

/**
 * Cron 触发器响应 DTO。
 */
public class CronTriggerResponse {

    private String id;
    private String tenantId;
    private String workflowId;
    private String workflowName;
    private String cronExpression;
    private String timezone;
    private Long powerjobJobId;
    private String misfireStrategy;
    private Boolean enabled;
    private String description;
    private Long totalTriggers;
    private Long successfulTriggers;
    private Long failedTriggers;
    private Instant lastTriggeredAt;
    private Instant nextTriggerTime;
    private String lastTriggerStatus;
    private Instant createdAt;
    private Instant updatedAt;

    public static CronTriggerResponse fromEntity(com.workflow.infra.entity.CronTriggerEntity entity) {
        CronTriggerResponse response = new CronTriggerResponse();
        response.setId(entity.getId().toString());
        response.setTenantId(entity.getTenantId());
        response.setWorkflowId(entity.getWorkflowId());
        response.setWorkflowName(entity.getWorkflowName());
        response.setCronExpression(entity.getCronExpression());
        response.setTimezone(entity.getTimezone());
        response.setPowerjobJobId(entity.getPowerjobJobId());
        response.setMisfireStrategy(entity.getMisfireStrategy());
        response.setEnabled(entity.getEnabled());
        response.setDescription(entity.getDescription());
        response.setTotalTriggers(entity.getTotalTriggers());
        response.setSuccessfulTriggers(entity.getSuccessfulTriggers());
        response.setFailedTriggers(entity.getFailedTriggers());
        response.setLastTriggeredAt(entity.getLastTriggeredAt());
        response.setNextTriggerTime(entity.getNextTriggerTime());
        response.setLastTriggerStatus(entity.getLastTriggerStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Long getPowerjobJobId() {
        return powerjobJobId;
    }

    public void setPowerjobJobId(Long powerjobJobId) {
        this.powerjobJobId = powerjobJobId;
    }

    public String getMisfireStrategy() {
        return misfireStrategy;
    }

    public void setMisfireStrategy(String misfireStrategy) {
        this.misfireStrategy = misfireStrategy;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getTotalTriggers() {
        return totalTriggers;
    }

    public void setTotalTriggers(Long totalTriggers) {
        this.totalTriggers = totalTriggers;
    }

    public Long getSuccessfulTriggers() {
        return successfulTriggers;
    }

    public void setSuccessfulTriggers(Long successfulTriggers) {
        this.successfulTriggers = successfulTriggers;
    }

    public Long getFailedTriggers() {
        return failedTriggers;
    }

    public void setFailedTriggers(Long failedTriggers) {
        this.failedTriggers = failedTriggers;
    }

    public Instant getLastTriggeredAt() {
        return lastTriggeredAt;
    }

    public void setLastTriggeredAt(Instant lastTriggeredAt) {
        this.lastTriggeredAt = lastTriggeredAt;
    }

    public Instant getNextTriggerTime() {
        return nextTriggerTime;
    }

    public void setNextTriggerTime(Instant nextTriggerTime) {
        this.nextTriggerTime = nextTriggerTime;
    }

    public String getLastTriggerStatus() {
        return lastTriggerStatus;
    }

    public void setLastTriggerStatus(String lastTriggerStatus) {
        this.lastTriggerStatus = lastTriggerStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

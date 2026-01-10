package com.workflow.trigger.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Webhook 注册响应 DTO。
 * <p>
 * 返回已注册的 Webhook 配置信息。
 */
public class WebhookRegistrationResponse {

    private String id;
    private String tenantId;
    private String workflowId;
    private String workflowName;
    private String webhookUrl;
    private String webhookPath;
    private Boolean enabled;
    private Map<String, String> headerMapping;
    private Long totalTriggers;
    private Long successfulTriggers;
    private Long failedTriggers;
    private Instant lastTriggeredAt;
    private String lastTriggerStatus;
    private Instant createdAt;
    private Instant updatedAt;

    public static WebhookRegistrationResponse fromEntity(
            com.workflow.infra.entity.WebhookRegistrationEntity entity,
            String baseUrl) {

        String webhookUrl = baseUrl != null
            ? baseUrl + "/api/webhook/" + entity.getWebhookPath()
            : "/api/webhook/" + entity.getWebhookPath();

        WebhookRegistrationResponse response = new WebhookRegistrationResponse();
        response.setId(entity.getId().toString());
        response.setTenantId(entity.getTenantId());
        response.setWorkflowId(entity.getWorkflowId());
        response.setWorkflowName(entity.getWorkflowName());
        response.setWebhookUrl(webhookUrl);
        response.setWebhookPath(entity.getWebhookPath());
        response.setEnabled(entity.getEnabled());
        response.setHeaderMapping(entity.getHeaderMapping());
        response.setTotalTriggers(entity.getTotalTriggers());
        response.setSuccessfulTriggers(entity.getSuccessfulTriggers());
        response.setFailedTriggers(entity.getFailedTriggers());
        response.setLastTriggeredAt(entity.getLastTriggeredAt());
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

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getWebhookPath() {
        return webhookPath;
    }

    public void setWebhookPath(String webhookPath) {
        this.webhookPath = webhookPath;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getHeaderMapping() {
        return headerMapping;
    }

    public void setHeaderMapping(Map<String, String> headerMapping) {
        this.headerMapping = headerMapping;
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

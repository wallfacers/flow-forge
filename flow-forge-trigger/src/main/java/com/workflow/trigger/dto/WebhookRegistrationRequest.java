package com.workflow.trigger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Webhook 注册请求 DTO。
 * <p>
 * 用于创建或更新 Webhook 触发器配置。
 */
public class WebhookRegistrationRequest {

    @NotBlank(message = "工作流ID不能为空")
    private String workflowId;

    private String workflowName;

    @NotBlank(message = "Webhook路径不能为空")
    @Size(max = 255, message = "Webhook路径长度不能超过255")
    private String webhookPath;

    @Size(max = 255, message = "密钥长度不能超过255")
    private String secretKey;

    private Boolean enabled = true;

    private Map<String, String> headerMapping;

    // Getters and Setters
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

    public String getWebhookPath() {
        return webhookPath;
    }

    public void setWebhookPath(String webhookPath) {
        this.webhookPath = webhookPath;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
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
}

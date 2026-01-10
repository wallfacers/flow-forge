package com.workflow.trigger.dto;

/**
 * Webhook 触发响应 DTO。
 * <p>
 * 返回工作流触发结果。
 */
public class WebhookTriggerResponse {

    private Boolean success;
    private String executionId;
    private String workflowId;
    private String message;
    private String error;
    private Long timestamp;

    public static WebhookTriggerResponse success(String executionId, String workflowId) {
        WebhookTriggerResponse response = new WebhookTriggerResponse();
        response.setSuccess(true);
        response.setExecutionId(executionId);
        response.setWorkflowId(workflowId);
        response.setMessage("工作流已触发");
        response.setTimestamp(System.currentTimeMillis());
        return response;
    }

    public static WebhookTriggerResponse failure(String error) {
        WebhookTriggerResponse response = new WebhookTriggerResponse();
        response.setSuccess(false);
        response.setMessage("触发失败");
        response.setError(error);
        response.setTimestamp(System.currentTimeMillis());
        return response;
    }

    // Getters and Setters
    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}

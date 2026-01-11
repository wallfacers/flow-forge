package com.workflow.trigger.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.workflow.engine.dispatcher.WorkflowDispatcher;

import java.util.Map;

/**
 * Webhook 执行响应
 * <p>
 * 支持同步/异步两种模式的响应格式
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookExecutionResponse {

    /** 执行模式：sync 或 async */
    private String mode;

    /** 执行 ID（异步模式）或结果 ID（同步模式） */
    private String executionId;

    /** 是否成功 */
    private boolean success;

    /** 错误信息（失败时） */
    private String error;

    /** 工作流 ID */
    private String workflowId;

    /** 工作流名称 */
    private String workflowName;

    /** 执行耗时（同步模式，毫秒） */
    private Long duration;

    /** 执行结果输出（同步模式） */
    private Map<String, Object> output;

    /** 触发器节点 ID */
    private String triggerNodeId;

    /** 触发器类型 */
    private String triggerType;

    /** 时间戳 */
    private long timestamp;

    /**
     * 创建异步响应
     */
    public static WebhookExecutionResponse async(String executionId, String workflowId, String workflowName) {
        WebhookExecutionResponse response = new WebhookExecutionResponse();
        response.mode = "async";
        response.executionId = executionId;
        response.success = true;
        response.workflowId = workflowId;
        response.workflowName = workflowName;
        response.timestamp = System.currentTimeMillis();
        return response;
    }

    /**
     * 创建同步响应（成功）
     */
    public static WebhookExecutionResponse syncSuccess(String executionId, String workflowId,
                                                       Map<String, Object> output, long duration) {
        WebhookExecutionResponse response = new WebhookExecutionResponse();
        response.mode = "sync";
        response.executionId = executionId;
        response.success = true;
        response.workflowId = workflowId;
        response.output = output;
        response.duration = duration;
        response.timestamp = System.currentTimeMillis();
        return response;
    }

    /**
     * 创建错误响应
     */
    public static WebhookExecutionResponse error(String error) {
        WebhookExecutionResponse response = new WebhookExecutionResponse();
        response.success = false;
        response.error = error;
        response.timestamp = System.currentTimeMillis();
        return response;
    }

    /**
     * 从 DispatchResult 创建同步响应
     */
    public static WebhookExecutionResponse fromDispatchResult(WorkflowDispatcher.DispatchResult result,
                                                              String workflowId) {
        if (result.isSuccess()) {
            return syncSuccess(result.getExecutionId(), workflowId, result.getOutputData(), result.getDurationMs());
        } else {
            WebhookExecutionResponse response = new WebhookExecutionResponse();
            response.mode = "sync";
            response.executionId = result.getExecutionId();
            response.success = false;
            response.error = result.getErrorMessage();
            response.workflowId = workflowId;
            response.duration = result.getDurationMs();
            response.timestamp = System.currentTimeMillis();
            return response;
        }
    }

    // Getters and Setters

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
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

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }

    public String getTriggerNodeId() {
        return triggerNodeId;
    }

    public void setTriggerNodeId(String triggerNodeId) {
        this.triggerNodeId = triggerNodeId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

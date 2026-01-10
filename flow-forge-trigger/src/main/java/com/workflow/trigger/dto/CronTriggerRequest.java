package com.workflow.trigger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Cron 触发器创建/更新请求 DTO。
 */
public class CronTriggerRequest {

    @NotBlank(message = "工作流ID不能为空")
    private String workflowId;

    private String workflowName;

    @NotBlank(message = "Cron表达式不能为空")
    @Size(max = 100, message = "Cron表达式长度不能超过100")
    private String cronExpression;

    @Size(max = 50, message = "时区长度不能超过50")
    private String timezone = "Asia/Shanghai";

    private Object inputData;

    @Pattern(regexp = "FIRE|SKIP|ONCE", message = "错失触发策略必须是 FIRE, SKIP 或 ONCE")
    private String misfireStrategy = "FIRE";

    private Boolean enabled = true;

    @Size(max = 500, message = "描述长度不能超过500")
    private String description;

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

    public Object getInputData() {
        return inputData;
    }

    public void setInputData(Object inputData) {
        this.inputData = inputData;
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
}

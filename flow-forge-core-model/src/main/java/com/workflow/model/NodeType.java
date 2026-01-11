package com.workflow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 节点类型枚举
 * <p>
 * 定义工作流引擎支持的所有节点类型
 * </p>
 */
public enum NodeType {

    /**
     * HTTP请求节点 - 执行HTTP请求并返回响应
     */
    HTTP("http", "HTTP Request Node"),

    /**
     * 日志节点 - 输出日志信息
     */
    LOG("log", "Log Output Node"),

    /**
     * 脚本节点 - 执行GraalVM沙箱脚本
     */
    SCRIPT("script", "Script Execution Node"),

    /**
     * 条件分支节点 - 根据SpEL表达式进行分支判断
     */
    IF("if", "Conditional Branch Node"),

    /**
     * 合并节点 - 等待多个输入路径合并
     */
    MERGE("merge", "Merge Node"),

    /**
     * Webhook触发节点 - 注册Webhook回调
     */
    WEBHOOK("webhook", "Webhook Trigger Node"),

    /**
     * 等待节点 - 等待外部回调恢复
     */
    WAIT("wait", "Wait for Callback Node"),

    /**
     * 开始节点 - 工作流入口
     */
    START("start", "Workflow Start Node"),

    /**
     * 结束节点 - 工作流出口
     */
    END("end", "Workflow End Node"),

    /**
     * 触发器节点 - 工作流入口触发器
     * <p>
     * 支持 WEBHOOK、CRON、MANUAL、EVENT 四种类型
     * </p>
     */
    TRIGGER("trigger", "Workflow Trigger Node");

    private final String code;
    private final String description;

    NodeType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static NodeType fromCode(String code) {
        for (NodeType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown node type: " + code);
    }

    public String getDescription() {
        return description;
    }
}

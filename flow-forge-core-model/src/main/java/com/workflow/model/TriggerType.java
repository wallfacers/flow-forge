package com.workflow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 触发器类型枚举
 * <p>
 * 定义工作流入口触发器的类型
 * </p>
 */
public enum TriggerType {

    /**
     * HTTP Webhook 触发器
     * <p>通过 HTTP 请求触发工作流，支持同步/异步模式</p>
     */
    WEBHOOK("webhook", "HTTP Webhook Trigger"),

    /**
     * Cron 定时触发器
     * <p>基于 Cron 表达式的定时触发</p>
     */
    CRON("cron", "Scheduled Cron Trigger"),

    /**
     * 手动触发器
     * <p>通过 API 手动触发工作流执行</p>
     */
    MANUAL("manual", "Manual API Trigger"),

    /**
     * 事件触发器
     * <p>订阅系统事件或其他工作流完成事件触发</p>
     */
    EVENT("event", "Event Bus Trigger");

    private final String code;
    private final String description;

    TriggerType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static TriggerType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (TriggerType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown trigger type: " + code);
    }

    /**
     * 根据代码获取枚举值（忽略大小写）
     */
    public static TriggerType fromCodeIgnoreCase(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (TriggerType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown trigger type: " + code);
    }

    /**
     * 检查代码是否匹配
     */
    public static boolean isValidCode(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        for (TriggerType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }
}

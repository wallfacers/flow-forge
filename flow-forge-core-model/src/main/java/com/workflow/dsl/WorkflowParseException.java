package com.workflow.dsl;

/**
 * 工作流解析异常
 * <p>
 * 当DSL解析失败时抛出此异常
 * </p>
 */
public class WorkflowParseException extends Exception {

    public WorkflowParseException(String message) {
        super(message);
    }

    public WorkflowParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

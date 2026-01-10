package com.workflow.model;

/**
 * 工作流验证异常
 * <p>
 * 当工作流定义验证失败时抛出此异常
 * </p>
 */
public class WorkflowValidationException extends RuntimeException {

    public WorkflowValidationException(String message) {
        super(message);
    }

    public WorkflowValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

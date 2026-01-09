package com.workflow.model;

/**
 * 工作流执行异常
 * <p>
 * 当工作流执行过程中发生错误时抛出此异常
 * </p>
 */
public class WorkflowException extends RuntimeException {

    private final String nodeId;
    private final String workflowId;

    public WorkflowException(String message) {
        super(message);
        this.nodeId = null;
        this.workflowId = null;
    }

    public WorkflowException(String message, Throwable cause) {
        super(message, cause);
        this.nodeId = null;
        this.workflowId = null;
    }

    public WorkflowException(String message, String nodeId) {
        super(message);
        this.nodeId = nodeId;
        this.workflowId = null;
    }

    public WorkflowException(String message, String nodeId, String workflowId) {
        super(message);
        this.nodeId = nodeId;
        this.workflowId = workflowId;
    }

    public WorkflowException(String message, String nodeId, Throwable cause) {
        super(message, cause);
        this.nodeId = nodeId;
        this.workflowId = null;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getWorkflowId() {
        return workflowId;
    }
}

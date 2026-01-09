package com.workflow.model;

/**
 * 执行状态枚举
 */
public enum ExecutionStatus {

    /**
     * 等待执行
     */
    PENDING,

    /**
     * 执行中
     */
    RUNNING,

    /**
     * 执行成功
     */
    SUCCESS,

    /**
     * 执行失败
     */
    FAILED,

    /**
     * 等待回调
     */
    WAITING,

    /**
     * 已取消
     */
    CANCELLED,

    /**
     * 超时
     */
    TIMEOUT
}

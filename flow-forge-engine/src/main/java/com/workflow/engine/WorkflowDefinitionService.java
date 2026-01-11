package com.workflow.engine;

import com.workflow.model.WorkflowDefinition;
import com.workflow.model.WorkflowValidationException;

import java.util.Optional;

/**
 * 工作流定义服务接口
 * <p>
 * 用于获取工作流定义，支持从不同来源加载（数据库、文件、缓存等）。
 * <p>
 * 实现类可以根据需要从以下来源加载工作流定义：
 * <ul>
 *   <li>数据库存储</li>
 *   <li>文件系统</li>
 *   <li>Redis 缓存</li>
 *   <li>远程 API</li>
 * </ul>
 */
public interface WorkflowDefinitionService {

    /**
     * 根据工作流 ID 获取工作流定义
     *
     * @param workflowId 工作流 ID
     * @return 工作流定义
     * @throws WorkflowValidationException 如果工作流不存在或解析失败
     */
    WorkflowDefinition getWorkflowDefinition(String workflowId);

    /**
     * 根据工作流 ID 获取工作流定义（可选）
     *
     * @param workflowId 工作流 ID
     * @return 工作流定义的 Optional
     */
    Optional<WorkflowDefinition> findWorkflowDefinition(String workflowId);

    /**
     * 保存工作流定义
     *
     * @param definition 工作流定义
     */
    void saveWorkflowDefinition(WorkflowDefinition definition);
}

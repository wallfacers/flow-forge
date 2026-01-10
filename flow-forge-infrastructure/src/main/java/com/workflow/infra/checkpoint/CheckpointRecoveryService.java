package com.workflow.infra.checkpoint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.infra.entity.WorkflowExecutionEntity;
import com.workflow.infra.repository.WorkflowExecutionRepository;
import com.workflow.model.*;
import com.workflow.engine.scheduler.InDegreeScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 检查点恢复服务。
 * <p>
 * 负责从数据库加载检查点数据，恢复工作流执行。
 * <p>
 * 恢复流程：
 * <ol>
 *   <li>加载执行历史记录</li>
 *   <li>恢复入度映射</li>
 *   <li>恢复已完成节点结果</li>
 *   <li>创建新的执行记录（标记为恢复执行）</li>
 *   <li>返回可执行的节点列表</li>
 * </ol>
 */
@Service
public class CheckpointRecoveryService {

    private static final Logger logger = LoggerFactory.getLogger(CheckpointRecoveryService.class);

    private final WorkflowExecutionRepository executionRepository;
    private final InDegreeScheduler inDegreeScheduler;
    private final ObjectMapper objectMapper;

    public CheckpointRecoveryService(WorkflowExecutionRepository executionRepository,
                                   InDegreeScheduler inDegreeScheduler,
                                   ObjectMapper objectMapper) {
        this.executionRepository = executionRepository;
        this.inDegreeScheduler = inDegreeScheduler;
        this.objectMapper = objectMapper;
    }

    /**
     * 从检查点恢复执行上下文。
     *
     * @param originalExecutionId 原始执行ID
     * @param newExecutionId     新执行ID
     * @return 恢复的执行上下文
     */
    @Transactional(readOnly = true)
    public RecoveryResult recover(String originalExecutionId, String newExecutionId) {
        // 1. 加载原始执行记录
        WorkflowExecutionEntity original = executionRepository
                .findByExecutionIdAndDeletedAtIsNull(originalExecutionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Original execution not found: " + originalExecutionId));

        logger.info("Recovering execution: originalId={}, newId={}", originalExecutionId, newExecutionId);

        // 2. 解析工作流定义
        WorkflowDefinition definition = parseWorkflowDefinition(original.getWorkflowDefinition());

        // 3. 恢复入度映射
        Map<String, AtomicInteger> inDegreeMap = restoreInDegreeMap(
                original.getCheckpointData(), definition);

        // 4. 恢复已完成节点结果
        ExecutionContext context = restoreExecutionContext(original, definition);

        // 5. 创建新的执行记录
        WorkflowExecutionEntity newExecution = createNewExecution(original, newExecutionId, definition);
        newExecution = executionRepository.save(newExecution);

        // 6. 查找可执行的节点
        List<Node> readyNodes = inDegreeScheduler.findReadyNodes(definition.getNodes(), inDegreeMap);

        // 7. 构建恢复结果
        RecoveryResult result = new RecoveryResult();
        result.setNewExecutionId(newExecutionId);
        result.setNewExecutionEntityId(newExecution.getId());
        result.setDefinition(definition);
        result.setContext(context);
        result.setInDegreeMap(inDegreeMap);
        result.setReadyNodes(readyNodes);
        result.setCompletedNodes(context.getNodeResults().keySet());
        result.setOriginalExecutionId(originalExecutionId);
        result.setResumedFromId(original.getId());

        logger.info("Recovery completed: newExecutionId={}, readyNodes={}, completedNodes={}",
                newExecutionId, readyNodes.size(), context.getNodeResults().size());

        return result;
    }

    /**
     * 快速恢复：仅获取可执行的节点（不创建新执行记录）。
     *
     * @param executionId 执行ID
     * @return 可执行的节点列表
     */
    @Transactional(readOnly = true)
    public List<Node> getReadyNodes(String executionId) {
        WorkflowExecutionEntity execution = executionRepository
                .findByExecutionIdAndDeletedAtIsNull(executionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Execution not found: " + executionId));

        WorkflowDefinition definition = parseWorkflowDefinition(execution.getWorkflowDefinition());
        Map<String, AtomicInteger> inDegreeMap = restoreInDegreeMap(
                execution.getCheckpointData(), definition);

        return inDegreeScheduler.findReadyNodes(definition.getNodes(), inDegreeMap);
    }

    /**
     * 检查执行是否可以恢复。
     *
     * @param executionId 执行ID
     * @return 是否可以恢复
     */
    @Transactional(readOnly = true)
    public boolean canRecover(String executionId) {
        return executionRepository
                .findByExecutionIdAndDeletedAtIsNull(executionId)
                .map(WorkflowExecutionEntity::canResume)
                .orElse(false);
    }

    /**
     * 获取可恢复的执行列表。
     *
     * @param tenantId 租户ID
     * @return 可恢复的执行列表
     */
    @Transactional(readOnly = true)
    public List<WorkflowExecutionEntity> getRecoverableExecutions(String tenantId) {
        return executionRepository.findResumableExecutions(tenantId);
    }

    /**
     * 获取可重试的执行列表。
     *
     * @param tenantId 租户ID
     * @return 可重试的执行列表
     */
    @Transactional(readOnly = true)
    public List<WorkflowExecutionEntity> getRetryableExecutions(String tenantId) {
        return executionRepository.findRetryableExecutions(tenantId);
    }

    /**
     * 解析工作流定义。
     */
    private WorkflowDefinition parseWorkflowDefinition(Map<String, Object> workflowDefMap) {
        try {
            // 使用 WorkflowDslParser 解析
            String json = objectMapper.writeValueAsString(workflowDefMap);
            WorkflowDslParser parser = new WorkflowDslParser();
            return parser.parse(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse workflow definition", e);
        }
    }

    /**
     * 恢复入度映射。
     */
    private Map<String, AtomicInteger> restoreInDegreeMap(Map<String, Object> checkpointData,
                                                           WorkflowDefinition definition) {
        // 先计算初始入度
        Map<String, AtomicInteger> inDegreeMap = inDegreeScheduler.calculateInDegrees(definition);

        // 如果有检查点数据，应用已完成节点的入度减少
        if (checkpointData != null && checkpointData.containsKey("inDegreeSnapshot")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> snapshot = (Map<String, Object>) checkpointData.get("inDegreeSnapshot");

            for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
                String nodeId = entry.getKey();
                Integer inDegree = ((Number) entry.getValue()).intValue();
                AtomicInteger current = inDegreeMap.get(nodeId);
                if (current != null) {
                    current.set(inDegree);
                }
            }

            logger.debug("Restored in-degree map from checkpoint: {}", snapshot);
        }

        return inDegreeMap;
    }

    /**
     * 恢复执行上下文。
     */
    private ExecutionContext restoreExecutionContext(WorkflowExecutionEntity entity,
                                                    WorkflowDefinition definition) {
        ExecutionContext.Builder builder = ExecutionContext.builder()
                .executionId(entity.getExecutionId())
                .workflowId(entity.getWorkflowId())
                .tenantId(entity.getTenantId())
                .status(entity.getStatus())
                .input(entity.getInputData() != null
                        ? new HashMap<>(entity.getInputData())
                        : new HashMap<>());

        // 恢复全局变量
        if (entity.getGlobalVariables() != null) {
            builder.globalVariables(new HashMap<>(entity.getGlobalVariables()));
        }

        ExecutionContext context = builder.build();

        // 恢复已完成节点结果
        if (entity.getCheckpointData() != null
                && entity.getCheckpointData().containsKey("nodeResultRefs")) {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> resultRefs =
                    (Map<String, Map<String, String>>) entity.getCheckpointData().get("nodeResultRefs");

            for (Map.Entry<String, Map<String, String>> entry : resultRefs.entrySet()) {
                String nodeId = entry.getKey();
                Map<String, String> ref = entry.getValue();

                ExecutionStatus status = ExecutionStatus.valueOf(ref.get("status"));
                NodeResult.Builder resultBuilder = NodeResult.builder(nodeId, status);

                if ("true".equals(ref.get("hasOutput"))) {
                    // 这里只标记有输出，实际输出数据可以从节点日志中恢复
                    resultBuilder.output(Collections.singletonMap("_restored", true));
                }

                if (ref.containsKey("error")) {
                    resultBuilder.errorMessage(ref.get("error"));
                }

                context.getNodeResults().put(nodeId, resultBuilder.build());
            }
        }

        return context;
    }

    /**
     * 创建新的执行记录（标记为恢复执行）。
     */
    private WorkflowExecutionEntity createNewExecution(WorkflowExecutionEntity original,
                                                      String newExecutionId,
                                                      WorkflowDefinition definition) {
        WorkflowExecutionEntity newExecution = new WorkflowExecutionEntity();
        newExecution.setExecutionId(newExecutionId);
        newExecution.setWorkflowId(original.getWorkflowId());
        newExecution.setWorkflowName(original.getWorkflowName());
        newExecution.setTenantId(original.getTenantId());
        newExecution.setStatus(ExecutionStatus.RUNNING);
        newExecution.setTotalNodes(original.getTotalNodes());
        newExecution.setCompletedNodes(original.getCompletedNodes());
        newExecution.setFailedNodes(original.getFailedNodes());
        newExecution.setRetryCount(0);
        newExecution.setMaxRetryCount(original.getMaxRetryCount());
        newExecution.setIsResumed(true);
        newExecution.setResumedFromId(original.getId());
        newExecution.setStartedAt(Instant.now());

        // 复制工作流定义
        newExecution.setWorkflowDefinition(new HashMap<>(original.getWorkflowDefinition()));

        // 复制输入数据
        if (original.getInputData() != null) {
            newExecution.setInputData(new HashMap<>(original.getInputData()));
        }

        // 复制全局变量
        if (original.getGlobalVariables() != null) {
            newExecution.setGlobalVariables(new HashMap<>(original.getGlobalVariables()));
        }

        return newExecution;
    }

    /**
     * 恢复结果。
     */
    public static class RecoveryResult {
        private String newExecutionId;
        private UUID newExecutionEntityId;
        private String originalExecutionId;
        private UUID resumedFromId;
        private WorkflowDefinition definition;
        private ExecutionContext context;
        private Map<String, AtomicInteger> inDegreeMap;
        private List<Node> readyNodes;
        private Set<String> completedNodes;

        public String getNewExecutionId() {
            return newExecutionId;
        }

        public void setNewExecutionId(String newExecutionId) {
            this.newExecutionId = newExecutionId;
        }

        public UUID getNewExecutionEntityId() {
            return newExecutionEntityId;
        }

        public void setNewExecutionEntityId(UUID newExecutionEntityId) {
            this.newExecutionEntityId = newExecutionEntityId;
        }

        public String getOriginalExecutionId() {
            return originalExecutionId;
        }

        public void setOriginalExecutionId(String originalExecutionId) {
            this.originalExecutionId = originalExecutionId;
        }

        public UUID getResumedFromId() {
            return resumedFromId;
        }

        public void setResumedFromId(UUID resumedFromId) {
            this.resumedFromId = resumedFromId;
        }

        public WorkflowDefinition getDefinition() {
            return definition;
        }

        public void setDefinition(WorkflowDefinition definition) {
            this.definition = definition;
        }

        public ExecutionContext getContext() {
            return context;
        }

        public void setContext(ExecutionContext context) {
            this.context = context;
        }

        public Map<String, AtomicInteger> getInDegreeMap() {
            return inDegreeMap;
        }

        public void setInDegreeMap(Map<String, AtomicInteger> inDegreeMap) {
            this.inDegreeMap = inDegreeMap;
        }

        public List<Node> readyNodes() {
            return readyNodes;
        }

        public void setReadyNodes(List<Node> readyNodes) {
            this.readyNodes = readyNodes;
        }

        public Set<String> getCompletedNodes() {
            return completedNodes;
        }

        public void setCompletedNodes(Set<String> completedNodes) {
            this.completedNodes = completedNodes;
        }
    }
}

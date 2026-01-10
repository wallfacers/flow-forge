package com.workflow.infra.checkpoint;

import com.workflow.context.VariableResolver;
import com.workflow.infra.entity.NodeExecutionLogEntity;
import com.workflow.infra.entity.WorkflowExecutionEntity;
import com.workflow.infra.repository.NodeExecutionLogRepository;
import com.workflow.infra.repository.WorkflowExecutionRepository;
import com.workflow.model.ExecutionContext;
import com.workflow.model.ExecutionStatus;
import com.workflow.model.NodeResult;
import com.workflow.model.WorkflowDefinition;
import com.workflow.node.NodeExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 检查点服务。
 * <p>
 * 负责在工作流执行过程中保存检查点数据，支持断点续传。
 * <p>
 * 保存的数据包括：
 * <ul>
 *   <li>工作流执行历史（输入、输出、状态）</li>
 *   <li>节点执行日志（每个节点的执行状态）</li>
 *   <li>检查点数据（入度快照、已完成节点列表）</li>
 * </ul>
 */
@Service
public class CheckpointService {

    private static final Logger logger = LoggerFactory.getLogger(CheckpointService.class);

    private final WorkflowExecutionRepository executionRepository;
    private final NodeExecutionLogRepository nodeLogRepository;

    // 内存缓存：executionId -> 检查点数据
    private final Map<String, CheckpointData> checkpointCache = new ConcurrentHashMap<>();

    public CheckpointService(WorkflowExecutionRepository executionRepository,
                            NodeExecutionLogRepository nodeLogRepository) {
        this.executionRepository = executionRepository;
        this.nodeLogRepository = nodeLogRepository;
    }

    /**
     * 创建工作流执行记录。
     *
     * @param definition 工作流定义
     * @param context    执行上下文
     * @return 创建的执行实体
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WorkflowExecutionEntity createExecution(WorkflowDefinition definition, ExecutionContext context) {
        WorkflowExecutionEntity entity = new WorkflowExecutionEntity();
        entity.setExecutionId(context.getExecutionId());
        entity.setWorkflowId(definition.getId());
        entity.setWorkflowName(definition.getName());
        entity.setTenantId(context.getTenantId());
        entity.setStatus(ExecutionStatus.RUNNING);
        entity.setTotalNodes(definition.getNodes().size());
        entity.setCompletedNodes(0);
        entity.setFailedNodes(0);
        entity.setRetryCount(0);
        entity.setMaxRetryCount(3);
        entity.setIsResumed(false);
        entity.setStartedAt(Instant.now());

        // 保存工作流定义
        Map<String, Object> workflowDef = new HashMap<>();
        workflowDef.put("id", definition.getId());
        workflowDef.put("name", definition.getName());
        workflowDef.put("nodes", definition.getNodes());
        workflowDef.put("edges", definition.getEdges());
        entity.setWorkflowDefinition(workflowDef);

        // 保存输入数据
        entity.setInputData(context.getInput());

        // 保存全局变量
        if (context.getGlobalVariables() != null) {
            entity.setGlobalVariables(new HashMap<>(context.getGlobalVariables()));
        }

        WorkflowExecutionEntity saved = executionRepository.save(entity);
        logger.debug("Created execution record: executionId={}, id={}",
                context.getExecutionId(), saved.getId());

        return saved;
    }

    /**
     * 保存节点执行开始日志。
     *
     * @param executionId    执行ID
     * @param nodeId         节点ID
     * @param nodeName       节点名称
     * @param nodeType       节点类型
     * @param nodeConfig     节点配置
     * @param inputSnapshot  输入快照
     * @return 创建的日志实体
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NodeExecutionLogEntity saveNodeStart(String executionId, String nodeId, String nodeName,
                                               String nodeType, Map<String, Object> nodeConfig,
                                               Map<String, Object> inputSnapshot) {
        // 先查找执行记录获取UUID
        WorkflowExecutionEntity execution = executionRepository
                .findByExecutionIdAndDeletedAtIsNull(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        NodeExecutionLogEntity log = new NodeExecutionLogEntity();
        log.setExecutionId(execution.getId());
        log.setExecutionIdStr(executionId);
        log.setNodeId(nodeId);
        log.setNodeName(nodeName);
        log.setNodeType(parseNodeType(nodeType));
        log.setStatus(NodeExecutionLogEntity.NodeExecutionStatus.RUNNING);
        log.setRetryCount(0);
        log.setStartedAt(Instant.now());
        log.setNodeConfig(nodeConfig);
        log.setInputSnapshot(inputSnapshot);

        NodeExecutionLogEntity saved = nodeLogRepository.save(log);
        logger.debug("Saved node start log: executionId={}, nodeId={}", executionId, nodeId);

        return saved;
    }

    /**
     * 保存节点执行完成日志。
     *
     * @param executionId  执行ID
     * @param nodeId       节点ID
     * @param result       执行结果
     * @param inDegreeSnapshot 节点执行完成时的入度快照
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveNodeComplete(String executionId, String nodeId, NodeResult result,
                                 Map<String, Integer> inDegreeSnapshot) {
        NodeExecutionLogEntity log = nodeLogRepository
                .findByExecutionIdStrAndNodeIdAndDeletedAtIsNull(executionId, nodeId)
                .orElse(null);

        if (log != null) {
            if (result.isSuccess()) {
                log.setStatus(NodeExecutionLogEntity.NodeExecutionStatus.COMPLETED);
                log.setOutputData(result.getOutput());
            } else {
                log.setStatus(NodeExecutionLogEntity.NodeExecutionStatus.FAILED);
                log.setErrorMessage(result.getErrorMessage());
                log.setErrorStackTrace(result.getErrorStackTrace());
            }
            log.setCompletedAt(Instant.now());
            if (log.getStartedAt() != null) {
                log.setDurationMs(log.getCompletedAt().toEpochMilli() - log.getStartedAt().toEpochMilli());
            }
            nodeLogRepository.save(log);
        }

        // 更新执行历史
        WorkflowExecutionEntity execution = executionRepository
                .findByExecutionIdAndDeletedAtIsNull(executionId)
                .orElse(null);

        if (execution != null) {
            if (result.isSuccess()) {
                execution.incrementCompletedNodes();
            } else {
                execution.incrementFailedNodes();
            }

            // 保存入度快照到检查点数据
            if (inDegreeSnapshot != null && !inDegreeSnapshot.isEmpty()) {
                Map<String, Object> checkpointData = execution.getCheckpointData();
                if (checkpointData == null) {
                    checkpointData = new HashMap<>();
                }
                checkpointData.put("inDegreeSnapshot", new HashMap<>(inDegreeSnapshot));

                // 保存已完成节点列表
                Set<String> completedNodes = getCompletedNodeIds(execution.getId());
                checkpointData.put("completedNodes", new ArrayList<>(completedNodes));

                execution.setCheckpointData(checkpointData);
            }

            executionRepository.save(execution);
        }

        logger.debug("Saved node complete log: executionId={}, nodeId={}, success={}",
                executionId, nodeId, result.isSuccess());
    }

    /**
     * 更新工作流执行状态。
     *
     * @param executionId 执行ID
     * @param status      新状态
     * @param errorMessage 错误消息（可选）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateExecutionStatus(String executionId, ExecutionStatus status, String errorMessage) {
        WorkflowExecutionEntity execution = executionRepository
                .findByExecutionIdAndDeletedAtIsNull(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        execution.setStatus(status);

        if (errorMessage != null) {
            execution.setErrorMessage(errorMessage);
        }

        if (status == ExecutionStatus.COMPLETED || status == ExecutionStatus.FAILED
                || status == ExecutionStatus.CANCELLED) {
            execution.setCompletedAt(Instant.now());
            if (execution.getStartedAt() != null) {
                execution.setDurationMs(execution.getCompletedAt().toEpochMilli()
                        - execution.getStartedAt().toEpochMilli());
            }
        }

        executionRepository.save(execution);
        logger.debug("Updated execution status: executionId={}, status={}", executionId, status);
    }

    /**
     * 设置输出数据。
     *
     * @param executionId 执行ID
     * @param outputData  输出数据
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setOutputData(String executionId, Map<String, Object> outputData) {
        WorkflowExecutionEntity execution = executionRepository
                .findByExecutionIdAndDeletedAtIsNull(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        execution.setOutputData(outputData);
        executionRepository.save(execution);
    }

    /**
     * 保存检查点到数据库和内存缓存。
     *
     * @param executionId    执行ID
     * @param inDegreeMap    当前入度映射
     * @param context        执行上下文
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCheckpoint(String executionId,
                               Map<String, ? extends Number> inDegreeMap,
                               ExecutionContext context) {
        WorkflowExecutionEntity execution = executionRepository
                .findByExecutionIdAndDeletedAtIsNull(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        // 构建检查点数据
        Map<String, Object> checkpointData = new HashMap<>();

        // 入度快照
        Map<String, Integer> inDegreeSnapshot = new HashMap<>();
        for (Map.Entry<String, ? extends Number> entry : inDegreeMap.entrySet()) {
            inDegreeSnapshot.put(entry.getKey(), entry.getValue().intValue());
        }
        checkpointData.put("inDegreeSnapshot", inDegreeSnapshot);

        // 已完成节点列表
        Set<String> completedNodes = context.getNodeResults().keySet();
        checkpointData.put("completedNodes", new ArrayList<>(completedNodes));

        // 节点结果快照（仅保存输出数据的引用，避免保存大对象）
        Map<String, Map<String, String>> nodeResultRefs = new HashMap<>();
        for (Map.Entry<String, NodeResult> entry : context.getNodeResults().entrySet()) {
            Map<String, String> ref = new HashMap<>();
            ref.put("status", entry.getValue().getExecutionStatus().toString());
            ref.put("hasOutput", entry.getValue().getOutput() != null ? "true" : "false");
            if (entry.getValue().getErrorMessage() != null) {
                ref.put("error", entry.getValue().getErrorMessage());
            }
            nodeResultRefs.put(entry.getKey(), ref);
        }
        checkpointData.put("nodeResultRefs", nodeResultRefs);

        execution.setCheckpointData(checkpointData);
        executionRepository.save(execution);

        // 同时保存到内存缓存（快速访问）
        CheckpointData checkpoint = new CheckpointData();
        checkpoint.setExecutionId(executionId);
        checkpoint.setInDegreeSnapshot(inDegreeSnapshot);
        checkpoint.setCompletedNodes(new ArrayList<>(completedNodes));
        checkpoint.setSavedAt(Instant.now());
        checkpointCache.put(executionId, checkpoint);

        logger.debug("Saved checkpoint: executionId={}, completedNodes={}",
                executionId, completedNodes.size());
    }

    /**
     * 获取已完成的节点ID列表。
     *
     * @param executionId 执行记录UUID
     * @return 已完成节点ID列表
     */
    private Set<String> getCompletedNodeIds(UUID executionId) {
        List<String> completedIds = nodeLogRepository.findCompletedNodeIds(executionId);
        return new HashSet<>(completedIds);
    }

    /**
     * 解析节点类型。
     */
    private com.workflow.model.NodeType parseNodeType(String nodeType) {
        if (nodeType == null) {
            return com.workflow.model.NodeType.LOG;
        }
        try {
            return com.workflow.model.NodeType.valueOf(nodeType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return com.workflow.model.NodeType.LOG;
        }
    }

    /**
     * 从内存缓存获取检查点数据。
     *
     * @param executionId 执行ID
     * @return 检查点数据，不存在返回null
     */
    public CheckpointData getCheckpointFromCache(String executionId) {
        return checkpointCache.get(executionId);
    }

    /**
     * 清除内存缓存。
     *
     * @param executionId 执行ID
     */
    public void clearCache(String executionId) {
        checkpointCache.remove(executionId);
    }

    /**
     * 清除所有内存缓存。
     */
    public void clearAllCache() {
        checkpointCache.clear();
    }

    /**
     * 检查点数据。
     */
    public static class CheckpointData {
        private String executionId;
        private Map<String, Integer> inDegreeSnapshot;
        private List<String> completedNodes;
        private Instant savedAt;

        public String getExecutionId() {
            return executionId;
        }

        public void setExecutionId(String executionId) {
            this.executionId = executionId;
        }

        public Map<String, Integer> getInDegreeSnapshot() {
            return inDegreeSnapshot;
        }

        public void setInDegreeSnapshot(Map<String, Integer> inDegreeSnapshot) {
            this.inDegreeSnapshot = inDegreeSnapshot;
        }

        public List<String> getCompletedNodes() {
            return completedNodes;
        }

        public void setCompletedNodes(List<String> completedNodes) {
            this.completedNodes = completedNodes;
        }

        public Instant getSavedAt() {
            return savedAt;
        }

        public void setSavedAt(Instant savedAt) {
            this.savedAt = savedAt;
        }
    }
}

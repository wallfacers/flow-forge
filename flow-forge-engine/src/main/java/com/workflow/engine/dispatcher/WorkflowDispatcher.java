package com.workflow.engine.dispatcher;

import com.workflow.context.VariableResolver;
import com.workflow.infra.checkpoint.CheckpointRecoveryService;
import com.workflow.infra.checkpoint.CheckpointService;
import com.workflow.engine.retry.RetryPolicy;
import com.workflow.engine.scheduler.InDegreeScheduler;
import com.workflow.model.*;
import com.workflow.node.NodeExecutor;
import com.workflow.node.NodeExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * 工作流调度器。
 * <p>
 * 使用 Java 21 虚拟线程实现高并发 DAG 执行。
 * <p>
 * 核心功能：
 * <ul>
 *   <li>基于入度的拓扑调度</li>
 *   <li>虚拟线程并发执行独立节点</li>
 *   <li>自动检查点保存</li>
 *   <li>失败重试机制</li>
 *   <li>断点续传支持</li>
 * </ul>
 */
@Component
public class WorkflowDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowDispatcher.class);

    /**
     * 虚拟线程执行器（用于并发执行节点）
     */
    private final ExecutorService virtualThreadExecutor;

    /**
     * 节点执行器工厂
     */
    private final NodeExecutorFactory executorFactory;

    /**
     * 入度调度器
     */
    private final InDegreeScheduler inDegreeScheduler;

    /**
     * 检查点服务
     */
    private final CheckpointService checkpointService;

    /**
     * 恢复服务
     */
    private final CheckpointRecoveryService recoveryService;

    /**
     * 重试策略
     */
    private final RetryPolicy retryPolicy;

    /**
     * 变量解析器
     */
    private final VariableResolver variableResolver;

    /**
     * 正在运行的执行（executionId -> Future）
     */
    private final ConcurrentHashMap<String, Future<?>> runningExecutions;

    /**
     * 取消标志（executionId -> boolean）
     */
    private final ConcurrentHashMap<String, Boolean> cancellationFlags;

    public WorkflowDispatcher(NodeExecutorFactory executorFactory,
                              InDegreeScheduler inDegreeScheduler,
                              CheckpointService checkpointService,
                              CheckpointRecoveryService recoveryService,
                              RetryPolicy retryPolicy,
                              VariableResolver variableResolver) {
        this.executorFactory = executorFactory;
        this.inDegreeScheduler = inDegreeScheduler;
        this.checkpointService = checkpointService;
        this.recoveryService = recoveryService;
        this.retryPolicy = retryPolicy;
        this.variableResolver = variableResolver;

        // 创建虚拟线程执行器
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.runningExecutions = new ConcurrentHashMap<>();
        this.cancellationFlags = new ConcurrentHashMap<>();

        logger.info("WorkflowDispatcher initialized with virtual thread executor");
    }

    /**
     * 同步执行工作流。
     *
     * @param definition 工作流定义
     * @param input      输入参数
     * @return 执行结果
     */
    public DispatchResult execute(WorkflowDefinition definition, Map<String, Object> input) {
        return execute(definition, input, null);
    }

    /**
     * 同步执行工作流（带回调）。
     *
     * @param definition 工作流定义
     * @param input      输入参数
     * @param callback   完成回调
     * @return 执行结果
     */
    public DispatchResult execute(WorkflowDefinition definition, Map<String, Object> input,
                                 Consumer<DispatchResult> callback) {
        String executionId = generateExecutionId(definition.getId());

        // 创建执行上下文
        ExecutionContext context = createExecutionContext(definition, input, executionId);

        // 创建执行记录
        checkpointService.createExecution(definition, context);

        // 计算初始入度
        Map<String, AtomicInteger> inDegreeMap = inDegreeScheduler.calculateInDegrees(definition);

        // 执行工作流
        DispatchResult result = executeInternal(definition, context, inDegreeMap, null);

        // 更新最终状态
        if (result.isSuccess()) {
            checkpointService.updateExecutionStatus(executionId, ExecutionStatus.COMPLETED, null);
            checkpointService.setOutputData(executionId, result.getOutputData());
        } else {
            checkpointService.updateExecutionStatus(executionId, ExecutionStatus.FAILED, result.getErrorMessage());
        }

        // 执行回调
        if (callback != null) {
            callback.accept(result);
        }

        return result;
    }

    /**
     * 异步执行工作流。
     *
     * @param definition 工作流定义
     * @param input      输入参数
     * @return Future，可用于获取执行结果或取消执行
     */
    public Future<DispatchResult> executeAsync(WorkflowDefinition definition, Map<String, Object> input) {
        return executeAsync(definition, input, null);
    }

    /**
     * 异步执行工作流（带回调）。
     *
     * @param definition 工作流定义
     * @param input      输入参数
     * @param callback   完成回调
     * @return Future，可用于获取执行结果或取消执行
     */
    public Future<DispatchResult> executeAsync(WorkflowDefinition definition, Map<String, Object> input,
                                              Consumer<DispatchResult> callback) {
        String executionId = generateExecutionId(definition.getId());

        // 创建执行上下文
        ExecutionContext context = createExecutionContext(definition, input, executionId);

        // 创建执行记录
        checkpointService.createExecution(definition, context);

        // 计算初始入度
        Map<String, AtomicInteger> inDegreeMap = inDegreeScheduler.calculateInDegrees(definition);

        // 提交异步任务
        Future<DispatchResult> future = virtualThreadExecutor.submit(() -> {
            try {
                DispatchResult result = executeInternal(definition, context, inDegreeMap, executionId);

                // 更新最终状态
                if (result.isSuccess()) {
                    checkpointService.updateExecutionStatus(executionId, ExecutionStatus.COMPLETED, null);
                    checkpointService.setOutputData(executionId, result.getOutputData());
                } else {
                    checkpointService.updateExecutionStatus(executionId, ExecutionStatus.FAILED, result.getErrorMessage());
                }

                // 执行回调
                if (callback != null) {
                    callback.accept(result);
                }

                return result;
            } finally {
                runningExecutions.remove(executionId);
                cancellationFlags.remove(executionId);
            }
        });

        runningExecutions.put(executionId, future);

        return future;
    }

    /**
     * 恢复执行（断点续传）。
     *
     * @param originalExecutionId 原始执行ID
     * @return 新的执行结果
     */
    public DispatchResult resume(String originalExecutionId) {
        return resume(originalExecutionId, null);
    }

    /**
     * 恢复执行（断点续传，带回调）。
     *
     * @param originalExecutionId 原始执行ID
     * @param callback            完成回调
     * @return 新的执行结果
     */
    public DispatchResult resume(String originalExecutionId, Consumer<DispatchResult> callback) {
        // 检查是否可以恢复
        if (!recoveryService.canRecover(originalExecutionId)) {
            throw new IllegalArgumentException("Execution cannot be resumed: " + originalExecutionId);
        }

        // 恢复执行状态
        String newExecutionId = generateExecutionId(originalExecutionId + "-resumed");
        CheckpointRecoveryService.RecoveryResult recovery = recoveryService.recover(originalExecutionId, newExecutionId);

        // 执行工作流
        DispatchResult result = executeInternal(
                recovery.getDefinition(),
                recovery.getContext(),
                recovery.getInDegreeMap(),
                newExecutionId);

        // 更新最终状态
        if (result.isSuccess()) {
            checkpointService.updateExecutionStatus(newExecutionId, ExecutionStatus.COMPLETED, null);
            checkpointService.setOutputData(newExecutionId, result.getOutputData());
        } else {
            checkpointService.updateExecutionStatus(newExecutionId, ExecutionStatus.FAILED, result.getErrorMessage());
        }

        // 执行回调
        if (callback != null) {
            callback.accept(result);
        }

        return result;
    }

    /**
     * 异步恢复执行。
     *
     * @param originalExecutionId 原始执行ID
     * @return Future
     */
    public Future<DispatchResult> resumeAsync(String originalExecutionId) {
        return resumeAsync(originalExecutionId, null);
    }

    /**
     * 异步恢复执行（带回调）。
     *
     * @param originalExecutionId 原始执行ID
     * @param callback            完成回调
     * @return Future
     */
    public Future<DispatchResult> resumeAsync(String originalExecutionId, Consumer<DispatchResult> callback) {
        // 检查是否可以恢复
        if (!recoveryService.canRecover(originalExecutionId)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Execution cannot be resumed: " + originalExecutionId));
        }

        return virtualThreadExecutor.submit(() -> resume(originalExecutionId, callback));
    }

    /**
     * 取消执行。
     *
     * @param executionId 执行ID
     * @return 是否成功取消
     */
    public boolean cancel(String executionId) {
        cancellationFlags.put(executionId, true);

        Future<?> future = runningExecutions.get(executionId);
        if (future != null && !future.isDone()) {
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                checkpointService.updateExecutionStatus(executionId, ExecutionStatus.CANCELLED, "Cancelled by user");
                runningExecutions.remove(executionId);
                logger.info("Execution cancelled: {}", executionId);
            }
            return cancelled;
        }

        return false;
    }

    /**
     * 检查执行是否完成。
     *
     * @param executionId 执行ID
     * @return 是否完成
     */
    public boolean isCompleted(String executionId) {
        Future<?> future = runningExecutions.get(executionId);
        return future == null || future.isDone();
    }

    /**
     * 获取正在执行的执行ID列表。
     *
     * @return 执行ID列表
     */
    public Set<String> getRunningExecutions() {
        return new HashSet<>(runningExecutions.keySet());
    }

    /**
     * 关闭调度器。
     */
    public void shutdown() {
        logger.info("Shutting down WorkflowDispatcher...");
        virtualThreadExecutor.shutdown();
        try {
            if (!virtualThreadExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("Forcing shutdown after timeout");
                virtualThreadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            virtualThreadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("WorkflowDispatcher shutdown complete");
    }

    // ========== 内部方法 ==========

    /**
     * 内部执行逻辑。
     */
    private DispatchResult executeInternal(WorkflowDefinition definition,
                                          ExecutionContext context,
                                          Map<String, AtomicInteger> inDegreeMap,
                                          String executionId) {
        Instant startTime = Instant.now();
        List<Node> allNodes = definition.getNodes();

        logger.info("Starting execution: executionId={}, workflowId={}, totalNodes={}",
                context.getExecutionId(), definition.getId(), allNodes.size());

        try {
            // 查找初始可执行节点（入度为0）
            List<Node> readyNodes = inDegreeScheduler.findReadyNodes(allNodes, inDegreeMap);

            // 使用虚拟线程并发执行节点
            List<CompletableFuture<NodeResult>> futures = new ArrayList<>();

            for (Node node : readyNodes) {
                CompletableFuture<NodeResult> future = executeNodeAsync(
                        node, definition, context, inDegreeMap, executionId);
                futures.add(future);
            }

            // 等待所有节点完成
            CompletableFuture<Void> allOf = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));

            allOf.join();

            // 收集结果
            Map<String, NodeResult> finalResults = context.getNodeResults();
            boolean allSuccess = finalResults.values().stream().allMatch(NodeResult::isSuccess);

            if (allSuccess) {
                // 构建输出数据
                Map<String, Object> outputData = buildOutputData(context);

                DispatchResult result = DispatchResult.success(context.getExecutionId(), outputData);
                result.setNodeResults(new HashMap<>(finalResults));
                result.setDurationMs(Duration.between(startTime, Instant.now()).toMillis());

                logger.info("Execution completed successfully: executionId={}, duration={}ms",
                        context.getExecutionId(), result.getDurationMs());

                return result;
            } else {
                // 收集错误信息
                List<String> errors = finalResults.values().stream()
                        .filter(r -> !r.isSuccess())
                        .map(r -> r.getNodeId() + ": " + r.getErrorMessage())
                        .toList();

                DispatchResult result = DispatchResult.failure(context.getExecutionId(),
                        "Some nodes failed: " + String.join("; ", errors));
                result.setNodeResults(new HashMap<>(finalResults));
                result.setDurationMs(Duration.between(startTime, Instant.now()).toMillis());

                logger.warn("Execution completed with failures: executionId={}, failedNodes={}",
                        context.getExecutionId(), errors.size());

                return result;
            }

        } catch (Exception e) {
            logger.error("Execution failed: executionId={}", context.getExecutionId(), e);

            DispatchResult result = DispatchResult.failure(context.getExecutionId(),
                    "Execution failed: " + e.getMessage());
            result.setDurationMs(Duration.between(startTime, Instant.now()).toMillis());

            return result;
        }
    }

    /**
     * 异步执行节点（使用虚拟线程）。
     */
    private CompletableFuture<NodeResult> executeNodeAsync(Node node,
                                                          WorkflowDefinition definition,
                                                          ExecutionContext context,
                                                          Map<String, AtomicInteger> inDegreeMap,
                                                          String executionId) {
        return CompletableFuture.supplyAsync(() -> {
            // 检查取消标志
            if (Boolean.TRUE.equals(cancellationFlags.get(executionId))) {
                return NodeResult.failure(node.getId(), "Execution cancelled");
            }

            return executeNode(node, definition, context, inDegreeMap, executionId);
        }, virtualThreadExecutor);
    }

    /**
     * 执行单个节点（带重试）。
     */
    private NodeResult executeNode(Node node,
                                  WorkflowDefinition definition,
                                  ExecutionContext context,
                                  Map<String, AtomicInteger> inDegreeMap,
                                  String executionId) {
        // 保存节点开始日志
        checkpointService.saveNodeStart(
                executionId,
                node.getId(),
                node.getName(),
                node.getType().toString(),
                node.getConfig(),
                context.getInput()
        );

        // 获取节点执行器
        NodeExecutor executor = executorFactory.getExecutor(node.getType());

        // 尝试执行（带重试）
        int attempt = 0;
        NodeResult result = null;

        while (attempt <= node.getRetryCount()) {
            try {
                result = executor.execute(node, context);

                if (result.isSuccess()) {
                    break;
                }

                // 检查是否需要重试
                RetryPolicy.RetryDecision decision = retryPolicy.shouldRetry(node, result, attempt);
                if (!decision.shouldRetry()) {
                    logger.warn("Node execution failed, not retrying: nodeId={}, reason={}",
                            node.getId(), decision.getReason());
                    break;
                }

                logger.info("Retrying node execution: nodeId={}, attempt={}, delay={}ms, reason={}",
                        node.getId(), attempt + 1, decision.getDelayMs(), decision.getReason());

                // 延迟后重试
                if (decision.getDelayMs() > 0) {
                    try {
                        Thread.sleep(decision.getDelayMs());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return NodeResult.failure(node.getId(), "Retry interrupted");
                    }
                }

            } catch (Exception e) {
                logger.error("Node execution exception: nodeId={}, attempt={}",
                        node.getId(), attempt + 1, e);
                result = NodeResult.failure(node.getId(), "Exception: " + e.getMessage());

                // 检查异常类型是否可重试
                if (attempt >= node.getRetryCount()) {
                    break;
                }
            }

            attempt++;
        }

        // 保存节点完成日志
        Map<String, Integer> inDegreeSnapshot = inDegreeScheduler.createSnapshot(inDegreeMap);
        checkpointService.saveNodeComplete(executionId, node.getId(), result, inDegreeSnapshot);

        // 如果节点执行成功，触发后继节点
        if (result.isSuccess()) {
            // 将结果存入上下文
            context.getNodeResults().put(node.getId(), result);

            // 获取出边
            List<Edge> outEdges = definition.getOutEdges(node.getId());

            // 更新后继节点入度，触发就绪的后继节点
            List<String> newReadyNodeIds = inDegreeScheduler.nodeCompleted(node.getId(), outEdges, inDegreeMap);

            if (!newReadyNodeIds.isEmpty()) {
                logger.debug("Node {} completed, triggering {} successors: {}",
                        node.getId(), newReadyNodeIds.size(), newReadyNodeIds);

                // 递归执行后继节点
                for (String successorId : newReadyNodeIds) {
                    Node successor = definition.getNodeById(successorId);
                    if (successor != null) {
                        executeNodeAsync(successor, definition, context, inDegreeMap, executionId);
                    }
                }
            }

            // 保存检查点
            checkpointService.saveCheckpoint(executionId, inDegreeMap, context);
        }

        return result;
    }

    /**
     * 创建执行上下文。
     */
    private ExecutionContext createExecutionContext(WorkflowDefinition definition,
                                                   Map<String, Object> input,
                                                   String executionId) {
        return ExecutionContext.builder()
                .executionId(executionId)
                .workflowId(definition.getId())
                .tenantId(definition.getTenantId() != null ? definition.getTenantId() : "default")
                .status(ExecutionStatus.RUNNING)
                .input(input != null ? new HashMap<>(input) : new HashMap<>())
                .globalVariables(definition.getGlobalVariables() != null
                        ? new HashMap<>(definition.getGlobalVariables())
                        : new HashMap<>())
                .build();
    }

    /**
     * 构建输出数据。
     */
    private Map<String, Object> buildOutputData(ExecutionContext context) {
        Map<String, Object> output = new HashMap<>();

        // 添加系统变量
        Map<String, Object> system = new HashMap<>();
        system.put("executionId", context.getExecutionId());
        system.put("workflowId", context.getWorkflowId());
        system.put("tenantId", context.getTenantId());
        system.put("status", context.getStatus().toString());
        system.put("currentTime", System.currentTimeMillis());
        output.put("system", system);

        // 添加所有节点结果的引用
        Map<String, String> nodeResults = new HashMap<>();
        for (Map.Entry<String, NodeResult> entry : context.getNodeResults().entrySet()) {
            String status = entry.getValue().getExecutionStatus().toString();
            String hasOutput = entry.getValue().getOutput() != null ? "true" : "false";
            nodeResults.put(entry.getKey(), status + " (output: " + hasOutput + ")");
        }
        output.put("nodeResults", nodeResults);

        return output;
    }

    /**
     * 生成执行ID。
     */
    private String generateExecutionId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 调度结果。
     */
    public static class DispatchResult {
        private final String executionId;
        private final boolean success;
        private final String errorMessage;
        private Map<String, Object> outputData;
        private Map<String, NodeResult> nodeResults;
        private long durationMs;

        private DispatchResult(String executionId, boolean success, String errorMessage) {
            this.executionId = executionId;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static DispatchResult success(String executionId, Map<String, Object> outputData) {
            return new DispatchResult(executionId, true, null);
        }

        public static DispatchResult failure(String executionId, String errorMessage) {
            return new DispatchResult(executionId, false, errorMessage);
        }

        public String getExecutionId() {
            return executionId;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public Map<String, Object> getOutputData() {
            return outputData;
        }

        public void setOutputData(Map<String, Object> outputData) {
            this.outputData = outputData;
        }

        public Map<String, NodeResult> getNodeResults() {
            return nodeResults;
        }

        public void setNodeResults(Map<String, NodeResult> nodeResults) {
            this.nodeResults = nodeResults;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }
    }
}

package com.workflow.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流执行上下文
 * <p>
 * 保存工作流执行过程中的所有节点结果和全局变量
 * 支持JSONPath变量引用和断点续传
 * </p>
 */
@Data
@Builder
public class ExecutionContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 执行实例ID（UUID）
     */
    private String executionId;

    /**
     * 工作流定义ID
     */
    private String workflowId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 执行状态
     */
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.PENDING;

    /**
     * 当前执行的节点ID
     */
    private String currentNodeId;

    /**
     * 节点执行结果映射
     * key: nodeId, value: NodeResult
     */
    @Builder.Default
    private Map<String, NodeResult> nodeResults = new ConcurrentHashMap<>();

    /**
     * 全局变量
     */
    @Builder.Default
    private Map<String, Object> globalVariables = new ConcurrentHashMap<>();

    /**
     * 输入参数（工作流启动时传入）
     */
    @Builder.Default
    private Map<String, Object> input = new HashMap<>();

    /**
     * 工作流级别配置
     */
    @Builder.Default
    private Map<String, Object> workflowConfig = new HashMap<>();

    /**
     * 开始时间
     */
    private Instant startTime;

    /**
     * 结束时间
     */
    private Instant endTime;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 是否启用断点续传
     */
    @Builder.Default
    private boolean checkpointEnabled = true;

    /**
     * 当前入度映射（用于断点续传恢复）
     * key: nodeId, value: current in-degree
     */
    @Builder.Default
    private Map<String, Integer> inDegreeSnapshot = new HashMap<>();

    /**
     * 已完成的节点集合（用于断点续传恢复）
     */
    @Builder.Default
    private Set<String> completedNodes = new HashSet<>();

    /**
     * 添加节点执行结果
     *
     * @param nodeId 节点ID
     * @param result 执行结果
     */
    public void appendResult(String nodeId, NodeResult result) {
        if (nodeResults == null) {
            nodeResults = new ConcurrentHashMap<>();
        }
        nodeResults.put(nodeId, result);
        completedNodes.add(nodeId);
    }

    /**
     * 获取节点执行结果
     *
     * @param nodeId 节点ID
     * @return 执行结果，不存在返回null
     */
    public NodeResult getNodeResult(String nodeId) {
        return nodeResults != null ? nodeResults.get(nodeId) : null;
    }

    /**
     * 解析变量引用
     * <p>
     * 支持以下格式：
     * - {{nodeId}} 获取节点完整结果
     * - {{nodeId.output}} 获取节点output字段
     * - {{nodeId.output.data}} 使用JSONPath获取嵌套值
     * - {{global.varName}} 获取全局变量
     * </p>
     *
     * @param expression 变量表达式
     * @return 解析后的值，解析失败返回null
     */
    public Object resolveVariable(String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }

        // 移除{{ }}包装
        String cleanExpr = expression.trim();
        if (cleanExpr.startsWith("{{") && cleanExpr.endsWith("}}")) {
            cleanExpr = cleanExpr.substring(2, cleanExpr.length() - 2).trim();
        }

        // 处理全局变量引用
        if (cleanExpr.startsWith("global.")) {
            String varName = cleanExpr.substring(7);
            return globalVariables != null ? globalVariables.get(varName) : null;
        }

        // 处理输入参数引用
        if (cleanExpr.startsWith("input.")) {
            String key = cleanExpr.substring(6);
            return input != null ? input.get(key) : null;
        }

        // 处理节点结果引用
        int dotIndex = cleanExpr.indexOf('.');
        if (dotIndex > 0) {
            String nodeId = cleanExpr.substring(0, dotIndex);
            String path = cleanExpr.substring(dotIndex + 1);

            NodeResult result = getNodeResult(nodeId);
            if (result != null && result.getOutput() != null) {
                return resolveByPath(result.getOutput(), path);
            }
            return null;
        }

        // 直接使用节点ID获取整个结果
        return getNodeResult(cleanExpr);
    }

    /**
     * 通过JSONPath风格的路径获取值
     *
     * @param data 数据对象
     * @param path 路径（如: output.data.userId）
     * @return 解析后的值
     */
    private Object resolveByPath(Object data, String path) {
        if (data == null || path == null || path.isBlank()) {
            return data;
        }

        String[] parts = path.split("\\.");
        Object current = data;

        for (String part : parts) {
            if (current == null) {
                return null;
            }

            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else if (current instanceof List) {
                try {
                    int index = Integer.parseInt(part);
                    current = ((List<?>) current).get(index);
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    return null;
                }
            } else {
                // 尝试通过反射获取属性
                try {
                    java.lang.reflect.Field field = current.getClass().getDeclaredField(part);
                    field.setAccessible(true);
                    current = field.get(current);
                } catch (Exception e) {
                    return null;
                }
            }
        }

        return current;
    }

    /**
     * 设置全局变量
     *
     * @param key   变量名
     * @param value 变量值
     */
    public void setGlobalVariable(String key, Object value) {
        if (globalVariables == null) {
            globalVariables = new ConcurrentHashMap<>();
        }
        globalVariables.put(key, value);
    }

    /**
     * 获取全局变量
     *
     * @param key 变量名
     * @return 变量值
     */
    public Object getGlobalVariable(String key) {
        return globalVariables != null ? globalVariables.get(key) : null;
    }

    /**
     * 节点是否已完成
     *
     * @param nodeId 节点ID
     * @return true表示已完成
     */
    public boolean isNodeCompleted(String nodeId) {
        return completedNodes != null && completedNodes.contains(nodeId);
    }

    /**
     * 判断是否有节点失败
     *
     * @return true表示有节点失败
     */
    public boolean hasFailedNode() {
        if (nodeResults == null) {
            return false;
        }
        return nodeResults.values().stream()
                .anyMatch(NodeResult::isFailed);
    }

    /**
     * 获取失败的节点结果
     *
     * @return 失败的节点结果，无失败返回null
     */
    public NodeResult getFirstFailedResult() {
        if (nodeResults == null) {
            return null;
        }
        return nodeResults.values().stream()
                .filter(NodeResult::isFailed)
                .findFirst()
                .orElse(null);
    }

    /**
     * 标记执行开始
     */
    public void markStart() {
        this.startTime = Instant.now();
        this.status = ExecutionStatus.RUNNING;
    }

    /**
     * 标记执行成功
     */
    public void markSuccess() {
        this.endTime = Instant.now();
        this.status = ExecutionStatus.SUCCESS;
    }

    /**
     * 标记执行失败
     *
     * @param errorMessage 错误信息
     */
    public void markFailure(String errorMessage) {
        this.endTime = Instant.now();
        this.status = ExecutionStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    /**
     * 标记执行失败
     *
     * @param e 异常
     */
    public void markFailure(Exception e) {
        markFailure(e.getMessage());
    }

    /**
     * 创建检查点数据（用于断点续传）
     *
     * @return 检查点数据
     */
    public CheckpointData createCheckpoint() {
        return CheckpointData.builder()
                .executionId(executionId)
                .workflowId(workflowId)
                .tenantId(tenantId)
                .status(status)
                .currentNodeId(currentNodeId)
                .nodeResults(new HashMap<>(nodeResults))
                .globalVariables(new HashMap<>(globalVariables))
                .input(new HashMap<>(input))
                .inDegreeSnapshot(new HashMap<>(inDegreeSnapshot))
                .completedNodes(new HashSet<>(completedNodes))
                .build();
    }

    /**
     * 从检查点数据恢复
     *
     * @param checkpoint 检查点数据
     */
    public void restoreFromCheckpoint(CheckpointData checkpoint) {
        this.executionId = checkpoint.getExecutionId();
        this.workflowId = checkpoint.getWorkflowId();
        this.tenantId = checkpoint.getTenantId();
        this.status = checkpoint.getStatus();
        this.currentNodeId = checkpoint.getCurrentNodeId();
        this.nodeResults = new ConcurrentHashMap<>(checkpoint.getNodeResults());
        this.globalVariables = new ConcurrentHashMap<>(checkpoint.getGlobalVariables());
        this.input = new HashMap<>(checkpoint.getInput());
        this.inDegreeSnapshot = new HashMap<>(checkpoint.getInDegreeSnapshot());
        this.completedNodes = new HashSet<>(checkpoint.getCompletedNodes());
    }

    /**
     * 获取执行耗时（毫秒）
     *
     * @return 耗时，未结束返回null
     */
    public Long getDuration() {
        if (startTime == null) {
            return null;
        }
        Instant end = endTime != null ? endTime : Instant.now();
        return java.time.Duration.between(startTime, end).toMillis();
    }
}

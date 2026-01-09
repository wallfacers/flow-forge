package com.workflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 节点执行结果
 * <p>
 * 封装节点执行后的输出数据、状态和元信息
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeResult {

    /**
     * 节点ID
     */
    private String nodeId;

    /**
     * 执行状态
     */
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.SUCCESS;

    /**
     * 输出数据
     */
    @Builder.Default
    private Map<String, Object> output = new HashMap<>();

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;

    /**
     * 异常堆栈（失败时）
     */
    private String stackTrace;

    /**
     * 开始时间
     */
    private Instant startTime;

    /**
     * 结束时间
     */
    private Instant endTime;

    /**
     * 执行耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 重试次数
     */
    @Builder.Default
    private int retryAttempt = 0;

    /**
     * 是否为大结果（超过阈值需要存MinIO）
     */
    private boolean largeResult;

    /**
     * 大结果存储ID（当largeResult=true时）
     */
    private String blobId;

    /**
     * 创建成功结果
     *
     * @param nodeId 节点ID
     * @param output 输出数据
     * @return 成功结果对象
     */
    public static NodeResult success(String nodeId, Map<String, Object> output) {
        return NodeResult.builder()
                .nodeId(nodeId)
                .status(ExecutionStatus.SUCCESS)
                .output(output)
                .build();
    }

    /**
     * 创建失败结果
     *
     * @param nodeId       节点ID
     * @param errorMessage 错误信息
     * @return 失败结果对象
     */
    public static NodeResult failure(String nodeId, String errorMessage) {
        return NodeResult.builder()
                .nodeId(nodeId)
                .status(ExecutionStatus.FAILED)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 创建失败结果（带异常）
     *
     * @param nodeId 节点ID
     * @param e      异常
     * @return 失败结果对象
     */
    public static NodeResult failure(String nodeId, Exception e) {
        return NodeResult.builder()
                .nodeId(nodeId)
                .status(ExecutionStatus.FAILED)
                .errorMessage(e.getMessage())
                .stackTrace(getStackTrace(e))
                .build();
    }

    /**
     * 创建等待结果（用于需要回调的节点）
     *
     * @param nodeId      节点ID
     * @param callbackUrl 回调URL
     * @return 等待结果对象
     */
    public static NodeResult waiting(String nodeId, String callbackUrl) {
        Map<String, Object> output = new HashMap<>();
        output.put("callbackUrl", callbackUrl);
        return NodeResult.builder()
                .nodeId(nodeId)
                .status(ExecutionStatus.WAITING)
                .output(output)
                .build();
    }

    /**
     * 标记开始时间
     */
    public void markStart() {
        this.startTime = Instant.now();
    }

    /**
     * 标记结束时间并计算耗时
     */
    public void markEnd() {
        this.endTime = Instant.now();
        if (this.startTime != null) {
            this.durationMs = Duration.between(startTime, endTime).toMillis();
        }
    }

    /**
     * 判断结果是否成功
     *
     * @return true表示成功
     */
    public boolean isSuccess() {
        return status == ExecutionStatus.SUCCESS;
    }

    /**
     * 判断结果是否失败
     *
     * @return true表示失败
     */
    public boolean isFailed() {
        return status == ExecutionStatus.FAILED;
    }

    /**
     * 判断是否为等待状态
     *
     * @return true表示等待中
     */
    public boolean isWaiting() {
        return status == ExecutionStatus.WAITING;
    }

    /**
     * 获取输出值
     *
     * @param key 键
     * @return 值，不存在返回null
     */
    public Object getOutputValue(String key) {
        return output != null ? output.get(key) : null;
    }

    /**
     * 获取输出字符串值
     *
     * @param key 键
     * @return 字符串值
     */
    public String getOutputString(String key) {
        Object value = getOutputValue(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 获取异常堆栈字符串
     */
    private static String getStackTrace(Throwable e) {
        if (e == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("\tat ").append(element).append("\n");
        }
        return sb.toString();
    }

    /**
     * 估算结果大小（字节）
     * <p>
     * 用于判断是否需要存入MinIO
     * </p>
     *
     * @return 估算大小
     */
    public long estimateSize() {
        // 简单估算：假设每个对象平均100字节
        long size = 0;
        if (output != null) {
            size += output.size() * 100L;
        }
        if (errorMessage != null) {
            size += errorMessage.length() * 2L;  // Java char is 2 bytes
        }
        if (stackTrace != null) {
            size += stackTrace.length() * 2L;
        }
        return size;
    }

    /**
     * 判断是否为大结果（超过2MB阈值）
     *
     * @return true表示为大结果
     */
    public boolean isLargeResult() {
        return estimateSize() > 2 * 1024 * 1024;  // 2MB threshold
    }
}

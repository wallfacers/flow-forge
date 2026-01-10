package com.workflow.engine.retry;

import com.workflow.model.Node;
import com.workflow.model.NodeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 重试策略。
 * <p>
 * 支持多种重试策略：
 * <ul>
 *   <li>固定间隔 (Fixed)</li>
 *   <li>线性退避 (Linear)</li>
 *   <li>指数退避 (Exponential)</li>
 *   <li>随机抖动 (Jitter)</li>
 * </ul>
 * <p>
 * 默认使用指数退避策略，避免雷群效应。
 */
@Component
public class RetryPolicy {

    private static final Logger logger = LoggerFactory.getLogger(RetryPolicy.class);

    /**
     * 默认基础间隔：1秒
     */
    private static final long DEFAULT_BASE_INTERVAL_MS = 1000;

    /**
     * 默认最大间隔：60秒
     */
    private static final long DEFAULT_MAX_INTERVAL_MS = 60_000;

    /**
     * 默认最大重试次数：3次
     */
    private static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * 默认重试策略类型
     */
    private static final RetryType DEFAULT_RETRY_TYPE = RetryType.EXPONENTIAL_BACKOFF;

    /**
     * 不需要重试的异常类型
     */
    private static final Set<Class<? extends Throwable>> NON_RETRYABLE_EXCEPTIONS = new HashSet<>();

    static {
        // 非法参数异常不需要重试
        NON_RETRYABLE_EXCEPTIONS.add(IllegalArgumentException.class);
        // 非法状态异常不需要重试
        NON_RETRYABLE_EXCEPTIONS.add(IllegalStateException.class);
        // 中断异常不需要重试
        NON_RETRYABLE_EXCEPTIONS.add(InterruptedException.class);
    }

    /**
     * 重试策略类型
     */
    public enum RetryType {
        /**
         * 固定间隔
         */
        FIXED,
        /**
         * 线性退避：间隔 = baseInterval * (1 + attempt)
         */
        LINEAR_BACKOFF,
        /**
         * 指数退避：间隔 = baseInterval * (2 ^ attempt)
         */
        EXPONENTIAL_BACKOFF,
        /**
         * 指数退避 + 随机抖动（推荐）
         */
        EXPONENTIAL_WITH_JITTER
    }

    /**
     * 重试决策
     */
    public static class RetryDecision {
        private final boolean shouldRetry;
        private final long delayMs;
        private final String reason;

        private RetryDecision(boolean shouldRetry, long delayMs, String reason) {
            this.shouldRetry = shouldRetry;
            this.delayMs = delayMs;
            this.reason = reason;
        }

        public static RetryDecision retry(long delayMs, String reason) {
            return new RetryDecision(true, delayMs, reason);
        }

        public static RetryDecision stop(String reason) {
            return new RetryDecision(false, 0, reason);
        }

        public boolean shouldRetry() {
            return shouldRetry;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * 重试配置
     */
    public static class RetryConfig {
        private final RetryType type;
        private final long baseIntervalMs;
        private final long maxIntervalMs;
        private final int maxRetries;
        private final double jitterFactor;
        private final Set<Class<? extends Throwable>> nonRetryableExceptions;

        private RetryConfig(Builder builder) {
            this.type = builder.type;
            this.baseIntervalMs = builder.baseIntervalMs;
            this.maxIntervalMs = builder.maxIntervalMs;
            this.maxRetries = builder.maxRetries;
            this.jitterFactor = builder.jitterFactor;
            this.nonRetryableExceptions = new HashSet<>(builder.nonRetryableExceptions);
            // 添加默认的非可重试异常
            this.nonRetryableExceptions.addAll(NON_RETRYABLE_EXCEPTIONS);
        }

        public static Builder builder() {
            return new Builder();
        }

        public RetryType getType() {
            return type;
        }

        public long getBaseIntervalMs() {
            return baseIntervalMs;
        }

        public long getMaxIntervalMs() {
            return maxIntervalMs;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public double getJitterFactor() {
            return jitterFactor;
        }

        public Set<Class<? extends Throwable>> getNonRetryableExceptions() {
            return nonRetryableExceptions;
        }

        /**
         * 获取默认配置
         */
        public static RetryConfig defaultConfig() {
            return new Builder()
                    .type(DEFAULT_RETRY_TYPE)
                    .baseIntervalMs(DEFAULT_BASE_INTERVAL_MS)
                    .maxIntervalMs(DEFAULT_MAX_INTERVAL_MS)
                    .maxRetries(DEFAULT_MAX_RETRIES)
                    .jitterFactor(0.1)
                    .build();
        }

        public static class Builder {
            private RetryType type = DEFAULT_RETRY_TYPE;
            private long baseIntervalMs = DEFAULT_BASE_INTERVAL_MS;
            private long maxIntervalMs = DEFAULT_MAX_INTERVAL_MS;
            private int maxRetries = DEFAULT_MAX_RETRIES;
            private double jitterFactor = 0.1;
            private Set<Class<? extends Throwable>> nonRetryableExceptions = new HashSet<>();

            public Builder type(RetryType type) {
                this.type = type;
                return this;
            }

            public Builder baseIntervalMs(long baseIntervalMs) {
                this.baseIntervalMs = baseIntervalMs;
                return this;
            }

            public Builder maxIntervalMs(long maxIntervalMs) {
                this.maxIntervalMs = maxIntervalMs;
                return this;
            }

            public Builder maxRetries(int maxRetries) {
                this.maxRetries = maxRetries;
                return this;
            }

            public Builder jitterFactor(double jitterFactor) {
                this.jitterFactor = jitterFactor;
                return this;
            }

            public Builder addNonRetryableException(Class<? extends Throwable> exceptionClass) {
                this.nonRetryableExceptions.add(exceptionClass);
                return this;
            }

            public RetryConfig build() {
                return new RetryConfig(this);
            }
        }
    }

    /**
     * 判断是否应该重试。
     *
     * @param node      失败的节点
     * @param result    执行结果
     * @param attempt   当前尝试次数（从0开始）
     * @param config    重试配置
     * @return 重试决策
     */
    public RetryDecision shouldRetry(Node node, NodeResult result, int attempt, RetryConfig config) {
        // 检查重试次数上限
        if (attempt >= config.getMaxRetries()) {
            return RetryDecision.stop(
                    String.format("Max retries exceeded: %d >= %d", attempt, config.getMaxRetries()));
        }

        // 检查节点配置的重试次数
        if (node.getRetryCount() > 0 && attempt >= node.getRetryCount()) {
            return RetryDecision.stop(
                    String.format("Node retry count exceeded: %d >= %d", attempt, node.getRetryCount()));
        }

        // 检查异常类型是否可重试
        String errorMsg = result.getErrorMessage();
        if (errorMsg != null) {
            for (Class<? extends Throwable> exceptionClass : config.getNonRetryableExceptions()) {
                if (errorMsg.contains(exceptionClass.getSimpleName())) {
                    return RetryDecision.stop(
                            String.format("Non-retryable exception: %s", exceptionClass.getSimpleName()));
                }
            }
        }

        // 计算延迟时间
        long delayMs = calculateDelay(attempt, config);

        return RetryDecision.retry(delayMs,
                String.format("Retry attempt %d after %dms", attempt + 1, delayMs));
    }

    /**
     * 使用默认配置判断是否应该重试。
     *
     * @param node    失败的节点
     * @param result  执行结果
     * @param attempt 当前尝试次数
     * @return 重试决策
     */
    public RetryDecision shouldRetry(Node node, NodeResult result, int attempt) {
        return shouldRetry(node, result, attempt, RetryConfig.defaultConfig());
    }

    /**
     * 计算重试延迟时间。
     *
     * @param attempt 当前尝试次数
     * @param config  重试配置
     * @return 延迟时间（毫秒）
     */
    public long calculateDelay(int attempt, RetryConfig config) {
        long baseDelay;

        switch (config.getType()) {
            case FIXED:
                baseDelay = config.getBaseIntervalMs();
                break;

            case LINEAR_BACKOFF:
                baseDelay = config.getBaseIntervalMs() * (attempt + 1);
                break;

            case EXPONENTIAL_BACKOFF:
                baseDelay = config.getBaseIntervalMs() * (1L << attempt);
                break;

            case EXPONENTIAL_WITH_JITTER:
                baseDelay = config.getBaseIntervalMs() * (1L << attempt);
                // 添加随机抖动
                double jitter = baseDelay * config.getJitterFactor();
                double randomJitter = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * jitter;
                baseDelay = (long) (baseDelay + randomJitter);
                break;

            default:
                baseDelay = config.getBaseIntervalMs();
        }

        // 限制最大间隔
        return Math.min(baseDelay, config.getMaxIntervalMs());
    }

    /**
     * 计算重试延迟时间（使用默认配置）。
     *
     * @param attempt 当前尝试次数
     * @return 延迟时间（毫秒）
     */
    public long calculateDelay(int attempt) {
        return calculateDelay(attempt, RetryConfig.defaultConfig());
    }

    /**
     * 执行重试延迟。
     *
     * @param delayMs 延迟时间（毫秒）
     */
    public void delay(long delayMs) {
        if (delayMs <= 0) {
            return;
        }

        try {
            logger.debug("Sleeping for {}ms before retry", delayMs);
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Retry delay interrupted", e);
        }
    }

    /**
     * 执行带有重试的操作。
     *
     * @param node      节点
     * @param operation 要执行的操作
     * @param config    重试配置
     * @return 执行结果
     * @throws Exception 最终失败时抛出异常
     */
    public NodeResult executeWithRetry(Node node, RetryableOperation operation, RetryConfig config)
            throws Exception {
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= config.getMaxRetries()) {
            try {
                NodeResult result = operation.execute(attempt);
                if (result.isSuccess()) {
                    if (attempt > 0) {
                        logger.info("Operation succeeded on attempt {}/{} for node {}",
                                attempt + 1, config.getMaxRetries() + 1, node.getId());
                    }
                    return result;
                }

                // 执行失败，检查是否需要重试
                RetryDecision decision = shouldRetry(node, result, attempt, config);
                if (!decision.shouldRetry()) {
                    logger.debug("Operation failed for node {}, not retrying: {}",
                            node.getId(), decision.getReason());
                    return result;
                }

                logger.warn("Operation failed for node {} on attempt {}, retrying: {}",
                        node.getId(), attempt + 1, decision.getReason());

            } catch (Exception e) {
                lastException = e;

                // 检查异常类型是否可重试
                if (config.getNonRetryableExceptions().stream()
                        .anyMatch(clazz -> clazz.isInstance(e))) {
                    throw e;
                }

                if (attempt >= config.getMaxRetries()) {
                    logger.error("Operation failed for node {} after {} attempts, giving up",
                            node.getId(), attempt + 1);
                    throw e;
                }

                long delayMs = calculateDelay(attempt, config);
                logger.warn("Operation failed for node {} on attempt {}, retrying after {}ms: {}",
                        node.getId(), attempt + 1, delayMs, e.getMessage());

                if (delayMs > 0) {
                    delay(delayMs);
                }
            }

            attempt++;
        }

        // 所有重试都失败
        if (lastException != null) {
            throw lastException;
        }

        return NodeResult.failure(node.getId(), "Max retries exceeded");
    }

    /**
     * 执行带有重试的操作（使用默认配置）。
     *
     * @param node      节点
     * @param operation 要执行的操作
     * @return 执行结果
     * @throws Exception 最终失败时抛出异常
     */
    public NodeResult executeWithRetry(Node node, RetryableOperation operation) throws Exception {
        return executeWithRetry(node, operation, RetryConfig.defaultConfig());
    }

    /**
     * 可重试的操作接口
     */
    @FunctionalInterface
    public interface RetryableOperation {
        /**
         * 执行操作
         *
         * @param attempt 当前尝试次数
         * @return 执行结果
         * @throws Exception 执行异常
         */
        NodeResult execute(int attempt) throws Exception;
    }
}

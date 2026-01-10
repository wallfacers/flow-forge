package com.workflow.sandbox;

import com.workflow.model.WorkflowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * GraalSandbox 对象池。
 * <p>
 * 解决 GraalVM Context 与虚拟线程的兼容性问题。
 * 所有沙箱实例在平台线程中预先创建，虚拟线程从池中借用。
 * <p>
 * 使用方法：
 * <pre>{@code
 * GraalSandboxPool pool = GraalSandboxPool.getInstance();
 * try (GraalSandbox sandbox = pool.acquire()) {
 *     sandbox.execute(code, bindings);
 * }
 * }</pre>
 */
public class GraalSandboxPool {

    private static final Logger logger = LoggerFactory.getLogger(GraalSandboxPool.class);

    private static volatile GraalSandboxPool INSTANCE;
    private static final ReentrantLock INIT_LOCK = new ReentrantLock();

    private final BlockingQueue<GraalSandbox> pool;
    private final AtomicInteger totalCreated;
    private final int maxPoolSize;

    /**
     * 默认池大小
     */
    private static final int DEFAULT_POOL_SIZE = 10;

    /**
     * 获取单例实例。
     *
     * @return 沙箱池
     */
    public static GraalSandboxPool getInstance() {
        if (INSTANCE == null) {
            INIT_LOCK.lock();
            try {
                if (INSTANCE == null) {
                    INSTANCE = new GraalSandboxPool(DEFAULT_POOL_SIZE);
                }
            } finally {
                INIT_LOCK.unlock();
            }
        }
        return INSTANCE;
    }

    /**
     * 创建指定大小的沙箱池。
     * <p>
     * 注意：必须在平台线程中创建！
     *
     * @param poolSize 池大小
     */
    public GraalSandboxPool(int poolSize) {
        this.pool = new LinkedBlockingQueue<>(poolSize);
        this.totalCreated = new AtomicInteger(0);
        this.maxPoolSize = poolSize;

        // 预先创建沙箱实例（必须在平台线程中执行）
        for (int i = 0; i < poolSize; i++) {
            try {
                GraalSandbox sandbox = new GraalSandbox();
                pool.offer(sandbox);
                totalCreated.incrementAndGet();
            } catch (Exception e) {
                logger.error("Failed to create sandbox during pool initialization", e);
            }
        }

        logger.info("GraalSandboxPool initialized with {} instances", pool.size());
    }

    /**
     * 从池中获取沙箱实例。
     * <p>
     * 如果池为空且未达到最大限制，会创建新实例。
     *
     * @return 沙箱租用对象
     */
    public SandboxLease acquire() {
        GraalSandbox sandbox = pool.poll();

        // Try to create new instance if pool is empty and under limit
        if (sandbox == null) {
            int current = totalCreated.get();
            // Use CAS loop to atomically increment
            while (current < maxPoolSize) {
                if (totalCreated.compareAndSet(current, current + 1)) {
                    try {
                        sandbox = new GraalSandbox();
                        logger.debug("Created new sandbox, total: {}", totalCreated.get());
                        break;
                    } catch (Exception e) {
                        totalCreated.decrementAndGet();
                        throw new WorkflowException("Failed to create sandbox", e);
                    }
                }
                current = totalCreated.get();
            }
        }

        // If still no instance available, wait for one
        if (sandbox == null) {
            try {
                sandbox = pool.poll(5, TimeUnit.SECONDS);
                if (sandbox == null) {
                    throw new WorkflowException("Timeout waiting for available sandbox instance");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new WorkflowException("Interrupted while waiting for sandbox", e);
            }
        }

        return new SandboxLease(sandbox, this);
    }

    /**
     * 归还沙箱实例到池中。
     *
     * @param sandbox 沙箱实例
     */
    void release(GraalSandbox sandbox) {
        if (sandbox != null && pool.remainingCapacity() > 0) {
            pool.offer(sandbox);
        } else if (sandbox != null) {
            // 池已满，关闭多余的实例
            try {
                sandbox.close();
            } catch (Exception e) {
                logger.warn("Error closing sandbox", e);
            }
        }
    }

    /**
     * 关闭池并释放所有资源。
     */
    public void shutdown() {
        GraalSandbox sandbox;
        while ((sandbox = pool.poll()) != null) {
            try {
                sandbox.close();
            } catch (Exception e) {
                logger.warn("Error closing sandbox during shutdown", e);
            }
        }
        totalCreated.set(0);
        logger.info("GraalSandboxPool shutdown complete");
    }

    /**
     * 获取当前池大小。
     *
     * @return 池大小
     */
    public int getPoolSize() {
        return pool.size();
    }

    /**
     * 沙箱租用对象。
     * <p>
     * 实现 AutoCloseable，支持 try-with-resources 语法。
     */
    public static class SandboxLease implements AutoCloseable {
        private final GraalSandbox sandbox;
        private final GraalSandboxPool pool;

        SandboxLease(GraalSandbox sandbox, GraalSandboxPool pool) {
            this.sandbox = sandbox;
            this.pool = pool;
        }

        /**
         * 获取沙箱实例。
         *
         * @return 沙箱实例
         */
        public GraalSandbox get() {
            return sandbox;
        }

        /**
         * 归还沙箱到池中。
         */
        @Override
        public void close() {
            pool.release(sandbox);
        }
    }
}

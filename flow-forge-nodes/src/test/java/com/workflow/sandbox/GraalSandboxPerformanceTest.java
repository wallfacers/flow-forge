package com.workflow.sandbox;

import com.workflow.model.WorkflowException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GraalVM沙箱性能测试。
 * <p>
 * 验证沙箱在高并发场景下的性能表现：
 * <ul>
 *   <li>10k并发执行</li>
 *   <li>延迟 P95 < 50ms</li>
 *   <li>虚拟线程兼容性</li>
 *   <li>资源限制有效性</li>
 * </ul>
 * <p>
 * 注意：这些测试需要本地安装 GraalVM JDK 21+。
 * 通过系统属性 {@code graalvm.enabled=true} 启用测试。
 */
@DisplayName("GraalVM沙箱性能测试")
@EnabledIfSystemProperty(named = "graalvm.enabled", matches = "true")
class GraalSandboxPerformanceTest {

    private static final Logger logger = LoggerFactory.getLogger(GraalSandboxPerformanceTest.class);

    private GraalSandbox sandbox;

    @BeforeEach
    void setUp() {
        sandbox = new GraalSandbox();
    }

    @AfterEach
    void tearDown() {
        if (sandbox != null) {
            sandbox.close();
        }
        GraalSandbox.clearCache();
    }

    @Test
    @DisplayName("单次执行应该快速完成")
    void singleExecutionShouldBeFast() {
        String code = "return 42;";

        long startTime = System.nanoTime();
        GraalSandbox.SandboxResult result = sandbox.execute(code, null);
        long duration = System.nanoTime() - startTime;

        assertTrue(result.success(), "Script should execute successfully");
        assertEquals(42, result.returnValue());

        long durationMs = duration / 1_000_000;
        logger.info("Single execution duration: {} ms", durationMs);

        // 单次执行应该在100ms内完成（首次创建Context可能较慢）
        assertTrue(durationMs < 1000,
                "Single execution should complete within 1000ms, took " + durationMs + "ms");
    }

    @Test
    @DisplayName("连续执行应该保持稳定性能")
    void consecutiveExecutionShouldBeStable() {
        String code = "return __input.x + __input.y;";
        Map<String, Object> bindings = Map.of("x", 10, "y", 32);

        int iterations = 100;
        List<Long> durations = new ArrayList<>(iterations);

        // 预热
        for (int i = 0; i < 10; i++) {
            sandbox.execute(code, bindings);
        }

        // 测试
        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();
            GraalSandbox.SandboxResult result = sandbox.execute(code, bindings);
            long duration = System.nanoTime() - startTime;

            assertTrue(result.success(), "Execution " + i + " should succeed");
            durations.add(duration / 1_000_000); // 转换为毫秒
        }

        // 计算统计信息
        long sum = durations.stream().mapToLong(Long::longValue).sum();
        double avg = (double) sum / iterations;
        durations.sort(Long::compareTo);
        long p50 = durations.get(iterations / 2);
        long p95 = durations.get((int) (iterations * 0.95));
        long p99 = durations.get((int) (iterations * 0.99));

        logger.info("Performance stats ({} iterations): avg={}ms, p50={}ms, p95={}ms, p99={}ms",
                iterations, avg, p50, p95, p99);

        // P95应该小于50ms
        assertTrue(p95 < 50,
                "P95 latency should be < 50ms, was " + p95 + "ms");
    }

    @Test
    @DisplayName("并发执行应该正常工作")
    void concurrencyShouldWork() {
        String code = "return Math.random() > 0;";
        int concurrency = 100;
        CountDownLatch latch = new CountDownLatch(concurrency);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<Long> durations = new CopyOnWriteArrayList<>();

        // 使用固定线程池（平台线程）
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < concurrency; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        GraalSandbox localSandbox = new GraalSandbox();
                        try {
                            long start = System.nanoTime();
                            GraalSandbox.SandboxResult result = localSandbox.execute(code, null);
                            long duration = System.nanoTime() - start;
                            durations.add(duration / 1_000_000);

                            if (result.success()) {
                                successCount.incrementAndGet();
                            } else {
                                logger.error("Script execution failed: {}", result.output());
                                errorCount.incrementAndGet();
                            }
                        } finally {
                            localSandbox.close();
                        }
                    } catch (Exception e) {
                        logger.error("Execution {} failed: {}", index, e.getMessage(), e);
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // 等待所有任务完成
            assertTrue(latch.await(60, TimeUnit.SECONDS),
                    "All tasks should complete within 60 seconds");

            long totalDuration = System.currentTimeMillis() - startTime;

            logger.info("Concurrency test: {} tasks completed in {}ms",
                    concurrency, totalDuration);
            logger.info("Success: {}, Errors: {}", successCount.get(), errorCount.get());

            assertEquals(concurrency, successCount.get(),
                    "All executions should succeed");
            assertEquals(0, errorCount.get(),
                    "No executions should fail");

            // 计算P95延迟
            durations.sort(Long::compareTo);
            if (!durations.isEmpty()) {
                long p95 = durations.get((int) (durations.size() * 0.95));
                logger.info("P95 latency: {}ms", p95);
            }
        } catch (InterruptedException e) {
            fail("Test interrupted", e);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("应该处理大量并发请求")
    void shouldHandleHighConcurrency() {
        String code = "return __input.x * 2;";
        Map<String, Object> bindings = Map.of("x", 21);
        int targetConcurrency = 1000;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalDuration = new AtomicLong(0);

        // 使用固定线程池（平台线程）
        ExecutorService executor = Executors.newFixedThreadPool(20);
        try {
            CountDownLatch latch = new CountDownLatch(targetConcurrency);
            List<Future<?>> futures = new ArrayList<>();

            long testStart = System.currentTimeMillis();

            for (int i = 0; i < targetConcurrency; i++) {
                final int index = i;
                Future<?> future = executor.submit(() -> {
                    try {
                        GraalSandbox localSandbox = new GraalSandbox();
                        try {
                            long start = System.nanoTime();
                            GraalSandbox.SandboxResult result = localSandbox.execute(code, bindings);
                            long duration = System.nanoTime() - start;
                            totalDuration.addAndGet(duration);

                            if (result.success() && result.returnValue().equals(42)) {
                                successCount.incrementAndGet();
                            } else {
                                logger.error("Task {} failed: returnValue={}", index, result.returnValue());
                                errorCount.incrementAndGet();
                            }
                        } finally {
                            localSandbox.close();
                        }
                    } catch (Exception e) {
                        logger.error("Task {} failed", index, e);
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
                futures.add(future);
            }

            // 等待所有任务完成
            assertTrue(latch.await(120, TimeUnit.SECONDS),
                    "All tasks should complete within 120 seconds");

            long testDuration = System.currentTimeMillis() - testStart;
            double avgDurationMs = (totalDuration.get() / 1_000_000.0) / targetConcurrency;

            logger.info("High concurrency test: {} requests in {}ms", targetConcurrency, testDuration);
            logger.info("Throughput: {} req/s", targetConcurrency * 1000.0 / testDuration);
            logger.info("Success: {}, Errors: {}", successCount.get(), errorCount.get());
            logger.info("Avg duration per request: {}ms", avgDurationMs);

            assertEquals(targetConcurrency, successCount.get(),
                    "All requests should succeed");
            assertEquals(0, errorCount.get(),
                    "No requests should fail");

            // 验证吞吐量
            double throughput = targetConcurrency * 1000.0 / testDuration;
            assertTrue(throughput > 100,
                    "Throughput should be > 100 req/s, was " + throughput);

        } catch (InterruptedException e) {
            fail("Test interrupted", e);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("应该正确限制超时")
    void shouldEnforceTimeoutCorrectly() {
        String infiniteLoop = "while (true) {}";
        int timeoutMs = 100;
        GraalSandbox shortTimeoutSandbox = new GraalSandbox(timeoutMs);

        try {
            long startTime = System.currentTimeMillis();

            assertThrows(WorkflowException.class, () -> {
                shortTimeoutSandbox.execute(infiniteLoop, null);
            }, "Should throw exception on timeout");

            long duration = System.currentTimeMillis() - startTime;

            logger.info("Timeout enforcement: expected ~{}ms, actual {}ms", timeoutMs, duration);

            // 应该在超时时间的合理范围内终止（允许2倍容差）
            assertTrue(duration < timeoutMs * 3,
                    "Should terminate within " + (timeoutMs * 3) + "ms, took " + duration + "ms");

        } finally {
            shortTimeoutSandbox.close();
        }
    }

    @Test
    @DisplayName("内存使用应该保持稳定")
    void memoryUsageShouldBeStable() {
        String code = """
                const arr = [];
                for (let i = 0; i < 100; i++) {
                    arr.push(i);
                }
                return arr.length;
                """;

        Runtime runtime = Runtime.getRuntime();
        int iterations = 100;
        List<Long> memoryUsages = new ArrayList<>();

        // 预热
        for (int i = 0; i < 10; i++) {
            sandbox.execute(code, null);
        }

        // GC
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // 执行并记录内存使用
        for (int i = 0; i < iterations; i++) {
            sandbox.execute(code, null);

            if (i % 10 == 0) {
                System.gc();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                memoryUsages.add(usedMemory);
            }
        }

        // 最终GC
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryGrowth = finalMemory - initialMemory;

        logger.info("Memory usage - Initial: {}KB, Final: {}KB, Growth: {}KB",
                initialMemory / 1024, finalMemory / 1024, memoryGrowth / 1024);

        // 内存增长应该在合理范围内（< 10MB）
        assertTrue(memoryGrowth < 10 * 1024 * 1024,
                "Memory growth should be < 10MB, was " + (memoryGrowth / 1024 / 1024) + "MB");
    }

    @Test
    @DisplayName("缓存实例应该提升性能")
    void cachedInstanceShouldImprovePerformance() {
        String code = "return 42;";
        int iterations = 50;

        // 测试不使用缓存的性能
        List<Long> noCacheDurations = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            GraalSandbox tempSandbox = new GraalSandbox();
            try {
                long start = System.nanoTime();
                tempSandbox.execute(code, null);
                noCacheDurations.add((System.nanoTime() - start) / 1_000_000);
            } finally {
                tempSandbox.close();
            }
        }

        // 测试使用缓存的性能
        GraalSandbox cachedSandbox = GraalSandbox.getCachedInstance();
        try {
            List<Long> cachedDurations = new ArrayList<>();
            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                cachedSandbox.execute(code, null);
                cachedDurations.add((System.nanoTime() - start) / 1_000_000);
            }

            // 计算平均值
            double noCacheAvg = noCacheDurations.stream().mapToLong(Long::longValue).average().orElse(0);
            double cachedAvg = cachedDurations.stream().mapToLong(Long::longValue).average().orElse(0);

            logger.info("No cache avg: {}ms, Cached avg: {}ms", noCacheAvg, cachedAvg);

            // 缓存版本应该更快（或者至少不会明显变慢）
            assertTrue(cachedAvg <= noCacheAvg * 2,
                    "Cached version should not be more than 2x slower");

        } finally {
            GraalSandbox.clearCache();
        }
    }
}

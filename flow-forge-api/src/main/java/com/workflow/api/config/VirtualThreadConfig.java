package com.workflow.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 虚拟线程配置
 * <p>
 * 配置 Spring Boot 3.2+ 使用 Java 21 虚拟线程
 * </p>
 *
 * <p>虚拟线程优势：</p>
 * <ul>
 *   <li>轻量级：每个虚拟线程仅占用几 KB 内存</li>
 *   <li>高并发：可创建百万级虚拟线程</li>
 *   <li>阻塞友好：阻塞操作不阻塞底层平台线程</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class VirtualThreadConfig {

    /**
     * 异步任务执行器 - 使用虚拟线程
     * <p>
     * 用于 @Async 注解的方法执行
     * </p>
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Spring MVC 异步执行器 - 使用虚拟线程
     * <p>
     * 用于 Controller 异步返回 (Callable, DeferredResult 等)
     * </p>
     */
    @Bean(name = "mvcTaskExecutor")
    public Executor mvcTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

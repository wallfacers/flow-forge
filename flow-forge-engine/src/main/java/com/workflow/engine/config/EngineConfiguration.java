package com.workflow.engine.config;

import com.workflow.context.VariableResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 引擎模块配置类
 * <p>
 * 注册引擎相关的 Spring Bean
 * </p>
 */
@Configuration
public class EngineConfiguration {

    /**
     * 注册变量解析器 Bean
     */
    @Bean
    public VariableResolver variableResolver() {
        return new VariableResolver();
    }
}

package com.workflow.infra.multitenant;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 多租户配置
 * <p>
 * 注册租户拦截器到Spring MVC
 * </p>
 */
@Configuration
public class TenantConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;

    public TenantConfig(TenantInterceptor tenantInterceptor) {
        this.tenantInterceptor = tenantInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/public/**", "/swagger-ui/**", "/v3/api-docs/**");
    }
}

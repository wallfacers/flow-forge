package com.workflow.infra.multitenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 租户拦截器
 * <p>
 * 从 HTTP 请求头中提取租户ID并设置到 TenantContext
 * 请求完成后自动清理 ThreadLocal，避免内存泄漏
 * </p>
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantInterceptor.class);

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                            @NonNull HttpServletResponse response,
                            @NonNull Object handler) {

        // 从请求头获取租户ID
        String tenantId = request.getHeader(TenantContext.TENANT_HEADER);

        if (tenantId != null && !tenantId.isEmpty()) {
            TenantContext.setTenantId(tenantId);
            log.debug("Set tenant context: {}", tenantId);
        } else {
            // 如果没有提供租户ID，使用默认租户
            TenantContext.setTenantId(TenantContext.DEFAULT_TENANT_ID);
            log.debug("No tenant ID provided, using default tenant: {}", TenantContext.DEFAULT_TENANT_ID);
        }

        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                               @NonNull HttpServletResponse response,
                               @NonNull Object handler,
                               Exception ex) {

        // 请求完成后清理 ThreadLocal
        String tenantId = TenantContext.getTenantId();
        TenantContext.clear();
        log.debug("Cleared tenant context: {}", tenantId);
    }
}

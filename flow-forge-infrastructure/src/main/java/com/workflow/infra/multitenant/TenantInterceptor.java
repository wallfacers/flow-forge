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
 * 从 HTTP 请求头中提取租户ID并设置到 TenantContext。
 * 使用 ScopedValue 适配层（TenantContextHolder）管理请求生命周期中的租户ID。
 * </p>
 * <p>
 * <b>工作原理：</b>
 * <ul>
 *   <li>请求到达：从 X-Tenant-ID 头提取租户ID并存储到 TenantContextHolder</li>
 *   <li>请求处理：TenantContext.getTenantId() 从 Holder 读取租户ID</li>
 *   <li>异步任务：使用 TenantContext.runWithTenant() 绑定 ScopedValue</li>
 *   <li>请求完成：自动清理 Holder 中的租户ID</li>
 * </ul>
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

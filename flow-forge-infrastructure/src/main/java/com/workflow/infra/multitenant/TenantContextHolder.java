package com.workflow.infra.multitenant;

/**
 * 租户上下文持有者。
 * <p>
 * 用于在 Servlet 请求生命周期中持有租户ID，支持 ScopedValue 的作用域绑定。
 * 拦截器在请求开始时设置租户ID，请求结束时清理。
 * </p>
 * <p>
 * <b>内部使用：</b>此类仅供 TenantInterceptor 使用，应用代码应使用 TenantContext。
 * </p>
 */
final class TenantContextHolder {

    /**
     * 存储当前请求的租户ID（用于 ScopedValue 绑定）
     */
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContextHolder() {
        // 私有构造函数，防止实例化
    }

    /**
     * 设置当前租户ID。
     *
     * @param tenantId 租户ID
     */
    static void setTenantId(String tenantId) {
        if (tenantId != null && !tenantId.isEmpty()) {
            CURRENT_TENANT.set(tenantId);
        } else {
            CURRENT_TENANT.set(TenantContext.DEFAULT_TENANT_ID);
        }
    }

    /**
     * 获取当前租户ID。
     *
     * @return 租户ID，可能为 null
     */
    static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    /**
     * 清除当前租户ID。
     */
    static void clear() {
        CURRENT_TENANT.remove();
    }
}

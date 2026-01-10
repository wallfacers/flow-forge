package com.workflow.infra.multitenant;

/**
 * 租户上下文
 * <p>
 * 使用 ThreadLocal 存储当前请求的租户ID，用于多租户数据隔离
 * </p>
 */
public final class TenantContext {

    /**
     * 默认租户ID
     */
    public static final String DEFAULT_TENANT_ID = "default";

    /**
     * ThreadLocal 存储租户ID
     */
    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    /**
     * 请求头中的租户ID字段名
     */
    public static final String TENANT_HEADER = "X-Tenant-ID";

    private TenantContext() {
        // 私有构造函数，防止实例化
    }

    /**
     * 获取当前租户ID
     *
     * @return 租户ID，如果未设置则返回默认租户ID
     */
    public static String getTenantId() {
        String tenantId = TENANT_ID.get();
        return tenantId != null ? tenantId : DEFAULT_TENANT_ID;
    }

    /**
     * 设置当前租户ID
     *
     * @param tenantId 租户ID
     */
    public static void setTenantId(String tenantId) {
        if (tenantId != null && !tenantId.isEmpty()) {
            TENANT_ID.set(tenantId);
        } else {
            TENANT_ID.set(DEFAULT_TENANT_ID);
        }
    }

    /**
     * 清除当前租户ID
     * <p>
     * 请求处理完成后应调用此方法，避免内存泄漏
     * </p>
     */
    public static void clear() {
        TENANT_ID.remove();
    }

    /**
     * 检查是否设置了租户ID
     *
     * @return true表示已设置
     */
    public static boolean hasTenantId() {
        return TENANT_ID.get() != null;
    }

    /**
     * 在指定的租户上下文中执行任务
     *
     * @param tenantId 租户ID
     * @param task     要执行的任务
     */
    public static void runWithTenant(String tenantId, Runnable task) {
        String previousTenantId = TENANT_ID.get();
        try {
            setTenantId(tenantId);
            task.run();
        } finally {
            if (previousTenantId != null) {
                TENANT_ID.set(previousTenantId);
            } else {
                TENANT_ID.remove();
            }
        }
    }

    /**
     * 在指定的租户上下文中执行任务并返回结果
     *
     * @param tenantId 租户ID
     * @param supplier 要执行的任务
     * @param <T>      返回值类型
     * @return 任务执行结果
     */
    public static <T> T supplyWithTenant(String tenantId, java.util.function.Supplier<T> supplier) {
        String previousTenantId = TENANT_ID.get();
        try {
            setTenantId(tenantId);
            return supplier.get();
        } finally {
            if (previousTenantId != null) {
                TENANT_ID.set(previousTenantId);
            } else {
                TENANT_ID.remove();
            }
        }
    }
}

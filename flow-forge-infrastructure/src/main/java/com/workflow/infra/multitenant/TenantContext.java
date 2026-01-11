package com.workflow.infra.multitenant;

import java.util.function.Supplier;

/**
 * 租户上下文
 * <p>
 * 使用 Java 21 ScopedValue 存储当前请求的租户ID，用于多租户数据隔离。
 * ScopedValue 相比 ThreadLocal 的优势：
 * <ul>
 *   <li>不可变性：一旦设置不能修改，避免意外修改</li>
 *   <li>自动传播：虚拟线程间自动传递值，无需手动处理</li>
 *   <li>作用域管理：作用域结束自动清理，无需手动 clear()</li>
 *   <li>内存安全：虚拟线程环境下无内存泄漏风险</li>
 * </ul>
 * <p>
 * <b>注意：</b>这是一个预览特性（Preview Feature），需要使用 {@code --enable-preview}
 * 编译和运行选项。
 * </p>
 * <p>
 * <b>架构说明：</b>
 * <ul>
 *   <li>HTTP 请求：TenantInterceptor 设置租户ID → 通过 TenantContextHolder 管理</li>
 *   <li>异步任务：使用 runWithTenant/supplyWithTenant 方法自动传播租户ID</li>
 *   <li>虚拟线程：ScopedValue 自动传播，无需手动处理</li>
 * </ul>
 * </p>
 */
public final class TenantContext {

    /**
     * 默认租户ID
     */
    public static final String DEFAULT_TENANT_ID = "default";

    /**
     * 使用 ScopedValue 存储租户ID（Java 21 预览特性）
     * <p>
     * ScopedValue 绑定到作用域而非线程，在虚拟线程环境中自动传播。
     * </p>
     */
    private static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();

    /**
     * 请求头中的租户ID字段名
     */
    public static final String TENANT_HEADER = "X-Tenant-ID";

    private TenantContext() {
        // 私有构造函数，防止实例化
    }

    /**
     * 获取当前租户ID。
     * <p>
     * 优先从 ScopedValue 获取（用于作用域绑定场景），
     * 如果未绑定则从 TenantContextHolder 获取（用于 HTTP 请求场景）。
     * </p>
     *
     * @return 租户ID，如果未设置则返回默认租户ID
     */
    public static String getTenantId() {
        // 优先从 ScopedValue 获取
        if (TENANT_ID.isBound()) {
            return TENANT_ID.get();
        }
        // 从 Holder 获取（HTTP 请求场景）
        String tenantId = TenantContextHolder.getTenantId();
        return tenantId != null ? tenantId : DEFAULT_TENANT_ID;
    }

    /**
     * 检查是否设置了租户ID
     *
     * @return true 表示已设置
     */
    public static boolean hasTenantId() {
        return TENANT_ID.isBound() || TenantContextHolder.getTenantId() != null;
    }

    /**
     * 在指定的租户上下文中执行任务。
     * <p>
     * 使用 ScopedValue.where() 绑定租户ID到当前作用域，
     * 任务执行完成后自动清理，无需手动调用 clear()。
     * </p>
     * <p>
     * 此方法适用于异步任务、虚拟线程场景，租户ID会自动传播到子线程。
     * </p>
     *
     * @param tenantId 租户ID
     * @param task     要执行的任务
     */
    public static void runWithTenant(String tenantId, Runnable task) {
        String effectiveTenantId = (tenantId != null && !tenantId.isEmpty())
                ? tenantId : DEFAULT_TENANT_ID;
        ScopedValue.where(TENANT_ID, effectiveTenantId).run(task);
    }

    /**
     * 在指定的租户上下文中执行任务并返回结果。
     * <p>
     * 使用 ScopedValue.where() 绑定租户ID到当前作用域，
     * 任务执行完成后自动清理，无需手动调用 clear()。
     * </p>
     * <p>
     * 此方法适用于异步任务、虚拟线程场景，租户ID会自动传播到子线程。
     * </p>
     *
     * @param tenantId 租户ID
     * @param supplier 要执行的任务
     * @param <T>      返回值类型
     * @return 任务执行结果
     */
    public static <T> T supplyWithTenant(String tenantId, Supplier<T> supplier) {
        String effectiveTenantId = (tenantId != null && !tenantId.isEmpty())
                ? tenantId : DEFAULT_TENANT_ID;
        return ScopedValue.where(TENANT_ID, effectiveTenantId).get(supplier::get);
    }

    /**
     * 在指定的租户上下文中执行可能抛出异常的任务。
     *
     * @param tenantId 租户ID
     * @param callable 要执行的任务
     * @param <T>      返回值类型
     * @return 任务执行结果
     * @throws Exception 任务执行时抛出的异常
     */
    public static <T> T callWithTenant(String tenantId, java.util.concurrent.Callable<T> callable) throws Exception {
        String effectiveTenantId = (tenantId != null && !tenantId.isEmpty())
                ? tenantId : DEFAULT_TENANT_ID;
        return ScopedValue.where(TENANT_ID, effectiveTenantId).call(callable::call);
    }

    // ==================== 内部方法（供 TenantInterceptor 使用） ====================

    /**
     * 设置当前租户ID（内部方法）。
     * <p>
     * 此方法仅供 TenantInterceptor 使用，用于在 HTTP 请求开始时设置租户ID。
     * 应用代码应使用 {@link #runWithTenant(String, Runnable)} 方法。
     * </p>
     *
     * @param tenantId 租户ID
     */
    static void setTenantId(String tenantId) {
        TenantContextHolder.setTenantId(tenantId);
    }

    /**
     * 清除当前租户ID（内部方法）。
     * <p>
     * 此方法仅供 TenantInterceptor 使用，用于在 HTTP 请求结束时清理租户ID。
     * </p>
     */
    static void clear() {
        TenantContextHolder.clear();
    }
}

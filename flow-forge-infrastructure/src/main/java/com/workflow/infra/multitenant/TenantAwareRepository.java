package com.workflow.infra.multitenant;

import com.workflow.infra.entity.WorkflowExecutionEntity;
import com.workflow.model.ExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 租户感知的执行历史Repository接口
 * <p>
 * 所有查询方法都会自动使用当前租户上下文进行过滤
 * </p>
 */
public interface TenantAwareRepository {

    /**
     * 创建租户感知的查询代理
     * <p>
     * 使用时需要在实际Repository中注入TenantContext并手动过滤
     * </p>
     */
    class TenantQueryExecutor {

        /**
         * 检查实体是否属于当前租户
         *
         * @param tenantId 实体的租户ID
         * @return true表示属于当前租户
         */
        public static boolean isCurrentTenant(String tenantId) {
            return TenantContext.getTenantId().equals(tenantId);
        }

        /**
         * 获取当前租户ID
         */
        public static String getCurrentTenantId() {
            return TenantContext.getTenantId();
        }

        /**
         * 验证租户访问权限，如果不匹配则抛出异常
         *
         * @param tenantId 实体的租户ID
         * @param resourceId 资源ID（用于错误消息）
         * @throws IllegalStateException 租户不匹配时抛出
         */
        public static void validateTenantAccess(String tenantId, String resourceId) {
            if (!TenantContext.getTenantId().equals(tenantId)) {
                throw new IllegalStateException(
                        "Access denied: resource " + resourceId + " belongs to tenant " + tenantId +
                        ", but current tenant is " + TenantContext.getTenantId());
            }
        }
    }
}

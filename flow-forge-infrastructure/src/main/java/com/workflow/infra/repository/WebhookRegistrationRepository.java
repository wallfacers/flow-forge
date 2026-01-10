package com.workflow.infra.repository;

import com.workflow.infra.entity.WebhookRegistrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Webhook 注册表 Repository。
 * <p>
 * 提供 Webhook 触发器的 CRUD 操作和自定义查询。
 * 支持多租户隔离查询。
 */
@Repository
public interface WebhookRegistrationRepository extends JpaRepository<WebhookRegistrationEntity, UUID> {

    /**
     * 根据 Webhook 路径查找记录（未删除）
     */
    Optional<WebhookRegistrationEntity> findByWebhookPathAndDeletedAtIsNull(String webhookPath);

    /**
     * 根据租户ID和工作流ID查找所有 Webhook（未删除）
     */
    List<WebhookRegistrationEntity> findByTenantIdAndWorkflowIdAndDeletedAtIsNull(
            String tenantId, String workflowId);

    /**
     * 根据租户ID查找所有启用的 Webhook（未删除）
     */
    List<WebhookRegistrationEntity> findByTenantIdAndEnabledTrueAndDeletedAtIsNull(String tenantId);

    /**
     * 根据租户ID查找所有 Webhook（未删除）
     */
    List<WebhookRegistrationEntity> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId);

    /**
     * 根据工作流ID查找所有 Webhook（未删除）
     */
    List<WebhookRegistrationEntity> findByWorkflowIdAndDeletedAtIsNull(String workflowId);

    /**
     * 检查 Webhook 路径是否存在（未删除）
     */
    boolean existsByWebhookPathAndDeletedAtIsNull(String webhookPath);

    /**
     * 检查租户下是否存在指定工作流的 Webhook（未删除）
     */
    boolean existsByTenantIdAndWorkflowIdAndDeletedAtIsNull(String tenantId, String workflowId);

    /**
     * 软删除：根据 ID 标记为已删除
     */
    @Modifying
    @Query("UPDATE WebhookRegistrationEntity w SET w.deletedAt = :deletedAt WHERE w.id = :id")
    int softDeleteById(@Param("id") UUID id, @Param("deletedAt") java.time.Instant deletedAt);

    /**
     * 软删除：根据租户ID和工作流ID标记为已删除
     */
    @Modifying
    @Query("UPDATE WebhookRegistrationEntity w SET w.deletedAt = :deletedAt " +
           "WHERE w.tenantId = :tenantId AND w.workflowId = :workflowId AND w.deletedAt IS NULL")
    int softDeleteByTenantAndWorkflow(@Param("tenantId") String tenantId,
                                       @Param("workflowId") String workflowId,
                                       @Param("deletedAt") java.time.Instant deletedAt);

    /**
     * 查找需要清理的已删除记录
     */
    @Query("SELECT w FROM WebhookRegistrationEntity w WHERE w.deletedAt IS NOT NULL AND w.deletedAt < :beforeDate")
    List<WebhookRegistrationEntity> findDeletedBefore(@Param("beforeDate") java.time.Instant beforeDate);

    /**
     * 清理过期的已删除记录
     */
    @Modifying
    @Query("DELETE FROM WebhookRegistrationEntity w WHERE w.deletedAt IS NOT NULL AND w.deletedAt < :beforeDate")
    int purgeDeletedBefore(@Param("beforeDate") java.time.Instant beforeDate);

    /**
     * 统计租户的 Webhook 数量
     */
    long countByTenantIdAndDeletedAtIsNull(String tenantId);

    /**
     * 统计租户启用的 Webhook 数量
     */
    long countByTenantIdAndEnabledTrueAndDeletedAtIsNull(String tenantId);
}

package com.workflow.infra.repository;

import com.workflow.infra.entity.CronTriggerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cron 触发器配置 Repository。
 * <p>
 * 提供 Cron 触发器的 CRUD 操作和自定义查询。
 * 支持多租户隔离查询。
 */
@Repository
public interface CronTriggerRepository extends JpaRepository<CronTriggerEntity, UUID> {

    /**
     * 根据 PowerJob 任务ID查找记录（未删除）
     */
    Optional<CronTriggerEntity> findByPowerjobJobIdAndDeletedAtIsNull(Long powerjobJobId);

    /**
     * 根据租户ID和工作流ID查找 Cron 触发器（未删除）
     */
    Optional<CronTriggerEntity> findByTenantIdAndWorkflowIdAndDeletedAtIsNull(
            String tenantId, String workflowId);

    /**
     * 根据租户ID查找所有启用的 Cron 触发器（未删除）
     */
    List<CronTriggerEntity> findByTenantIdAndEnabledTrueAndDeletedAtIsNull(String tenantId);

    /**
     * 根据租户ID查找所有 Cron 触发器（未删除）
     */
    List<CronTriggerEntity> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId);

    /**
     * 查找所有启用的 Cron 触发器（未删除）
     */
    List<CronTriggerEntity> findByEnabledTrueAndDeletedAtIsNull();

    /**
     * 检查租户下是否存在指定工作流的 Cron 触发器（未删除）
     */
    boolean existsByTenantIdAndWorkflowIdAndDeletedAtIsNull(String tenantId, String workflowId);

    /**
     * 检查 PowerJob 任务ID是否存在
     */
    boolean existsByPowerjobJobIdAndDeletedAtIsNull(Long powerjobJobId);

    /**
     * 软删除：根据 ID 标记为已删除
     */
    @Modifying
    @Query("UPDATE CronTriggerEntity c SET c.deletedAt = :deletedAt WHERE c.id = :id")
    int softDeleteById(@Param("id") UUID id, @Param("deletedAt") Instant deletedAt);

    /**
     * 软删除：根据租户ID和工作流ID标记为已删除
     */
    @Modifying
    @Query("UPDATE CronTriggerEntity c SET c.deletedAt = :deletedAt " +
           "WHERE c.tenantId = :tenantId AND c.workflowId = :workflowId AND c.deletedAt IS NULL")
    int softDeleteByTenantAndWorkflow(@Param("tenantId") String tenantId,
                                      @Param("workflowId") String workflowId,
                                      @Param("deletedAt") Instant deletedAt);

    /**
     * 软删除：根据 PowerJob 任务ID标记为已删除
     */
    @Modifying
    @Query("UPDATE CronTriggerEntity c SET c.enabled = false, c.deletedAt = :deletedAt " +
           "WHERE c.powerjobJobId = :powerjobJobId AND c.deletedAt IS NULL")
    int softDeleteByPowerjobJobId(@Param("powerjobJobId") Long powerjobJobId, @Param("deletedAt") Instant deletedAt);

    /**
     * 查找需要清理的已删除记录
     */
    @Query("SELECT c FROM CronTriggerEntity c WHERE c.deletedAt IS NOT NULL AND c.deletedAt < :beforeDate")
    List<CronTriggerEntity> findDeletedBefore(@Param("beforeDate") Instant beforeDate);

    /**
     * 清理过期的已删除记录
     */
    @Modifying
    @Query("DELETE FROM CronTriggerEntity c WHERE c.deletedAt IS NOT NULL AND c.deletedAt < :beforeDate")
    int purgeDeletedBefore(@Param("beforeDate") Instant beforeDate);

    /**
     * 统计租户的 Cron 触发器数量
     */
    long countByTenantIdAndDeletedAtIsNull(String tenantId);

    /**
     * 统计租户启用的 Cron 触发器数量
     */
    long countByTenantIdAndEnabledTrueAndDeletedAtIsNull(String tenantId);

    /**
     * 查找所有需要调度到 PowerJob 的启用触发器
     */
    @Query("SELECT c FROM CronTriggerEntity c " +
           "WHERE c.enabled = true " +
           "AND c.deletedAt IS NULL " +
           "AND (c.powerjobJobId IS NULL OR c.powerjobJobId = 0)")
    List<CronTriggerEntity> findPendingScheduling();
}

package com.workflow.infra.repository;

import com.workflow.infra.entity.WorkflowExecutionEntity;
import com.workflow.model.ExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 工作流执行历史 Repository。
 * <p>
 * 提供工作流执行历史的 CRUD 操作和自定义查询。
 * 支持多租户隔离查询。
 */
@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecutionEntity, UUID> {

    /**
     * 根据执行ID查找记录（未删除）
     */
    Optional<WorkflowExecutionEntity> findByExecutionIdAndDeletedAtIsNull(String executionId);

    /**
     * 根据工作流ID查找所有执行记录（未删除）
     */
    List<WorkflowExecutionEntity> findByWorkflowIdAndDeletedAtIsNullOrderByStartedAtDesc(String workflowId);

    /**
     * 根据租户ID和状态查找执行记录（未删除）
     */
    List<WorkflowExecutionEntity> findByTenantIdAndStatusAndDeletedAtIsNullOrderByStartedAtDesc(
            String tenantId, ExecutionStatus status);

    /**
     * 根据租户ID查找所有执行记录（未删除）
     */
    List<WorkflowExecutionEntity> findByTenantIdAndDeletedAtIsNullOrderByStartedAtDesc(String tenantId);

    /**
     * 查找可以恢复的执行记录（失败或运行中状态）
     */
    @Query("""
            SELECT w FROM WorkflowExecutionEntity w
            WHERE w.tenantId = :tenantId
            AND w.deletedAt IS NULL
            AND (w.status = 'FAILED' OR w.status = 'RUNNING' OR w.status = 'WAITING')
            ORDER BY w.startedAt DESC
            """)
    List<WorkflowExecutionEntity> findResumableExecutions(@Param("tenantId") String tenantId);

    /**
     * 查找可以重试的执行记录（失败状态且未达重试上限）
     */
    @Query("""
            SELECT w FROM WorkflowExecutionEntity w
            WHERE w.tenantId = :tenantId
            AND w.deletedAt IS NULL
            AND w.status = 'FAILED'
            AND w.retryCount < w.maxRetryCount
            ORDER BY w.startedAt DESC
            """)
    List<WorkflowExecutionEntity> findRetryableExecutions(@Param("tenantId") String tenantId);

    /**
     * 统计租户的执行数量（按状态分组）
     */
    @Query("""
            SELECT w.status, COUNT(w) FROM WorkflowExecutionEntity w
            WHERE w.tenantId = :tenantId
            AND w.deletedAt IS NULL
            GROUP BY w.status
            """)
    List<Object[]> countByTenantIdGroupByStatus(@Param("tenantId") String tenantId);

    /**
     * 查找指定时间范围内的执行记录
     */
    List<WorkflowExecutionEntity> findByTenantIdAndStartedAtBetweenAndDeletedAtIsNullOrderByStartedAtDesc(
            String tenantId, Instant startTime, Instant endTime);

    /**
     * 统计租户的总执行次数
     */
    long countByTenantIdAndDeletedAtIsNull(String tenantId);

    /**
     * 软删除：根据执行ID标记为已删除
     */
    @Query("UPDATE WorkflowExecutionEntity w SET w.deletedAt = :deletedAt WHERE w.executionId = :executionId")
    int softDeleteByExecutionId(@Param("executionId") String executionId, @Param("deletedAt") Instant deletedAt);

    /**
     * 清理过期的已删除记录
     */
    @Query("DELETE FROM WorkflowExecutionEntity w WHERE w.deletedAt IS NOT NULL AND w.deletedAt < :beforeDate")
    int purgeDeletedBefore(@Param("beforeDate") Instant beforeDate);

    /**
     * 查找指定恢复来源的所有执行记录
     */
    List<WorkflowExecutionEntity> findByResumedFromIdAndDeletedAtIsNullOrderByStartedAtDesc(UUID resumedFromId);

    /**
     * 检查执行ID是否存在
     */
    boolean existsByExecutionIdAndDeletedAtIsNull(String executionId);
}

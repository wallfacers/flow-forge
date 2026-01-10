package com.workflow.infra.repository;

import com.workflow.infra.entity.NodeExecutionLogEntity;
import com.workflow.infra.entity.NodeExecutionLogEntity.NodeExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 节点执行日志 Repository。
 * <p>
 * 提供节点执行日志的 CRUD 操作和自定义查询。
 * 支持按执行ID、节点ID、状态等条件查询。
 */
@Repository
public interface NodeExecutionLogRepository extends JpaRepository<NodeExecutionLogEntity, UUID> {

    /**
     * 根据执行ID查找所有节点日志（未删除）
     */
    List<NodeExecutionLogEntity> findByExecutionIdAndDeletedAtIsNullOrderByStartedAtAsc(UUID executionId);

    /**
     * 根据执行ID字符串查找所有节点日志（未删除）
     */
    List<NodeExecutionLogEntity> findByExecutionIdStrAndDeletedAtIsNullOrderByStartedAtAsc(String executionIdStr);

    /**
     * 根据执行ID和节点ID查找日志（未删除）
     */
    Optional<NodeExecutionLogEntity> findByExecutionIdAndNodeIdAndDeletedAtIsNull(UUID executionId, String nodeId);

    /**
     * 根据执行ID字符串和节点ID查找日志（未删除）
     */
    Optional<NodeExecutionLogEntity> findByExecutionIdStrAndNodeIdAndDeletedAtIsNull(String executionIdStr, String nodeId);

    /**
     * 根据执行ID和状态查找节点日志（未删除）
     */
    List<NodeExecutionLogEntity> findByExecutionIdAndStatusAndDeletedAtIsNull(UUID executionId, NodeExecutionStatus status);

    /**
     * 根据执行ID字符串和状态查找节点日志（未删除）
     */
    List<NodeExecutionLogEntity> findByExecutionIdStrAndStatusAndDeletedAtIsNull(String executionIdStr, NodeExecutionStatus status);

    /**
     * 查找失败的节点日志（未删除）
     */
    @Query("""
            SELECT n FROM NodeExecutionLogEntity n
            WHERE n.executionId = :executionId
            AND n.deletedAt IS NULL
            AND n.status = 'FAILED'
            ORDER BY n.startedAt DESC
            """)
    List<NodeExecutionLogEntity> findFailedNodes(@Param("executionId") UUID executionId);

    /**
     * 查找失败的节点日志（未删除，使用执行ID字符串）
     */
    @Query("""
            SELECT n FROM NodeExecutionLogEntity n
            WHERE n.executionIdStr = :executionIdStr
            AND n.deletedAt IS NULL
            AND n.status = 'FAILED'
            ORDER BY n.startedAt DESC
            """)
    List<NodeExecutionLogEntity> findFailedNodesByExecutionIdStr(@Param("executionIdStr") String executionIdStr);

    /**
     * 统计执行ID下各状态的节点数量
     */
    @Query("""
            SELECT n.status, COUNT(n) FROM NodeExecutionLogEntity n
            WHERE n.executionId = :executionId
            AND n.deletedAt IS NULL
            GROUP BY n.status
            """)
    List<Object[]> countByExecutionIdGroupByStatus(@Param("executionId") UUID executionId);

    /**
     * 统计执行ID字符串下各状态的节点数量
     */
    @Query("""
            SELECT n.status, COUNT(n) FROM NodeExecutionLogEntity n
            WHERE n.executionIdStr = :executionIdStr
            AND n.deletedAt IS NULL
            GROUP BY n.status
            """)
    List<Object[]> countByExecutionIdStrGroupByStatus(@Param("executionIdStr") String executionIdStr);

    /**
     * 查找执行中或等待中的节点
     */
    @Query("""
            SELECT n FROM NodeExecutionLogEntity n
            WHERE n.executionId = :executionId
            AND n.deletedAt IS NULL
            AND (n.status = 'RUNNING' OR n.status = 'WAITING')
            ORDER BY n.startedAt ASC
            """)
    List<NodeExecutionLogEntity> findActiveNodes(@Param("executionId") UUID executionId);

    /**
     * 查找执行中或等待中的节点（使用执行ID字符串）
     */
    @Query("""
            SELECT n FROM NodeExecutionLogEntity n
            WHERE n.executionIdStr = :executionIdStr
            AND n.deletedAt IS NULL
            AND (n.status = 'RUNNING' OR n.status = 'WAITING')
            ORDER BY n.startedAt ASC
            """)
    List<NodeExecutionLogEntity> findActiveNodesByExecutionIdStr(@Param("executionIdStr") String executionIdStr);

    /**
     * 查找已完成的节点列表
     */
    @Query("""
            SELECT n.nodeId FROM NodeExecutionLogEntity n
            WHERE n.executionId = :executionId
            AND n.deletedAt IS NULL
            AND n.status = 'SUCCESS'
            """)
    List<String> findCompletedNodeIds(@Param("executionId") UUID executionId);

    /**
     * 查找已完成的节点列表（使用执行ID字符串）
     */
    @Query("""
            SELECT n.nodeId FROM NodeExecutionLogEntity n
            WHERE n.executionIdStr = :executionIdStr
            AND n.deletedAt IS NULL
            AND n.status = 'SUCCESS'
            """)
    List<String> findCompletedNodeIdsByExecutionIdStr(@Param("executionIdStr") String executionIdStr);

    /**
     * 批量保存节点日志
     */
    List<NodeExecutionLogEntity> saveAll(List<NodeExecutionLogEntity> entities);

    /**
     * 根据执行ID删除所有节点日志（软删除）
     */
    @Query("UPDATE NodeExecutionLogEntity n SET n.deletedAt = :deletedAt WHERE n.executionId = :executionId")
    int softDeleteByExecutionId(@Param("executionId") UUID executionId, @Param("deletedAt") java.time.Instant deletedAt);

    /**
     * 根据执行ID字符串删除所有节点日志（软删除）
     */
    @Query("UPDATE NodeExecutionLogEntity n SET n.deletedAt = :deletedAt WHERE n.executionIdStr = :executionIdStr")
    int softDeleteByExecutionIdStr(@Param("executionIdStr") String executionIdStr, @Param("deletedAt") java.time.Instant deletedAt);

    /**
     * 清理过期的已删除记录
     */
    @Query("DELETE FROM NodeExecutionLogEntity n WHERE n.deletedAt IS NOT NULL AND n.deletedAt < :beforeDate")
    int purgeDeletedBefore(@Param("beforeDate") java.time.Instant beforeDate);

    /**
     * 统计执行节点数量
     */
    long countByExecutionIdAndDeletedAtIsNull(UUID executionId);

    /**
     * 统计执行节点数量（使用执行ID字符串）
     */
    long countByExecutionIdStrAndDeletedAtIsNull(String executionIdStr);
}

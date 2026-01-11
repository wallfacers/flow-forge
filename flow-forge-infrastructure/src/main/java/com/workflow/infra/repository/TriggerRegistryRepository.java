package com.workflow.infra.repository;

import com.workflow.infra.entity.TriggerRegistryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 触发器注册表 Repository
 */
@Repository
public interface TriggerRegistryRepository extends JpaRepository<TriggerRegistryEntity, UUID> {

    /**
     * 根据 webhook 路径查找触发器
     */
    Optional<TriggerRegistryEntity> findByWebhookPathAndDeletedAtIsNull(String webhookPath);

    /**
     * 根据工作流ID查找所有触发器
     */
    List<TriggerRegistryEntity> findByWorkflowIdAndDeletedAtIsNullOrderByCreatedAtDesc(String workflowId);

    /**
     * 根据租户ID查找所有触发器
     */
    List<TriggerRegistryEntity> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId);

    /**
     * 根据工作流ID和节点ID查找触发器
     */
    Optional<TriggerRegistryEntity> findByWorkflowIdAndNodeIdAndDeletedAtIsNull(String workflowId, String nodeId);

    /**
     * 根据类型查找触发器
     */
    List<TriggerRegistryEntity> findByTriggerTypeAndDeletedAtIsNull(String triggerType);

    /**
     * 根据工作流ID和类型查找触发器
     */
    Optional<TriggerRegistryEntity> findByWorkflowIdAndTriggerTypeAndDeletedAtIsNull(String workflowId, String triggerType);

    /**
     * 检查 webhook 路径是否存在
     */
    boolean existsByWebhookPathAndDeletedAtIsNull(String webhookPath);

    /**
     * 删除工作流中不在指定节点列表中的触发器
     */
    @Query("DELETE FROM TriggerRegistryEntity t WHERE t.workflowId = :workflowId AND t.nodeId NOT IN :nodeIds")
    int deleteByWorkflowIdAndNodeIdNotIn(@Param("workflowId") String workflowId, @Param("nodeIds") List<String> nodeIds);

    /**
     * 根据 PowerJob 任务ID查找触发器
     */
    Optional<TriggerRegistryEntity> findByPowerjobJobIdAndDeletedAtIsNull(Long powerjobJobId);

    /**
     * 统计租户的触发器数量
     */
    @Query("SELECT COUNT(t) FROM TriggerRegistryEntity t WHERE t.tenantId = :tenantId AND t.deletedAt IS NULL")
    long countByTenantId(@Param("tenantId") String tenantId);

    /**
     * 根据工作流ID查找触发器（不检查删除标记）
     */
    @Query("SELECT t FROM TriggerRegistryEntity t WHERE t.workflowId = :workflowId")
    List<TriggerRegistryEntity> findAllByWorkflowId(@Param("workflowId") String workflowId);

    /**
     * 根据租户ID查找触发器（分页，仅未删除）
     */
    @Query("SELECT t FROM TriggerRegistryEntity t WHERE t.tenantId = :tenantId AND t.deletedAt IS NULL")
    org.springframework.data.domain.Page<TriggerRegistryEntity> findAllByTenantIdAndDeletedAtIsNull(
            @Param("tenantId") String tenantId, org.springframework.data.domain.Pageable pageable);
}

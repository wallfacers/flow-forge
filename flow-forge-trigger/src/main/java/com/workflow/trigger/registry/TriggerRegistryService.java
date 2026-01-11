package com.workflow.trigger.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.infra.entity.TriggerRegistryEntity;
import com.workflow.infra.repository.TriggerRegistryRepository;
import com.workflow.model.TriggerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 触发器注册表服务
 * <p>
 * 统一管理所有类型的触发器（WEBHOOK、CRON、MANUAL、EVENT）。
 * <p>
 * 提供 Redis 缓存以加速查询，缓存策略：
 * <ul>
 *   <li>按 webhook 路径查询：缓存 5 分钟</li>
 *   <li>按工作流 ID 查询：缓存 5 分钟</li>
 *   <li>按租户 ID 查询：缓存 2 分钟</li>
 * </ul>
 * <p>
 * 当工作流保存时，自动同步触发器配置到注册表。
 *
 * @see TriggerRegistryEntity
 * @see TriggerType
 */
@Service
public class TriggerRegistryService {

    private static final Logger log = LoggerFactory.getLogger(TriggerRegistryService.class);

    /** 缓存名称：按 webhook 路径 */
    public static final String CACHE_BY_WEBHOOK_PATH = "trigger:webhook:path";

    /** 缓存名称：按工作流 ID */
    public static final String CACHE_BY_WORKFLOW_ID = "trigger:workflow:id";

    /** 缓存名称：按租户 ID */
    public static final String CACHE_BY_TENANT_ID = "trigger:tenant:id";

    private final TriggerRegistryRepository repository;
    private final ObjectMapper objectMapper;

    public TriggerRegistryService(TriggerRegistryRepository repository,
                                   ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // ====================
    // 查询操作
    // ====================

    /**
     * 根据 webhook 路径查找触发器（带缓存）
     *
     * @param webhookPath webhook 路径
     * @return 触发器实体
     */
    @Cacheable(value = CACHE_BY_WEBHOOK_PATH, key = "#webhookPath", unless = "#result == null")
    public Optional<TriggerRegistryEntity> findByWebhookPath(String webhookPath) {
        return repository.findByWebhookPathAndDeletedAtIsNull(webhookPath);
    }

    /**
     * 根据工作流 ID 查找所有触发器（带缓存）
     *
     * @param workflowId 工作流 ID
     * @return 触发器列表
     */
    @Cacheable(value = CACHE_BY_WORKFLOW_ID, key = "#workflowId")
    public List<TriggerRegistryEntity> findByWorkflowId(String workflowId) {
        return repository.findByWorkflowIdAndDeletedAtIsNullOrderByCreatedAtDesc(workflowId);
    }

    /**
     * 根据租户 ID 查找所有触发器（带缓存）
     *
     * @param tenantId 租户 ID
     * @return 触发器列表
     */
    @Cacheable(value = CACHE_BY_TENANT_ID, key = "#tenantId")
    public List<TriggerRegistryEntity> findByTenantId(String tenantId) {
        return repository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId);
    }

    /**
     * 根据工作流 ID 和节点 ID 查找触发器
     *
     * @param workflowId 工作流 ID
     * @param nodeId     节点 ID
     * @return 触发器实体
     */
    public Optional<TriggerRegistryEntity> findByWorkflowIdAndNodeId(String workflowId, String nodeId) {
        return repository.findByWorkflowIdAndNodeIdAndDeletedAtIsNull(workflowId, nodeId);
    }

    /**
     * 根据工作流 ID 和类型查找触发器
     *
     * @param workflowId 工作流 ID
     * @param triggerType 触发器类型
     * @return 触发器实体
     */
    public Optional<TriggerRegistryEntity> findByWorkflowIdAndType(String workflowId, TriggerType triggerType) {
        return repository.findByWorkflowIdAndTriggerTypeAndDeletedAtIsNull(workflowId, triggerType.getCode());
    }

    /**
     * 根据 PowerJob 任务 ID 查找触发器
     *
     * @param powerjobJobId PowerJob 任务 ID
     * @return 触发器实体
     */
    public Optional<TriggerRegistryEntity> findByPowerJobId(Long powerjobJobId) {
        return repository.findByPowerjobJobIdAndDeletedAtIsNull(powerjobJobId);
    }

    /**
     * 获取租户的所有触发器（分页）
     *
     * @param tenantId 租户 ID
     * @param pageable 分页参数
     * @return 分页结果
     */
    public Page<TriggerRegistryEntity> findByTenantIdPaginated(String tenantId, Pageable pageable) {
        return repository.findAllByTenantIdAndDeletedAtIsNull(tenantId, pageable);
    }

    /**
     * 获取所有启用状态的触发器
     *
     * @return 启用的触发器列表
     */
    public List<TriggerRegistryEntity> findEnabled() {
        return repository.findAll().stream()
                .filter(e -> e.getDeletedAt() == null && e.getEnabled())
                .collect(Collectors.toList());
    }

    /**
     * 统计租户的触发器数量
     *
     * @param tenantId 租户 ID
     * @return 触发器数量
     */
    public long countByTenantId(String tenantId) {
        return repository.countByTenantId(tenantId);
    }

    /**
     * 检查 webhook 路径是否存在
     *
     * @param webhookPath webhook 路径
     * @return 是否存在
     */
    public boolean existsByWebhookPath(String webhookPath) {
        return repository.existsByWebhookPathAndDeletedAtIsNull(webhookPath);
    }

    // ====================
    // 写入操作
    // ====================

    /**
     * 创建触发器注册记录
     *
     * @param entity 触发器实体
     * @return 保存后的实体
     */
    @Transactional
    @CachePut(value = CACHE_BY_WORKFLOW_ID, key = "#entity.workflowId")
    public TriggerRegistryEntity create(TriggerRegistryEntity entity) {
        entity.setId(UUID.randomUUID());
        TriggerRegistryEntity saved = repository.save(entity);
        log.info("Created trigger registry: id={}, workflowId={}, nodeId={}, type={}",
                saved.getId(), saved.getWorkflowId(), saved.getNodeId(), saved.getTriggerType());
        return saved;
    }

    /**
     * 更新触发器注册记录
     *
     * @param entity 触发器实体
     * @return 更新后的实体
     */
    @Transactional
    @CachePut(value = CACHE_BY_WORKFLOW_ID, key = "#entity.workflowId")
    public TriggerRegistryEntity update(TriggerRegistryEntity entity) {
        TriggerRegistryEntity saved = repository.save(entity);
        log.info("Updated trigger registry: id={}, workflowId={}, nodeId={}",
                saved.getId(), saved.getWorkflowId(), saved.getNodeId());
        return saved;
    }

    /**
     * 删除触发器注册记录（软删除）
     *
     * @param id 触发器 ID
     */
    @Transactional
    @CacheEvict(value = {CACHE_BY_WEBHOOK_PATH, CACHE_BY_WORKFLOW_ID, CACHE_BY_TENANT_ID}, allEntries = false)
    public void softDelete(UUID id) {
        TriggerRegistryEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Trigger not found: " + id));
        entity.setDeletedAt(java.time.Instant.now());
        repository.save(entity);
        log.info("Soft deleted trigger registry: id={}, workflowId={}", id, entity.getWorkflowId());
    }

    /**
     * 删除工作流的所有触发器（不在指定节点列表中的）
     *
     * @param workflowId 工作流 ID
     * @param nodeIds    保留的节点 ID 列表
     * @return 删除的数量
     */
    @Transactional
    @CacheEvict(value = CACHE_BY_WORKFLOW_ID, key = "#workflowId")
    public int deleteByWorkflowIdAndNodeIdNotIn(String workflowId, List<String> nodeIds) {
        int deleted = repository.deleteByWorkflowIdAndNodeIdNotIn(workflowId, nodeIds);
        log.info("Deleted {} triggers for workflowId={}, not in nodes={}", deleted, workflowId, nodeIds);
        return deleted;
    }

    /**
     * 清空工作流的所有触发器（用于工作流删除）
     *
     * @param workflowId 工作流 ID
     */
    @Transactional
    @CacheEvict(value = CACHE_BY_WORKFLOW_ID, key = "#workflowId")
    public void deleteAllByWorkflowId(String workflowId) {
        List<TriggerRegistryEntity> triggers = repository.findAllByWorkflowId(workflowId);
        for (TriggerRegistryEntity trigger : triggers) {
            trigger.setDeletedAt(java.time.Instant.now());
        }
        repository.saveAll(triggers);
        log.info("Soft deleted all triggers for workflowId={}, count={}", workflowId, triggers.size());
    }

    // ====================
    // 同步操作
    // ====================

    /**
     * 从工作流定义同步触发器到注册表
     * <p>
     * 当工作流保存时调用此方法，保持注册表与工作流定义一致。
     *
     * @param workflowId     工作流 ID
     * @param tenantId       租户 ID
     * @param workflowName   工作流名称
     * @param triggerNodeMap 触发器节点映射 (nodeId -> Node)
     */
    @Transactional
    @CacheEvict(value = {CACHE_BY_WEBHOOK_PATH, CACHE_BY_WORKFLOW_ID, CACHE_BY_TENANT_ID}, key = "#workflowId")
    public void syncTriggersFromWorkflow(String workflowId, String tenantId, String workflowName,
                                         Map<String, com.workflow.model.Node> triggerNodeMap) {
        log.info("Syncing triggers for workflow: workflowId={}, triggerCount={}",
                workflowId, triggerNodeMap.size());

        // 获取现有触发器
        List<TriggerRegistryEntity> existingTriggers = repository.findAllByWorkflowId(workflowId);
        Map<String, TriggerRegistryEntity> existingMap = existingTriggers.stream()
                .filter(t -> t.getDeletedAt() == null)
                .collect(Collectors.toMap(TriggerRegistryEntity::getNodeId, t -> t));

        // 获取当前节点 ID 列表
        Set<String> currentNodeIds = triggerNodeMap.keySet();

        // 删除不再存在的触发器
        for (TriggerRegistryEntity existing : existingTriggers) {
            if (existing.getDeletedAt() == null && !currentNodeIds.contains(existing.getNodeId())) {
                existing.setDeletedAt(java.time.Instant.now());
                repository.save(existing);
                log.info("Deleted stale trigger: nodeId={}, workflowId={}", existing.getNodeId(), workflowId);
            }
        }

        // 创建或更新触发器
        for (Map.Entry<String, com.workflow.model.Node> entry : triggerNodeMap.entrySet()) {
            String nodeId = entry.getKey();
            com.workflow.model.Node node = entry.getValue();

            TriggerRegistryEntity existing = existingMap.get(nodeId);
            if (existing != null) {
                // 更新现有触发器
                updateTriggerFromNode(existing, node, tenantId, workflowName);
                repository.save(existing);
                log.info("Updated trigger: nodeId={}, workflowId={}, type={}",
                        nodeId, workflowId, node.getConfig().get("type"));
            } else {
                // 创建新触发器
                TriggerRegistryEntity newTrigger = createTriggerFromNode(node, workflowId, tenantId, workflowName);
                repository.save(newTrigger);
                log.info("Created trigger: nodeId={}, workflowId={}, type={}",
                        nodeId, workflowId, node.getConfig().get("type"));
            }
        }
    }

    /**
     * 从节点创建触发器实体
     */
    private TriggerRegistryEntity createTriggerFromNode(com.workflow.model.Node node,
                                                         String workflowId,
                                                         String tenantId,
                                                         String workflowName) {
        Map<String, Object> config = node.getConfig();
        String triggerType = getTriggerTypeFromConfig(config);

        TriggerRegistryEntity.TriggerRegistryEntityBuilder builder = TriggerRegistryEntity.builder()
                .workflowId(workflowId)
                .tenantId(tenantId)
                .nodeId(node.getId())
                .triggerType(triggerType)
                .triggerConfig(config)
                .enabled(true)
                .totalTriggers(0L)
                .successfulTriggers(0L)
                .failedTriggers(0L);

        // 根据类型设置特定字段
        setTriggerSpecificFields(builder, triggerType, config);

        return builder.build();
    }

    /**
     * 更新现有触发器
     */
    private void updateTriggerFromNode(TriggerRegistryEntity entity,
                                       com.workflow.model.Node node,
                                       String tenantId,
                                       String workflowName) {
        Map<String, Object> config = node.getConfig();
        String triggerType = getTriggerTypeFromConfig(config);

        entity.setTenantId(tenantId);
        entity.setTriggerType(triggerType);
        entity.setTriggerConfig(config);

        // 更新类型特定字段
        updateTriggerSpecificFields(entity, triggerType, config);
    }

    /**
     * 从配置中获取触发器类型
     */
    private String getTriggerTypeFromConfig(Map<String, Object> config) {
        Object typeObj = config.get("type");
        if (typeObj == null) {
            return "MANUAL";
        }
        String type = typeObj.toString().toUpperCase();
        try {
            TriggerType triggerType = TriggerType.fromCode(type);
            return triggerType.getCode();
        } catch (IllegalArgumentException e) {
            return "MANUAL";
        }
    }

    /**
     * 设置触发器特定字段
     */
    @SuppressWarnings("unchecked")
    private void setTriggerSpecificFields(TriggerRegistryEntity.TriggerRegistryEntityBuilder builder,
                                          String triggerType,
                                          Map<String, Object> config) {
        switch (triggerType.toLowerCase()) {
            case "webhook" -> {
                Object webhookPath = config.get("webhookPath");
                if (webhookPath != null) {
                    builder.webhookPath(webhookPath.toString());
                }
                Object secretKey = config.get("secretKey");
                if (secretKey != null) {
                    builder.secretKey(secretKey.toString());
                }
            }
            case "cron" -> {
                Object cronExpression = config.get("cronExpression");
                if (cronExpression != null) {
                    builder.cronExpression(cronExpression.toString());
                }
                Object timezone = config.get("timezone");
                if (timezone != null) {
                    builder.timezone(timezone.toString());
                }
            }
        }
    }

    /**
     * 更新触发器特定字段
     */
    @SuppressWarnings("unchecked")
    private void updateTriggerSpecificFields(TriggerRegistryEntity entity,
                                             String triggerType,
                                             Map<String, Object> config) {
        switch (triggerType.toLowerCase()) {
            case "webhook" -> {
                Object webhookPath = config.get("webhookPath");
                if (webhookPath != null) {
                    entity.setWebhookPath(webhookPath.toString());
                }
                Object secretKey = config.get("secretKey");
                if (secretKey != null) {
                    entity.setSecretKey(secretKey.toString());
                } else {
                    entity.setSecretKey(null); // 清除旧密钥
                }
            }
            case "cron" -> {
                Object cronExpression = config.get("cronExpression");
                if (cronExpression != null) {
                    entity.setCronExpression(cronExpression.toString());
                }
                Object timezone = config.get("timezone");
                if (timezone != null) {
                    entity.setTimezone(timezone.toString());
                }
            }
        }
    }

    // ====================
    // 统计操作
    // ====================

    /**
     * 增加触发统计
     *
     * @param id      触发器 ID
     * @param success 是否成功
     */
    @Transactional
    public void incrementTrigger(UUID id, boolean success) {
        repository.findById(id).ifPresent(entity -> {
            entity.incrementTrigger(success);
            repository.save(entity);
        });
    }

    /**
     * 重置统计信息
     *
     * @param id 触发器 ID
     */
    @Transactional
    public void resetStatistics(UUID id) {
        repository.findById(id).ifPresent(entity -> {
            entity.resetStatistics();
            repository.save(entity);
            log.info("Reset statistics for trigger: id={}", id);
        });
    }

    /**
     * 启用/禁用触发器
     *
     * @param id      触发器 ID
     * @param enabled 是否启用
     */
    @Transactional
    @CacheEvict(value = {CACHE_BY_WEBHOOK_PATH, CACHE_BY_WORKFLOW_ID, CACHE_BY_TENANT_ID}, allEntries = true)
    public void toggleEnabled(UUID id, boolean enabled) {
        repository.findById(id).ifPresent(entity -> {
            entity.setEnabled(enabled);
            repository.save(entity);
            log.info("Trigger {}: id={}", enabled ? "enabled" : "disabled", id);
        });
    }

    // ====================
    // 清除缓存
    // ====================

    /**
     * 清除所有触发器缓存
     */
    @CacheEvict(value = {CACHE_BY_WEBHOOK_PATH, CACHE_BY_WORKFLOW_ID, CACHE_BY_TENANT_ID}, allEntries = true)
    public void clearAllCache() {
        log.info("Cleared all trigger cache");
    }

    /**
     * 清除指定工作流的缓存
     */
    @CacheEvict(value = {CACHE_BY_WEBHOOK_PATH, CACHE_BY_WORKFLOW_ID, CACHE_BY_TENANT_ID}, key = "#workflowId")
    public void clearWorkflowCache(String workflowId) {
        log.info("Cleared trigger cache for workflow: {}", workflowId);
    }
}

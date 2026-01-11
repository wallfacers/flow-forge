package com.workflow.trigger.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.WorkflowDefinitionService;
import com.workflow.engine.dispatcher.WorkflowDispatcher;
import com.workflow.infra.entity.TriggerRegistryEntity;
import com.workflow.model.WorkflowDefinition;
import com.workflow.model.WorkflowValidationException;
import com.workflow.trigger.dto.WebhookExecutionResponse;
import com.workflow.trigger.registry.TriggerRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Webhook 触发器服务（重构版）
 * <p>
 * 基于 trigger_registry 表提供 Webhook 触发功能。
 * <p>
 * 支持同步/异步两种模式：
 * <ul>
 *   <li>同步模式：等待工作流执行完成，返回完整结果</li>
 *   <li>异步模式：立即返回 executionId，后台执行</li>
 * </ul>
 * <p>
 * 执行模式判断优先级：HTTP 请求头 Prefer > 节点配置 asyncMode > 默认异步
 *
 * @see TriggerRegistryService
 * @see WorkflowDispatcher
 */
@Service
public class WebhookTriggerService {

    private static final Logger log = LoggerFactory.getLogger(WebhookTriggerService.class);

    /** 同步执行默认超时时间（毫秒） */
    private static final long DEFAULT_SYNC_TIMEOUT_MS = 30000;

    /** 请求头：指定同步模式 */
    private static final String HEADER_PREFER = "Prefer";
    private static final String PREFER_WAIT_SYNC = "wait=sync";
    private static final String PREFER_WAIT_ASYNC = "wait=async";

    private final TriggerRegistryService registryService;
    private final WorkflowDefinitionService workflowDefinitionService;
    private final WorkflowDispatcher workflowDispatcher;
    private final ObjectMapper objectMapper;

    public WebhookTriggerService(TriggerRegistryService registryService,
                                  WorkflowDefinitionService workflowDefinitionService,
                                  WorkflowDispatcher workflowDispatcher,
                                  ObjectMapper objectMapper) {
        this.registryService = registryService;
        this.workflowDefinitionService = workflowDefinitionService;
        this.workflowDispatcher = workflowDispatcher;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理 Webhook 触发请求
     * <p>
     * 根据配置和请求头决定同步或异步执行。
     *
     * @param webhookPath webhook 路径
     * @param payload     请求体数据
     * @param headers     HTTP 请求头
     * @return 执行响应
     */
    @Transactional
    public WebhookExecutionResponse handleWebhook(String webhookPath,
                                                   Map<String, Object> payload,
                                                   Map<String, String> headers) {
        // 查找触发器注册
        TriggerRegistryEntity trigger = registryService.findByWebhookPath(webhookPath)
                .orElse(null);

        if (trigger == null) {
            log.warn("Webhook not found: {}", webhookPath);
            return WebhookExecutionResponse.error("Webhook 未注册");
        }

        if (!trigger.getEnabled()) {
            log.warn("Webhook is disabled: {}", webhookPath);
            return WebhookExecutionResponse.error("Webhook 已禁用");
        }

        // 验证签名（如果配置了密钥）
        if (trigger.getSecretKey() != null && !trigger.getSecretKey().isEmpty()) {
            String signature = headers.get("X-Signature");
            if (!verifySignature(payload, trigger.getSecretKey(), signature)) {
                log.warn("Invalid signature for webhook: {}", webhookPath);
                registryService.incrementTrigger(trigger.getId(), false);
                return WebhookExecutionResponse.error("签名验证失败");
            }
        }

        // 确定执行模式
        boolean isSync = determineExecutionMode(trigger, headers);

        // 准备输入数据
        Map<String, Object> inputData = prepareInputData(payload, headers, trigger);

        if (isSync) {
            return handleSyncExecution(trigger, inputData);
        } else {
            return handleAsyncExecution(trigger, inputData);
        }
    }

    /**
     * 处理同步执行
     */
    private WebhookExecutionResponse handleSyncExecution(TriggerRegistryEntity trigger,
                                                         Map<String, Object> inputData) {
        Instant startTime = Instant.now();
        long timeout = getSyncTimeout(trigger);

        try {
            // 加载工作流定义
            WorkflowDefinition definition = loadWorkflowDefinition(trigger.getWorkflowId());
            CompletableFuture<WebhookExecutionResponse> future = CompletableFuture.supplyAsync(() -> {
                try {
                    WorkflowDispatcher.DispatchResult result = workflowDispatcher.execute(definition, inputData);
                    registryService.incrementTrigger(trigger.getId(), result.isSuccess());
                    return WebhookExecutionResponse.fromDispatchResult(result, trigger.getWorkflowId());
                } catch (Exception e) {
                    log.error("Sync execution failed: workflowId={}", trigger.getWorkflowId(), e);
                    registryService.incrementTrigger(trigger.getId(), false);
                    return WebhookExecutionResponse.error("执行失败: " + e.getMessage());
                }
            });

            // 等待结果或超时
            WebhookExecutionResponse response = future.orTimeout(timeout, TimeUnit.MILLISECONDS).join();
            response.setTriggerNodeId(trigger.getNodeId());
            response.setTriggerType(trigger.getTriggerType());

            log.info("Webhook sync execution completed: path={}, workflowId={}, duration={}ms",
                    trigger.getWebhookPath(), trigger.getWorkflowId(),
                    Duration.between(startTime, Instant.now()).toMillis());

            return response;

        } catch (CompletionException e) {
            // 检查是否是超时
            if (e.getCause() instanceof TimeoutException) {
                log.warn("Webhook sync execution timed out: path={}, timeout={}ms",
                        trigger.getWebhookPath(), timeout);
                registryService.incrementTrigger(trigger.getId(), false);

                WebhookExecutionResponse errorResponse = WebhookExecutionResponse.error("执行超时");
                errorResponse.setExecutionId(generateExecutionId(trigger.getWorkflowId()));
                errorResponse.setWorkflowId(trigger.getWorkflowId());
                return errorResponse;
            }
            // 其他错误
            log.error("Webhook sync execution error: path={}", trigger.getWebhookPath(), e.getCause());
            registryService.incrementTrigger(trigger.getId(), false);
            return WebhookExecutionResponse.error("执行错误: " + e.getCause().getMessage());
        } catch (Exception e) {
            log.error("Webhook sync execution error: path={}", trigger.getWebhookPath(), e);
            registryService.incrementTrigger(trigger.getId(), false);
            return WebhookExecutionResponse.error("执行错误: " + e.getMessage());
        }
    }

    /**
     * 处理异步执行
     */
    private WebhookExecutionResponse handleAsyncExecution(TriggerRegistryEntity trigger,
                                                          Map<String, Object> inputData) {
        try {
            // 加载工作流定义
            WorkflowDefinition definition = loadWorkflowDefinition(trigger.getWorkflowId());

            // 异步执行
            workflowDispatcher.executeAsync(definition, inputData, result -> {
                // 执行完成后更新统计
                registryService.incrementTrigger(trigger.getId(), result.isSuccess());
                log.info("Webhook async execution completed: path={}, workflowId={}, success={}",
                        trigger.getWebhookPath(), trigger.getWorkflowId(), result.isSuccess());
            });

            String executionId = generateExecutionId(trigger.getWorkflowId());

            log.info("Webhook async execution started: path={}, workflowId={}, executionId={}",
                    trigger.getWebhookPath(), trigger.getWorkflowId(), executionId);

            return WebhookExecutionResponse.async(executionId, trigger.getWorkflowId(), null);

        } catch (Exception e) {
            log.error("Failed to start async execution: path={}", trigger.getWebhookPath(), e);
            registryService.incrementTrigger(trigger.getId(), false);
            return WebhookExecutionResponse.error("启动异步执行失败: " + e.getMessage());
        }
    }

    /**
     * 确定执行模式（同步/异步）
     * <p>
     * 优先级：请求头 Prefer > 节点配置 asyncMode > 默认异步
     */
    private boolean determineExecutionMode(TriggerRegistryEntity trigger, Map<String, String> headers) {
        // 1. 检查请求头
        String preferHeader = headers.get(HEADER_PREFER);
        if (preferHeader != null) {
            if (preferHeader.contains(PREFER_WAIT_SYNC)) {
                return true;
            }
            if (preferHeader.contains(PREFER_WAIT_ASYNC)) {
                return false;
            }
        }

        // 2. 检查节点配置
        Map<String, Object> config = trigger.getTriggerConfig();
        if (config != null) {
            Object asyncMode = config.get("asyncMode");
            if ("sync".equalsIgnoreCase(String.valueOf(asyncMode))) {
                return true;
            }
        }

        // 3. 默认异步
        return false;
    }

    /**
     * 获取同步执行超时时间
     */
    private long getSyncTimeout(TriggerRegistryEntity trigger) {
        Map<String, Object> config = trigger.getTriggerConfig();
        if (config != null) {
            Object timeout = config.get("timeout");
            if (timeout instanceof Number) {
                return ((Number) timeout).longValue();
            }
        }
        return DEFAULT_SYNC_TIMEOUT_MS;
    }

    /**
     * 准备输入数据
     */
    private Map<String, Object> prepareInputData(Map<String, Object> payload,
                                                  Map<String, String> headers,
                                                  TriggerRegistryEntity trigger) {
        Map<String, Object> input = new HashMap<>();

        // 添加请求数据
        input.put("data", payload != null ? payload : new HashMap<>());

        // 添加 HTTP 请求元数据
        Map<String, Object> httpMetadata = new HashMap<>();
        httpMetadata.put("headers", headers);
        httpMetadata.put("path", trigger.getWebhookPath());
        httpMetadata.put("method", "POST");
        httpMetadata.put("timestamp", System.currentTimeMillis());
        input.put("_http", httpMetadata);

        // 添加触发器配置中的初始数据
        Map<String, Object> config = trigger.getTriggerConfig();
        if (config != null && config.containsKey("inputData")) {
            Object inputData = config.get("inputData");
            if (inputData instanceof Map) {
                input.putAll((Map<String, Object>) inputData);
            }
        }

        return input;
    }

    /**
     * 加载工作流定义
     */
    private WorkflowDefinition loadWorkflowDefinition(String workflowId) {
        try {
            return workflowDefinitionService.getWorkflowDefinition(workflowId);
        } catch (Exception e) {
            throw new WorkflowValidationException("Workflow not found: " + workflowId, e);
        }
    }

    /**
     * 验证 HMAC 签名
     */
    private boolean verifySignature(Map<String, Object> payload, String secret, String signature) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }

        try {
            String expectedSignature = calculateSignature(payload, secret);
            return constantTimeEquals(expectedSignature, signature);
        } catch (Exception e) {
            log.error("Failed to verify signature", e);
            return false;
        }
    }

    /**
     * 计算请求签名
     */
    private String calculateSignature(Map<String, Object> payload, String secret) {
        try {
            String payloadStr = objectMapper.writeValueAsString(payload);
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payloadStr.getBytes());
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate signature", e);
        }
    }

    /**
     * 常量时间比较，防止时序攻击
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        byte[] aBytes = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }

    /**
     * 生成执行 ID
     */
    private String generateExecutionId(String workflowId) {
        return workflowId + "-" + System.currentTimeMillis() + "-" +
                UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 获取 Webhook 配置（用于查询接口）
     */
    public Optional<TriggerRegistryEntity> getWebhookByPath(String webhookPath) {
        return registryService.findByWebhookPath(webhookPath);
    }
}

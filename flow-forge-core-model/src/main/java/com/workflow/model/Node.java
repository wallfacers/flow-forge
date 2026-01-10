package com.workflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流节点模型
 * <p>
 * 定义DAG中的单个节点，包含节点ID、名称、类型和配置
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Node {

    /**
     * 节点唯一标识符
     */
    private String id;

    /**
     * 节点名称
     */
    private String name;

    /**
     * 节点类型
     */
    private NodeType type;

    /**
     * 节点配置参数
     * <p>
     * 根据节点类型不同，包含不同的配置项：
     * - HTTP: url, method, headers, body, timeout
     * - SCRIPT: language, code, timeout
     * - IF: condition, trueBranch, falseBranch
     * - LOG: message, level
     * </p>
     */
    @Builder.Default
    private Map<String, Object> config = new HashMap<>();

    /**
     * 显式依赖的节点ID列表
     * <p>
     * 用于定义节点执行顺序，DAG构建时会验证是否有循环依赖
     * </p>
     */
    @Builder.Default
    private List<String> dependencies = new ArrayList<>();

    /**
     * 节点描述
     */
    private String description;

    /**
     * 是否启用该节点
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * 重试次数
     */
    @Builder.Default
    private int retryCount = 0;

    /**
     * 重试间隔（毫秒）
     */
    @Builder.Default
    private long retryInterval = 1000;

    /**
     * 超时时间（毫秒）
     */
    @Builder.Default
    private long timeout = 30000;

    /**
     * 获取配置值
     *
     * @param key 配置键
     * @return 配置值，不存在返回null
     */
    public Object getConfigValue(String key) {
        return config.get(key);
    }

    /**
     * 获取字符串类型配置值
     *
     * @param key 配置键
     * @return 配置值，不存在返回null
     */
    public String getConfigString(String key) {
        Object value = config.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 获取整数类型配置值
     *
     * @param key 配置键
     * @return 配置值，不存在或类型错误返回null
     */
    public Integer getConfigInt(String key) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    /**
     * 获取布尔类型配置值
     *
     * @param key 配置键
     * @return 配置值，不存在返回false
     */
    public boolean getConfigBoolean(String key) {
        Object value = config.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
    }

    /**
     * 设置配置值
     *
     * @param key   配置键
     * @param value 配置值
     */
    public void setConfigValue(String key, Object value) {
        if (this.config == null) {
            this.config = new HashMap<>();
        }
        this.config.put(key, value);
    }

    /**
     * 验证节点配置是否有效
     *
     * @throws WorkflowValidationException 配置无效时抛出
     */
    public void validate() throws WorkflowValidationException {
        if (id == null || id.isBlank()) {
            throw new WorkflowValidationException("Node ID cannot be null or empty");
        }
        if (type == null) {
            throw new WorkflowValidationException("Node type cannot be null for node: " + id);
        }

        // 根据节点类型验证特定配置
        switch (type) {
            case HTTP -> validateHttpNode();
            case SCRIPT -> validateScriptNode();
            case IF -> validateIfNode();
        }
    }

    private void validateHttpNode() throws WorkflowValidationException {
        String url = getConfigString("url");
        if (url == null || url.isBlank()) {
            throw new WorkflowValidationException("HTTP node requires 'url' config: " + id);
        }
    }

    private void validateScriptNode() throws WorkflowValidationException {
        String code = getConfigString("code");
        if (code == null || code.isBlank()) {
            throw new WorkflowValidationException("Script node requires 'code' config: " + id);
        }
    }

    private void validateIfNode() throws WorkflowValidationException {
        String condition = getConfigString("condition");
        if (condition == null || condition.isBlank()) {
            throw new WorkflowValidationException("IF node requires 'condition' config: " + id);
        }
    }
}

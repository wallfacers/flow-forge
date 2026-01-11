package com.workflow.node.end;

import com.workflow.context.VariableResolver;
import com.workflow.model.ExecutionContext;
import com.workflow.model.Node;
import com.workflow.model.NodeResult;
import com.workflow.model.NodeType;
import com.workflow.node.AbstractNodeExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结束节点执行器
 * <p>
 * 标记工作流的结束点，支持聚合多个上游节点的输出。
 * <p>
 * 输出聚合配置格式：
 * <pre>
 * {
 *   "aggregateOutputs": {
 *     "result": {
 *       "fromNodes": ["node1", "node2"],
 *       "transform": {
 *         "userId": "{{node1.output.userId}}",
 *         "profile": "{{node2.output.profile}}",
 *         "status": "{{node1.output.status}}"
 *       }
 *     },
 *     "summary": {
 *       "fromNodes": ["node3"],
 *       "transform": {
 *         "count": "{{node3.output.count}}"
 *       }
 *     }
 *   }
 * }
 * </pre>
 * <p>
 * 如果没有配置聚合，则返回所有上游节点的输出。
 * <p>
 * 输出格式（无聚合配置时）：
 * <pre>
 * {
 *   "node1": { ... node1 output ... },
 *   "node2": { ... node2 output ... }
 * }
 * </pre>
 *
 * @see AbstractNodeExecutor
 */
@Component
public class EndNodeExecutor extends AbstractNodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(EndNodeExecutor.class);

    /** 变量表达式正则: {{nodeId.output.key}} 或 {{nodeId.key}} */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    public EndNodeExecutor(VariableResolver variableResolver) {
        super(variableResolver);
    }

    @Override
    public NodeType getSupportedType() {
        return NodeType.END;
    }

    @Override
    protected NodeResult doExecute(Node node,
                                   ExecutionContext context,
                                   Map<String, Object> resolvedConfig) {

        String nodeId = node.getId();

        log.info("End node executed: nodeId={}, workflowId={}",
                nodeId, context.getWorkflowId());

        Map<String, Object> output = new HashMap<>();

        // 检查是否有聚合配置
        Object aggregateConfig = resolvedConfig.get("aggregateOutputs");
        if (aggregateConfig instanceof Map<?, ?> aggregateMap) {
            // 执行聚合
            output = aggregateOutputs(aggregateMap, context, nodeId);
        } else {
            // 无聚合配置，返回所有已完成节点的输出
            output = collectAllOutputs(context, nodeId);
        }

        // 添加工作流执行元数据
        output.put("_metadata", buildMetadata(context, nodeId));

        return NodeResult.success(nodeId, output);
    }

    /**
     * 聚合指定节点的输出
     *
     * @param aggregateMap 聚合配置映射
     * @param context      执行上下文
     * @param nodeId       当前节点ID
     * @return 聚合后的输出
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> aggregateOutputs(Map<?, ?> aggregateMap,
                                                 ExecutionContext context,
                                                 String nodeId) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : aggregateMap.entrySet()) {
            String outputKey = (String) entry.getKey();
            Object configValue = entry.getValue();

            if (configValue instanceof Map<?, ?> transformConfig) {
                // 获取 fromNodes 列表
                List<String> fromNodes = extractFromNodes(transformConfig);

                // 获取 transform 配置
                Map<String, Object> transform = (Map<String, Object>) transformConfig.get("transform");

                if (transform != null && !transform.isEmpty()) {
                    // 执行转换
                    Object transformed = applyTransform(transform, context, nodeId, fromNodes);
                    result.put(outputKey, transformed);
                } else {
                    // 没有转换配置，直接合并 fromNodes 的输出
                    Map<String, Object> merged = mergeNodeOutputs(fromNodes, context);
                    result.put(outputKey, merged);
                }
            } else if (configValue instanceof String) {
                // 简单的字符串值，尝试解析变量
                String resolved = variableResolver.resolve((String) configValue, context);
                result.put(outputKey, resolved);
            } else {
                // 其他类型直接使用
                result.put(outputKey, configValue);
            }
        }

        return result;
    }

    /**
     * 从配置中提取 fromNodes 列表
     */
    private List<String> extractFromNodes(Map<?, ?> config) {
        Object fromNodesObj = config.get("fromNodes");
        List<String> fromNodes = new ArrayList<>();

        if (fromNodesObj instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    fromNodes.add(item.toString());
                }
            }
        } else if (fromNodesObj instanceof String str) {
            // 逗号分隔的字符串
            String[] parts = str.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    fromNodes.add(trimmed);
                }
            }
        }

        return fromNodes;
    }

    /**
     * 应用转换表达式
     *
     * @param transform  转换配置映射
     * @param context   执行上下文
     * @param nodeId    当前节点ID
     * @param fromNodes 源节点列表
     * @return 转换后的值
     */
    private Object applyTransform(Map<String, Object> transform,
                                  ExecutionContext context,
                                  String nodeId,
                                  List<String> fromNodes) {
        // 如果只有一个值且是简单类型，直接返回
        if (transform.size() == 1) {
            Map.Entry<String, Object> singleEntry = transform.entrySet().iterator().next();
            if (singleEntry.getKey().equals("_value") || transform.containsKey("_value")) {
                return resolveValue(singleEntry.getValue(), context, nodeId, fromNodes);
            }
        }

        // 转换为 Map
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : transform.entrySet()) {
            Object value = resolveValue(entry.getValue(), context, nodeId, fromNodes);
            result.put(entry.getKey(), value);
        }
        return result;
    }

    /**
     * 解析单个值（处理变量引用）
     */
    private Object resolveValue(Object value,
                                 ExecutionContext context,
                                 String nodeId,
                                 List<String> fromNodes) {
        if (value == null) {
            return null;
        }

        if (value instanceof String strValue) {
            // 检查是否包含变量引用
            if (strValue.contains("{{") && strValue.contains("}}")) {
                // 使用 VariableResolver 解析
                return variableResolver.resolve(strValue, context);
            }
            return strValue;
        }

        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                Object resolved = resolveValue(entry.getValue(), context, nodeId, fromNodes);
                result.put(entry.getKey().toString(), resolved);
            }
            return result;
        }

        if (value instanceof List<?> listValue) {
            List<Object> result = new ArrayList<>();
            for (Object item : listValue) {
                result.add(resolveValue(item, context, nodeId, fromNodes));
            }
            return result;
        }

        return value;
    }

    /**
     * 合并多个节点的输出
     */
    private Map<String, Object> mergeNodeOutputs(List<String> fromNodes, ExecutionContext context) {
        Map<String, Object> merged = new LinkedHashMap<>();

        for (String sourceNodeId : fromNodes) {
            NodeResult nodeResult = context.getNodeResults().get(sourceNodeId);
            if (nodeResult != null && nodeResult.getOutput() != null) {
                merged.putAll(nodeResult.getOutput());
            }
        }

        return merged;
    }

    /**
     * 收集所有已完成节点的输出
     */
    private Map<String, Object> collectAllOutputs(ExecutionContext context, String endNodeId) {
        Map<String, Object> output = new LinkedHashMap<>();

        for (Map.Entry<String, NodeResult> entry : context.getNodeResults().entrySet()) {
            String nodeId = entry.getKey();
            NodeResult result = entry.getValue();

            // 排除结束节点本身
            if (nodeId.equals(endNodeId)) {
                continue;
            }

            // 如果节点有输出，添加到结果中
            if (result.getOutput() != null && !result.getOutput().isEmpty()) {
                output.put(nodeId, result.getOutput());
            }
        }

        log.debug("Collected outputs from {} nodes for end node {}",
                output.size(), endNodeId);

        return output;
    }

    /**
     * 构建工作流执行元数据
     */
    private Map<String, Object> buildMetadata(ExecutionContext context, String nodeId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("endNodeId", nodeId);
        metadata.put("workflowId", context.getWorkflowId());
        metadata.put("tenantId", context.getTenantId());
        metadata.put("executionId", context.getExecutionId());
        metadata.put("completedAt", System.currentTimeMillis());

        // 统计节点执行情况
        int totalNodes = context.getNodeResults().size();
        long successCount = context.getNodeResults().values().stream()
                .filter(r -> com.workflow.model.ExecutionStatus.SUCCESS.equals(r.getStatus()))
                .count();
        long failureCount = context.getNodeResults().values().stream()
                .filter(r -> com.workflow.model.ExecutionStatus.FAILED.equals(r.getStatus()))
                .count();

        metadata.put("totalNodes", totalNodes);
        metadata.put("successCount", successCount);
        metadata.put("failureCount", failureCount);

        return metadata;
    }
}

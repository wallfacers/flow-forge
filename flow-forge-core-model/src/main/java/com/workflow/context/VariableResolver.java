package com.workflow.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.workflow.model.ExecutionContext;
import com.workflow.model.NodeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量解析器
 * <p>
 * 解析工作流中的变量引用，支持：
 * - {{nodeId}} 获取节点完整结果
 * - {{nodeId.output}} 获取节点output字段
 * - {{nodeId.output.data}} 使用JSONPath获取嵌套值
 * - {{global.varName}} 获取全局变量
 * - {{input.key}} 获取输入参数
 * - {{system.*}} 系统变量
 * </p>
 */
public class VariableResolver {

    private static final Logger logger = LoggerFactory.getLogger(VariableResolver.class);

    /**
     * 变量引用模式：{{...}}
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /**
     * JSONPath配置
     */
    private static final Configuration JSONPATH_CONFIG = Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS)
            .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
            .build();

    private final ObjectMapper objectMapper;

    public VariableResolver() {
        this.objectMapper = new ObjectMapper();
    }

    public VariableResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析字符串中的所有变量引用
     *
     * @param template  包含变量引用的模板字符串
     * @param context   执行上下文
     * @return 解析后的字符串
     */
    public String resolve(String template, ExecutionContext context) {
        if (template == null || template.isBlank()) {
            return template;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            Object value = resolveExpression(expression, context);

            // 替变量为实际值
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 解析表达式并返回值
     *
     * @param expression 表达式（不含{{}}）
     * @param context    执行上下文
     * @return 解析后的值
     */
    public Object resolveExpression(String expression, ExecutionContext context) {
        if (expression == null || expression.isBlank()) {
            return null;
        }

        // 处理全局变量引用
        if (expression.startsWith("global.")) {
            return resolveGlobalVariable(expression, context);
        }

        // 处理输入参数引用
        if (expression.startsWith("input.")) {
            return resolveInputVariable(expression, context);
        }

        // 处理系统变量
        if (expression.startsWith("system.")) {
            return resolveSystemVariable(expression, context);
        }

        // 处理节点结果引用
        return resolveNodeReference(expression, context);
    }

    /**
     * 解析全局变量
     *
     * @param expression 表达式（如：global.apiKey）
     * @param context    执行上下文
     * @return 变量值
     */
    private Object resolveGlobalVariable(String expression, ExecutionContext context) {
        String varName = expression.substring(7); // 去掉"global."
        return context.getGlobalVariable(varName);
    }

    /**
     * 解析输入参数
     *
     * @param expression 表达式（如：input.userId）
     * @param context    执行上下文
     * @return 参数值
     */
    private Object resolveInputVariable(String expression, ExecutionContext context) {
        String key = expression.substring(6); // 去掉"input."
        Map<String, Object> input = context.getInput();
        return input != null ? input.get(key) : null;
    }

    /**
     * 解析系统变量
     *
     * @param expression 表达式（如：system.executionId）
     * @param context    执行上下文
     * @return 系统变量值
     */
    private Object resolveSystemVariable(String expression, ExecutionContext context) {
        String varName = expression.substring(7); // 去掉"system."

        return switch (varName) {
            case "executionId" -> context.getExecutionId();
            case "workflowId" -> context.getWorkflowId();
            case "tenantId" -> context.getTenantId();
            case "currentTime" -> System.currentTimeMillis();
            case "startTime" -> context.getStartTime() != null ?
                    context.getStartTime().toEpochMilli() : null;
            case "status" -> context.getStatus();
            default -> null;
        };
    }

    /**
     * 解析节点结果引用
     *
     * @param expression 表达式（如：node1.output.data.userId）
     * @param context    执行上下文
     * @return 引用值
     */
    private Object resolveNodeReference(String expression, ExecutionContext context) {
        // 分割节点ID和路径
        int dotIndex = expression.indexOf('.');
        String nodeId;
        String path;

        if (dotIndex > 0) {
            nodeId = expression.substring(0, dotIndex);
            path = expression.substring(dotIndex + 1);
        } else {
            nodeId = expression;
            path = null;
        }

        NodeResult result = context.getNodeResult(nodeId);
        if (result == null) {
            logger.warn("Node result not found for reference: {}", expression);
            return null;
        }

        // 如果没有路径，返回整个结果
        if (path == null || path.isBlank()) {
            return result;
        }

        // 解析路径
        return resolvePath(result.getOutput(), path);
    }

    /**
     * 使用JSONPath风格解析路径
     *
     * @param data 数据对象
     * @param path 路径（如：output.data.userId）
     * @return 解析后的值
     */
    public Object resolvePath(Object data, String path) {
        if (data == null || path == null || path.isBlank()) {
            return data;
        }

        // 将点分隔路径转换为JSONPath表达式
        String jsonPath = "$." + path;

        try {
            // 转换为JSON文档
            Object document = convertToJsonDocument(data);

            // 使用JSONPath解析
            DocumentContext jsonContext = JsonPath.using(JSONPATH_CONFIG).parse(document);
            Object result = jsonContext.read(jsonPath);

            // 处理结果
            if (result instanceof List) {
                List<?> list = (List<?>) result;
                return list.size() == 1 ? list.get(0) : list;
            }

            return result;

        } catch (Exception e) {
            logger.debug("Failed to resolve path: {}, error: {}", path, e.getMessage());

            // 降级到简单的点分隔解析
            return resolveBySimplePath(data, path);
        }
    }

    /**
     * 简单的点分隔路径解析（降级方案）
     *
     * @param data 数据对象
     * @param path 路径
     * @return 解析后的值
     */
    private Object resolveBySimplePath(Object data, String path) {
        if (data == null) {
            return null;
        }

        String[] parts = path.split("\\.");
        Object current = data;

        for (String part : parts) {
            if (current == null) {
                return null;
            }

            // 处理数组索引 [0]
            if (part.contains("[") && part.endsWith("]")) {
                String fieldPart = part.substring(0, part.indexOf("["));
                String indexPart = part.substring(part.indexOf("[") + 1, part.length() - 1);

                if (!fieldPart.isEmpty()) {
                    current = getFieldValue(current, fieldPart);
                }

                if (current instanceof List) {
                    try {
                        int index = Integer.parseInt(indexPart);
                        current = ((List<?>) current).get(index);
                    } catch (NumberFormatException | IndexOutOfBoundsException e) {
                        return null;
                    }
                }
            } else {
                current = getFieldValue(current, part);
            }
        }

        return current;
    }

    /**
     * 从对象获取字段值
     *
     * @param obj  对象
     * @param field 字段名
     * @return 字段值
     */
    private Object getFieldValue(Object obj, String field) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).get(field);
        }

        // 使用Jackson读取字段
        try {
            JsonNode node = objectMapper.valueToTree(obj);
            JsonNode fieldNode = node.get(field);
            if (fieldNode == null) {
                return null;
            }

            if (fieldNode.isValueNode()) {
                return fieldNode.asText();
            } else if (fieldNode.isArray()) {
                List<Object> list = new ArrayList<>();
                for (JsonNode item : fieldNode) {
                    list.add(jsonNodeToObject(item));
                }
                return list;
            } else if (fieldNode.isObject()) {
                return jsonNodeToMap(fieldNode);
            }

            return fieldNode.toString();

        } catch (Exception e) {
            logger.debug("Failed to get field value: {} from {}", field, obj.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 将对象转换为JSON文档（用于JSONPath解析）
     */
    private Object convertToJsonDocument(Object data) {
        if (data == null) {
            return null;
        }

        // 如果已经是JsonNode或基本类型，直接返回
        if (data instanceof JsonNode ||
            data instanceof String ||
            data instanceof Number ||
            data instanceof Boolean) {
            return data;
        }

        // 转换为JsonNode
        return objectMapper.valueToTree(data);
    }

    /**
     * JsonNode转Map
     */
    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        Map<String, Object> map = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            map.put(entry.getKey(), jsonNodeToObject(entry.getValue()));
        }
        return map;
    }

    /**
     * JsonNode转Object
     */
    private Object jsonNodeToObject(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isInt()) {
            return node.asInt();
        } else if (node.isLong()) {
            return node.asLong();
        } else if (node.isDouble()) {
            return node.asDouble();
        } else if (node.isTextual()) {
            return node.asText();
        } else if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode item : node) {
                list.add(jsonNodeToObject(item));
            }
            return list;
        } else if (node.isObject()) {
            return jsonNodeToMap(node);
        }
        return node.toString();
    }

    /**
     * 解析Map中的所有变量引用
     *
     * @param map    包含变量引用的Map
     * @param context 执行上下文
     * @return 解析后的新Map
     */
    public Map<String, Object> resolveMap(Map<String, Object> map, ExecutionContext context) {
        if (map == null || map.isEmpty()) {
            return map;
        }

        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = resolveValue(entry.getValue(), context);
            result.put(entry.getKey(), value);
        }
        return result;
    }

    /**
     * 递归解析值中的变量引用
     *
     * @param value   原始值
     * @param context 执行上下文
     * @return 解析后的值
     */
    public Object resolveValue(Object value, ExecutionContext context) {
        if (value == null) {
            return null;
        }

        if (value instanceof String) {
            String strValue = (String) value;
            // 检查是否包含变量引用
            if (strValue.contains("{{")) {
                // 尝试解析为表达式
                Matcher matcher = VARIABLE_PATTERN.matcher(strValue);
                if (matcher.matches()) {
                    // 整个字符串是一个变量引用，返回解析后的对象
                    String expression = matcher.group(1).trim();
                    return resolveExpression(expression, context);
                } else {
                    // 字符串中包含变量引用，进行字符串替换
                    return resolve(strValue, context);
                }
            }
            return value;
        }

        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return resolveMap(map, context);
        }

        if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(resolveValue(item, context));
            }
            return result;
        }

        return value;
    }

    /**
     * 提取字符串中的所有变量引用表达式
     *
     * @param template 模板字符串
     * @return 变量表达式列表
     */
    public List<String> extractExpressions(String template) {
        if (template == null || template.isBlank()) {
            return Collections.emptyList();
        }

        List<String> expressions = new ArrayList<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            expressions.add(matcher.group(1).trim());
        }

        return expressions;
    }

    /**
     * 检查字符串是否包含变量引用
     *
     * @param str 字符串
     * @return true表示包含变量引用
     */
    public boolean hasVariableReference(String str) {
        return str != null && str.contains("{{");
    }
}

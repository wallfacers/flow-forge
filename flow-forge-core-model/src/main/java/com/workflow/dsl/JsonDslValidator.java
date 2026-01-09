package com.workflow.dsl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.model.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JSON DSL验证器
 * <p>
 * 在解析前验证JSON格式是否符合规范，提供更友好的错误提示
 * </p>
 */
public class JsonDslValidator {

    private final ObjectMapper objectMapper;

    public JsonDslValidator() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 验证JSON字符串格式
     *
     * @param json JSON字符串
     * @return 验证结果
     */
    public ValidationResult validate(String json) {
        ValidationResult result = new ValidationResult();

        try {
            JsonNode root = objectMapper.readTree(json);

            // 验证顶层是对象
            if (!root.isObject()) {
                result.addError("Root must be a JSON object");
                return result;
            }

            // 验证必需字段
            validateRequiredFields(root, result);

            // 验证节点数组
            if (root.has("nodes")) {
                validateNodes(root.get("nodes"), result);
            }

            // 验证边数组
            if (root.has("edges")) {
                validateEdges(root.get("edges"), result);
            }

            // 验证节点和边的引用一致性
            if (root.has("nodes") && root.has("edges")) {
                validateReferences(root, result);
            }

        } catch (Exception e) {
            result.addError("Failed to parse JSON: " + e.getMessage());
        }

        return result;
    }

    /**
     * 验证必需字段
     */
    private void validateRequiredFields(JsonNode root, ValidationResult result) {
        if (!root.has("id") || root.get("id").isMissingNode() || root.get("id").isNull()) {
            result.addError("Missing required field: 'id'");
        } else if (!root.get("id").isTextual()) {
            result.addError("Field 'id' must be a string");
        }

        if (!root.has("name") || root.get("name").isMissingNode() || root.get("name").isNull()) {
            result.addError("Missing required field: 'name'");
        } else if (!root.get("name").isTextual()) {
            result.addError("Field 'name' must be a string");
        }

        if (!root.has("nodes") || root.get("nodes").isMissingNode() || root.get("nodes").isNull()) {
            result.addError("Missing required field: 'nodes'");
        } else if (!root.get("nodes").isArray()) {
            result.addError("Field 'nodes' must be an array");
        }
    }

    /**
     * 验证节点数组
     */
    private void validateNodes(JsonNode nodesNode, ValidationResult result) {
        if (!nodesNode.isArray()) {
            result.addError("Field 'nodes' must be an array");
            return;
        }

        if (nodesNode.size() == 0) {
            result.addError("Field 'nodes' must contain at least one node");
            return;
        }

        Set<String> nodeIds = new HashSet<>();

        for (int i = 0; i < nodesNode.size(); i++) {
            JsonNode nodeNode = nodesNode.get(i);

            if (!nodeNode.isObject()) {
                result.addError("Node at index " + i + " must be an object");
                continue;
            }

            // 验证节点ID
            if (!nodeNode.has("id") || nodeNode.get("id").isMissingNode()) {
                result.addError("Node at index " + i + " is missing required field: 'id'");
            } else if (!nodeNode.get("id").isTextual()) {
                result.addError("Node at index " + i + " has invalid 'id' type (expected string)");
            } else {
                String nodeId = nodeNode.get("id").asText();
                if (nodeIds.contains(nodeId)) {
                    result.addError("Duplicate node ID: " + nodeId);
                }
                nodeIds.add(nodeId);
            }

            // 验证节点类型
            if (!nodeNode.has("type") || nodeNode.get("type").isMissingNode()) {
                result.addError("Node '" + nodeNode.get("id").asText("?") + "' is missing required field: 'type'");
            } else if (!nodeNode.get("type").isTextual()) {
                result.addError("Node '" + nodeNode.get("id").asText("?") + "' has invalid 'type' type (expected string)");
            } else {
                // 验证节点类型是否有效
                String type = nodeNode.get("type").asText();
                try {
                    NodeType.fromCode(type);
                } catch (IllegalArgumentException e) {
                    result.addError("Node '" + nodeNode.get("id").asText("?") + "' has invalid type: " + type);
                }
            }

            // 验证特定节点类型的配置
            if (nodeNode.has("type") && nodeNode.get("type").isTextual()) {
                String type = nodeNode.get("type").asText();
                validateNodeConfig(nodeNode, type, result);
            }
        }
    }

    /**
     * 验证节点配置
     */
    private void validateNodeConfig(JsonNode nodeNode, String type, ValidationResult result) {
        String nodeId = nodeNode.get("id").asText("?");

        try {
            NodeType nodeType = NodeType.fromCode(type);

            switch (nodeType) {
                case HTTP:
                    if (!nodeNode.has("config") || !nodeNode.get("config").has("url")) {
                        result.addWarning("HTTP node '" + nodeId + "' should have 'config.url' field");
                    }
                    break;
                case SCRIPT:
                    if (!nodeNode.has("config") || !nodeNode.get("config").has("code")) {
                        result.addWarning("Script node '" + nodeId + "' should have 'config.code' field");
                    }
                    break;
                case IF:
                    if (!nodeNode.has("config") || !nodeNode.get("config").has("condition")) {
                        result.addWarning("IF node '" + nodeId + "' should have 'config.condition' field");
                    }
                    break;
            }
        } catch (IllegalArgumentException ignored) {
            // 节点类型无效，已在之前报错
        }
    }

    /**
     * 验证边数组
     */
    private void validateEdges(JsonNode edgesNode, ValidationResult result) {
        if (!edgesNode.isArray()) {
            result.addError("Field 'edges' must be an array");
            return;
        }

        for (int i = 0; i < edgesNode.size(); i++) {
            JsonNode edgeNode = edgesNode.get(i);

            if (!edgeNode.isObject()) {
                result.addError("Edge at index " + i + " must be an object");
                continue;
            }

            // 验证源节点
            if (!edgeNode.has("sourceNodeId") || edgeNode.get("sourceNodeId").isMissingNode()) {
                result.addError("Edge at index " + i + " is missing required field: 'sourceNodeId'");
            } else if (!edgeNode.get("sourceNodeId").isTextual()) {
                result.addError("Edge at index " + i + " has invalid 'sourceNodeId' type (expected string)");
            }

            // 验证目标节点
            if (!edgeNode.has("targetNodeId") || edgeNode.get("targetNodeId").isMissingNode()) {
                result.addError("Edge at index " + i + " is missing required field: 'targetNodeId'");
            } else if (!edgeNode.get("targetNodeId").isTextual()) {
                result.addError("Edge at index " + i + " has invalid 'targetNodeId' type (expected string)");
            }

            // 检查自环
            if (edgeNode.has("sourceNodeId") && edgeNode.has("targetNodeId") &&
                edgeNode.get("sourceNodeId").equals(edgeNode.get("targetNodeId"))) {
                result.addError("Edge at index " + i + " forms a self-loop on node: " +
                    edgeNode.get("sourceNodeId").asText());
            }
        }
    }

    /**
     * 验证节点和边的引用一致性
     */
    private void validateReferences(JsonNode root, ValidationResult result) {
        JsonNode nodesNode = root.get("nodes");
        JsonNode edgesNode = root.get("edges");

        // 收集所有节点ID
        Set<String> nodeIds = new HashSet<>();
        for (JsonNode nodeNode : nodesNode) {
            if (nodeNode.has("id")) {
                nodeIds.add(nodeNode.get("id").asText());
            }
        }

        // 验证边引用的节点是否存在
        for (int i = 0; i < edgesNode.size(); i++) {
            JsonNode edgeNode = edgesNode.get(i);

            if (edgeNode.has("sourceNodeId")) {
                String sourceId = edgeNode.get("sourceNodeId").asText();
                if (!nodeIds.contains(sourceId)) {
                    result.addError("Edge at index " + i + " references non-existent source node: " + sourceId);
                }
            }

            if (edgeNode.has("targetNodeId")) {
                String targetId = edgeNode.get("targetNodeId").asText();
                if (!nodeIds.contains(targetId)) {
                    result.addError("Edge at index " + i + " references non-existent target node: " + targetId);
                }
            }
        }
    }

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public String getErrorMessageSummary() {
            if (errors.isEmpty()) {
                return "Validation passed";
            }
            return "Validation failed with " + errors.size() + " error(s):\n" +
                String.join("\n", errors);
        }
    }
}

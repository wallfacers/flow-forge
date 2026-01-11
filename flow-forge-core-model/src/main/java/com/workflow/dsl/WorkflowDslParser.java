package com.workflow.dsl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.workflow.model.WorkflowDefinition;
import com.workflow.model.WorkflowValidationException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON DSL解析器
 * <p>
 * 将JSON格式的流程定义解析为WorkflowDefinition对象
 * </p>
 * <p>
 * JSON DSL格式示例:
 * <pre>
 * {
 *   "id": "workflow-001",
 *   "name": "示例工作流",
 *   "description": "一个简单的HTTP调用工作流",
 *   "version": "1.0.0",
 *   "tenantId": "tenant-001",
 *   "nodes": [
 *     {
 *       "id": "node-1",
 *       "name": "HTTP请求",
 *       "type": "http",
 *       "config": {
 *         "url": "https://api.example.com/data",
 *         "method": "GET",
 *         "timeout": 5000
 *       }
 *     },
 *     {
 *       "id": "node-2",
 *       "name": "日志输出",
 *       "type": "log",
 *       "config": {
 *         "message": "{{node-1.output}}"
 *       }
 *     }
 *   ],
 *   "edges": [
 *     {
 *       "sourceNodeId": "node-1",
 *       "targetNodeId": "node-2"
 *     }
 *   ],
 *   "globalVariables": {
 *     "apiKey": "xxx"
 *   }
 * }
 * </pre>
 * </p>
 */
public class WorkflowDslParser {

    private final ObjectMapper objectMapper;

    public WorkflowDslParser() {
        this.objectMapper = createObjectMapper();
    }

    public WorkflowDslParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 创建配置好的ObjectMapper
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
        return mapper;
    }

    /**
     * 从JSON字符串解析工作流定义
     *
     * @param json JSON字符串
     * @return 工作流定义对象
     * @throws WorkflowParseException 解析失败时抛出
     */
    public WorkflowDefinition parse(String json) throws WorkflowParseException {
        try {
            return parse(json, true);
        } catch (WorkflowValidationException e) {
            throw new WorkflowParseException("Workflow validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * 从JSON字符串解析工作流定义
     *
     * @param json             JSON字符串
     * @param validate         是否验证工作流定义
     * @return 工作流定义对象
     * @throws WorkflowParseException 解析失败时抛出
     * @throws WorkflowValidationException 验证失败时抛出
     */
    public WorkflowDefinition parse(String json, boolean validate)
            throws WorkflowParseException, WorkflowValidationException {
        try {
            JsonNode root = objectMapper.readTree(json);

            // 基本字段检查
            if (!root.has("id")) {
                throw new WorkflowParseException("Missing required field: id");
            }
            if (!root.has("name")) {
                throw new WorkflowParseException("Missing required field: name");
            }

            WorkflowDefinition definition = objectMapper.treeToValue(root, WorkflowDefinition.class);

            // 创建时间戳
            definition.markCreated();

            // 验证工作流
            if (validate) {
                definition.validate();
            }

            return definition;

        } catch (IOException e) {
            throw new WorkflowParseException("Failed to parse JSON: " + e.getMessage(), e);
        }
    }

    /**
     * 从文件解析工作流定义
     *
     * @param file 文件对象
     * @return 工作流定义对象
     * @throws WorkflowParseException 解析失败时抛出
     */
    public WorkflowDefinition parse(File file) throws WorkflowParseException {
        try {
            String json = Files.readString(file.toPath());
            return parse(json);
        } catch (IOException e) {
            throw new WorkflowParseException("Failed to read file: " + e.getMessage(), e);
        }
    }

    /**
     * 从路径解析工作流定义
     *
     * @param path 文件路径
     * @return 工作流定义对象
     * @throws WorkflowParseException 解析失败时抛出
     */
    public WorkflowDefinition parse(Path path) throws WorkflowParseException {
        try {
            String json = Files.readString(path);
            return parse(json);
        } catch (IOException e) {
            throw new WorkflowParseException("Failed to read file: " + e.getMessage(), e);
        }
    }

    /**
     * 从输入流解析工作流定义
     *
     * @param inputStream 输入流
     * @return 工作流定义对象
     * @throws WorkflowParseException 解析失败时抛出
     */
    public WorkflowDefinition parse(InputStream inputStream) throws WorkflowParseException {
        try {
            JsonNode root = objectMapper.readTree(inputStream);
            WorkflowDefinition definition = objectMapper.treeToValue(root, WorkflowDefinition.class);
            definition.markCreated();
            definition.validate();
            return definition;
        } catch (IOException e) {
            throw new WorkflowParseException("Failed to parse from input stream: " + e.getMessage(), e);
        } catch (WorkflowValidationException e) {
            throw new WorkflowParseException("Workflow validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * 将工作流定义序列化为JSON字符串
     *
     * @param definition 工作流定义
     * @return JSON字符串
     * @throws WorkflowParseException 序列化失败时抛出
     */
    public String toJson(WorkflowDefinition definition) throws WorkflowParseException {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(definition);
        } catch (IOException e) {
            throw new WorkflowParseException("Failed to serialize workflow: " + e.getMessage(), e);
        }
    }

    /**
     * 将工作流定义序列化为JSON字符串（紧凑格式）
     *
     * @param definition 工作流定义
     * @return JSON字符串
     * @throws WorkflowParseException 序列化失败时抛出
     */
    public String toCompactJson(WorkflowDefinition definition) throws WorkflowParseException {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (IOException e) {
            throw new WorkflowParseException("Failed to serialize workflow: " + e.getMessage(), e);
        }
    }

    /**
     * 解析JSON但不验证（用于快速检查格式）
     *
     * @param json JSON字符串
     * @return 解析结果
     */
    public ParseResult parseWithoutValidation(String json) {
        ParseResult result = new ParseResult();
        try {
            JsonNode root = objectMapper.readTree(json);

            // 检查基本字段
            result.setHasId(root.has("id"));
            result.setHasName(root.has("name"));
            result.setHasNodes(root.has("nodes") && root.get("nodes").isArray());
            result.setHasEdges(root.has("edges") && root.get("edges").isArray());

            // 统计节点和边数量
            if (result.hasNodes()) {
                result.setNodeCount(root.get("nodes").size());
            }
            if (result.hasEdges()) {
                result.setEdgeCount(root.get("edges").size());
            }

            result.setValid(result.hasId() && result.hasName() && result.hasNodes());
            result.setErrorMessage(result.isValid() ? null : "Missing required fields");

        } catch (IOException e) {
            result.setValid(false);
            result.setErrorMessage("Invalid JSON: " + e.getMessage());
        }
        return result;
    }

    /**
     * 解析结果（用于快速检查）
     */
    public static class ParseResult {
        private boolean valid;
        private String errorMessage;
        private boolean hasId;
        private boolean hasName;
        private boolean hasNodes;
        private boolean hasEdges;
        private int nodeCount;
        private int edgeCount;

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public boolean hasId() {
            return hasId;
        }

        public void setHasId(boolean hasId) {
            this.hasId = hasId;
        }

        public boolean hasName() {
            return hasName;
        }

        public void setHasName(boolean hasName) {
            this.hasName = hasName;
        }

        public boolean hasNodes() {
            return hasNodes;
        }

        public void setHasNodes(boolean hasNodes) {
            this.hasNodes = hasNodes;
        }

        public boolean hasEdges() {
            return hasEdges;
        }

        public void setHasEdges(boolean hasEdges) {
            this.hasEdges = hasEdges;
        }

        public int getNodeCount() {
            return nodeCount;
        }

        public void setNodeCount(int nodeCount) {
            this.nodeCount = nodeCount;
        }

        public int getEdgeCount() {
            return edgeCount;
        }

        public void setEdgeCount(int edgeCount) {
            this.edgeCount = edgeCount;
        }
    }
}

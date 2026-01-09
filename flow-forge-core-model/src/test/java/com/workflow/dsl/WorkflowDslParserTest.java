package com.workflow.dsl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DSL解析器测试
 */
@DisplayName("DSL解析器测试")
class WorkflowDslParserTest {

    private WorkflowDslParser parser;

    @BeforeEach
    void setUp() {
        parser = new WorkflowDslParser();
    }

    @Test
    @DisplayName("应该成功解析基本的工作流定义")
    void shouldParseBasicWorkflow() throws WorkflowParseException {
        String json = """
            {
              "id": "test-workflow",
              "name": "测试工作流",
              "nodes": [
                {
                  "id": "node1",
                  "name": "HTTP请求",
                  "type": "http",
                  "config": {
                    "url": "https://api.example.com",
                    "method": "GET"
                  }
                }
              ]
            }
            """;

        assertDoesNotThrow(() -> parser.parse(json));
    }

    @Test
    @DisplayName("应该检测缺失的必需字段")
    void shouldDetectMissingRequiredFields() {
        String json = """
            {
              "name": "缺少ID的工作流",
              "nodes": []
            }
            """;

        WorkflowParseException exception = assertThrows(
                WorkflowParseException.class,
                () -> parser.parse(json)
        );

        assertTrue(exception.getMessage().contains("id"));
    }

    @Test
    @DisplayName("应该拒绝无效的JSON")
    void shouldRejectInvalidJson() {
        String json = "{ invalid json }";

        assertThrows(WorkflowParseException.class, () -> parser.parse(json));
    }

    @Test
    @DisplayName("应该支持快速解析检查")
    void shouldSupportQuickParseCheck() {
        String json = """
            {
              "id": "quick-test",
              "name": "快速测试",
              "nodes": [
                {"id": "n1", "name": "N1", "type": "log"},
                {"id": "n2", "name": "N2", "type": "log"}
              ],
              "edges": [
                {"sourceNodeId": "n1", "targetNodeId": "n2"}
              ]
            }
            """;

        WorkflowDslParser.ParseResult result = parser.parseWithoutValidation(json);

        assertTrue(result.isValid());
        assertTrue(result.hasId());
        assertTrue(result.hasName());
        assertTrue(result.hasNodes());
        assertEquals(2, result.getNodeCount());
    }

    @Test
    @DisplayName("应该正确序列化工作流定义")
    void shouldSerializeWorkflow() throws Exception {
        String json = """
            {
              "id": "serialize-test",
              "name": "序列化测试",
              "nodes": [
                {"id": "n1", "name": "N1", "type": "log"}
              ]
            }
            """;

        var definition = parser.parse(json, false);
        String serialized = parser.toJson(definition);

        assertNotNull(serialized);
        assertTrue(serialized.contains("serialize-test"));
        assertTrue(serialized.contains("序列化测试"));
    }
}

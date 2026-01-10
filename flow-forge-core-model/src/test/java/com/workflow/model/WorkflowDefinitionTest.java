package com.workflow.model;

import com.workflow.dsl.WorkflowDslParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工作流定义测试
 */
@DisplayName("工作流定义测试")
class WorkflowDefinitionTest {

    private WorkflowDslParser parser;

    @BeforeEach
    void setUp() {
        parser = new WorkflowDslParser();
    }

    @Test
    @DisplayName("应该成功解析简单的工作流JSON")
    void shouldParseSimpleWorkflow() throws Exception {
        String json = """
            {
              "id": "wf-001",
              "name": "简单工作流",
              "nodes": [
                {"id": "node1", "name": "节点1", "type": "log"},
                {"id": "node2", "name": "节点2", "type": "log"}
              ],
              "edges": [
                {"sourceNodeId": "node1", "targetNodeId": "node2"}
              ]
            }
            """;

        WorkflowDefinition definition = parser.parse(json);

        assertNotNull(definition);
        assertEquals("wf-001", definition.getId());
        assertEquals("简单工作流", definition.getName());
        assertEquals(2, definition.getNodes().size());
        assertEquals(1, definition.getEdges().size());
    }

    @Test
    @DisplayName("应该检测到循环依赖")
    void shouldDetectCycle() throws Exception {
        String json = """
            {
              "id": "wf-cycle",
              "name": "循环工作流",
              "nodes": [
                {"id": "node1", "name": "节点1", "type": "log"},
                {"id": "node2", "name": "节点2", "type": "log"}
              ],
              "edges": [
                {"sourceNodeId": "node1", "targetNodeId": "node2"},
                {"sourceNodeId": "node2", "targetNodeId": "node1"}
              ]
            }
            """;

        // 使用不带验证的解析，然后手动验证
        WorkflowDefinition definition = parser.parse(json, false);

        WorkflowValidationException exception = assertThrows(
                WorkflowValidationException.class,
                definition::validate
        );

        assertTrue(exception.getMessage().contains("cycle"));
    }

    @Test
    @DisplayName("应该检测到孤立节点")
    void shouldDetectIsolatedNode() throws Exception {
        String json = """
            {
              "id": "wf-isolated",
              "name": "孤立节点工作流",
              "nodes": [
                {"id": "node1", "name": "节点1", "type": "log"},
                {"id": "node2", "name": "孤立节点", "type": "log"},
                {"id": "node3", "name": "节点3", "type": "log"}
              ],
              "edges": [
                {"sourceNodeId": "node1", "targetNodeId": "node3"}
              ]
            }
            """;

        // 使用不带验证的解析，然后手动验证
        WorkflowDefinition definition = parser.parse(json, false);

        WorkflowValidationException exception = assertThrows(
                WorkflowValidationException.class,
                definition::validate
        );

        assertTrue(exception.getMessage().contains("isolated"));
    }

    @Test
    @DisplayName("应该正确计算入度")
    void shouldCalculateInDegrees() throws Exception {
        String json = """
            {
              "id": "wf-indegree",
              "name": "入度测试",
              "nodes": [
                {"id": "start", "name": "开始", "type": "log"},
                {"id": "middle", "name": "中间", "type": "log"},
                {"id": "end", "name": "结束", "type": "log"}
              ],
              "edges": [
                {"sourceNodeId": "start", "targetNodeId": "middle"},
                {"sourceNodeId": "middle", "targetNodeId": "end"}
              ]
            }
            """;

        WorkflowDefinition definition = parser.parse(json);
        definition.buildGraph();

        assertEquals(0, definition.getInDegree("start"));
        assertEquals(1, definition.getInDegree("middle"));
        assertEquals(1, definition.getInDegree("end"));
    }

    @Test
    @DisplayName("应该找到起始节点")
    void shouldFindStartNodes() throws Exception {
        String json = """
            {
              "id": "wf-start",
              "name": "起始节点测试",
              "nodes": [
                {"id": "start", "name": "开始", "type": "log"},
                {"id": "process", "name": "处理", "type": "log"},
                {"id": "end", "name": "结束", "type": "log"}
              ],
              "edges": [
                {"sourceNodeId": "start", "targetNodeId": "process"},
                {"sourceNodeId": "process", "targetNodeId": "end"}
              ]
            }
            """;

        WorkflowDefinition definition = parser.parse(json);
        java.util.List<Node> startNodes = definition.getStartNodes();

        assertEquals(1, startNodes.size());
        assertEquals("start", startNodes.get(0).getId());
    }

    @Test
    @DisplayName("序列化和反序列化应该保持一致性")
    void shouldSerializeConsistently() throws Exception {
        String originalJson = """
            {
              "id": "wf-serialize",
              "name": "序列化测试",
              "description": "测试序列化一致性",
              "version": "2.0.0",
              "tenantId": "tenant-001",
              "nodes": [
                {"id": "n1", "name": "N1", "type": "http", "config": {"url": "http://example.com"}},
                {"id": "n2", "name": "N2", "type": "log"}
              ],
              "edges": [
                {"sourceNodeId": "n1", "targetNodeId": "n2"}
              ],
              "globalVariables": {"key": "value"}
            }
            """;

        WorkflowDefinition definition = parser.parse(originalJson);
        String serializedJson = parser.toJson(definition);
        WorkflowDefinition deserialized = parser.parse(serializedJson);

        assertEquals(definition.getId(), deserialized.getId());
        assertEquals(definition.getName(), deserialized.getName());
        assertEquals(definition.getNodes().size(), deserialized.getNodes().size());
        assertEquals(definition.getEdges().size(), deserialized.getEdges().size());
    }

    @Test
    @DisplayName("应该支持所有节点类型")
    void shouldSupportAllNodeTypes() throws Exception {
        for (NodeType type : NodeType.values()) {
            String json;
            if (type == NodeType.HTTP) {
                // HTTP节点需要url配置
                json = String.format("""
                    {
                      "id": "wf-type-%s",
                      "name": "%s类型测试",
                      "nodes": [
                        {"id": "node1", "name": "测试节点", "type": "%s", "config": {"url": "http://example.com"}}
                      ]
                    }
                    """, type.getCode(), type.getDescription(), type.getCode());
            } else if (type == NodeType.SCRIPT) {
                // SCRIPT节点需要code配置
                json = String.format("""
                    {
                      "id": "wf-type-%s",
                      "name": "%s类型测试",
                      "nodes": [
                        {"id": "node1", "name": "测试节点", "type": "%s", "config": {"code": "console.log('test')"}}
                      ]
                    }
                    """, type.getCode(), type.getDescription(), type.getCode());
            } else if (type == NodeType.IF) {
                // IF节点需要condition配置
                json = String.format("""
                    {
                      "id": "wf-type-%s",
                      "name": "%s类型测试",
                      "nodes": [
                        {"id": "node1", "name": "测试节点", "type": "%s", "config": {"condition": "true"}}
                      ]
                    }
                    """, type.getCode(), type.getDescription(), type.getCode());
            } else {
                json = String.format("""
                    {
                      "id": "wf-type-%s",
                      "name": "%s类型测试",
                      "nodes": [
                        {"id": "node1", "name": "测试节点", "type": "%s"}
                      ]
                    }
                    """, type.getCode(), type.getDescription(), type.getCode());
            }

            assertDoesNotThrow(() -> parser.parse(json),
                    "应该支持节点类型: " + type);
        }
    }
}

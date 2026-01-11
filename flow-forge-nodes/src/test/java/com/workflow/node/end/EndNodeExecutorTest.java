package com.workflow.node.end;

import com.workflow.context.VariableResolver;
import com.workflow.model.ExecutionContext;
import com.workflow.model.ExecutionStatus;
import com.workflow.model.Node;
import com.workflow.model.NodeType;
import com.workflow.model.NodeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * EndNodeExecutor 测试类
 * <p>
 * 测试工作流结束节点的执行逻辑和输出聚合功能
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("END 节点执行器测试")
public class EndNodeExecutorTest {

    private static final String TENANT_ID = "test-tenant";
    private static final String WORKFLOW_ID = "test-workflow";
    private static final String EXECUTION_ID = "exec-123";

    @Mock
    private VariableResolver variableResolver;

    private EndNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new EndNodeExecutor(variableResolver);
        // 默认变量解析行为：原样返回
        when(variableResolver.resolve(any(), any())).thenAnswer(invocation -> {
            String expression = (String) invocation.getArgument(0);
            // 简单模拟变量解析
            if (expression.contains("{{") && expression.contains("}}")) {
                return expression.replaceAll("\\{\\{(.*?)\\}\\}", "$1");
            }
            return expression;
        });
    }

    @Nested
    @DisplayName("基本功能测试")
    class BasicTests {

        @Test
        @DisplayName("支持的节点类型应为 END")
        void testGetSupportedType_IsEnd() {
            assertThat(executor.getSupportedType()).isEqualTo(NodeType.END);
        }

        @Test
        @DisplayName("执行 END 节点应成功")
        void testExecute_Success() {
            // Given
            Node node = new Node();
            node.setId("end-1");
            node.setType(NodeType.END);
            node.setName("结束节点");
            node.setConfig(new HashMap<>());

            ExecutionContext context = ExecutionContext.builder()
                    .executionId(EXECUTION_ID)
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .build();

            // When
            NodeResult result = executor.execute(node, context);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(result.getNodeId()).isEqualTo("end-1");
        }

        @Test
        @DisplayName("END 节点应包含执行元数据")
        void testExecute_IncludesMetadata() {
            // Given
            Node node = new Node();
            node.setId("end-metadata");
            node.setType(NodeType.END);
            node.setConfig(new HashMap<>());

            // 添加前置节点结果
            NodeResult priorResult = NodeResult.success("node-1", Map.of("data", "test"));
            ExecutionContext context = ExecutionContext.builder()
                    .executionId(EXECUTION_ID)
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .build();
            context.getNodeResults().put("node-1", priorResult);

            // When
            NodeResult result = executor.execute(node, context);

            // Then
            Map<String, Object> output = result.getOutput();
            assertThat(output).containsKey("_metadata");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) output.get("_metadata");
            assertThat(metadata.get("endNodeId")).isEqualTo("end-metadata");
            assertThat(metadata.get("workflowId")).isEqualTo(WORKFLOW_ID);
            assertThat(metadata.get("tenantId")).isEqualTo(TENANT_ID);
            assertThat(metadata.get("executionId")).isEqualTo(EXECUTION_ID);
            assertThat(metadata.get("totalNodes")).isEqualTo(1);
            assertThat(metadata.get("successCount")).isEqualTo(1L);
            assertThat(metadata.get("failureCount")).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("输出聚合测试")
    class AggregateOutputsTests {

        @Test
        @DisplayName("无聚合配置时返回所有节点输出")
        void testExecute_NoAggregation_ReturnsAllOutputs() {
            // Given
            Node node = new Node();
            node.setId("end-2");
            node.setType(NodeType.END);
            node.setConfig(new HashMap<>());

            // 添加多个前置节点结果
            NodeResult result1 = NodeResult.success("node-1", Map.of("count", 10));
            NodeResult result2 = NodeResult.success("node-2", Map.of("name", "test"));
            NodeResult result3 = NodeResult.success("node-3", Map.of("flag", true));

            ExecutionContext context = ExecutionContext.builder()
                    .executionId(EXECUTION_ID)
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .build();
            context.getNodeResults().put("node-1", result1);
            context.getNodeResults().put("node-2", result2);
            context.getNodeResults().put("node-3", result3);

            // When
            NodeResult result = executor.execute(node, context);

            // Then
            Map<String, Object> output = result.getOutput();
            assertThat(output).containsKey("node-1");
            assertThat(output).containsKey("node-2");
            assertThat(output).containsKey("node-3");
        }

        @Test
        @DisplayName("有聚合配置时应聚合指定节点输出")
        void testExecute_WithAggregation_AggregatesSpecifiedNodes() {
            // Given
            Map<String, Object> config = new HashMap<>();
            Map<String, Object> aggregateConfig = new HashMap<>();
            Map<String, Object> resultConfig = new HashMap<>();
            resultConfig.put("fromNodes", List.of("node-1", "node-2"));
            resultConfig.put("transform", Map.of(
                    "userId", "{{node-1.output.userId}}",
                    "count", "{{node-2.output.count}}"
            ));
            aggregateConfig.put("result", resultConfig);
            config.put("aggregateOutputs", aggregateConfig);

            Node node = new Node();
            node.setId("end-aggr");
            node.setType(NodeType.END);
            node.setConfig(config);

            // 添加前置节点结果
            NodeResult result1 = NodeResult.success("node-1", Map.of("userId", "12345"));
            NodeResult result2 = NodeResult.success("node-2", Map.of("count", 100));

            ExecutionContext context = ExecutionContext.builder()
                    .executionId(EXECUTION_ID)
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .build();
            context.getNodeResults().put("node-1", result1);
            context.getNodeResults().put("node-2", result2);

            // When
            NodeResult result = executor.execute(node, context);

            // Then
            Map<String, Object> output = result.getOutput();
            assertThat(output).containsKey("result");
            @SuppressWarnings("unchecked")
            Map<String, Object> aggregated = (Map<String, Object>) output.get("result");
            assertThat(aggregated.get("userId")).isEqualTo("12345");
            assertThat(aggregated.get("count")).isEqualTo(100);
        }

        @Test
        @DisplayName("聚合配置支持多个输出键")
        void testExecute_WithMultipleAggregations_AggregatesAll() {
            // Given
            Map<String, Object> config = new HashMap<>();
            Map<String, Object> aggregateConfig = new HashMap<>();

            Map<String, Object> summaryConfig = new HashMap<>();
            summaryConfig.put("fromNodes", List.of("node-1"));
            summaryConfig.put("transform", Map.of("total", "{{node-1.output.total}}"));

            Map<String, Object> detailConfig = new HashMap<>();
            detailConfig.put("fromNodes", List.of("node-2"));
            detailConfig.put("transform", Map.of("items", "{{node-2.output.items}}"));

            aggregateConfig.put("summary", summaryConfig);
            aggregateConfig.put("detail", detailConfig);
            config.put("aggregateOutputs", aggregateConfig);

            Node node = new Node();
            node.setId("end-multi");
            node.setType(NodeType.END);
            node.setConfig(config);

            NodeResult result1 = NodeResult.success("node-1", Map.of("total", 50));
            NodeResult result2 = NodeResult.success("node-2", Map.of("items", List.of("a", "b", "c")));

            ExecutionContext context = ExecutionContext.builder()
                    .executionId(EXECUTION_ID)
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .build();
            context.getNodeResults().put("node-1", result1);
            context.getNodeResults().put("node-2", result2);

            // When
            NodeResult result = executor.execute(node, context);

            // Then
            Map<String, Object> output = result.getOutput();
            assertThat(output).containsKey("summary");
            assertThat(output).containsKey("detail");
        }

        @Test
        @DisplayName("聚合配置无 transform 时合并节点输出")
        void testExecute_NoTransform_MergesNodeOutputs() {
            // Given
            Map<String, Object> config = new HashMap<>();
            Map<String, Object> aggregateConfig = new HashMap<>();
            Map<String, Object> resultConfig = new HashMap<>();
            resultConfig.put("fromNodes", List.of("node-1", "node-2"));
            // 无 transform，应合并所有 fromNodes 的输出
            aggregateConfig.put("result", resultConfig);
            config.put("aggregateOutputs", aggregateConfig);

            Node node = new Node();
            node.setId("end-merge");
            node.setType(NodeType.END);
            node.setConfig(config);

            NodeResult result1 = NodeResult.success("node-1", Map.of("key1", "value1"));
            NodeResult result2 = NodeResult.success("node-2", Map.of("key2", "value2"));

            ExecutionContext context = ExecutionContext.builder()
                    .executionId(EXECUTION_ID)
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .build();
            context.getNodeResults().put("node-1", result1);
            context.getNodeResults().put("node-2", result2);

            // When
            NodeResult result = executor.execute(node, context);

            // Then
            Map<String, Object> output = result.getOutput();
            @SuppressWarnings("unchecked")
            Map<String, Object> aggregated = (Map<String, Object>) output.get("result");
            assertThat(aggregated.get("key1")).isEqualTo("value1");
            assertThat(aggregated.get("key2")).isEqualTo("value2");
        }

        @Test
        @DisplayName("聚合支持字符串格式的 fromNodes")
        void testExecute_StringFromNodes_ParsesCorrectly() {
            // Given
            Map<String, Object> config = new HashMap<>();
            Map<String, Object> aggregateConfig = new HashMap<>();
            Map<String, Object> resultConfig = new HashMap<>();
            resultConfig.put("fromNodes", "node-a, node-b"); // 字符串格式
            resultConfig.put("transform", Map.of("value", "test"));
            aggregateConfig.put("result", resultConfig);
            config.put("aggregateOutputs", aggregateConfig);

            Node node = new Node();
            node.setId("end-string");
            node.setType(NodeType.END);
            node.setConfig(config);

            ExecutionContext context = ExecutionContext.builder()
                    .executionId(EXECUTION_ID)
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .build();

            // When - 不应抛出异常
            NodeResult result = executor.execute(node, context);

            // Then
            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        }

        @Test
        @DisplayName("聚合统计应正确计算成功/失败节点数")
        void testExecute_MixedNodeStatus_CalculatesStatsCorrectly() {
            // Given
            Node node = new Node();
            node.setId("end-stats");
            node.setType(NodeType.END);
            node.setConfig(new HashMap<>());

            // 混合状态：成功、失败
            NodeResult result1 = NodeResult.success("node-1", Map.of());
            NodeResult result2 = NodeResult.failure("node-2", "Error");
            NodeResult result3 = NodeResult.success("node-3", Map.of());
            NodeResult result4 = NodeResult.failure("node-4", "Error");

            ExecutionContext context = ExecutionContext.builder()
                    .executionId(EXECUTION_ID)
                    .tenantId(TENANT_ID)
                    .workflowId(WORKFLOW_ID)
                    .build();
            context.getNodeResults().put("node-1", result1);
            context.getNodeResults().put("node-2", result2);
            context.getNodeResults().put("node-3", result3);
            context.getNodeResults().put("node-4", result4);

            // When
            NodeResult result = executor.execute(node, context);

            // Then
            Map<String, Object> output = result.getOutput();
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) output.get("_metadata");
            assertThat(metadata.get("totalNodes")).isEqualTo(4);
            assertThat(metadata.get("successCount")).isEqualTo(2L);
            assertThat(metadata.get("failureCount")).isEqualTo(2L);
        }
    }
}

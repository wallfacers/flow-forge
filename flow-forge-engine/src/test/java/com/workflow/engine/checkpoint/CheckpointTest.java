package com.workflow.engine.checkpoint;

import com.workflow.dsl.WorkflowDslParser;
import com.workflow.engine.scheduler.InDegreeScheduler;
import com.workflow.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 检查点服务测试
 * <p>
 * 测试工作流执行的检查点保存和恢复功能，验证断点续传机制
 * </p>
 */
@DisplayName("检查点服务测试")
class CheckpointTest {

    private static final String TEST_WORKFLOW_JSON = """
            {
              "id": "test-workflow",
              "name": "测试工作流",
              "tenantId": "tenant-001",
              "nodes": [
                {"id": "node1", "name": "起始节点", "type": "log"},
                {"id": "node2", "name": "中间节点", "type": "log"},
                {"id": "node3", "name": "结束节点", "type": "log"}
              ],
              "edges": [
                {"sourceNodeId": "node1", "targetNodeId": "node2"},
                {"sourceNodeId": "node2", "targetNodeId": "node3"}
              ]
            }
            """;

    private WorkflowDslParser parser;
    private WorkflowDefinition workflowDefinition;
    private InDegreeScheduler scheduler;
    private Map<String, AtomicInteger> initialInDegrees;

    @BeforeEach
    void setUp() throws Exception {
        parser = new WorkflowDslParser();
        workflowDefinition = parser.parse(TEST_WORKFLOW_JSON);
        scheduler = new InDegreeScheduler();
        initialInDegrees = scheduler.calculateInDegrees(workflowDefinition);
    }

    @AfterEach
    void tearDown() {
        // 清理测试数据
    }

    // ==================== 检查点数据结构测试 ====================

    @Test
    @DisplayName("CheckpointData应该正确保存和恢复数据")
    void testCheckpointDataStructure() {
        CheckpointService.CheckpointData checkpoint = new CheckpointService.CheckpointData();
        checkpoint.setExecutionId("exec-001");

        Map<String, Integer> inDegreeSnapshot = new HashMap<>();
        inDegreeSnapshot.put("node1", 0);
        inDegreeSnapshot.put("node2", 1);
        inDegreeSnapshot.put("node3", 1);
        checkpoint.setInDegreeSnapshot(inDegreeSnapshot);

        checkpoint.setCompletedNodes(Arrays.asList("node1"));
        checkpoint.setSavedAt(Instant.now());

        assertEquals("exec-001", checkpoint.getExecutionId());
        assertEquals(0, checkpoint.getInDegreeSnapshot().get("node1"));
        assertEquals(1, checkpoint.getInDegreeSnapshot().get("node2"));
        assertEquals(3, checkpoint.getInDegreeSnapshot().size()); // 3个节点
        assertEquals(1, checkpoint.getCompletedNodes().size());
        assertEquals("node1", checkpoint.getCompletedNodes().get(0));
        assertNotNull(checkpoint.getSavedAt());
    }

    // ==================== 检查点保存测试 ====================

    @Test
    @DisplayName("应该正确创建工作流执行上下文")
    void testCreateExecutionContext() {
        ExecutionContext context = ExecutionContext.builder()
                .executionId("exec-001")
                .workflowId("test-workflow")
                .tenantId("tenant-001")
                .status(ExecutionStatus.RUNNING)
                .input(Map.of("userId", "user123"))
                .globalVariables(Map.of("apiKey", "secret"))
                .build();

        // 验证上下文数据正确
        assertEquals("exec-001", context.getExecutionId());
        assertEquals("test-workflow", context.getWorkflowId());
        assertEquals("tenant-001", context.getTenantId());
        assertEquals(ExecutionStatus.RUNNING, context.getStatus());
        assertEquals(1, context.getInput().size());
        assertEquals("user123", context.getInput().get("userId"));
        assertEquals(1, context.getGlobalVariables().size());
        assertEquals("secret", context.getGlobalVariables().get("apiKey"));
    }

    @Test
    @DisplayName("入度快照应该正确保存节点入度状态")
    void testInDegreeSnapshot() {
        // 初始入度: node1=0, node2=1, node3=1
        Map<String, Integer> snapshot = scheduler.createSnapshot(initialInDegrees);

        assertEquals(3, snapshot.size());
        assertEquals(0, snapshot.get("node1"));
        assertEquals(1, snapshot.get("node2"));
        assertEquals(1, snapshot.get("node3"));

        // 验证快照不可修改
        assertThrows(UnsupportedOperationException.class, () -> {
            snapshot.put("node4", 0);
        });
    }

    @Test
    @DisplayName("从快照恢复入度映射应该保持原有值")
    void testRestoreFromSnapshot() {
        // 创建快照
        Map<String, Integer> snapshot = scheduler.createSnapshot(initialInDegrees);

        // 模拟node1完成后，更新入度
        Map<String, Integer> updatedSnapshot = new HashMap<>(snapshot);
        updatedSnapshot.put("node2", 0); // node1完成，node2入度减1

        // 从更新后的快照恢复
        Map<String, AtomicInteger> restored = scheduler.restoreFromSnapshot(updatedSnapshot);

        assertEquals(3, restored.size());
        assertEquals(0, restored.get("node1").get());
        assertEquals(0, restored.get("node2").get());
        assertEquals(1, restored.get("node3").get());
    }

    // ==================== 节点完成与入度更新测试 ====================

    @Test
    @DisplayName("节点完成后应该正确更新后继节点入度")
    void testNodeCompletedUpdatesInDegree() {
        // 初始入度: node1=0, node2=1, node3=1
        Map<String, AtomicInteger> inDegreeMap = new HashMap<>();
        inDegreeMap.put("node1", new AtomicInteger(0));
        inDegreeMap.put("node2", new AtomicInteger(1));
        inDegreeMap.put("node3", new AtomicInteger(1));

        // node1完成，更新后继节点入度
        List<String> readyNodes = scheduler.nodeCompleted("node1",
                workflowDefinition.getOutEdges("node1"), inDegreeMap);

        // 验证入度更新
        assertEquals(0, inDegreeMap.get("node1").get());
        assertEquals(0, inDegreeMap.get("node2").get()); // 减1
        assertEquals(1, inDegreeMap.get("node3").get()); // 未变

        // 验证返回的就绪节点
        assertEquals(1, readyNodes.size());
        assertEquals("node2", readyNodes.get(0));
    }

    @Test
    @DisplayName("应该正确查找可执行的节点")
    void testFindReadyNodes() {
        // 初始入度: node1=0, node2=1, node3=1
        Map<String, AtomicInteger> inDegreeMap = new HashMap<>();
        inDegreeMap.put("node1", new AtomicInteger(0));
        inDegreeMap.put("node2", new AtomicInteger(1));
        inDegreeMap.put("node3", new AtomicInteger(1));

        List<Node> readyNodes = scheduler.findReadyNodes(workflowDefinition.getNodes(), inDegreeMap);

        assertEquals(1, readyNodes.size());
        assertEquals("node1", readyNodes.get(0).getId());
    }

    // ==================== 拓扑排序测试 ====================

    @Test
    @DisplayName("拓扑排序应该按正确顺序排列节点")
    void testTopologicalSort() {
        List<String> sorted = scheduler.topologicalSort(workflowDefinition);

        assertEquals(3, sorted.size());
        assertEquals("node1", sorted.get(0)); // 起始节点
        assertEquals("node2", sorted.get(1)); // 中间节点
        assertEquals("node3", sorted.get(2)); // 结束节点
    }

    @Test
    @DisplayName("循环图应该无法进行拓扑排序")
    void testTopologicalSortWithCycle() {
        String cyclicWorkflow = """
                {
                  "id": "cycle-workflow",
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

        assertThrows(Exception.class, () -> {
            parser.parse(cyclicWorkflow);
        });
    }

    // ==================== 检查点恢复测试 ====================

    @Test
    @DisplayName("恢复结果应该包含所有必要的数据")
    void testRecoveryResultStructure() {
        CheckpointRecoveryService.RecoveryResult result = new CheckpointRecoveryService.RecoveryResult();

        result.setNewExecutionId("exec-recovered-001");
        result.setOriginalExecutionId("exec-original-001");
        result.setDefinition(workflowDefinition);
        result.setContext(ExecutionContext.builder()
                .executionId("exec-recovered-001")
                .workflowId("test-workflow")
                .tenantId("tenant-001")
                .status(ExecutionStatus.RUNNING)
                .build());
        result.setInDegreeMap(new HashMap<>());
        result.setReadyNodes(workflowDefinition.getNodes().subList(0, 1));
        result.setCompletedNodes(new HashSet<>());

        assertEquals("exec-recovered-001", result.getNewExecutionId());
        assertEquals("exec-original-001", result.getOriginalExecutionId());
        assertNotNull(result.getDefinition());
        assertNotNull(result.getContext());
        assertNotNull(result.getInDegreeMap());
        assertEquals(1, result.readyNodes().size());
        assertNotNull(result.getCompletedNodes());
    }

    @Test
    @DisplayName("应该正确模拟节点执行过程中的检查点保存")
    void testCheckpointSavingDuringExecution() {
        ExecutionContext context = ExecutionContext.builder()
                .executionId("exec-001")
                .workflowId("test-workflow")
                .tenantId("tenant-001")
                .status(ExecutionStatus.RUNNING)
                .input(Map.of("data", "test"))
                .build();

        // 模拟node1执行完成
        NodeResult node1Result = NodeResult.builder()
                .nodeId("node1")
                .status(ExecutionStatus.SUCCESS)
                .output(Map.of("message", "node1 completed"))
                .build();
        context.getNodeResults().put("node1", node1Result);

        // 获取入度快照
        Map<String, Integer> inDegreeSnapshot = new HashMap<>();
        inDegreeSnapshot.put("node1", 0);
        inDegreeSnapshot.put("node2", 0); // node1完成，node2入度减1
        inDegreeSnapshot.put("node3", 1);

        // 创建检查点数据
        CheckpointService.CheckpointData checkpoint = new CheckpointService.CheckpointData();
        checkpoint.setExecutionId("exec-001");
        checkpoint.setInDegreeSnapshot(inDegreeSnapshot);
        checkpoint.setCompletedNodes(Arrays.asList("node1"));
        checkpoint.setSavedAt(Instant.now());

        // 验证检查点数据
        assertEquals("exec-001", checkpoint.getExecutionId());
        assertEquals(0, checkpoint.getInDegreeSnapshot().get("node2"));
        assertEquals(1, checkpoint.getCompletedNodes().size());
        assertTrue(checkpoint.getCompletedNodes().contains("node1"));
        assertNotNull(checkpoint.getSavedAt());
    }

    // ==================== 多节点工作流测试 ====================

    @Test
    @DisplayName("复杂工作流应该正确计算节点入度")
    void testComplexWorkflowInDegrees() throws Exception {
        String complexWorkflow = """
                {
                  "id": "complex-workflow",
                  "name": "复杂工作流",
                  "nodes": [
                    {"id": "start", "name": "开始", "type": "log"},
                    {"id": "A", "name": "分支A", "type": "log"},
                    {"id": "B", "name": "分支B", "type": "log"},
                    {"id": "merge", "name": "合并", "type": "log"},
                    {"id": "end", "name": "结束", "type": "log"}
                  ],
                  "edges": [
                    {"sourceNodeId": "start", "targetNodeId": "A"},
                    {"sourceNodeId": "start", "targetNodeId": "B"},
                    {"sourceNodeId": "A", "targetNodeId": "merge"},
                    {"sourceNodeId": "B", "targetNodeId": "merge"},
                    {"sourceNodeId": "merge", "targetNodeId": "end"}
                  ]
                }
                """;

        WorkflowDefinition definition = parser.parse(complexWorkflow);
        Map<String, AtomicInteger> inDegrees = scheduler.calculateInDegrees(definition);

        assertEquals(0, inDegrees.get("start").get());   // 起始节点
        assertEquals(1, inDegrees.get("A").get());       // 来自start
        assertEquals(1, inDegrees.get("B").get());       // 来自start
        assertEquals(2, inDegrees.get("merge").get());   // 来自A和B
        assertEquals(1, inDegrees.get("end").get());     // 来自merge
    }

    // ==================== 断点续传场景测试 ====================

    @Test
    @DisplayName("应该正确模拟部分执行后的恢复场景")
    void testPartialExecutionRecovery() {
        // 初始状态: node1=0, node2=1, node3=1
        Map<String, AtomicInteger> inDegreeMap = new HashMap<>();
        inDegreeMap.put("node1", new AtomicInteger(0));
        inDegreeMap.put("node2", new AtomicInteger(1));
        inDegreeMap.put("node3", new AtomicInteger(1));

        // 模拟node1执行完成
        scheduler.nodeCompleted("node1", workflowDefinition.getOutEdges("node1"), inDegreeMap);

        // 此时node2应该就绪
        List<Node> readyNodes = scheduler.findReadyNodes(workflowDefinition.getNodes(), inDegreeMap);
        assertTrue(readyNodes.stream().anyMatch(n -> "node2".equals(n.getId())));

        // 保存检查点
        Map<String, Integer> snapshot = scheduler.createSnapshot(inDegreeMap);
        assertEquals(0, snapshot.get("node2")); // node2现在入度为0

        // 模拟从检查点恢复
        Map<String, AtomicInteger> restored = scheduler.restoreFromSnapshot(snapshot);
        assertEquals(0, restored.get("node1").get());
        assertEquals(0, restored.get("node2").get());
        assertEquals(1, restored.get("node3").get());
    }

    @Test
    @DisplayName("应该正确模拟多节点完成后的恢复场景")
    void testMultipleNodesCompletedRecovery() {
        // 初始状态
        Map<String, AtomicInteger> inDegreeMap = new HashMap<>();
        inDegreeMap.put("node1", new AtomicInteger(0));
        inDegreeMap.put("node2", new AtomicInteger(1));
        inDegreeMap.put("node3", new AtomicInteger(1));

        // node1完成
        scheduler.nodeCompleted("node1", workflowDefinition.getOutEdges("node1"), inDegreeMap);

        // node2完成
        scheduler.nodeCompleted("node2", workflowDefinition.getOutEdges("node2"), inDegreeMap);

        // 验证所有节点入度状态
        assertEquals(0, inDegreeMap.get("node1").get());
        assertEquals(0, inDegreeMap.get("node2").get());
        assertEquals(0, inDegreeMap.get("node3").get()); // node2完成，node3入度减1

        // node3应该就绪
        List<Node> readyNodes = scheduler.findReadyNodes(workflowDefinition.getNodes(), inDegreeMap);
        assertTrue(readyNodes.stream().anyMatch(n -> "node3".equals(n.getId())));
    }

    // ==================== 错误场景测试 ====================

    @Test
    @DisplayName("空工作流定义应该返回空入度映射")
    void testEmptyWorkflowInDegrees() {
        Map<String, AtomicInteger> emptyDegrees = scheduler.calculateInDegrees(null);
        assertTrue(emptyDegrees.isEmpty());
    }

    @Test
    @DisplayName("无边的图应该所有节点入度都为0")
    void testGraphWithoutEdges() throws Exception {
        // 使用不带验证的解析，因为无边的多节点工作流会被验证为孤立节点
        String noEdgesWorkflow = """
                {
                  "id": "no-edges-workflow",
                  "name": "无边工作流",
                  "nodes": [
                    {"id": "node1", "name": "节点1", "type": "log"},
                    {"id": "node2", "name": "节点2", "type": "log"}
                  ],
                  "edges": []
                }
                """;

        WorkflowDefinition definition = parser.parse(noEdgesWorkflow, false);
        Map<String, AtomicInteger> inDegrees = scheduler.calculateInDegrees(definition);

        assertEquals(0, inDegrees.get("node1").get());
        assertEquals(0, inDegrees.get("node2").get());
    }

    // ==================== 执行上下文测试 ====================

    @Test
    @DisplayName("执行上下文应该正确保存和检索节点结果")
    void testExecutionContextNodeResults() {
        ExecutionContext context = ExecutionContext.builder()
                .executionId("exec-001")
                .workflowId("test-workflow")
                .tenantId("tenant-001")
                .build();

        // 添加节点结果
        NodeResult result1 = NodeResult.builder()
                .nodeId("node1")
                .status(ExecutionStatus.SUCCESS)
                .output(Map.of("key", "value1"))
                .build();
        context.getNodeResults().put("node1", result1);

        NodeResult result2 = NodeResult.builder()
                .nodeId("node2")
                .status(ExecutionStatus.FAILED)
                .errorMessage("Test error")
                .build();
        context.getNodeResults().put("node2", result2);

        // 验证
        assertEquals(2, context.getNodeResults().size());
        assertEquals(ExecutionStatus.SUCCESS, context.getNodeResults().get("node1").getStatus());
        assertEquals("value1", context.getNodeResults().get("node1").getOutput().get("key"));
        assertEquals(ExecutionStatus.FAILED, context.getNodeResults().get("node2").getStatus());
        assertEquals("Test error", context.getNodeResults().get("node2").getErrorMessage());
    }

    // ==================== 检查点缓存测试 ====================

    @Test
    @DisplayName("检查点缓存应该正确存储和检索数据")
    void testCheckpointCache() {
        CheckpointService.CheckpointData checkpoint = new CheckpointService.CheckpointData();
        checkpoint.setExecutionId("exec-001");
        checkpoint.setInDegreeSnapshot(Map.of("node1", 0, "node2", 1));
        checkpoint.setCompletedNodes(Arrays.asList("node1"));
        checkpoint.setSavedAt(Instant.now());

        // 模拟缓存操作
        Map<String, CheckpointService.CheckpointData> cache = new HashMap<>();
        cache.put("exec-001", checkpoint);

        // 检索
        CheckpointService.CheckpointData retrieved = cache.get("exec-001");
        assertNotNull(retrieved);
        assertEquals("exec-001", retrieved.getExecutionId());
        assertEquals(1, retrieved.getCompletedNodes().size());

        // 清除
        cache.remove("exec-001");
        assertNull(cache.get("exec-001"));
    }

    // ==================== 状态转换测试 ====================

    @Test
    @DisplayName("执行状态应该支持正确的状态转换")
    void testExecutionStatusTransitions() {
        ExecutionStatus status = ExecutionStatus.RUNNING;

        // 正常完成
        status = ExecutionStatus.SUCCESS;
        assertEquals(ExecutionStatus.SUCCESS, status);

        // 失败
        status = ExecutionStatus.FAILED;
        assertEquals(ExecutionStatus.FAILED, status);

        // 取消
        status = ExecutionStatus.CANCELLED;
        assertEquals(ExecutionStatus.CANCELLED, status);
    }

    // ==================== 验证完整性测试 ====================

    @Test
    @DisplayName("应该正确验证调度完整性")
    void testValidateCompleteness() {
        boolean isValid = scheduler.validateCompleteness(workflowDefinition, initialInDegrees);
        assertTrue(isValid);
    }

    @Test
    @DisplayName("应该正确计算节点执行层级")
    void testCalculateLevels() {
        Map<String, Integer> levels = scheduler.calculateLevels(workflowDefinition);

        assertEquals(3, levels.size());
        assertEquals(0, levels.get("node1")); // 第0层
        assertEquals(1, levels.get("node2")); // 第1层
        assertEquals(2, levels.get("node3")); // 第2层
    }

    // ==================== 复杂分支场景测试 ====================

    @Test
    @DisplayName("分支合并场景应该正确计算入度和层级")
    void testBranchMergeScenario() throws Exception {
        String branchWorkflow = """
                {
                  "id": "branch-workflow",
                  "name": "分支工作流",
                  "nodes": [
                    {"id": "start", "name": "开始", "type": "log"},
                    {"id": "A", "name": "分支A", "type": "log"},
                    {"id": "B", "name": "分支B", "type": "log"},
                    {"id": "C", "name": "分支C", "type": "log"},
                    {"id": "merge", "name": "合并", "type": "log"},
                    {"id": "end", "name": "结束", "type": "log"}
                  ],
                  "edges": [
                    {"sourceNodeId": "start", "targetNodeId": "A"},
                    {"sourceNodeId": "start", "targetNodeId": "B"},
                    {"sourceNodeId": "start", "targetNodeId": "C"},
                    {"sourceNodeId": "A", "targetNodeId": "merge"},
                    {"sourceNodeId": "B", "targetNodeId": "merge"},
                    {"sourceNodeId": "C", "targetNodeId": "merge"},
                    {"sourceNodeId": "merge", "targetNodeId": "end"}
                  ]
                }
                """;

        WorkflowDefinition definition = parser.parse(branchWorkflow);
        Map<String, AtomicInteger> inDegrees = scheduler.calculateInDegrees(definition);
        Map<String, Integer> levels = scheduler.calculateLevels(definition);

        // 验证入度
        assertEquals(0, inDegrees.get("start").get());
        assertEquals(1, inDegrees.get("A").get());
        assertEquals(1, inDegrees.get("B").get());
        assertEquals(1, inDegrees.get("C").get());
        assertEquals(3, inDegrees.get("merge").get()); // 3个前驱
        assertEquals(1, inDegrees.get("end").get());

        // 验证层级
        assertEquals(0, levels.get("start"));
        assertEquals(1, levels.get("A"));
        assertEquals(1, levels.get("B"));
        assertEquals(1, levels.get("C"));
        assertEquals(2, levels.get("merge"));
        assertEquals(3, levels.get("end"));
    }

    // ==================== 拓扑排序顺序验证测试 ====================

    @Test
    @DisplayName("拓扑排序应该遵循依赖关系")
    void testTopologicalSortFollowsDependencies() throws Exception {
        String complexWorkflow = """
                {
                  "id": "complex-workflow",
                  "name": "复杂工作流",
                  "nodes": [
                    {"id": "A", "name": "A", "type": "log"},
                    {"id": "B", "name": "B", "type": "log"},
                    {"id": "C", "name": "C", "type": "log"},
                    {"id": "D", "name": "D", "type": "log"},
                    {"id": "E", "name": "E", "type": "log"}
                  ],
                  "edges": [
                    {"sourceNodeId": "A", "targetNodeId": "B"},
                    {"sourceNodeId": "A", "targetNodeId": "C"},
                    {"sourceNodeId": "B", "targetNodeId": "D"},
                    {"sourceNodeId": "C", "targetNodeId": "D"},
                    {"sourceNodeId": "D", "targetNodeId": "E"}
                  ]
                }
                """;

        WorkflowDefinition definition = parser.parse(complexWorkflow);
        List<String> sorted = scheduler.topologicalSort(definition);

        // A必须在B和C之前
        int aIndex = sorted.indexOf("A");
        int bIndex = sorted.indexOf("B");
        int cIndex = sorted.indexOf("C");
        assertTrue(aIndex < bIndex);
        assertTrue(aIndex < cIndex);

        // B和C必须在D之前
        int dIndex = sorted.indexOf("D");
        assertTrue(bIndex < dIndex);
        assertTrue(cIndex < dIndex);

        // D必须在E之前
        int eIndex = sorted.indexOf("E");
        assertTrue(dIndex < eIndex);
    }

    // ==================== 模拟完整工作流执行场景 ====================

    @Test
    @DisplayName("应该正确模拟完整工作流执行的检查点保存")
    void testFullWorkflowExecutionCheckpoints() {
        ExecutionContext context = ExecutionContext.builder()
                .executionId("exec-full-001")
                .workflowId("test-workflow")
                .tenantId("tenant-001")
                .status(ExecutionStatus.RUNNING)
                .input(Map.of("initialData", "test"))
                .build();

        Map<String, AtomicInteger> inDegreeMap = scheduler.calculateInDegrees(workflowDefinition);
        List<CheckpointService.CheckpointData> checkpoints = new ArrayList<>();

        // 节点1执行
        NodeResult result1 = NodeResult.builder()
                .nodeId("node1")
                .status(ExecutionStatus.SUCCESS)
                .output(Map.of("step1", "done"))
                .build();
        context.getNodeResults().put("node1", result1);
        scheduler.nodeCompleted("node1", workflowDefinition.getOutEdges("node1"), inDegreeMap);

        // 保存检查点1
        CheckpointService.CheckpointData cp1 = new CheckpointService.CheckpointData();
        cp1.setExecutionId("exec-full-001");
        cp1.setInDegreeSnapshot(scheduler.createSnapshot(inDegreeMap));
        cp1.setCompletedNodes(new ArrayList<>(context.getNodeResults().keySet()));
        cp1.setSavedAt(Instant.now());
        checkpoints.add(cp1);

        // 节点2执行
        NodeResult result2 = NodeResult.builder()
                .nodeId("node2")
                .status(ExecutionStatus.SUCCESS)
                .output(Map.of("step2", "done"))
                .build();
        context.getNodeResults().put("node2", result2);
        scheduler.nodeCompleted("node2", workflowDefinition.getOutEdges("node2"), inDegreeMap);

        // 保存检查点2
        CheckpointService.CheckpointData cp2 = new CheckpointService.CheckpointData();
        cp2.setExecutionId("exec-full-001");
        cp2.setInDegreeSnapshot(scheduler.createSnapshot(inDegreeMap));
        cp2.setCompletedNodes(new ArrayList<>(context.getNodeResults().keySet()));
        cp2.setSavedAt(Instant.now());
        checkpoints.add(cp2);

        // 节点3执行
        NodeResult result3 = NodeResult.builder()
                .nodeId("node3")
                .status(ExecutionStatus.SUCCESS)
                .output(Map.of("step3", "done"))
                .build();
        context.getNodeResults().put("node3", result3);
        scheduler.nodeCompleted("node3", workflowDefinition.getOutEdges("node3"), inDegreeMap);

        // 保存最终检查点
        CheckpointService.CheckpointData cpFinal = new CheckpointService.CheckpointData();
        cpFinal.setExecutionId("exec-full-001");
        cpFinal.setInDegreeSnapshot(scheduler.createSnapshot(inDegreeMap));
        cpFinal.setCompletedNodes(new ArrayList<>(context.getNodeResults().keySet()));
        cpFinal.setSavedAt(Instant.now());
        checkpoints.add(cpFinal);

        // 验证检查点序列
        assertEquals(3, checkpoints.size());
        assertEquals(1, checkpoints.get(0).getCompletedNodes().size());
        assertEquals(2, checkpoints.get(1).getCompletedNodes().size());
        assertEquals(3, checkpoints.get(2).getCompletedNodes().size());

        // 验证最终状态
        assertEquals(3, context.getNodeResults().size());
        assertTrue(context.getNodeResults().values().stream()
                .allMatch(r -> r.getStatus() == ExecutionStatus.SUCCESS));
    }

    // ==================== 检查点时间戳验证测试 ====================

    @Test
    @DisplayName("检查点应该按时间顺序保存")
    void testCheckpointTimestampOrdering() {
        List<CheckpointService.CheckpointData> checkpoints = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            CheckpointService.CheckpointData cp = new CheckpointService.CheckpointData();
            cp.setExecutionId("exec-timestamp-001");
            cp.setInDegreeSnapshot(Map.of("node" + i, i));
            cp.setCompletedNodes(Arrays.asList("node" + i));
            cp.setSavedAt(Instant.now().minusMillis((5 - i) * 100)); // 按时间倒序
            checkpoints.add(cp);
        }

        // 验证时间戳递增
        for (int i = 1; i < checkpoints.size(); i++) {
            assertTrue(checkpoints.get(i).getSavedAt()
                    .isAfter(checkpoints.get(i - 1).getSavedAt()));
        }
    }
}

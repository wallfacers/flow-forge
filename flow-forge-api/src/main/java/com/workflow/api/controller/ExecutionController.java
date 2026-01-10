package com.workflow.api.controller;

import com.workflow.infra.entity.NodeExecutionLogEntity;
import com.workflow.infra.repository.NodeExecutionLogRepository;
import com.workflow.infra.repository.WorkflowExecutionRepository;
import com.workflow.model.WorkflowDefinition;
import com.workflow.api.dto.ExecutionHistoryDTO;
import com.workflow.visualizer.dto.PageResponse;
import com.workflow.api.mapper.ExecutionHistoryMapper;
import com.workflow.visualizer.dto.GraphData;
import com.workflow.visualizer.util.GraphGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 执行历史查询API
 * <p>
 * 提供工作流执行历史的查询、可视化数据获取等功能
 * </p>
 */
@Tag(name = "Execution History", description = "执行历史查询API")
@RestController
@RequestMapping("/api/executions")
public class ExecutionController {

    private static final Logger log = LoggerFactory.getLogger(ExecutionController.class);

    private final WorkflowExecutionRepository executionRepository;
    private final NodeExecutionLogRepository nodeLogRepository;

    public ExecutionController(WorkflowExecutionRepository executionRepository,
                                NodeExecutionLogRepository nodeLogRepository) {
        this.executionRepository = executionRepository;
        this.nodeLogRepository = nodeLogRepository;
    }

    /**
     * 获取执行历史列表（分页）
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 分页结果
     */
    @Operation(summary = "获取执行历史列表", description = "分页查询工作流执行历史记录")
    @GetMapping
    public ResponseEntity<PageResponse<ExecutionHistoryDTO>> getExecutions(
            @Parameter(description = "页码（从0开始）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "工作流ID（可选）") @RequestParam(required = false) String workflowId,
            @Parameter(description = "执行状态（可选）") @RequestParam(required = false) String status) {

        log.debug("Fetching executions: page={}, size={}, workflowId={}, status={}", page, size, workflowId, status);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<com.workflow.infra.entity.WorkflowExecutionEntity> result;

        if (workflowId != null && !workflowId.isEmpty()) {
            result = executionRepository.findByWorkflowIdAndDeletedAtIsNull(workflowId, pageable);
        } else {
            result = executionRepository.findAllByDeletedAtIsNull(pageable);
        }

        // TODO: 根据status过滤（需要在Repository添加方法）

        PageResponse<ExecutionHistoryDTO> response = ExecutionHistoryMapper.toPageResponse(result);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取单个执行详情
     *
     * @param executionId 执行ID
     * @return 执行详情
     */
    @Operation(summary = "获取执行详情", description = "获取单个工作流执行的详细信息")
    @GetMapping("/{executionId}")
    public ResponseEntity<ExecutionHistoryDTO> getExecution(
            @Parameter(description = "执行ID") @PathVariable String executionId) {

        log.debug("Fetching execution details for: {}", executionId);

        return executionRepository.findByExecutionIdAndDeletedAtIsNull(executionId)
                .map(entity -> {
                    List<NodeExecutionLogEntity> nodeLogs = nodeLogRepository
                            .findByExecutionIdStr(executionId);
                    return ResponseEntity.ok(ExecutionHistoryMapper.toDto(entity, nodeLogs));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取执行历史的可视化图数据
     *
     * @param executionId 执行ID
     * @return 可视化图数据
     */
    @Operation(summary = "获取可视化图数据", description = "获取执行历史的DAG可视化数据，包含节点状态")
    @GetMapping("/{executionId}/graph")
    public ResponseEntity<GraphData> getExecutionGraph(
            @Parameter(description = "执行ID") @PathVariable String executionId) {

        log.debug("Fetching graph data for execution: {}", executionId);

        return executionRepository.findByExecutionIdAndDeletedAtIsNull(executionId)
                .map(entity -> {
                    // 从workflowDefinition重建WorkflowDefinition
                    Map<String, Object> defMap = entity.getWorkflowDefinition();
                    WorkflowDefinition workflow = rebuildWorkflowDefinition(defMap);

                    // 获取节点状态
                    Map<String, String> nodeStatus = new HashMap<>();
                    List<NodeExecutionLogEntity> nodeLogs = nodeLogRepository
                            .findByExecutionIdStr(executionId);
                    for (NodeExecutionLogEntity log : nodeLogs) {
                        if (log.getStatus() != null) {
                            nodeStatus.put(log.getNodeId(), log.getStatus().name().toLowerCase());
                        }
                    }

                    // 生成可视化数据
                    GraphData graphData = GraphGenerator.generateWithStatus(workflow, nodeStatus);

                    return ResponseEntity.ok(graphData);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取执行历史的节点详情列表
     *
     * @param executionId 执行ID
     * @return 节点详情列表
     */
    @Operation(summary = "获取节点详情", description = "获取执行历史中所有节点的详细执行信息")
    @GetMapping("/{executionId}/nodes")
    public ResponseEntity<List<ExecutionHistoryDTO.NodeExecutionDetail>> getExecutionNodes(
            @Parameter(description = "执行ID") @PathVariable String executionId) {

        log.debug("Fetching node details for execution: {}", executionId);

        List<NodeExecutionLogEntity> nodeLogs = nodeLogRepository.findByExecutionIdStr(executionId);

        if (nodeLogs.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<ExecutionHistoryDTO.NodeExecutionDetail> details = nodeLogs.stream()
                .map(ExecutionHistoryMapper::toNodeDetail)
                .toList();

        return ResponseEntity.ok(details);
    }

    /**
     * 获取单个节点的执行详情
     *
     * @param executionId 执行ID
     * @param nodeId 节点ID
     * @return 节点详情
     */
    @Operation(summary = "获取节点执行详情", description = "获取单个节点的详细执行信息")
    @GetMapping("/{executionId}/nodes/{nodeId}")
    public ResponseEntity<ExecutionHistoryDTO.NodeExecutionDetail> getNodeExecution(
            @Parameter(description = "执行ID") @PathVariable String executionId,
            @Parameter(description = "节点ID") @PathVariable String nodeId) {

        log.debug("Fetching node execution details: execution={}, node={}", executionId, nodeId);

        return executionRepository.findByExecutionIdAndDeletedAtIsNull(executionId)
                .flatMap(entity -> nodeLogRepository.findByExecutionIdStrAndNodeIdAndDeletedAtIsNull(executionId, nodeId))
                .map(ExecutionHistoryMapper::toNodeDetail)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 从存储的定义重建WorkflowDefinition
     * <p>
     * 这是一个简化的实现，实际应该使用WorkflowDslParser
     * </p>
     */
    private WorkflowDefinition rebuildWorkflowDefinition(Map<String, Object> defMap) {
        // TODO: 使用WorkflowDslParser正确解析
        // 这里返回一个空定义作为占位符
        return new WorkflowDefinition();
    }
}

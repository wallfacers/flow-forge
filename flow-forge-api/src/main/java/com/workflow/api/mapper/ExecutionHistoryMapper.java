package com.workflow.api.mapper;

import com.workflow.api.dto.ExecutionHistoryDTO;
import com.workflow.visualizer.dto.PageResponse;
import com.workflow.infra.entity.NodeExecutionLogEntity;
import com.workflow.infra.entity.WorkflowExecutionEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 执行历史映射器
 * <p>
 * 将实体类映射为DTO，支持分页查询
 * </p>
 */
public class ExecutionHistoryMapper {

    private static final Logger log = LoggerFactory.getLogger(ExecutionHistoryMapper.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将实体转换为DTO
     *
     * @param entity 执行历史实体
     * @return DTO
     */
    public static ExecutionHistoryDTO toDto(WorkflowExecutionEntity entity) {
        if (entity == null) {
            return null;
        }

        return ExecutionHistoryDTO.builder()
                .executionId(entity.getExecutionId())
                .workflowId(entity.getWorkflowId())
                .workflowName(entity.getWorkflowName())
                .tenantId(entity.getTenantId())
                .status(entity.getStatus())
                .errorMessage(entity.getErrorMessage())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .durationMs(entity.getDurationMs())
                .totalNodes(entity.getTotalNodes())
                .completedNodes(entity.getCompletedNodes())
                .failedNodes(entity.getFailedNodes())
                .progress(entity.getProgressPercentage())
                .retryCount(entity.getRetryCount())
                .isResumed(entity.getIsResumed())
                .resumedFromId(entity.getResumedFromId() != null ? entity.getResumedFromId().toString() : null)
                .nodeDetails(new ArrayList<>())
                .build();
    }

    /**
     * 将实体转换为DTO（带节点详情）
     *
     * @param entity 执行历史实体
     * @param nodeLogs 节点执行日志列表
     * @return DTO
     */
    public static ExecutionHistoryDTO toDto(WorkflowExecutionEntity entity, List<NodeExecutionLogEntity> nodeLogs) {
        ExecutionHistoryDTO dto = toDto(entity);
        if (dto != null && nodeLogs != null) {
            List<ExecutionHistoryDTO.NodeExecutionDetail> details = nodeLogs.stream()
                    .map(ExecutionHistoryMapper::toNodeDetail)
                    .collect(Collectors.toList());
            dto.setNodeDetails(details);
        }
        return dto;
    }

    /**
     * 将节点日志实体转换为节点详情DTO
     *
     * @param entity 节点执行日志实体
     * @return 节点详情DTO
     */
    public static ExecutionHistoryDTO.NodeExecutionDetail toNodeDetail(NodeExecutionLogEntity entity) {
        if (entity == null) {
            return null;
        }

        String outputJson = null;
        if (entity.getOutputData() != null) {
            try {
                outputJson = objectMapper.writeValueAsString(entity.getOutputData());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize output data for node: {}", entity.getNodeId(), e);
            }
        }

        return ExecutionHistoryDTO.NodeExecutionDetail.builder()
                .nodeId(entity.getNodeId())
                .nodeName(entity.getNodeName())
                .nodeType(entity.getNodeType() != null ? entity.getNodeType().getCode() : "unknown")
                .status(entity.getStatus() != null ? entity.getStatus().name().toLowerCase() : "unknown")
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .durationMs(entity.getDurationMs())
                .retryCount(entity.getRetryCount())
                .errorMessage(entity.getErrorMessage())
                .outputData(outputJson)
                .build();
    }

    /**
     * 将实体列表转换为DTO列表
     *
     * @param entities 实体列表
     * @return DTO列表
     */
    public static List<ExecutionHistoryDTO> toDtoList(List<WorkflowExecutionEntity> entities) {
        if (entities == null) {
            return new ArrayList<>();
        }
        return entities.stream()
                .map(ExecutionHistoryMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 将分页实体转换为分页DTO
     *
     * @param page 分页实体
     * @return 分页DTO
     */
    public static PageResponse<ExecutionHistoryDTO> toPageResponse(Page<WorkflowExecutionEntity> page) {
        if (page == null) {
            return PageResponse.empty();
        }

        List<ExecutionHistoryDTO> dtoList = toDtoList(page.getContent());

        return PageResponse.<ExecutionHistoryDTO>builder()
                .content(dtoList)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .build();
    }

    /**
     * 将分页实体转换为分页DTO（带节点详情）
     *
     * @param page 分页实体
     * @param nodeLogsMap 执行ID -> 节点日志列表映射
     * @return 分页DTO
     */
    public static PageResponse<ExecutionHistoryDTO> toPageResponseWithDetails(
            Page<WorkflowExecutionEntity> page,
            java.util.Map<java.util.UUID, List<NodeExecutionLogEntity>> nodeLogsMap) {

        if (page == null) {
            return PageResponse.empty();
        }

        List<ExecutionHistoryDTO> dtoList = page.getContent().stream()
                .map(entity -> {
                    List<NodeExecutionLogEntity> logs = nodeLogsMap != null
                            ? nodeLogsMap.get(entity.getId())
                            : null;
                    return toDto(entity, logs);
                })
                .collect(Collectors.toList());

        return PageResponse.<ExecutionHistoryDTO>builder()
                .content(dtoList)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .build();
    }
}

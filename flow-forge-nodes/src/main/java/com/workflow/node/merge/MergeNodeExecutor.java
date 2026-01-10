package com.workflow.node.merge;

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
import java.util.List;
import java.util.Map;

/**
 * MERGE node executor for joining multiple execution paths.
 * <p>
 * The MERGE node waits for all incoming edges to complete (handled by the
 * scheduler's in-degree tracking) and then merges the outputs from all
 * predecessor nodes into a combined result.
 * <p>
 * Config format:
 * <pre>
 * {
 *   "mergeStrategy": "all" | "first" | "last" | "array",
 *   "includeNodeIds": ["node1", "node2"],
 *   "excludeNulls": true
 * }
 * </pre>
 * <p>
 * Output format (mergeStrategy: "all"):
 * <pre>
 * {
 *   "merged": {
 *     "node1": {"status": 200, "data": {...}},
 *     "node2": {"status": 200, "data": {...}}
 *   },
 *   "nodeIds": ["node1", "node2"],
 *   "count": 2
 * }
 * </pre>
 * <p>
 * Output format (mergeStrategy: "array"):
 * <pre>
 * {
 *   "results": [
 *     {"nodeId": "node1", "output": {...}},
 *     {"nodeId": "node2", "output": {...}}
 *   ],
 *   "count": 2
 * }
 * </pre>
 */
@Component
public class MergeNodeExecutor extends AbstractNodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(MergeNodeExecutor.class);

    private static final String DEFAULT_STRATEGY = "all";

    public MergeNodeExecutor(VariableResolver variableResolver) {
        super(variableResolver);
    }

    @Override
    public NodeType getSupportedType() {
        return NodeType.MERGE;
    }

    @Override
    protected NodeResult doExecute(Node node,
                                   ExecutionContext context,
                                   Map<String, Object> resolvedConfig) {
        String nodeId = node.getId();

        // Get merge strategy
        String strategy = getConfigString(resolvedConfig, "mergeStrategy", DEFAULT_STRATEGY);
        boolean excludeNulls = getConfigBoolean(resolvedConfig, "excludeNulls", true);

        // Get node IDs to include (optional - if not specified, include all predecessors)
        @SuppressWarnings("unchecked")
        List<String> includeNodeIds = (List<String>) resolvedConfig.get("includeNodeIds");

        // Collect results from predecessor nodes
        List<PredecessorResult> predecessorResults = collectPredecessorResults(context, includeNodeIds, excludeNulls);

        logger.debug("MERGE node {} collected {} predecessor results with strategy '{}'",
                nodeId, predecessorResults.size(), strategy);

        // Build output based on strategy
        Map<String, Object> output = buildOutput(predecessorResults, strategy);

        return NodeResult.success(nodeId, output);
    }

    /**
     * Collect results from all predecessor nodes.
     *
     * @param context         the execution context
     * @param includeNodeIds  optional list of node IDs to include (null = all)
     * @param excludeNulls    whether to exclude null results
     * @return list of predecessor results
     */
    private List<PredecessorResult> collectPredecessorResults(ExecutionContext context,
                                                                List<String> includeNodeIds,
                                                                boolean excludeNulls) {
        List<PredecessorResult> results = new ArrayList<>();
        Map<String, Object> nodeResults = new HashMap<>(context.getNodeResults());

        for (Map.Entry<String, Object> entry : nodeResults.entrySet()) {
            String nodeId = entry.getKey();
            Object result = entry.getValue();

            // Skip if not in include list (when specified)
            if (includeNodeIds != null && !includeNodeIds.isEmpty() && !includeNodeIds.contains(nodeId)) {
                continue;
            }

            // Skip null results if configured
            if (result == null && excludeNulls) {
                continue;
            }

            results.add(new PredecessorResult(nodeId, result));
        }

        return results;
    }

    /**
     * Build output based on merge strategy.
     *
     * @param results the predecessor results
     * @param strategy the merge strategy
     * @return the output map
     */
    private Map<String, Object> buildOutput(List<PredecessorResult> results, String strategy) {
        Map<String, Object> output = new HashMap<>();

        switch (strategy.toLowerCase()) {
            case "first":
                // Return only the first result
                if (!results.isEmpty()) {
                    PredecessorResult first = results.get(0);
                    output.put("nodeId", first.nodeId);
                    output.put("result", first.result);
                    output.put("count", 1);
                } else {
                    output.put("nodeId", null);
                    output.put("result", null);
                    output.put("count", 0);
                }
                break;

            case "last":
                // Return only the last result
                if (!results.isEmpty()) {
                    PredecessorResult last = results.get(results.size() - 1);
                    output.put("nodeId", last.nodeId);
                    output.put("result", last.result);
                    output.put("count", 1);
                } else {
                    output.put("nodeId", null);
                    output.put("result", null);
                    output.put("count", 0);
                }
                break;

            case "array":
                // Return results as an array with node IDs
                List<Map<String, Object>> resultsArray = new ArrayList<>();
                for (PredecessorResult pr : results) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("nodeId", pr.nodeId);
                    item.put("result", pr.result);
                    resultsArray.add(item);
                }
                output.put("results", resultsArray);
                output.put("count", results.size());
                break;

            case "all":
            default:
                // Return all results indexed by node ID (default strategy)
                Map<String, Object> merged = new HashMap<>();
                List<String> nodeIds = new ArrayList<>();
                for (PredecessorResult pr : results) {
                    // Extract output map from NodeResult
                    Object value = pr.result;
                    if (value instanceof com.workflow.model.NodeResult nodeResult) {
                        value = nodeResult.getOutput();
                    }
                    merged.put(pr.nodeId, value);
                    nodeIds.add(pr.nodeId);
                }
                output.put("merged", merged);
                output.put("nodeIds", nodeIds);
                output.put("count", results.size());
                break;
        }

        return output;
    }

    /**
     * Helper class to store predecessor node results.
     */
    private static class PredecessorResult {
        final String nodeId;
        final Object result;

        PredecessorResult(String nodeId, Object result) {
            this.nodeId = nodeId;
            this.result = result;
        }
    }
}

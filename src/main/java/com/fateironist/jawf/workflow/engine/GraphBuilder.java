package com.fateironist.jawf.workflow.engine;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.fateironist.jawf.workflow.model.Workflow;
import com.fateironist.jawf.workflow.model.edge.Edge;
import com.fateironist.jawf.workflow.model.node.Node;
import com.fateironist.jawf.workflow.model.NodeStatus;
import com.fateironist.jawf.workflow.validation.WorkflowValidator;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图构建器。
 * <p>
 * 将 {@link Workflow} 领域模型转换为 Spring AI Alibaba {@link StateGraph}。
 * <p>
 * <b>映射规则</b>：
 * <ul>
 *   <li>每个 {@link Node} 映射为一个图节点（{@code addNode}）</li>
 *   <li>每个 {@link Edge} 映射为一条图边（{@code addEdge} 或 {@code addConditionalEdges}）</li>
 *   <li>{@link com.fateironist.jawf.workflow.model.node.StartNode} 映射为 {@link StateGraph#START}</li>
 *   <li>{@link com.fateironist.jawf.workflow.model.node.EndNode} 映射为 {@link StateGraph#END}</li>
 *   <li>节点的输入输出中的 {@code ${...}} 引用在执行前从 {@link OverAllState} 解析</li>
 * </ul>
 */
@Slf4j
public class GraphBuilder {

    /**
     * 将 Workflow 转换为 StateGraph。
     *
     * @param workflow 工作流对象
     * @return 构建好的 StateGraph
     * @throws GraphStateException 如果图构建失败
     */
    public static StateGraph build(Workflow workflow) throws GraphStateException {
        // 1. 校验工作流
        WorkflowValidator.validate(workflow);

        // 2. 获取拓扑排序后的节点顺序
        List<String> sortedNodeIds = WorkflowValidator.topologicalSort(
                workflow.getNodes(), workflow.getEdges());

        // 3. 构建节点 ID 到节点的映射
        Map<String, Node> nodeMap = workflow.getNodes().stream()
                .collect(Collectors.toMap(Node::getId, n -> n));

        // 4. 创建 StateGraph
        StateGraph graph = new StateGraph();

        // 5. 添加节点（按拓扑排序顺序，跳过 Start 节点）
        for (String nodeId : sortedNodeIds) {
            Node node = nodeMap.get(nodeId);
            // StartNode 是特殊节点，不需要添加到图中
            if (node instanceof com.fateironist.jawf.workflow.model.node.StartNode) {
                log.debug("[GraphBuilder] 跳过开始节点: {} ({})", nodeId, node.getName());
                continue;
            }
            graph.addNode(nodeId, createNodeAction(node));
            log.debug("[GraphBuilder] 添加节点: {}", nodeId);
        }

        // 6. 添加边
        addEdges(graph, workflow, nodeMap, sortedNodeIds);

        // 7. 为每个 EndNode 添加到 __END__ 的边
        for (Node node : workflow.getNodes()) {
            if (node instanceof com.fateironist.jawf.workflow.model.node.EndNode) {
                graph.addEdge(node.getId(), StateGraph.END);
                log.debug("[GraphBuilder] 添加结束边: {} -> __END__", node.getId());
            }
        }

        return graph;
    }

    /**
     * 将 Node 映射为图节点 ID。
     * <p>
     * StartNode 映射为 {@code __START__}，其他节点（包括 EndNode）使用原始 ID。
     * EndNode 执行后会通过额外的边连接到 {@code __END__}。
     */
    private static String toGraphNodeId(Node node) {
        if (node instanceof com.fateironist.jawf.workflow.model.node.StartNode) {
            return StateGraph.START;
        }
        return node.getId();
    }

    /**
     * 创建节点的异步执行动作。
     * <p>
     * 包含重试逻辑：执行失败时重试，超过 maxRetry 后标记 SKIPPED。
     */
    private static AsyncNodeAction createNodeAction(Node node) {
        return AsyncNodeAction.node_async(state -> {
            log.info("[Node:{}] 开始执行", node.getId());
            node.setStatus(NodeStatus.RUNNING);
            node.setProgress(0.0);

            int maxRetry = node.getMaxRetry();
            Exception lastException = null;

            for (int attempt = 0; attempt <= maxRetry; attempt++) {
                try {
                    if (attempt > 0) {
                        node.setRetry(attempt);
                        log.info("[Node:{}] 第 {} 次重试", node.getId(), attempt);
                    }

                    Map<String, Object> result = node.execute(state);
                    node.setStatus(NodeStatus.SUCCESS);
                    node.setProgress(1.0);
                    log.info("[Node:{}] 执行成功", node.getId());
                    return result;

                } catch (Exception e) {
                    lastException = e;
                    node.setError(e.getMessage());
                    log.warn("[Node:{}] 执行失败 (尝试 {}/{}): {}",
                            node.getId(), attempt + 1, maxRetry + 1, e.getMessage());

                    if (attempt < maxRetry) {
                        // 等待一段时间后重试（指数退避）
                        try {
                            Thread.sleep((long) Math.pow(2, attempt) * 100);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            // 超过最大重试次数，标记为 SKIPPED
            node.setStatus(NodeStatus.SKIPPED);
            node.setProgress(1.0);
            log.warn("[Node:{}] 超过最大重试次数，标记为 SKIPPED", node.getId());

            // 返回空结果，让工作流继续执行
            return Collections.emptyMap();
        });
    }

    /**
     * 添加边到图中。
     */
    private static void addEdges(StateGraph graph, Workflow workflow,
                                  Map<String, Node> nodeMap, List<String> sortedNodeIds)
            throws GraphStateException {

        // 按起始节点分组边
        Map<String, List<Edge>> edgesByFrom = workflow.getEdges().stream()
                .collect(Collectors.groupingBy(Edge::getFromNodeId));

        for (String nodeId : sortedNodeIds) {
            List<Edge> outEdges = edgesByFrom.getOrDefault(nodeId, Collections.emptyList());
            if (outEdges.isEmpty()) {
                continue;
            }

            Node fromNode = nodeMap.get(nodeId);
            String fromGraphId = toGraphNodeId(fromNode);

            // 如果只有一条出边且是普通边，直接连接
            if (outEdges.size() == 1 && outEdges.get(0) instanceof com.fateironist.jawf.workflow.model.edge.NormalEdge) {
                Edge edge = outEdges.get(0);
                Node toNode = nodeMap.get(edge.getToNodeId());
                String toGraphId = toGraphNodeId(toNode);
                graph.addEdge(fromGraphId, toGraphId);
                log.debug("[GraphBuilder] 添加边: {} -> {}", fromGraphId, toGraphId);
                continue;
            }

            // 多条出边或有条件边，使用条件路由
            // 构建路由映射：条件结果 -> 目标节点
            Map<String, String> routeMap = new LinkedHashMap<>();
            for (Edge edge : outEdges) {
                Node toNode = nodeMap.get(edge.getToNodeId());
                String toGraphId = toGraphNodeId(toNode);

                if (edge instanceof com.fateironist.jawf.workflow.model.edge.ConditionEdge conditionEdge) {
                    // 条件边：使用条件表达式作为路由 key
                    String routeKey = conditionEdge.getCondition();
                    routeMap.put(routeKey, toGraphId);
                } else {
                    // 普通边：使用 "default" 作为路由 key
                    routeMap.put("default", toGraphId);
                }
            }

            // 创建条件边动作
            AsyncEdgeAction edgeAction = AsyncEdgeAction.edge_async(state -> {
                for (Edge edge : outEdges) {
                    if (edge.evaluate(state)) {
                        // 返回路由映射的 key，而不是目标节点 ID
                        if (edge instanceof com.fateironist.jawf.workflow.model.edge.ConditionEdge conditionEdge) {
                            return conditionEdge.getCondition();
                        } else {
                            return "default";
                        }
                    }
                }
                // 默认返回第一个路由 key
                return routeMap.keySet().iterator().next();
            });

            // 添加条件边
            graph.addConditionalEdges(fromGraphId, edgeAction, routeMap);
            log.debug("[GraphBuilder] 添加条件边: {} -> {}", fromGraphId, routeMap);
        }
    }
}

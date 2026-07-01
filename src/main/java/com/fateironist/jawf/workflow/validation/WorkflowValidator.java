package com.fateironist.jawf.workflow.validation;

import com.fateironist.jawf.workflow.model.Workflow;
import com.fateironist.jawf.workflow.model.edge.Edge;
import com.fateironist.jawf.workflow.model.node.Node;
import com.fateironist.jawf.workflow.model.node.StartNode;
import com.fateironist.jawf.workflow.model.node.EndNode;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 工作流校验器。
 * <p>
 * 在构建工作流前对图进行合法性校验，包括：
 * <ul>
 *   <li>存在且仅存在一个开始节点和一个结束节点</li>
 *   <li>所有边引用的节点都存在</li>
 *   <li>图不存在环（通过拓扑排序检测）</li>
 *   <li>所有节点都可达（从开始节点出发）</li>
 * </ul>
 */
@Slf4j
public class WorkflowValidator {

    /**
     * 校验工作流合法性。
     *
     * @param workflow 工作流对象
     * @throws IllegalArgumentException 如果校验失败
     */
    public static void validate(Workflow workflow) {
        List<Node> nodes = workflow.getNodes();
        List<Edge> edges = workflow.getEdges();

        // 1. 检查节点列表非空
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("工作流至少需要一个节点");
        }

        // 2. 检查开始节点和结束节点
        long startCount = nodes.stream().filter(n -> n instanceof StartNode).count();
        long endCount = nodes.stream().filter(n -> n instanceof EndNode).count();
        if (startCount != 1) {
            throw new IllegalArgumentException("工作流必须有且仅有一个开始节点，当前: " + startCount);
        }
        if (endCount != 1) {
            throw new IllegalArgumentException("工作流必须有且仅有一个结束节点，当前: " + endCount);
        }

        // 3. 检查节点 ID 唯一性
        Set<String> nodeIds = new HashSet<>();
        for (Node node : nodes) {
            if (node.getId() == null || node.getId().isBlank()) {
                throw new IllegalArgumentException("节点 ID 不能为空");
            }
            if (!nodeIds.add(node.getId())) {
                throw new IllegalArgumentException("节点 ID 重复: " + node.getId());
            }
        }

        // 4. 检查边引用的节点是否存在
        for (Edge edge : edges) {
            if (!nodeIds.contains(edge.getFromNodeId())) {
                throw new IllegalArgumentException(
                        "边 " + edge.getId() + " 引用的起始节点不存在: " + edge.getFromNodeId());
            }
            if (!nodeIds.contains(edge.getToNodeId())) {
                throw new IllegalArgumentException(
                        "边 " + edge.getId() + " 引用的目标节点不存在: " + edge.getToNodeId());
            }
        }

        // 5. 拓扑排序检测环
        List<String> sortedOrder = topologicalSort(nodes, edges);
        log.debug("[WorkflowValidator] 拓扑排序结果: {}", sortedOrder);

        // 6. 检查从开始节点可达性（非致命警告）
        Set<String> reachable = findReachableNodes(workflow);
        for (Node node : nodes) {
            if (!reachable.contains(node.getId()) && !(node instanceof EndNode)) {
                log.warn("[WorkflowValidator] 节点不可达: {} ({})", node.getId(), node.getName());
            }
        }
    }

    /**
     * 拓扑排序。
     * <p>
     * 使用 Kahn 算法（BFS），如果排序结果不包含所有节点，则说明图中存在环。
     *
     * @return 拓扑排序后的节点 ID 列表
     * @throws IllegalArgumentException 如果图中存在环
     */
    public static List<String> topologicalSort(List<Node> nodes, List<Edge> edges) {
        // 构建邻接表和入度表
        Map<String, Set<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (Node node : nodes) {
            adjacency.put(node.getId(), new HashSet<>());
            inDegree.put(node.getId(), 0);
        }

        for (Edge edge : edges) {
            String from = edge.getFromNodeId();
            String to = edge.getToNodeId();
            // 避免重复边导致入度重复计算
            if (adjacency.get(from).add(to)) {
                inDegree.merge(to, 1, Integer::sum);
            }
        }

        // BFS 拓扑排序
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            sorted.add(nodeId);
            for (String neighbor : adjacency.get(nodeId)) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        // 检测环
        if (sorted.size() != nodes.size()) {
            // 找出环中的节点
            Set<String> cycleNodes = new HashSet<>();
            for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() > 0) {
                    cycleNodes.add(entry.getKey());
                }
            }
            throw new IllegalArgumentException(
                    "工作流图中存在环，涉及节点: " + cycleNodes);
        }

        return sorted;
    }

    /**
     * 查找从开始节点可达的所有节点。
     */
    static Set<String> findReachableNodes(Workflow workflow) {
        Optional<Node> startOpt = workflow.getStartNode();
        if (startOpt.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> reachable = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startOpt.get().getId());

        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            if (!reachable.add(nodeId)) {
                continue;
            }
            for (Edge edge : workflow.getOutEdges(nodeId)) {
                if (!reachable.contains(edge.getToNodeId())) {
                    queue.add(edge.getToNodeId());
                }
            }
        }

        return reachable;
    }
}

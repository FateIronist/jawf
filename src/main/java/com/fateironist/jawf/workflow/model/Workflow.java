package com.fateironist.jawf.workflow.model;

import com.fateironist.jawf.workflow.model.edge.Edge;
import com.fateironist.jawf.workflow.model.node.Node;
import com.fateironist.jawf.workflow.model.node.StartNode;
import com.fateironist.jawf.workflow.model.node.EndNode;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工作流顶层容器。
 * <p>
 * 管理所有节点和边，记录对应图的 {@code graphId}，
 * 提供工作流的构建、序列化等能力。
 * <p>
 * <b>节点与边的关系</b>：
 * <ul>
 *   <li>节点之间通过"引用"进行连接，而不是直接传值</li>
 *   <li>所有参数统一按 key-value 方式存储在 {@code OverAllState} 中</li>
 *   <li>节点输入输出中的 {@code ${...}} 在执行前从 OverAllState 解析为实际值</li>
 * </ul>
 */
@Data
public class Workflow {

    /** 工作流 ID */
    private String id;

    /** 工作流名称 */
    private String name;

    /** 对应图的 ID（编译后由 Spring AI Alibaba Graph 分配） */
    private String graphId;

    /** 工作流描述 */
    private String description;

    /** 节点列表 */
    private List<Node> nodes = new ArrayList<>();

    /** 边列表 */
    private List<Edge> edges = new ArrayList<>();

    /** 工作流变量（全局变量，可在节点间共享） */
    private Map<String, Object> variables = new HashMap<>();

    /** 工作流状态 */
    private WorkflowStatus status = WorkflowStatus.IDLE;

    /**
     * 添加节点。
     */
    public Workflow addNode(Node node) {
        nodes.add(node);
        return this;
    }

    /**
     * 添加边。
     */
    public Workflow addEdge(Edge edge) {
        edges.add(edge);
        return this;
    }

    /**
     * 根据 ID 查找节点。
     */
    public Optional<Node> findNodeById(String nodeId) {
        return nodes.stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst();
    }

    /**
     * 根据 ID 查找边。
     */
    public Optional<Edge> findEdgeById(String edgeId) {
        return edges.stream()
                .filter(e -> e.getId().equals(edgeId))
                .findFirst();
    }

    /**
     * 获取指定节点的所有出边（以该节点为起点的边）。
     */
    public List<Edge> getOutEdges(String nodeId) {
        return edges.stream()
                .filter(e -> e.getFromNodeId().equals(nodeId))
                .toList();
    }

    /**
     * 获取指定节点的所有入边（以该节点为终点的边）。
     */
    public List<Edge> getInEdges(String nodeId) {
        return edges.stream()
                .filter(e -> e.getToNodeId().equals(nodeId))
                .toList();
    }

    /**
     * 获取开始节点。
     */
    public Optional<Node> getStartNode() {
        return nodes.stream()
                .filter(n -> n instanceof StartNode)
                .findFirst();
    }

    /**
     * 获取结束节点。
     */
    public Optional<Node> getEndNode() {
        return nodes.stream()
                .filter(n -> n instanceof EndNode)
                .findFirst();
    }

    /**
     * 重置所有节点状态（用于重新执行）。
     */
    public void reset() {
        status = WorkflowStatus.IDLE;
        nodes.forEach(Node::reset);
    }
}

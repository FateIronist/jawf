package com.fateironist.jawf.workflow.model.edge;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 * 工作流边抽象基类。
 * <p>
 * 连接两个节点，定义数据流向。边可以是无条件的（{@link NormalEdge}）
 * 或带条件的（{@link ConditionEdge}）。
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = NormalEdge.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = NormalEdge.class, name = "normal"),
        @JsonSubTypes.Type(value = ConditionEdge.class, name = "condition")
})
public abstract class Edge {

    /** 全局唯一 ID */
    protected String id;

    /** 边名称 */
    protected String name;

    /** 起始节点 ID */
    protected String fromNodeId;

    /** 起始节点名称 */
    protected String fromNodeName;

    /** 目标节点 ID */
    protected String toNodeId;

    /** 目标节点名称 */
    protected String toNodeName;

    /**
     * 评估此边是否应该被激活。
     * <p>
     * 对于 {@link NormalEdge} 始终返回 {@code true}；
     * 对于 {@link ConditionEdge} 需要根据条件表达式求值。
     *
     * @param state 当前工作流全局状态
     * @return 是否激活
     */
    public abstract boolean evaluate(OverAllState state);
}

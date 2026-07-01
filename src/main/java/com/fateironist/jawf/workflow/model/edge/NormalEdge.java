package com.fateironist.jawf.workflow.model.edge;

import com.alibaba.cloud.ai.graph.OverAllState;

/**
 * 普通边（无条件）。
 * <p>
 * 始终激活，将执行流无条件地从起始节点传递到目标节点。
 */
public class NormalEdge extends Edge {

    public NormalEdge() {
    }

    public NormalEdge(String id, String name, String fromNodeId, String toNodeId) {
        this.id = id;
        this.name = name;
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
    }

    @Override
    public boolean evaluate(OverAllState state) {
        return true;
    }
}

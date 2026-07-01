package com.fateironist.jawf.workflow.model.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fateironist.jawf.workflow.expression.ExpressionEngine;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流结束节点。
 * <p>
 * 作为工作流出口，从 {@link OverAllState} 中收集最终输出。
 * 支持通过 {@code ${...}} 引用其他节点的输出。
 */
public class EndNode extends Node {

    public EndNode() {
        this.id = "end";
        this.name = "结束";
        this.typeIdentifier = "end";
        this.maxRetry = 0;
    }

    public EndNode(String id, String name) {
        this.id = id;
        this.name = name;
        this.typeIdentifier = "end";
        this.maxRetry = 0;
    }

    @Override
    public Map<String, Object> execute(OverAllState state) {
        // 结束节点：解析 output 中的引用，收集最终输出
        Map<String, Object> result = new HashMap<>();
        String prefix = getStateKeyPrefix();
        for (Map.Entry<String, Object> entry : output.entrySet()) {
            Object resolved = ExpressionEngine.resolveValue(entry.getValue(), state);
            result.put(prefix + entry.getKey(), resolved);
        }
        return result;
    }
}

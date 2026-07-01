package com.fateironist.jawf.workflow.model.node;

import com.alibaba.cloud.ai.graph.OverAllState;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流开始节点。
 * <p>
 * 作为工作流入口，将初始输入写入 {@link OverAllState}。
 * 不执行任何业务逻辑，仅负责传递初始参数。
 */
public class StartNode extends Node {

    public StartNode() {
        this.id = "start";
        this.name = "开始";
        this.typeIdentifier = "start";
        this.maxRetry = 0;
    }

    public StartNode(String id, String name) {
        this.id = id;
        this.name = name;
        this.typeIdentifier = "start";
        this.maxRetry = 0;
    }

    @Override
    public Map<String, Object> execute(OverAllState state) {
        // 开始节点：将 input 中的初始值写入 OverallState
        Map<String, Object> result = new HashMap<>();
        String prefix = getStateKeyPrefix();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            result.put(prefix + entry.getKey(), entry.getValue());
        }
        return result;
    }
}

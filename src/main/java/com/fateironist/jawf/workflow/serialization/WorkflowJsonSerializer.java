package com.fateironist.jawf.workflow.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fateironist.jawf.workflow.model.Workflow;
import com.fateironist.jawf.workflow.model.WorkflowStatus;
import com.fateironist.jawf.workflow.model.edge.ConditionEdge;
import com.fateironist.jawf.workflow.model.edge.Edge;
import com.fateironist.jawf.workflow.model.edge.NormalEdge;
import com.fateironist.jawf.workflow.model.node.EndNode;
import com.fateironist.jawf.workflow.model.node.LLMNode;
import com.fateironist.jawf.workflow.model.node.Node;
import com.fateironist.jawf.workflow.model.node.StartNode;

/**
 * 工作流 JSON 序列化器。
 * <p>
 * 将 {@link Workflow} 序列化为 JSON 格式，包含：
 * <ul>
 *   <li>工作流元数据（id, name, description）</li>
 *   <li>节点列表（含类型、输入输出、布局信息、UI 元数据）</li>
 *   <li>边列表（含类型、起止节点、条件表达式）</li>
 *   <li>变量定义</li>
 * </ul>
 * <p>
 * JSON 结构设计为前端友好，便于实时展示和编辑。
 */
public class WorkflowJsonSerializer {

    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * 将 Workflow 序列化为 JSON 字符串。
     *
     * @param workflow 工作流对象
     * @return JSON 字符串
     */
    public static String serialize(Workflow workflow) {
        try {
            ObjectNode root = serializeToNode(workflow);
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("工作流序列化失败", e);
        }
    }

    /**
     * 将 Workflow 序列化为 ObjectNode。
     */
    public static ObjectNode serializeToNode(Workflow workflow) {
        ObjectNode root = mapper.createObjectNode();

        // 基本信息
        root.put("id", workflow.getId());
        root.put("name", workflow.getName());
        root.put("description", workflow.getDescription());
        root.put("graphId", workflow.getGraphId());
        root.put("status", workflow.getStatus().name());

        // 变量
        ObjectNode variables = mapper.createObjectNode();
        if (workflow.getVariables() != null) {
            workflow.getVariables().forEach((k, v) -> setJsonValue(variables, k, v));
        }
        root.set("variables", variables);

        // 节点
        ArrayNode nodesArray = mapper.createArrayNode();
        for (Node node : workflow.getNodes()) {
            nodesArray.add(serializeNode(node));
        }
        root.set("nodes", nodesArray);

        // 边
        ArrayNode edgesArray = mapper.createArrayNode();
        for (Edge edge : workflow.getEdges()) {
            edgesArray.add(serializeEdge(edge));
        }
        root.set("edges", edgesArray);

        return root;
    }

    /**
     * 序列化单个节点。
     */
    private static ObjectNode serializeNode(Node node) {
        ObjectNode nodeObj = mapper.createObjectNode();

        // 基本属性
        nodeObj.put("id", node.getId());
        nodeObj.put("name", node.getName());
        nodeObj.put("type", getTypeName(node));
        nodeObj.put("status", node.getStatus().name());
        nodeObj.put("progress", node.getProgress());
        nodeObj.put("retry", node.getRetry());
        nodeObj.put("maxRetry", node.getMaxRetry());
        nodeObj.put("error", node.getError());

        // 输入
        ObjectNode inputObj = mapper.createObjectNode();
        if (node.getInput() != null) {
            node.getInput().forEach((k, v) -> setJsonValue(inputObj, k, v));
        }
        nodeObj.set("input", inputObj);

        // 输出
        ObjectNode outputObj = mapper.createObjectNode();
        if (node.getOutput() != null) {
            node.getOutput().forEach((k, v) -> setJsonValue(outputObj, k, v));
        }
        nodeObj.set("output", outputObj);

        // 布局信息
        ObjectNode position = mapper.createObjectNode();
        if (node.getX() != null) position.put("x", node.getX());
        if (node.getY() != null) position.put("y", node.getY());
        nodeObj.set("position", position);

        // UI 元数据
        ObjectNode ui = mapper.createObjectNode();
        if (node.getIcon() != null) ui.put("icon", node.getIcon());
        if (node.getColor() != null) ui.put("color", node.getColor());
        if (node.getTypeIdentifier() != null) ui.put("typeIdentifier", node.getTypeIdentifier());
        if (node.getExpanded() != null) ui.put("expanded", node.getExpanded());
        nodeObj.set("ui", ui);

        // LLM 节点特有属性
        if (node instanceof LLMNode llmNode) {
            ObjectNode llmConfig = mapper.createObjectNode();
            llmConfig.put("modelIdentifier", llmNode.getModelIdentifier());
            llmConfig.put("systemPrompt", llmNode.getSystemPrompt());
            llmConfig.put("userPromptTemplate", llmNode.getUserPromptTemplate());
            nodeObj.set("llmConfig", llmConfig);
        }

        return nodeObj;
    }

    /**
     * 序列化单条边。
     */
    private static ObjectNode serializeEdge(Edge edge) {
        ObjectNode edgeObj = mapper.createObjectNode();

        edgeObj.put("id", edge.getId());
        edgeObj.put("name", edge.getName());
        edgeObj.put("type", edge instanceof ConditionEdge ? "condition" : "normal");
        edgeObj.put("fromNodeId", edge.getFromNodeId());
        edgeObj.put("fromNodeName", edge.getFromNodeName());
        edgeObj.put("toNodeId", edge.getToNodeId());
        edgeObj.put("toNodeName", edge.getToNodeName());

        // 条件边特有属性
        if (edge instanceof ConditionEdge conditionEdge) {
            edgeObj.put("condition", conditionEdge.getCondition());
        }

        return edgeObj;
    }

    /**
     * 获取节点类型名称。
     */
    private static String getTypeName(Node node) {
        if (node instanceof StartNode) return "start";
        if (node instanceof EndNode) return "end";
        if (node instanceof LLMNode) return "llm";
        return "unknown";
    }

    /**
     * 设置 JSON 值（支持多种类型）。
     */
    private static void setJsonValue(ObjectNode node, String key, Object value) {
        if (value == null) {
            node.putNull(key);
        } else if (value instanceof String s) {
            node.put(key, s);
        } else if (value instanceof Integer i) {
            node.put(key, i);
        } else if (value instanceof Long l) {
            node.put(key, l);
        } else if (value instanceof Double d) {
            node.put(key, d);
        } else if (value instanceof Boolean b) {
            node.put(key, b);
        } else {
            node.put(key, String.valueOf(value));
        }
    }
}

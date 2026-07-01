package com.fateironist.jawf.workflow.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fateironist.jawf.workflow.model.Workflow;
import com.fateironist.jawf.workflow.model.WorkflowStatus;
import com.fateironist.jawf.workflow.model.edge.ConditionEdge;
import com.fateironist.jawf.workflow.model.edge.Edge;
import com.fateironist.jawf.workflow.model.edge.NormalEdge;
import com.fateironist.jawf.workflow.model.node.EndNode;
import com.fateironist.jawf.workflow.model.node.LLMNode;
import com.fateironist.jawf.workflow.model.node.Node;
import com.fateironist.jawf.workflow.model.node.StartNode;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流 JSON 反序列化器。
 * <p>
 * 从 JSON 格式恢复 {@link Workflow} 对象，支持：
 * <ul>
 *   <li>工作流元数据</li>
 *   <li>节点列表（含类型、输入输出、布局信息、UI 元数据）</li>
 *   <li>边列表（含类型、起止节点、条件表达式）</li>
 *   <li>变量定义</li>
 * </ul>
 */
public class WorkflowJsonDeserializer {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 从 JSON 字符串反序列化为 Workflow。
     *
     * @param json JSON 字符串
     * @return Workflow 对象
     */
    public static Workflow deserialize(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            return deserializeFromNode(root);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("工作流反序列化失败", e);
        }
    }

    /**
     * 从 JsonNode 反序列化为 Workflow。
     */
    public static Workflow deserializeFromNode(JsonNode root) {
        Workflow workflow = new Workflow();

        // 基本信息
        workflow.setId(getStringOrDefault(root, "id", null));
        workflow.setName(getStringOrDefault(root, "name", null));
        workflow.setDescription(getStringOrDefault(root, "description", null));
        workflow.setGraphId(getStringOrDefault(root, "graphId", null));
        workflow.setStatus(parseStatus(getStringOrDefault(root, "status", "IDLE")));

        // 变量
        JsonNode variablesNode = root.get("variables");
        if (variablesNode != null && variablesNode.isObject()) {
            Map<String, Object> variables = new HashMap<>();
            variablesNode.fields().forEachRemaining(entry -> {
                variables.put(entry.getKey(), parseValue(entry.getValue()));
            });
            workflow.setVariables(variables);
        }

        // 节点
        JsonNode nodesNode = root.get("nodes");
        if (nodesNode != null && nodesNode.isArray()) {
            for (JsonNode nodeJson : nodesNode) {
                Node node = deserializeNode(nodeJson);
                workflow.addNode(node);
            }
        }

        // 边
        JsonNode edgesNode = root.get("edges");
        if (edgesNode != null && edgesNode.isArray()) {
            for (JsonNode edgeJson : edgesNode) {
                Edge edge = deserializeEdge(edgeJson);
                workflow.addEdge(edge);
            }
        }

        return workflow;
    }

    /**
     * 反序列化节点。
     */
    private static Node deserializeNode(JsonNode nodeJson) {
        String type = getStringOrDefault(nodeJson, "type", "llm");
        String id = getStringOrDefault(nodeJson, "id", null);
        String name = getStringOrDefault(nodeJson, "name", null);

        Node node = switch (type) {
            case "start" -> new StartNode(id, name);
            case "end" -> new EndNode(id, name);
            case "llm" -> {
                LLMNode llmNode = new LLMNode(id, name);
                // LLM 特有配置
                JsonNode llmConfig = nodeJson.get("llmConfig");
                if (llmConfig != null) {
                    llmNode.setModelIdentifier(getStringOrDefault(llmConfig, "modelIdentifier", null));
                    llmNode.setSystemPrompt(getStringOrDefault(llmConfig, "systemPrompt", null));
                    llmNode.setUserPromptTemplate(getStringOrDefault(llmConfig, "userPromptTemplate", null));
                }
                yield llmNode;
            }
            default -> throw new IllegalArgumentException("未知节点类型: " + type);
        };

        // 输入
        JsonNode inputNode = nodeJson.get("input");
        if (inputNode != null && inputNode.isObject()) {
            Map<String, Object> input = new HashMap<>();
            inputNode.fields().forEachRemaining(entry -> {
                input.put(entry.getKey(), parseValue(entry.getValue()));
            });
            node.setInput(input);
        }

        // 输出
        JsonNode outputNode = nodeJson.get("output");
        if (outputNode != null && outputNode.isObject()) {
            Map<String, Object> output = new HashMap<>();
            outputNode.fields().forEachRemaining(entry -> {
                output.put(entry.getKey(), parseValue(entry.getValue()));
            });
            node.setOutput(output);
        }

        // 状态
        node.setStatus(parseNodeStatus(getStringOrDefault(nodeJson, "status", "PENDING")));
        node.setProgress(nodeJson.has("progress") ? nodeJson.get("progress").asDouble() : 0.0);
        node.setRetry(nodeJson.has("retry") ? nodeJson.get("retry").asInt() : 0);
        node.setMaxRetry(nodeJson.has("maxRetry") ? nodeJson.get("maxRetry").asInt() : 3);
        node.setError(getStringOrDefault(nodeJson, "error", null));

        // 布局信息
        JsonNode positionNode = nodeJson.get("position");
        if (positionNode != null) {
            if (positionNode.has("x")) node.setX(positionNode.get("x").asDouble());
            if (positionNode.has("y")) node.setY(positionNode.get("y").asDouble());
        }

        // UI 元数据
        JsonNode uiNode = nodeJson.get("ui");
        if (uiNode != null) {
            node.setIcon(getStringOrDefault(uiNode, "icon", null));
            node.setColor(getStringOrDefault(uiNode, "color", null));
            node.setTypeIdentifier(getStringOrDefault(uiNode, "typeIdentifier", null));
            if (uiNode.has("expanded")) node.setExpanded(uiNode.get("expanded").asBoolean());
        }

        return node;
    }

    /**
     * 反序列化边。
     */
    private static Edge deserializeEdge(JsonNode edgeJson) {
        String type = getStringOrDefault(edgeJson, "type", "normal");
        String id = getStringOrDefault(edgeJson, "id", null);
        String name = getStringOrDefault(edgeJson, "name", null);
        String fromNodeId = getStringOrDefault(edgeJson, "fromNodeId", null);
        String fromNodeName = getStringOrDefault(edgeJson, "fromNodeName", null);
        String toNodeId = getStringOrDefault(edgeJson, "toNodeId", null);
        String toNodeName = getStringOrDefault(edgeJson, "toNodeName", null);

        Edge edge;
        if ("condition".equals(type)) {
            String condition = getStringOrDefault(edgeJson, "condition", null);
            ConditionEdge conditionEdge = new ConditionEdge(id, name, fromNodeId, toNodeId, condition);
            conditionEdge.setFromNodeName(fromNodeName);
            conditionEdge.setToNodeName(toNodeName);
            edge = conditionEdge;
        } else {
            NormalEdge normalEdge = new NormalEdge(id, name, fromNodeId, toNodeId);
            normalEdge.setFromNodeName(fromNodeName);
            normalEdge.setToNodeName(toNodeName);
            edge = normalEdge;
        }

        return edge;
    }

    /**
     * 解析 JSON 值为 Java 对象。
     */
    private static Object parseValue(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        if (valueNode.isTextual()) {
            return valueNode.asText();
        }
        if (valueNode.isInt()) {
            return valueNode.asInt();
        }
        if (valueNode.isLong()) {
            return valueNode.asLong();
        }
        if (valueNode.isDouble() || valueNode.isFloat()) {
            return valueNode.asDouble();
        }
        if (valueNode.isBoolean()) {
            return valueNode.asBoolean();
        }
        return valueNode.toString();
    }

    /**
     * 解析工作流状态。
     */
    private static WorkflowStatus parseStatus(String status) {
        try {
            return WorkflowStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return WorkflowStatus.IDLE;
        }
    }

    /**
     * 解析节点状态。
     */
    private static com.fateironist.jawf.workflow.model.NodeStatus parseNodeStatus(String status) {
        try {
            return com.fateironist.jawf.workflow.model.NodeStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return com.fateironist.jawf.workflow.model.NodeStatus.PENDING;
        }
    }

    /**
     * 获取字符串值（带默认值）。
     */
    private static String getStringOrDefault(JsonNode node, String field, String defaultValue) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return defaultValue;
    }
}

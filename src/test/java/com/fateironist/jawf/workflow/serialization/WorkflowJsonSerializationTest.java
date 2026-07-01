package com.fateironist.jawf.workflow.serialization;

import com.fateironist.jawf.workflow.model.Workflow;
import com.fateironist.jawf.workflow.model.WorkflowStatus;
import com.fateironist.jawf.workflow.model.edge.ConditionEdge;
import com.fateironist.jawf.workflow.model.edge.NormalEdge;
import com.fateironist.jawf.workflow.model.node.EndNode;
import com.fateironist.jawf.workflow.model.node.LLMNode;
import com.fateironist.jawf.workflow.model.node.StartNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Workflow JSON 序列化/反序列化测试。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowJsonSerializationTest {

    @Test
    @Order(1)
    @DisplayName("序列化/反序列化 - 基本往返")
    void testRoundTrip() {
        Workflow original = createSampleWorkflow();

        // 序列化
        String json = WorkflowJsonSerializer.serialize(original);
        assertNotNull(json);
        assertFalse(json.isEmpty());

        // 反序列化
        Workflow restored = WorkflowJsonDeserializer.deserialize(json);

        // 验证基本字段
        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getDescription(), restored.getDescription());

        // 验证节点数量
        assertEquals(original.getNodes().size(), restored.getNodes().size());

        // 验证边数量
        assertEquals(original.getEdges().size(), restored.getEdges().size());
    }

    @Test
    @Order(2)
    @DisplayName("序列化/反序列化 - 节点属性保留")
    void testNodePropertiesPreserved() {
        Workflow original = createSampleWorkflow();

        String json = WorkflowJsonSerializer.serialize(original);
        Workflow restored = WorkflowJsonDeserializer.deserialize(json);

        // 验证 StartNode
        StartNode origStart = (StartNode) original.getNodes().get(0);
        StartNode restStart = (StartNode) restored.getNodes().get(0);
        assertEquals(origStart.getId(), restStart.getId());
        assertEquals(origStart.getName(), restStart.getName());

        // 验证 LLMNode
        LLMNode origLlm = (LLMNode) original.getNodes().get(1);
        LLMNode restLlm = (LLMNode) restored.getNodes().get(1);
        assertEquals(origLlm.getId(), restLlm.getId());
        assertEquals(origLlm.getModelIdentifier(), restLlm.getModelIdentifier());
        assertEquals(origLlm.getSystemPrompt(), restLlm.getSystemPrompt());
        assertEquals(origLlm.getUserPromptTemplate(), restLlm.getUserPromptTemplate());
    }

    @Test
    @Order(3)
    @DisplayName("序列化/反序列化 - 布局信息保留")
    void testLayoutPreserved() {
        Workflow original = createSampleWorkflow();

        // 设置布局信息
        original.getNodes().get(0).setX(100.0);
        original.getNodes().get(0).setY(200.0);

        String json = WorkflowJsonSerializer.serialize(original);
        Workflow restored = WorkflowJsonDeserializer.deserialize(json);

        assertEquals(100.0, restored.getNodes().get(0).getX());
        assertEquals(200.0, restored.getNodes().get(0).getY());
    }

    @Test
    @Order(4)
    @DisplayName("序列化/反序列化 - UI 元数据保留")
    void testUiMetadataPreserved() {
        Workflow original = createSampleWorkflow();

        // 设置 UI 元数据
        original.getNodes().get(0).setIcon("play-circle");
        original.getNodes().get(0).setColor("#4CAF50");
        original.getNodes().get(0).setExpanded(true);

        String json = WorkflowJsonSerializer.serialize(original);
        Workflow restored = WorkflowJsonDeserializer.deserialize(json);

        assertEquals("play-circle", restored.getNodes().get(0).getIcon());
        assertEquals("#4CAF50", restored.getNodes().get(0).getColor());
        assertTrue(restored.getNodes().get(0).getExpanded());
    }

    @Test
    @Order(5)
    @DisplayName("序列化/反序列化 - 条件边保留")
    void testConditionEdgePreserved() {
        Workflow original = createSampleWorkflow();

        String json = WorkflowJsonSerializer.serialize(original);
        Workflow restored = WorkflowJsonDeserializer.deserialize(json);

        // 找到条件边
        ConditionEdge origCondition = original.getEdges().stream()
                .filter(e -> e instanceof ConditionEdge)
                .map(e -> (ConditionEdge) e)
                .findFirst()
                .orElse(null);

        ConditionEdge restCondition = restored.getEdges().stream()
                .filter(e -> e instanceof ConditionEdge)
                .map(e -> (ConditionEdge) e)
                .findFirst()
                .orElse(null);

        assertNotNull(origCondition);
        assertNotNull(restCondition);
        assertEquals(origCondition.getCondition(), restCondition.getCondition());
    }

    @Test
    @Order(6)
    @DisplayName("序列化/反序列化 - 变量保留")
    void testVariablesPreserved() {
        Workflow original = createSampleWorkflow();
        original.getVariables().put("globalPrompt", "你是一个助手");
        original.getVariables().put("maxTokens", 1000);

        String json = WorkflowJsonSerializer.serialize(original);
        Workflow restored = WorkflowJsonDeserializer.deserialize(json);

        assertEquals("你是一个助手", restored.getVariables().get("globalPrompt"));
        assertEquals(1000, restored.getVariables().get("maxTokens"));
    }

    @Test
    @Order(7)
    @DisplayName("序列化 - JSON 结构正确")
    void testJsonStructure() {
        Workflow workflow = createSampleWorkflow();
        String json = WorkflowJsonSerializer.serialize(workflow);

        // 验证 JSON 包含必要字段
        assertTrue(json.contains("\"id\""));
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"nodes\""));
        assertTrue(json.contains("\"edges\""));
        assertTrue(json.contains("\"variables\""));
        assertTrue(json.contains("\"position\""));
        assertTrue(json.contains("\"ui\""));
    }

    @Test
    @Order(8)
    @DisplayName("反序列化 - 空 JSON 处理")
    void testDeserializeEmptyJson() {
        String json = "{}";
        Workflow workflow = WorkflowJsonDeserializer.deserialize(json);

        assertNotNull(workflow);
        assertNull(workflow.getId());
        assertTrue(workflow.getNodes().isEmpty());
        assertTrue(workflow.getEdges().isEmpty());
    }

    // ==================== 辅助方法 ====================

    private Workflow createSampleWorkflow() {
        Workflow workflow = new Workflow();
        workflow.setId("test-workflow");
        workflow.setName("测试工作流");
        workflow.setDescription("用于测试的工作流");
        workflow.setStatus(WorkflowStatus.IDLE);

        // 创建节点
        StartNode start = new StartNode("start", "开始");
        start.setInput(Map.of("userQuery", "你好"));

        LLMNode llm = new LLMNode("llm_1", "AI 处理");
        llm.setModelIdentifier("dashscope_deepseek-v4-flash");
        llm.setSystemPrompt("你是一个助手");
        llm.setUserPromptTemplate("请回答：${start.userQuery}");
        llm.setMaxRetry(2);

        EndNode end = new EndNode("end", "结束");
        end.setOutput(Map.of("result", "${llm_1.response}"));

        workflow.addNode(start);
        workflow.addNode(llm);
        workflow.addNode(end);

        // 创建边
        workflow.addEdge(new NormalEdge("e1", "开始到AI", "start", "llm_1"));
        workflow.addEdge(new ConditionEdge("e2", "AI到结束", "llm_1", "end", "${llm_1.response} != ''"));

        return workflow;
    }
}

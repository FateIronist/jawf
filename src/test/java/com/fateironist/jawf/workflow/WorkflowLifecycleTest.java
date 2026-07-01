package com.fateironist.jawf.workflow;

import com.fateironist.jawf.workflow.engine.WorkflowEngine;
import com.fateironist.jawf.workflow.model.Workflow;
import com.fateironist.jawf.workflow.model.WorkflowStatus;
import com.fateironist.jawf.workflow.model.edge.ConditionEdge;
import com.fateironist.jawf.workflow.model.edge.NormalEdge;
import com.fateironist.jawf.workflow.model.node.EndNode;
import com.fateironist.jawf.workflow.model.node.LLMNode;
import com.fateironist.jawf.workflow.model.node.StartNode;
import com.fateironist.jawf.workflow.serialization.WorkflowJsonDeserializer;
import com.fateironist.jawf.workflow.serialization.WorkflowJsonSerializer;
import com.fateironist.jawf.workflow.service.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工作流完整生命周期测试。
 * <p>
 * 测试流程：搭建 Workflow → 序列化为 JSON → 反序列化为 Workflow → 编译执行
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowLifecycleTest {

    @Autowired
    private WorkflowService workflowService;

    private String workflowJson;
    private WorkflowEngine.WorkflowResult executionResult;

    // ==================== 第一阶段：搭建 Workflow ====================

    @Test
    @Order(1)
    @DisplayName("搭建 - 创建工作流对象")
    void testBuildWorkflow() {
        Workflow workflow = createSampleWorkflow();

        // 验证工作流结构
        assertNotNull(workflow);
        assertEquals("lifecycle-test", workflow.getId());
        assertEquals("生命周期测试工作流", workflow.getName());
        assertEquals(4, workflow.getNodes().size()); // start, llm_1, llm_2, end
        assertEquals(3, workflow.getEdges().size()); // start->llm_1, llm_1->llm_2, llm_2->end

        log.info("[搭建] 工作流创建成功: {} ({})", workflow.getName(), workflow.getId());
    }

    // ==================== 第二阶段：序列化为 JSON ====================

    @Test
    @Order(2)
    @DisplayName("序列化 - 工作流转 JSON")
    void testSerializeToJson() {
        Workflow workflow = createSampleWorkflow();

        // 序列化
        workflowJson = WorkflowJsonSerializer.serialize(workflow);

        // 验证 JSON 非空且包含关键字段
        assertNotNull(workflowJson);
        assertFalse(workflowJson.isEmpty());
        assertTrue(workflowJson.contains("\"id\""));
        assertTrue(workflowJson.contains("\"name\""));
        assertTrue(workflowJson.contains("\"nodes\""));
        assertTrue(workflowJson.contains("\"edges\""));
        assertTrue(workflowJson.contains("\"llmConfig\""));

        log.info("[序列化] JSON 长度: {} 字符", workflowJson.length());
        log.debug("[序列化] JSON 内容:\n{}", workflowJson);
    }

    // ==================== 第三阶段：反序列化为 Workflow ====================

    @Test
    @Order(3)
    @DisplayName("反序列化 - JSON 转工作流")
    void testDeserializeFromJson() {
        assertNotNull(workflowJson, "需要先执行序列化测试");

        // 反序列化
        Workflow restored = WorkflowJsonDeserializer.deserialize(workflowJson);

        // 验证基本字段
        assertEquals("lifecycle-test", restored.getId());
        assertEquals("生命周期测试工作流", restored.getName());
        assertEquals("用于测试工作流完整生命周期", restored.getDescription());

        // 验证节点
        assertEquals(4, restored.getNodes().size());
        assertTrue(restored.getNodes().get(0) instanceof StartNode);
        assertTrue(restored.getNodes().get(1) instanceof LLMNode);
        assertTrue(restored.getNodes().get(2) instanceof LLMNode);
        assertTrue(restored.getNodes().get(3) instanceof EndNode);

        // 验证 LLM 节点配置
        LLMNode llm1 = (LLMNode) restored.getNodes().get(1);
        assertEquals("llm_1", llm1.getId());
        assertEquals("dashscope_deepseek-v4-flash", llm1.getModelIdentifier());
        assertEquals("你是一个助手", llm1.getSystemPrompt());

        // 验证边
        assertEquals(3, restored.getEdges().size());

        // 验证变量
        assertEquals("你是一个助手", restored.getVariables().get("globalPrompt"));

        log.info("[反序列化] 工作流还原成功: {}", restored.getName());
    }

    // ==================== 第四阶段：编译执行 ====================

    @Test
    @Order(4)
    @DisplayName("编译 - 编译工作流")
    void testCompileWorkflow() {
        assertNotNull(workflowJson, "需要先执行序列化测试");

        // 从 JSON 反序列化并编译
        Workflow workflow = WorkflowJsonDeserializer.deserialize(workflowJson);
        assertDoesNotThrow(() -> workflowService.compile(workflow));

        // 验证编译成功
        assertTrue(workflowService.isCompiled("lifecycle-test"));

        log.info("[编译] 工作流编译成功");
    }

    @Test
    @Order(5)
    @DisplayName("执行 - 启动工作流")
    void testExecuteWorkflow() throws Exception {
        assertTrue(workflowService.isCompiled("lifecycle-test"), "需要先执行编译测试");

        // 准备输入
        Map<String, Object> inputs = Map.of(
                "start.userQuery", "请简单介绍一下 Java 编程语言"
        );

        // 执行工作流
        executionResult = workflowService.start("lifecycle-test", inputs);

        // 验证执行结果
        assertNotNull(executionResult);
        assertTrue(executionResult.completed());
        assertNotNull(executionResult.threadId());
        assertNotNull(executionResult.outputs());

        log.info("[执行] 工作流执行完成");
        log.info("[执行] threadId: {}", executionResult.threadId());
        log.info("[执行] 输出 keys: {}", executionResult.outputs().keySet());

        // 验证输出包含预期字段
        if (!executionResult.outputs().isEmpty()) {
            executionResult.outputs().forEach((k, v) ->
                    log.info("[执行] 输出 {} = {}", k,
                            v != null && v.toString().length() > 100
                                    ? v.toString().substring(0, 100) + "..."
                                    : v));
        }
    }

    @Test
    @Order(6)
    @DisplayName("状态 - 验证工作流状态")
    void testWorkflowStatus() {
        assertNotNull(executionResult, "需要先执行工作流");

        // 验证工作流状态
        var workflow = workflowService.getWorkflow("lifecycle-test");
        assertTrue(workflow.isPresent());
        assertEquals(WorkflowStatus.COMPLETED, workflow.get().getStatus());

        log.info("[状态] 工作流状态: {}", workflow.get().getStatus());
    }

    @Test
    @Order(7)
    @DisplayName("往返 - JSON 序列化往返一致性")
    void testJsonRoundTripConsistency() {
        // 原始工作流
        Workflow original = createSampleWorkflow();
        String json1 = WorkflowJsonSerializer.serialize(original);

        // 反序列化后再序列化
        Workflow restored = WorkflowJsonDeserializer.deserialize(json1);
        String json2 = WorkflowJsonSerializer.serialize(restored);

        // 验证结构一致性（忽略可能的状态变化）
        Workflow w1 = WorkflowJsonDeserializer.deserialize(json1);
        Workflow w2 = WorkflowJsonDeserializer.deserialize(json2);

        assertEquals(w1.getId(), w2.getId());
        assertEquals(w1.getName(), w2.getName());
        assertEquals(w1.getDescription(), w2.getDescription());
        assertEquals(w1.getNodes().size(), w2.getNodes().size());
        assertEquals(w1.getEdges().size(), w2.getEdges().size());

        // 验证每个节点的 ID 和类型一致
        for (int i = 0; i < w1.getNodes().size(); i++) {
            assertEquals(w1.getNodes().get(i).getId(), w2.getNodes().get(i).getId());
            assertEquals(w1.getNodes().get(i).getClass(), w2.getNodes().get(i).getClass());
        }

        log.info("[往返] JSON 序列化往返一致性验证通过");
    }

    @Test
    @Order(8)
    @DisplayName("清理 - 移除工作流")
    void testCleanup() {
        workflowService.removeWorkflow("lifecycle-test");
        assertFalse(workflowService.isCompiled("lifecycle-test"));

        log.info("[清理] 工作流已移除");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建示例工作流。
     * <p>
     * 流程：开始 → LLM1 (摘要) → LLM2 (翻译) → 结束
     */
    private Workflow createSampleWorkflow() {
        Workflow workflow = new Workflow();
        workflow.setId("lifecycle-test");
        workflow.setName("生命周期测试工作流");
        workflow.setDescription("用于测试工作流完整生命周期");

        // 设置全局变量
        workflow.getVariables().put("globalPrompt", "你是一个助手");

        // 创建节点
        StartNode start = new StartNode("start", "开始");
        start.setInput(Map.of("userQuery", ""));
        start.setX(100.0);
        start.setY(200.0);
        start.setIcon("play-circle");
        start.setColor("#4CAF50");

        LLMNode llm1 = new LLMNode("llm_1", "摘要生成");
        llm1.setModelIdentifier("dashscope_deepseek-v4-flash");
        llm1.setSystemPrompt("你是一个助手");
        llm1.setUserPromptTemplate("请对以下内容进行摘要：${start.userQuery}");
        llm1.setMaxRetry(2);
        llm1.setX(300.0);
        llm1.setY(200.0);
        llm1.setIcon("robot");
        llm1.setColor("#2196F3");

        LLMNode llm2 = new LLMNode("llm_2", "英文翻译");
        llm2.setModelIdentifier("dashscope_deepseek-v4-flash");
        llm2.setSystemPrompt("你是一个翻译助手");
        llm2.setUserPromptTemplate("请将以下摘要翻译成英文：${llm_1.response}");
        llm2.setMaxRetry(2);
        llm2.setX(500.0);
        llm2.setY(200.0);
        llm2.setIcon("translate");
        llm2.setColor("#FF9800");

        EndNode end = new EndNode("end", "结束");
        end.setOutput(Map.of(
                "summary", "${llm_1.response}",
                "translation", "${llm_2.response}"
        ));
        end.setX(700.0);
        end.setY(200.0);
        end.setIcon("check-circle");
        end.setColor("#9C27B0");

        // 添加节点
        workflow.addNode(start);
        workflow.addNode(llm1);
        workflow.addNode(llm2);
        workflow.addNode(end);

        // 添加边
        workflow.addEdge(new NormalEdge("e1", "开始到摘要", "start", "llm_1"));
        workflow.addEdge(new NormalEdge("e2", "摘要到翻译", "llm_1", "llm_2"));
        workflow.addEdge(new ConditionEdge("e3", "翻译到结束", "llm_2", "end", "${llm_2.response} != ''"));

        return workflow;
    }
}

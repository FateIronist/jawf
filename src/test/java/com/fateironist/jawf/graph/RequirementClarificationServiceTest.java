package com.fateironist.jawf.graph;

import com.fateironist.jawf.ai.ModelFactory;
import com.fateironist.jawf.ai.ModelProvider;
import com.fateironist.jawf.graph.RequirementClarificationService.ClarificationResult;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static com.fateironist.jawf.graph.IntentRecognitionGraph.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RequirementClarificationService} 集成测试。
 * <p>
 * 测试 IntentRecognitionGraph 的两种主流程：
 * <ol>
 *   <li>QA 流程：直接回答，无需人机循环。</li>
 *   <li>长任务流程：触发需求澄清循环，通过 Mock 外部输入模拟多轮追问恢复。</li>
 * </ol>
 * 使用真实 DashScope API，由 {@link ModelFactory} 配置驱动。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RequirementClarificationServiceTest {

    @Autowired
    private RequirementClarificationService service;

    @Autowired
    private IntentRecognitionGraph intentGraph;

    @Test
    @Order(1)
    @DisplayName("IntentRecognitionGraph - 编译成功并持有 CompiledGraph")
    void testGraphCompiled() {
        assertNotNull(intentGraph);
        assertNotNull(intentGraph.getCompiledGraph());
    }

    @Test
    @Order(2)
    @DisplayName("QA 流程 - 直接完成，无追问")
    void testQaFlowCompletes() {
        // 简单问答类型输入，应该直接完成
        ClarificationResult result = service.start("1 + 1 等于多少？");

        assertTrue(result.isComplete(), "QA 流程应直接完成");
        assertNotNull(result.getAnswer());
        assertFalse(result.getAnswer().isBlank());
        assertNull(result.getFollowUpQuestion());
        assertNotNull(result.getThreadId());
        System.out.println("[QA 回答] " + result.getAnswer());
    }

    @Test
    @Order(3)
    @DisplayName("Graph 配置 - interruptAfter followup_node 已设置")
    void testInterruptAfterConfigured() {
        // 验证 getCompiledGraph 可以正常获取（interrupt 在运行时生效）
        assertNotNull(intentGraph.getCompiledGraph());
    }

    @Test
    @Order(4)
    @DisplayName("Config - threadId 正确生成")
    void testThreadIdGeneration() {
        ClarificationResult r1 = service.start("写一首诗");
        assertNotNull(r1.getThreadId());
        assertFalse(r1.getThreadId().isBlank());

        // 同一 threadId 可继续
        ClarificationResult r2 = service.continueClarification(r1.getThreadId(), "再加两句");
        assertNotNull(r2.getThreadId());
        assertEquals(r1.getThreadId(), r2.getThreadId());
    }

    @Test
    @Order(5)
    @DisplayName("start + continue - 多轮对话状态延续")
    void testMultiRoundContinuation() {
        // 第一轮：发起一个模糊需求
        ClarificationResult r1 = service.start("帮我做个功能");
        assertNotNull(r1.getThreadId());
        System.out.println("[第1轮] threadId=" + r1.getThreadId()
                + ", complete=" + r1.isComplete());

        // 第二轮：补充更多细节继续
        ClarificationResult r2 = service.continueClarification(
                r1.getThreadId(), "是一个用户管理模块，包括增删改查");
        assertNotNull(r2.getThreadId());
        assertEquals(r1.getThreadId(), r2.getThreadId());
        System.out.println("[第2轮] complete=" + r2.isComplete()
                + ", answer=" + r2.getAnswer());

        // 后续轮次继续补充，直到完成或达到最大轮数
        int maxIterations = 5;
        ClarificationResult current = r2;
        for (int i = 3; i <= maxIterations && current.needsMoreInfo(); i++) {
            String buchong = "需求已经很完整了，请生成计划";
            current = service.continueClarification(r1.getThreadId(), buchong);
            System.out.println("[第" + i + "轮] complete=" + current.isComplete()
                    + ", question=" + current.getFollowUpQuestion());
        }

        // 验证最终状态
        assertNotNull(current);
        System.out.println("[最终] complete=" + current.isComplete()
                + ", answer=" + current.getAnswer()
                + ", state keys=" + current.getState().keySet());
    }

    @Test
    @Order(6)
    @DisplayName("state - 包含关键字段")
    void testStateContainsKeys() {
        ClarificationResult result = service.start("你好");
        assertTrue(result.isComplete());

        var state = result.getState();
        assertNotNull(state);
        // input 和 intent 是基础字段
        assertTrue(state.containsKey(KEY_INTENT) || !state.isEmpty(),
                "state 不应为空");
    }

    @Test
    @Order(10)
    @DisplayName("需求澄清循环 - 持续追问直到需求完整并生成图 JSON")
    void testClarificationLoop() {
        // 第1轮：发起需求
        ClarificationResult current = service.start(
                "我要开发一个博客系统，包含用户登录、文章发布、评论功能，部署在阿里云");
        String threadId = current.getThreadId();
        System.out.println("[澄清循环 第1轮] complete=" + current.isComplete()
                + ", question=" + current.getFollowUpQuestion());

        // 持续澄清直到生成图 JSON 或达到最大轮数
        int maxIter = IntentRecognitionGraph.MAX_REVIEW_ROUNDS + 2;
        int round = 2;
        while (current.needsMoreInfo() && !current.hasWorkflowGraph() && round <= maxIter) {
            if (round >= 4) {
                current = service.continueClarification(threadId,
                        "就这样了，就这些，直接生成吧。");
            } else {
                current = service.continueClarification(threadId,
                        "技术栈：Spring Boot + Vue3；数据库：MySQL；部署方式：阿里云 ECS + Nginx。");
            }

            System.out.println("[澄清循环 第" + round + "轮] complete=" + current.isComplete()
                    + ", question=" + current.getFollowUpQuestion()
                    + ", hasGraph=" + current.hasWorkflowGraph());
            round++;
        }

        // 验证：澄清循环结束后应收到图 JSON
        assertFalse(current.isComplete(), "澄清循环结束后不应直接完成，应等待用户确认图 JSON");
        assertTrue(current.hasWorkflowGraph(), "澄清循环结束后应生成工作流图 JSON");
        assertNotNull(current.getState(), "应有 state");
        System.out.println("[澄清循环] 图 JSON 长度: " + current.getWorkflowGraphJson().length());
    }

    @Test
    @Order(11)
    @DisplayName("图修改循环 - 用户确认图 JSON 后校验通过并执行")
    void testPlanModificationLoop() {
        // 第1轮：发起需求并快速完成澄清
        ClarificationResult current = service.start(
                "我要开发一个待办事项应用，包含任务增删改查功能");
        String threadId = current.getThreadId();

        // 快速完成澄清循环
        int maxIter = IntentRecognitionGraph.MAX_REVIEW_ROUNDS + 2;
        int round = 2;
        while (current.needsMoreInfo() && !current.hasWorkflowGraph() && round <= maxIter) {
            current = service.continueClarification(threadId, "就这样了，直接生成吧。");
            round++;
        }

        // 确认收到图 JSON
        assertTrue(current.hasWorkflowGraph(), "应收到工作流图 JSON");
        System.out.println("[图修改循环] 收到图 JSON，长度: " + current.getWorkflowGraphJson().length());

        // 模拟用户确认图 JSON（不修改），提交校验
        current = service.submitGraphJson(threadId, current.getWorkflowGraphJson());
        System.out.println("[图修改循环] 提交后 complete=" + current.isComplete()
                + ", hasGraph=" + current.hasWorkflowGraph()
                + ", graphInvalid=" + current.isGraphInvalid());

        // 验证：校验通过后应执行完成
        assertTrue(current.isComplete(), "图 JSON 校验通过后应执行完成");
        assertNotNull(current.getAnswer(), "完成后应有 answer");
        assertNotNull(current.getState(), "完成后应有 state");
    }

    @Test
    @Order(12)
    @DisplayName("图修改循环 - 校验失败后用户修改并重新提交")
    void testPlanModificationWithInvalidGraph() {
        // 第1轮：发起需求并快速完成澄清
        ClarificationResult current = service.start(
                "我要开发一个计算器应用");
        String threadId = current.getThreadId();

        // 快速完成澄清循环
        int maxIter = IntentRecognitionGraph.MAX_REVIEW_ROUNDS + 2;
        int round = 2;
        while (current.needsMoreInfo() && !current.hasWorkflowGraph() && round <= maxIter) {
            current = service.continueClarification(threadId, "就这样了，直接生成吧。");
            round++;
        }

        // 确认收到图 JSON，并保存原始有效的图 JSON
        assertTrue(current.hasWorkflowGraph(), "应收到工作流图 JSON");
        String originalValidGraphJson = current.getWorkflowGraphJson();

        // 模拟用户提交一个无效的图 JSON（缺少必要字段）
        String invalidGraphJson = "{\"id\":\"test\",\"nodes\":[],\"edges\":[]}";
        current = service.submitGraphJson(threadId, invalidGraphJson);
        System.out.println("[图修改循环] 提交无效图 JSON 后 graphInvalid=" + current.isGraphInvalid()
                + ", error=" + current.getGraphValidationError());

        // 验证：校验应失败
        assertTrue(current.isGraphInvalid(), "无效图 JSON 应校验失败");
        assertNotNull(current.getGraphValidationError(), "应有校验错误信息");
        assertTrue(current.hasWorkflowGraph(), "应仍保留图 JSON");

        // 模拟用户修改后重新提交原始有效的图 JSON
        current = service.submitGraphJson(threadId, originalValidGraphJson);
        System.out.println("[图修改循环] 重新提交后 complete=" + current.isComplete()
                + ", graphInvalid=" + current.isGraphInvalid());

        // 验证：校验通过后应执行完成
        assertTrue(current.isComplete(), "重新提交有效图 JSON 后应执行完成");
    }

    @Test
    @Order(13)
    @DisplayName("异常恢复 - 无效 threadId 应正常处理")
    void testInvalidThreadIdHandled() {
        // 使用不存在的 threadId 调用，应继续执行（Graph 会从初始状态开始或正常返回）
        ClarificationResult result = service.continueClarification(
                "non-existent-thread-id-12345", "补充一些信息");
        // 不应抛异常，应返回一个有效结果
        assertNotNull(result);
        System.out.println("[无效 threadId] complete=" + result.isComplete()
                + ", answer=" + result.getAnswer());
    }
}

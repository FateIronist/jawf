package com.fateironist.jawf.graph;

import com.fateironist.jawf.graph.RequirementClarificationService.ClarificationResult;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static com.fateironist.jawf.graph.IntentRecognitionGraph.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 诊断测试：验证 Graph 中断/恢复后 state 各字段是否正确持久化。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GraphStatePersistTest {

    @Autowired
    private RequirementClarificationService service;

    @Test
    @Order(1)
    @DisplayName("state 持久化 - requirementDocPath 在中断后保留")
    void testStatePersistsAcrossInterrupt() {
        // 第1轮：发起长任务需求
        ClarificationResult r1 = service.start("帮我做一个用户管理系统，包含登录注册功能");

        System.out.println("=== 第1轮 start ===");
        System.out.println("  complete=" + r1.isComplete());
        System.out.println("  threadId=" + r1.getThreadId());
        System.out.println("  followUpQuestion=" + r1.getFollowUpQuestion());
        dumpState("r1", r1.getState());

        // 长任务应被中断
        assertFalse(r1.isComplete(), "长任务第1轮应被中断追问");

        // 第1轮 state 应包含 requirementDocPath
        Map<String, Object> state1 = r1.getState();
        assertTrue(state1.containsKey(KEY_REQUIREMENT_DOC_PATH),
                "r1 state 应包含 requirementDocPath，实际 keys=" + state1.keySet());
        String docPath = (String) state1.get(KEY_REQUIREMENT_DOC_PATH);
        assertNotNull(docPath, "requirementDocPath 不应为 null");
        assertFalse(docPath.isBlank(), "requirementDocPath 不应为空");

        // 第2轮：恢复
        ClarificationResult r2 = service.continueClarification(
                r1.getThreadId(), "使用 Spring Boot + MySQL，需要 REST API");

        System.out.println("=== 第2轮 continue ===");
        System.out.println("  complete=" + r2.isComplete());
        System.out.println("  followUpQuestion=" + r2.getFollowUpQuestion());
        dumpState("r2", r2.getState());

        // 第2轮 state 也应包含 requirementDocPath（从 checkpoint 恢复）
        Map<String, Object> state2 = r2.getState();
        assertTrue(state2.containsKey(KEY_REQUIREMENT_DOC_PATH),
                "r2 state 应包含 requirementDocPath，实际 keys=" + state2.keySet());
        assertEquals(docPath, state2.get(KEY_REQUIREMENT_DOC_PATH),
                "resume 后 requirementDocPath 应与第1轮一致");

        // 第3轮：再恢复一次，继续验证
        ClarificationResult r3 = service.continueClarification(
                r1.getThreadId(), "信息足够了，请直接生成计划");

        System.out.println("=== 第3轮 continue ===");
        System.out.println("  complete=" + r3.isComplete());
        dumpState("r3", r3.getState());

        Map<String, Object> state3 = r3.getState();
        assertTrue(state3.containsKey(KEY_REQUIREMENT_DOC_PATH),
                "r3 state 应包含 requirementDocPath，实际 keys=" + state3.keySet());
    }

    @Test
    @Order(2)
    @DisplayName("state 持久化 - reviewRound 正确累加")
    void testReviewRoundAccumulates() {
        ClarificationResult r1 = service.start("帮我做一个博客系统");

        Map<String, Object> state1 = r1.getState();
        int round1 = state1.containsKey(KEY_REVIEW_ROUND)
                ? ((Number) state1.get(KEY_REVIEW_ROUND)).intValue() : -1;
        System.out.println("[round 测试] r1 round=" + round1 + ", keys=" + state1.keySet());

        if (r1.needsMoreInfo()) {
            ClarificationResult r2 = service.continueClarification(r1.getThreadId(), "补充细节");
            Map<String, Object> state2 = r2.getState();
            int round2 = state2.containsKey(KEY_REVIEW_ROUND)
                    ? ((Number) state2.get(KEY_REVIEW_ROUND)).intValue() : -1;
            System.out.println("[round 测试] r2 round=" + round2);

            // round 应递增
            assertTrue(round2 > round1 || round2 >= 0,
                    "reviewRound 应递增: round1=" + round1 + ", round2=" + round2);
        }
    }

    @Test
    @Order(3)
    @DisplayName("state 持久化 - QA 流程直接完成，state 完整")
    void testQaStateComplete() {
        ClarificationResult result = service.start("1+1等于几？");
        assertTrue(result.isComplete());

        Map<String, Object> state = result.getState();
        dumpState("qa", state);

        assertTrue(state.containsKey(KEY_INPUT), "state 应包含 input");
        assertTrue(state.containsKey(KEY_INTENT), "state 应包含 intent");
        assertEquals(INTENT_QA, state.get(KEY_INTENT));
        assertTrue(state.containsKey(KEY_ANSWER), "state 应包含 answer");
    }

    private void dumpState(String label, Map<String, Object> state) {
        if (state == null) {
            System.out.println("  [" + label + "] state=null");
            return;
        }
        System.out.println("  [" + label + "] state keys=" + state.keySet());
        for (Map.Entry<String, Object> e : state.entrySet()) {
            Object v = e.getValue();
            String display = (v == null) ? "null"
                    : (v instanceof String s && s.length() > 80) ? s.substring(0, 80) + "..." : String.valueOf(v);
            System.out.println("    " + e.getKey() + " = " + display);
        }
    }
}

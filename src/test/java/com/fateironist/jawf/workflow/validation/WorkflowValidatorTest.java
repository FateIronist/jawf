package com.fateironist.jawf.workflow.validation;

import com.fateironist.jawf.workflow.model.Workflow;
import com.fateironist.jawf.workflow.model.edge.NormalEdge;
import com.fateironist.jawf.workflow.model.node.EndNode;
import com.fateironist.jawf.workflow.model.node.LLMNode;
import com.fateironist.jawf.workflow.model.node.StartNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkflowValidator 单元测试。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowValidatorTest {

    // ==================== 正常工作流 ====================

    @Test
    @Order(1)
    @DisplayName("校验 - 正常线性工作流")
    void testValidateLinearWorkflow() {
        Workflow workflow = createLinearWorkflow();
        assertDoesNotThrow(() -> WorkflowValidator.validate(workflow));
    }

    @Test
    @Order(2)
    @DisplayName("校验 - 正常分支工作流")
    void testValidateBranchWorkflow() {
        Workflow workflow = new Workflow();
        workflow.setId("branch-workflow");

        StartNode start = new StartNode("start", "开始");
        LLMNode llm1 = new LLMNode("llm_1", "LLM1");
        LLMNode llm2 = new LLMNode("llm_2", "LLM2");
        EndNode end = new EndNode("end", "结束");

        workflow.addNode(start);
        workflow.addNode(llm1);
        workflow.addNode(llm2);
        workflow.addNode(end);

        workflow.addEdge(new NormalEdge("e1", "e1", "start", "llm_1"));
        workflow.addEdge(new NormalEdge("e2", "e2", "start", "llm_2"));
        workflow.addEdge(new NormalEdge("e3", "e3", "llm_1", "end"));
        workflow.addEdge(new NormalEdge("e4", "e4", "llm_2", "end"));

        assertDoesNotThrow(() -> WorkflowValidator.validate(workflow));
    }

    // ==================== 节点校验 ====================

    @Test
    @Order(10)
    @DisplayName("校验 - 空节点列表")
    void testValidateEmptyNodes() {
        Workflow workflow = new Workflow();
        workflow.setId("empty-workflow");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> WorkflowValidator.validate(workflow)
        );
        assertTrue(ex.getMessage().contains("至少需要一个节点"));
    }

    @Test
    @Order(11)
    @DisplayName("校验 - 缺少开始节点")
    void testValidateNoStartNode() {
        Workflow workflow = new Workflow();
        workflow.setId("no-start");

        LLMNode llm = new LLMNode("llm_1", "LLM");
        EndNode end = new EndNode("end", "结束");

        workflow.addNode(llm);
        workflow.addNode(end);
        workflow.addEdge(new NormalEdge("e1", "e1", "llm_1", "end"));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> WorkflowValidator.validate(workflow)
        );
        assertTrue(ex.getMessage().contains("开始节点"));
    }

    @Test
    @Order(12)
    @DisplayName("校验 - 缺少结束节点")
    void testValidateNoEndNode() {
        Workflow workflow = new Workflow();
        workflow.setId("no-end");

        StartNode start = new StartNode("start", "开始");
        LLMNode llm = new LLMNode("llm_1", "LLM");

        workflow.addNode(start);
        workflow.addNode(llm);
        workflow.addEdge(new NormalEdge("e1", "e1", "start", "llm_1"));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> WorkflowValidator.validate(workflow)
        );
        assertTrue(ex.getMessage().contains("结束节点"));
    }

    @Test
    @Order(13)
    @DisplayName("校验 - 重复节点 ID")
    void testValidateDuplicateNodeId() {
        Workflow workflow = new Workflow();
        workflow.setId("dup-id");

        StartNode start = new StartNode("start", "开始");
        LLMNode llm1 = new LLMNode("llm_1", "LLM1");
        LLMNode llm2 = new LLMNode("llm_1", "LLM2"); // 重复 ID
        EndNode end = new EndNode("end", "结束");

        workflow.addNode(start);
        workflow.addNode(llm1);
        workflow.addNode(llm2);
        workflow.addNode(end);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> WorkflowValidator.validate(workflow)
        );
        assertTrue(ex.getMessage().contains("节点 ID 重复"));
    }

    @Test
    @Order(14)
    @DisplayName("校验 - 边引用不存在的节点")
    void testValidateEdgeReferenceMissingNode() {
        Workflow workflow = new Workflow();
        workflow.setId("missing-ref");

        StartNode start = new StartNode("start", "开始");
        EndNode end = new EndNode("end", "结束");

        workflow.addNode(start);
        workflow.addNode(end);
        workflow.addEdge(new NormalEdge("e1", "e1", "start", "nonexistent"));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> WorkflowValidator.validate(workflow)
        );
        assertTrue(ex.getMessage().contains("目标节点不存在"));
    }

    // ==================== 环检测 ====================

    @Test
    @Order(20)
    @DisplayName("环检测 - 存在环")
    void testCycleDetection() {
        Workflow workflow = new Workflow();
        workflow.setId("cyclic");

        StartNode start = new StartNode("start", "开始");
        LLMNode llm1 = new LLMNode("llm_1", "LLM1");
        LLMNode llm2 = new LLMNode("llm_2", "LLM2");
        EndNode end = new EndNode("end", "结束");

        workflow.addNode(start);
        workflow.addNode(llm1);
        workflow.addNode(llm2);
        workflow.addNode(end);

        // 创建环：llm_1 -> llm_2 -> llm_1
        workflow.addEdge(new NormalEdge("e1", "e1", "start", "llm_1"));
        workflow.addEdge(new NormalEdge("e2", "e2", "llm_1", "llm_2"));
        workflow.addEdge(new NormalEdge("e3", "e3", "llm_2", "llm_1")); // 环
        workflow.addEdge(new NormalEdge("e4", "e4", "llm_2", "end"));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> WorkflowValidator.validate(workflow)
        );
        assertTrue(ex.getMessage().contains("环"));
    }

    @Test
    @Order(21)
    @DisplayName("环检测 - 自环")
    void testSelfLoop() {
        Workflow workflow = new Workflow();
        workflow.setId("self-loop");

        StartNode start = new StartNode("start", "开始");
        LLMNode llm = new LLMNode("llm_1", "LLM");
        EndNode end = new EndNode("end", "结束");

        workflow.addNode(start);
        workflow.addNode(llm);
        workflow.addNode(end);

        // 自环：llm_1 -> llm_1
        workflow.addEdge(new NormalEdge("e1", "e1", "start", "llm_1"));
        workflow.addEdge(new NormalEdge("e2", "e2", "llm_1", "llm_1")); // 自环
        workflow.addEdge(new NormalEdge("e3", "e3", "llm_1", "end"));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> WorkflowValidator.validate(workflow)
        );
        assertTrue(ex.getMessage().contains("环"));
    }

    // ==================== 拓扑排序 ====================

    @Test
    @Order(30)
    @DisplayName("拓扑排序 - 正常排序")
    void testTopologicalSort() {
        Workflow workflow = createLinearWorkflow();
        var sorted = WorkflowValidator.topologicalSort(
                workflow.getNodes(), workflow.getEdges());

        assertEquals(4, sorted.size());
        assertEquals("start", sorted.get(0));
        assertEquals("llm_1", sorted.get(1));
        assertEquals("llm_2", sorted.get(2));
        assertEquals("end", sorted.get(3));
    }

    // ==================== 可达性检测 ====================

    @Test
    @Order(40)
    @DisplayName("可达性检测 - 不可达节点")
    void testUnreachableNode() {
        Workflow workflow = new Workflow();
        workflow.setId("unreachable");

        StartNode start = new StartNode("start", "开始");
        LLMNode llm1 = new LLMNode("llm_1", "可达");
        LLMNode llm2 = new LLMNode("llm_2", "不可达");
        EndNode end = new EndNode("end", "结束");

        workflow.addNode(start);
        workflow.addNode(llm1);
        workflow.addNode(llm2);
        workflow.addNode(end);

        workflow.addEdge(new NormalEdge("e1", "e1", "start", "llm_1"));
        workflow.addEdge(new NormalEdge("e2", "e2", "llm_1", "end"));
        // llm_2 没有入边，不可达

        var reachable = WorkflowValidator.findReachableNodes(workflow);
        assertTrue(reachable.contains("start"));
        assertTrue(reachable.contains("llm_1"));
        assertTrue(reachable.contains("end"));
        // llm_2 不可达但不报错（仅警告）
    }

    // ==================== 辅助方法 ====================

    private Workflow createLinearWorkflow() {
        Workflow workflow = new Workflow();
        workflow.setId("linear-workflow");

        StartNode start = new StartNode("start", "开始");
        LLMNode llm1 = new LLMNode("llm_1", "第一步");
        LLMNode llm2 = new LLMNode("llm_2", "第二步");
        EndNode end = new EndNode("end", "结束");

        workflow.addNode(start);
        workflow.addNode(llm1);
        workflow.addNode(llm2);
        workflow.addNode(end);

        workflow.addEdge(new NormalEdge("e1", "e1", "start", "llm_1"));
        workflow.addEdge(new NormalEdge("e2", "e2", "llm_1", "llm_2"));
        workflow.addEdge(new NormalEdge("e3", "e3", "llm_2", "end"));

        return workflow;
    }
}

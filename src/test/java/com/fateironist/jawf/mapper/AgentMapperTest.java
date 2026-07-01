package com.fateironist.jawf.mapper;

import com.fateironist.jawf.model.Agent;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentMapper 集成测试。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentMapperTest {

    @Autowired
    private AgentMapper agentMapper;

    @BeforeAll
    void setUp() {
        agentMapper.dropTable();
        agentMapper.createTable();
    }

    @AfterAll
    void tearDown() {
        agentMapper.dropTable();
    }

    // ==================== 插入测试 ====================

    @Test
    @Order(1)
    @DisplayName("INSERT - 插入单个 Agent")
    void testInsert() {
        Agent agent = createSampleAgent("test-agent-1", "测试Agent1", "dashscope_deepseek-v4-flash");
        int rows = agentMapper.insert(agent);

        assertEquals(1, rows);
        assertNotNull(agent.getId(), "自增 ID 应已设置");

        Agent found = agentMapper.selectById(agent.getId());
        assertNotNull(found);
        assertEquals("测试Agent1", found.getName());
        assertEquals("dashscope_deepseek-v4-flash", found.getDefaultModel());
        assertFalse(found.getParallelEnabled());
    }

    @Test
    @Order(2)
    @DisplayName("INSERT - 插入多个 Agent")
    void testBatchInsert() {
        agentMapper.deleteAll();

        agentMapper.insert(createSampleAgent("agent-a", "Agent A", "dashscope_deepseek-v4-flash"));
        agentMapper.insert(createSampleAgent("agent-b", "Agent B", "dashscope_deepseek-v4-flash"));
        agentMapper.insert(createSampleAgent("agent-c", "Agent C", "dashscope_qwen-plus"));

        assertEquals(3, agentMapper.selectAll().size());
    }

    // ==================== 查询测试 ====================

    @Test
    @Order(3)
    @DisplayName("SELECT - 按 agentId 查询")
    void testSelectByAgentId() {
        agentMapper.deleteAll();

        Agent agent = createSampleAgent("query-test", "查询测试", "dashscope_deepseek-v4-flash");
        agentMapper.insert(agent);

        Agent found = agentMapper.selectByAgentId("query-test");
        assertNotNull(found);
        assertEquals("查询测试", found.getName());
    }

    @Test
    @Order(4)
    @DisplayName("SELECT - 按名称查询")
    void testSelectByName() {
        agentMapper.deleteAll();

        Agent agent = createSampleAgent("name-test", "名称测试", "dashscope_deepseek-v4-flash");
        agentMapper.insert(agent);

        Agent found = agentMapper.selectByName("名称测试");
        assertNotNull(found);
        assertEquals("name-test", found.getAgentId());
    }

    @Test
    @Order(5)
    @DisplayName("SELECT - 按类型查询")
    void testSelectByType() {
        agentMapper.deleteAll();

        Agent llm1 = createSampleAgent("llm-1", "LLM Agent 1", "dashscope_deepseek-v4-flash");
        llm1.setAgentType("llm");
        agentMapper.insert(llm1);

        Agent llm2 = createSampleAgent("llm-2", "LLM Agent 2", "dashscope_deepseek-v4-flash");
        llm2.setAgentType("llm");
        agentMapper.insert(llm2);

        Agent tool = createSampleAgent("tool-1", "Tool Agent", "dashscope_deepseek-v4-flash");
        tool.setAgentType("tool");
        agentMapper.insert(tool);

        List<Agent> llmAgents = agentMapper.selectByType("llm");
        assertEquals(2, llmAgents.size());

        List<Agent> toolAgents = agentMapper.selectByType("tool");
        assertEquals(1, toolAgents.size());
    }

    @Test
    @Order(6)
    @DisplayName("SELECT - 按模型查询")
    void testSelectByModel() {
        agentMapper.deleteAll();

        Agent a1 = createSampleAgent("model-1", "Model Agent 1", "dashscope_deepseek-v4-flash");
        agentMapper.insert(a1);

        Agent a2 = createSampleAgent("model-2", "Model Agent 2", "dashscope_qwen-plus");
        agentMapper.insert(a2);

        List<Agent> deepseekAgents = agentMapper.selectByModel("dashscope_deepseek-v4-flash");
        assertEquals(1, deepseekAgents.size());
        assertEquals("model-1", deepseekAgents.get(0).getAgentId());
    }

    // ==================== 更新测试 ====================

    @Test
    @Order(7)
    @DisplayName("UPDATE - 更新 Agent 信息")
    void testUpdate() {
        agentMapper.deleteAll();

        Agent agent = createSampleAgent("update-test", "更新前", "dashscope_deepseek-v4-flash");
        agentMapper.insert(agent);

        agent.setName("更新后");
        agent.setDefaultModel("dashscope_qwen-plus");
        agent.setParallelEnabled(true);
        agent.setMaxParallel(4);
        int rows = agentMapper.update(agent);

        assertEquals(1, rows);

        Agent found = agentMapper.selectByAgentId("update-test");
        assertEquals("更新后", found.getName());
        assertEquals("dashscope_qwen-plus", found.getDefaultModel());
        assertTrue(found.getParallelEnabled());
        assertEquals(4, found.getMaxParallel());
    }

    @Test
    @Order(8)
    @DisplayName("UPDATE - 启用/禁用 Agent")
    void testUpdateEnabled() {
        agentMapper.deleteAll();

        Agent agent = createSampleAgent("enable-test", "启用测试", "dashscope_deepseek-v4-flash");
        agentMapper.insert(agent);

        agentMapper.updateEnabled("enable-test", false);
        Agent found = agentMapper.selectByAgentId("enable-test");
        assertFalse(found.getEnabled());

        // 查询启用列表应不包含该 Agent
        List<Agent> enabled = agentMapper.selectAllEnabled();
        assertTrue(enabled.stream().noneMatch(a -> a.getAgentId().equals("enable-test")));
    }

    // ==================== 删除测试 ====================

    @Test
    @Order(9)
    @DisplayName("DELETE - 删除 Agent")
    void testDelete() {
        agentMapper.deleteAll();

        Agent a1 = createSampleAgent("delete-1", "删除测试1", "dashscope_deepseek-v4-flash");
        Agent a2 = createSampleAgent("delete-2", "删除测试2", "dashscope_deepseek-v4-flash");
        agentMapper.insert(a1);
        agentMapper.insert(a2);

        assertEquals(1, agentMapper.deleteByAgentId("delete-1"));
        assertEquals(1, agentMapper.selectAll().size());
        assertNull(agentMapper.selectByAgentId("delete-1"));
    }

    // ==================== 并行属性测试 ====================

    @Test
    @Order(10)
    @DisplayName("并行属性 - 支持并行的 Agent")
    void testParallelAgent() {
        agentMapper.deleteAll();

        Agent agent = createSampleAgent("parallel-test", "并行Agent", "dashscope_deepseek-v4-flash");
        agent.setParallelEnabled(true);
        agent.setMaxParallel(8);
        agentMapper.insert(agent);

        Agent found = agentMapper.selectByAgentId("parallel-test");
        assertTrue(found.getParallelEnabled());
        assertEquals(8, found.getMaxParallel());
    }

    // ==================== 辅助方法 ====================

    private Agent createSampleAgent(String agentId, String name, String model) {
        Agent agent = new Agent();
        agent.setAgentId(agentId);
        agent.setName(name);
        agent.setDescription("测试用 Agent");
        agent.setDefaultModel(model);
        agent.setSystemPrompt("你是一个助手");
        agent.setParallelEnabled(false);
        agent.setMaxParallel(1);
        agent.setMaxRetry(3);
        agent.setTimeoutSeconds(60);
        agent.setAgentType("llm");
        agent.setEnabled(true);
        return agent;
    }
}

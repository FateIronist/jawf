package com.fateironist.jawf.service;

import com.fateironist.jawf.mapper.AgentMapper;
import com.fateironist.jawf.model.Agent;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentService 集成测试。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentServiceTest {

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentMapper agentMapper;

    @BeforeAll
    void setUp() {
        agentService.initTable();
    }

    @AfterAll
    void tearDown() {
        agentMapper.dropTable();
    }

    // ==================== 创建测试 ====================

    @Test
    @Order(1)
    @DisplayName("创建 - 自动生成 agentId")
    void testCreateWithAutoId() {
        Agent agent = new Agent();
        agent.setName("自动ID测试");
        agent.setDefaultModel("dashscope_deepseek-v4-flash");

        Agent created = agentService.create(agent);

        assertNotNull(created.getAgentId(), "应自动生成 agentId");
        assertNotNull(created.getId(), "应设置自增 ID");
        assertTrue(created.getEnabled());
        assertEquals("llm", created.getAgentType());
    }

    @Test
    @Order(2)
    @DisplayName("创建 - 指定 agentId")
    void testCreateWithSpecifiedId() {
        Agent agent = new Agent();
        agent.setAgentId("custom-id-123");
        agent.setName("指定ID测试");
        agent.setDefaultModel("dashscope_deepseek-v4-flash");

        Agent created = agentService.create(agent);
        assertEquals("custom-id-123", created.getAgentId());
    }

    // ==================== 查询测试 ====================

    @Test
    @Order(3)
    @DisplayName("查询 - 按 agentId 查询")
    void testGetByAgentId() {
        Agent agent = new Agent();
        agent.setAgentId("query-id");
        agent.setName("查询测试");
        agent.setDefaultModel("dashscope_deepseek-v4-flash");
        agentService.create(agent);

        Agent found = agentService.getByAgentId("query-id");
        assertNotNull(found);
        assertEquals("查询测试", found.getName());
    }

    @Test
    @Order(4)
    @DisplayName("查询 - 获取所有启用的 Agent")
    void testListAllEnabled() {
        // 创建一个禁用的 Agent
        Agent disabled = new Agent();
        disabled.setAgentId("disabled-agent");
        disabled.setName("禁用Agent");
        disabled.setDefaultModel("dashscope_deepseek-v4-flash");
        disabled.setEnabled(false);
        agentService.create(disabled);

        List<Agent> enabled = agentService.listAllEnabled();
        assertTrue(enabled.stream().noneMatch(a -> a.getAgentId().equals("disabled-agent")));
    }

    @Test
    @Order(5)
    @DisplayName("查询 - 按类型查询")
    void testListByType() {
        Agent toolAgent = new Agent();
        toolAgent.setAgentId("tool-agent");
        toolAgent.setName("工具Agent");
        toolAgent.setDefaultModel("dashscope_deepseek-v4-flash");
        toolAgent.setAgentType("tool");
        agentService.create(toolAgent);

        List<Agent> tools = agentService.listByType("tool");
        assertTrue(tools.stream().anyMatch(a -> a.getAgentId().equals("tool-agent")));
    }

    // ==================== 更新测试 ====================

    @Test
    @Order(6)
    @DisplayName("更新 - 修改 Agent 信息")
    void testUpdate() {
        Agent agent = new Agent();
        agent.setAgentId("update-id");
        agent.setName("更新前");
        agent.setDefaultModel("dashscope_deepseek-v4-flash");
        agentService.create(agent);

        agent.setName("更新后");
        agent.setParallelEnabled(true);
        agent.setMaxParallel(4);
        agentService.update(agent);

        Agent found = agentService.getByAgentId("update-id");
        assertEquals("更新后", found.getName());
        assertTrue(found.getParallelEnabled());
        assertEquals(4, found.getMaxParallel());
    }

    @Test
    @Order(7)
    @DisplayName("更新 - 启用/禁用")
    void testSetEnabled() {
        Agent agent = new Agent();
        agent.setAgentId("enable-id");
        agent.setName("启用测试");
        agent.setDefaultModel("dashscope_deepseek-v4-flash");
        agentService.create(agent);

        agentService.setEnabled("enable-id", false);
        Agent found = agentService.getByAgentId("enable-id");
        assertFalse(found.getEnabled());

        agentService.setEnabled("enable-id", true);
        found = agentService.getByAgentId("enable-id");
        assertTrue(found.getEnabled());
    }

    @Test
    @Order(8)
    @DisplayName("更新 - 修改模型")
    void testUpdateModel() {
        Agent agent = new Agent();
        agent.setAgentId("model-id");
        agent.setName("模型测试");
        agent.setDefaultModel("dashscope_deepseek-v4-flash");
        agentService.create(agent);

        agentService.updateModel("model-id", "dashscope_qwen-plus");
        Agent found = agentService.getByAgentId("model-id");
        assertEquals("dashscope_qwen-plus", found.getDefaultModel());
    }

    // ==================== 删除测试 ====================

    @Test
    @Order(9)
    @DisplayName("删除 - 删除 Agent")
    void testDelete() {
        Agent agent = new Agent();
        agent.setAgentId("delete-id");
        agent.setName("删除测试");
        agent.setDefaultModel("dashscope_deepseek-v4-flash");
        agentService.create(agent);

        assertTrue(agentService.delete("delete-id"));
        assertNull(agentService.getByAgentId("delete-id"));
    }

    @Test
    @Order(10)
    @DisplayName("删除 - 删除不存在的 Agent")
    void testDeleteNonExistent() {
        assertFalse(agentService.delete("non-existent-id"));
    }

    // ==================== 统计测试 ====================

    @Test
    @Order(11)
    @DisplayName("统计 - 启用的 Agent 数量")
    void testCountEnabled() {
        agentMapper.deleteAll();

        Agent a1 = new Agent();
        a1.setAgentId("count-1");
        a1.setName("统计1");
        a1.setDefaultModel("dashscope_deepseek-v4-flash");
        agentService.create(a1);

        Agent a2 = new Agent();
        a2.setAgentId("count-2");
        a2.setName("统计2");
        a2.setDefaultModel("dashscope_deepseek-v4-flash");
        agentService.create(a2);

        assertEquals(2, agentService.countEnabled());

        agentService.setEnabled("count-1", false);
        assertEquals(1, agentService.countEnabled());
    }

    // ==================== 并行属性测试 ====================

    @Test
    @Order(12)
    @DisplayName("并行属性 - 创建支持并行的 Agent")
    void testParallelAgent() {
        Agent agent = new Agent();
        agent.setAgentId("parallel-id");
        agent.setName("并行Agent");
        agent.setDefaultModel("dashscope_deepseek-v4-flash");
        agent.setParallelEnabled(true);
        agent.setMaxParallel(8);

        Agent created = agentService.create(agent);
        assertTrue(created.getParallelEnabled());
        assertEquals(8, created.getMaxParallel());

        Agent found = agentService.getByAgentId("parallel-id");
        assertTrue(found.getParallelEnabled());
        assertEquals(8, found.getMaxParallel());
    }
}

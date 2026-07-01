package com.fateironist.jawf.service;

import com.fateironist.jawf.mapper.MessageMapper;
import com.fateironist.jawf.model.Message;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MessageService 集成测试。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageServiceTest {

    @Autowired
    private MessageService messageService;

    @Autowired
    private MessageMapper messageMapper;

    @BeforeAll
    void setUp() {
        messageService.initTable();
    }

    @AfterAll
    void tearDown() {
        messageMapper.dropTable();
    }

    // ==================== 保存测试 ====================

    @Test
    @Order(1)
    @DisplayName("保存 - 用户消息")
    void testSaveUserMessage() {
        Message message = messageService.saveUserMessage("test-conv-1", "agent-1", "你好");
        assertNotNull(message.getId());
        assertEquals("user", message.getType());
        assertEquals("你好", message.getContent());
    }

    @Test
    @Order(2)
    @DisplayName("保存 - 助手消息")
    void testSaveAssistantMessage() {
        messageService.saveUserMessage("test-conv-2", "agent-1", "你好");
        Message message = messageService.saveAssistantMessage("test-conv-2", "agent-1", "你好！有什么可以帮助你的？");
        assertNotNull(message.getId());
        assertEquals("assistant", message.getType());
    }

    @Test
    @Order(3)
    @DisplayName("保存 - 系统消息")
    void testSaveSystemMessage() {
        Message message = messageService.saveSystemMessage("test-conv-3", "agent-1", "你是一个助手");
        assertNotNull(message.getId());
        assertEquals("system", message.getType());
    }

    @Test
    @Order(4)
    @DisplayName("保存 - 工具消息")
    void testSaveToolMessage() {
        Message message = messageService.saveToolMessage("test-conv-4", "agent-1", "call-123", "getWeather", "晴天");
        assertNotNull(message.getId());
        assertEquals("tool", message.getType());
        assertEquals("call-123", message.getToolCallId());
        assertEquals("getWeather", message.getToolName());
    }

    // ==================== 序号自动生成测试 ====================

    @Test
    @Order(5)
    @DisplayName("序号 - 自动生成递增序号")
    void testAutoSequence() {
        String convId = "seq-test-conv";
        Message m1 = messageService.saveUserMessage(convId, "agent-1", "消息1");
        Message m2 = messageService.saveAssistantMessage(convId, "agent-1", "回复1");
        Message m3 = messageService.saveUserMessage(convId, "agent-1", "消息2");

        assertEquals(1, m1.getSequence());
        assertEquals(2, m2.getSequence());
        assertEquals(3, m3.getSequence());
    }

    // ==================== 查询测试 ====================

    @Test
    @Order(6)
    @DisplayName("查询 - 获取会话所有消息")
    void testGetByConversationId() {
        String convId = "query-test-conv";
        messageService.saveSystemMessage(convId, "agent-1", "系统提示");
        messageService.saveUserMessage(convId, "agent-1", "用户消息");
        messageService.saveAssistantMessage(convId, "agent-1", "助手回复");

        List<Message> messages = messageService.getByConversationId(convId);
        assertEquals(3, messages.size());
        assertEquals("system", messages.get(0).getType());
        assertEquals("user", messages.get(1).getType());
        assertEquals("assistant", messages.get(2).getType());
    }

    @Test
    @Order(7)
    @DisplayName("查询 - 获取最近 N 条消息")
    void testGetRecent() {
        String convId = "recent-test-conv";
        for (int i = 1; i <= 5; i++) {
            messageService.saveUserMessage(convId, "agent-1", "消息" + i);
        }

        List<Message> recent = messageService.getRecentByConversationId(convId, 2);
        assertEquals(2, recent.size());
        assertEquals("消息5", recent.get(0).getContent());
        assertEquals("消息4", recent.get(1).getContent());
    }

    // ==================== 会话列表测试 ====================

    @Test
    @Order(8)
    @DisplayName("查询 - 获取 Agent 的会话列表")
    void testGetConversationIds() {
        messageService.saveUserMessage("conv-x", "agent-test", "消息");
        messageService.saveUserMessage("conv-y", "agent-test", "消息");
        messageService.saveUserMessage("conv-z", "agent-other", "消息");

        List<String> convIds = messageService.getConversationIdsByAgentId("agent-test");
        assertTrue(convIds.contains("conv-x"));
        assertTrue(convIds.contains("conv-y"));
        assertFalse(convIds.contains("conv-z"));
    }

    // ==================== 删除测试 ====================

    @Test
    @Order(9)
    @DisplayName("删除 - 删除会话消息")
    void testDeleteByConversationId() {
        String convId = "delete-test-conv";
        messageService.saveUserMessage(convId, "agent-1", "消息1");
        messageService.saveAssistantMessage(convId, "agent-1", "回复1");

        int deleted = messageService.deleteByConversationId(convId);
        assertEquals(2, deleted);
        assertEquals(0, messageService.countByConversationId(convId));
    }
}

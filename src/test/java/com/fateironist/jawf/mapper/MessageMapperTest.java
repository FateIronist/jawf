package com.fateironist.jawf.mapper;

import com.fateironist.jawf.model.Message;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MessageMapper 集成测试。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageMapperTest {

    @Autowired
    private MessageMapper messageMapper;

    @BeforeAll
    void setUp() {
        messageMapper.dropTable();
        messageMapper.createTable();
        messageMapper.createIndex();
    }

    @AfterAll
    void tearDown() {
        messageMapper.dropTable();
    }

    // ==================== 插入测试 ====================

    @Test
    @Order(1)
    @DisplayName("INSERT - 插入用户消息")
    void testInsertUserMessage() {
        messageMapper.deleteAll();

        Message message = createMessage("conv-1", "agent-1", "user", "你好", 0);
        int rows = messageMapper.insert(message);

        assertEquals(1, rows);
        assertNotNull(message.getId());

        Message found = messageMapper.selectById(message.getId());
        assertEquals("你好", found.getContent());
        assertEquals("user", found.getType());
    }

    @Test
    @Order(2)
    @DisplayName("INSERT - 插入助手消息")
    void testInsertAssistantMessage() {
        messageMapper.deleteAll();

        Message message = createMessage("conv-1", "agent-1", "assistant", "你好！有什么可以帮助你的吗？", 1);
        messageMapper.insert(message);

        Message found = messageMapper.selectById(message.getId());
        assertEquals("assistant", found.getType());
    }

    @Test
    @Order(3)
    @DisplayName("INSERT - 插入工具消息")
    void testInsertToolMessage() {
        messageMapper.deleteAll();

        Message message = createMessage("conv-1", "agent-1", "tool", "天气：晴天", 2);
        message.setToolCallId("call-123");
        message.setToolName("getWeather");
        messageMapper.insert(message);

        Message found = messageMapper.selectById(message.getId());
        assertEquals("tool", found.getType());
        assertEquals("call-123", found.getToolCallId());
        assertEquals("getWeather", found.getToolName());
    }

    // ==================== 查询测试 ====================

    @Test
    @Order(4)
    @DisplayName("SELECT - 按会话 ID 查询所有消息")
    void testSelectByConversationId() {
        messageMapper.deleteAll();

        messageMapper.insert(createMessage("conv-2", "agent-1", "system", "你是一个助手", 0));
        messageMapper.insert(createMessage("conv-2", "agent-1", "user", "你好", 1));
        messageMapper.insert(createMessage("conv-2", "agent-1", "assistant", "你好！", 2));

        List<Message> messages = messageMapper.selectByConversationId("conv-2");
        assertEquals(3, messages.size());
        assertEquals("system", messages.get(0).getType());
        assertEquals("user", messages.get(1).getType());
        assertEquals("assistant", messages.get(2).getType());
    }

    @Test
    @Order(5)
    @DisplayName("SELECT - 查询最近 N 条消息")
    void testSelectRecent() {
        messageMapper.deleteAll();

        for (int i = 0; i < 10; i++) {
            String type = (i % 2 == 0) ? "user" : "assistant";
            messageMapper.insert(createMessage("conv-3", "agent-1", type, "消息" + i, i));
        }

        List<Message> recent = messageMapper.selectRecentByConversationId("conv-3", 3);
        assertEquals(3, recent.size());
        // 最近的应该是序号 9, 8, 7（倒序）
        assertEquals("消息9", recent.get(0).getContent());
        assertEquals("消息8", recent.get(1).getContent());
        assertEquals("消息7", recent.get(2).getContent());
    }

    @Test
    @Order(6)
    @DisplayName("SELECT - 按类型查询消息")
    void testSelectByType() {
        messageMapper.deleteAll();

        messageMapper.insert(createMessage("conv-4", "agent-1", "user", "用户消息1", 0));
        messageMapper.insert(createMessage("conv-4", "agent-1", "assistant", "助手消息1", 1));
        messageMapper.insert(createMessage("conv-4", "agent-1", "user", "用户消息2", 2));
        messageMapper.insert(createMessage("conv-4", "agent-1", "assistant", "助手消息2", 3));

        List<Message> userMessages = messageMapper.selectByConversationIdAndType("conv-4", "user");
        assertEquals(2, userMessages.size());

        List<Message> assistantMessages = messageMapper.selectByConversationIdAndType("conv-4", "assistant");
        assertEquals(2, assistantMessages.size());
    }

    @Test
    @Order(7)
    @DisplayName("SELECT - 查询 Agent 的会话列表")
    void testSelectConversationIds() {
        messageMapper.deleteAll();

        messageMapper.insert(createMessage("conv-a", "agent-2", "user", "消息", 0));
        messageMapper.insert(createMessage("conv-b", "agent-2", "user", "消息", 0));
        messageMapper.insert(createMessage("conv-c", "agent-3", "user", "消息", 0));

        List<String> agent2Convs = messageMapper.selectConversationIdsByAgentId("agent-2");
        assertEquals(2, agent2Convs.size());
    }

    // ==================== 统计测试 ====================

    @Test
    @Order(8)
    @DisplayName("COUNT - 统计会话消息数")
    void testCount() {
        messageMapper.deleteAll();

        messageMapper.insert(createMessage("conv-5", "agent-1", "user", "消息1", 0));
        messageMapper.insert(createMessage("conv-5", "agent-1", "assistant", "消息2", 1));
        messageMapper.insert(createMessage("conv-5", "agent-1", "user", "消息3", 2));

        assertEquals(3, messageMapper.countByConversationId("conv-5"));
    }

    @Test
    @Order(9)
    @DisplayName("MAX - 获取最大序号")
    void testMaxSequence() {
        messageMapper.deleteAll();

        messageMapper.insert(createMessage("conv-6", "agent-1", "user", "消息1", 0));
        messageMapper.insert(createMessage("conv-6", "agent-1", "assistant", "消息2", 1));
        messageMapper.insert(createMessage("conv-6", "agent-1", "user", "消息3", 2));

        assertEquals(2, messageMapper.maxSequenceByConversationId("conv-6"));
        assertEquals(0, messageMapper.maxSequenceByConversationId("non-existent"));
    }

    // ==================== 删除测试 ====================

    @Test
    @Order(10)
    @DisplayName("DELETE - 删除会话消息")
    void testDeleteByConversationId() {
        messageMapper.deleteAll();

        messageMapper.insert(createMessage("conv-7", "agent-1", "user", "消息1", 0));
        messageMapper.insert(createMessage("conv-7", "agent-1", "assistant", "消息2", 1));
        messageMapper.insert(createMessage("conv-8", "agent-1", "user", "消息3", 0));

        int deleted = messageMapper.deleteByConversationId("conv-7");
        assertEquals(2, deleted);
        assertEquals(1, messageMapper.selectByConversationId("conv-8").size());
    }

    // ==================== 辅助方法 ====================

    private Message createMessage(String conversationId, String agentId, String type, String content, int sequence) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setAgentId(agentId);
        message.setType(type);
        message.setContent(content);
        message.setSequence(sequence);
        return message;
    }
}

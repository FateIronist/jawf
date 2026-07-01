package com.fateironist.jawf.service;

import com.fateironist.jawf.ai.MessageConverter;
import com.fateironist.jawf.mapper.MessageMapper;
import com.fateironist.jawf.model.Conversation;
import com.fateironist.jawf.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 消息服务层。
 * <p>
 * 提供聊天消息的业务操作，包括保存、查询、转换等。
 * 支持将 Spring AI 消息类型持久化到数据库，并从数据库加载转换为 Spring AI 消息。
 * <p>
 * <b>会话绑定</b>：所有消息必须关联到一个有效的会话（conversationId）。
 */
@Slf4j
@Service
public class MessageService {

    private final MessageMapper messageMapper;
    private final ConversationService conversationService;

    public MessageService(MessageMapper messageMapper, ConversationService conversationService) {
        this.messageMapper = messageMapper;
        this.conversationService = conversationService;
    }

    /**
     * 初始化表结构。
     */
    public void initTable() {
        messageMapper.createTable();
        messageMapper.createIndex();
        log.info("[MessageService] messages 表初始化完成");
    }

    // ==================== 保存消息 ====================

    /**
     * 保存单条消息。
     * <p>
     * 如果会话不存在，会自动创建一个新会话。
     *
     * @param message 消息对象（必须包含 conversationId 和 agentId）
     * @return 保存后的消息
     */
    @Transactional
    public Message save(Message message) {
        // 确保 conversationId 存在
        String conversationId = message.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("消息必须关联到一个会话（conversationId 不能为空）");
        }

        // 检查会话是否存在，不存在则自动创建
        Conversation conversation = conversationService.getByConversationId(conversationId);
        if (conversation == null) {
            String agentId = message.getAgentId();
            if (agentId == null || agentId.isBlank()) {
                agentId = "unknown";
            }
            conversation = conversationService.create(agentId, "自动创建的会话");
            // 使用传入的 conversationId 更新会话
            conversationService.bindGraphThread(conversation.getConversationId(), null);
            log.info("[MessageService] 自动创建会话: {}", conversationId);
        }

        // 设置序号
        if (message.getSequence() == null) {
            message.setSequence(messageMapper.maxSequenceByConversationId(conversationId) + 1);
        }

        messageMapper.insert(message);
        log.debug("[MessageService] 保存消息: conversation={}, type={}, seq={}",
                conversationId, message.getType(), message.getSequence());
        return message;
    }

    /**
     * 保存用户消息。
     */
    @Transactional
    public Message saveUserMessage(String conversationId, String agentId, String content) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setAgentId(agentId);
        message.setType(Message.Type.USER.getValue());
        message.setContent(content);
        return save(message);
    }

    /**
     * 保存助手消息。
     */
    @Transactional
    public Message saveAssistantMessage(String conversationId, String agentId, String content) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setAgentId(agentId);
        message.setType(Message.Type.ASSISTANT.getValue());
        message.setContent(content);
        return save(message);
    }

    /**
     * 保存系统消息。
     */
    @Transactional
    public Message saveSystemMessage(String conversationId, String agentId, String content) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setAgentId(agentId);
        message.setType(Message.Type.SYSTEM.getValue());
        message.setContent(content);
        return save(message);
    }

    /**
     * 保存工具调用结果消息。
     */
    @Transactional
    public Message saveToolMessage(String conversationId, String agentId,
                                   String toolCallId, String toolName, String content) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setAgentId(agentId);
        message.setType(Message.Type.TOOL.getValue());
        message.setContent(content);
        message.setToolCallId(toolCallId);
        message.setToolName(toolName);
        return save(message);
    }

    /**
     * 保存 Spring AI 消息。
     */
    @Transactional
    public Message saveSpringAiMessage(org.springframework.ai.chat.messages.Message springAiMessage,
                                       String conversationId, String agentId) {
        Message message = MessageConverter.fromSpringAi(springAiMessage, conversationId, agentId, 0);
        return save(message);
    }

    /**
     * 批量保存 Spring AI 消息列表。
     */
    @Transactional
    public List<Message> saveSpringAiMessages(List<org.springframework.ai.chat.messages.Message> springAiMessages,
                                               String conversationId, String agentId) {
        List<Message> messages = MessageConverter.fromSpringAiList(springAiMessages, conversationId, agentId);
        for (Message message : messages) {
            save(message);
        }
        return messages;
    }

    // ==================== 查询消息 ====================

    /**
     * 根据 ID 查询消息。
     */
    public Message getById(Long id) {
        return messageMapper.selectById(id);
    }

    /**
     * 获取指定会话的所有消息（按序号排序）。
     */
    public List<Message> getByConversationId(String conversationId) {
        return messageMapper.selectByConversationId(conversationId);
    }

    /**
     * 获取指定会话的最近 N 条消息。
     */
    public List<Message> getRecentByConversationId(String conversationId, int limit) {
        return messageMapper.selectRecentByConversationId(conversationId, limit);
    }

    /**
     * 获取指定会话的指定类型消息。
     */
    public List<Message> getByConversationIdAndType(String conversationId, String type) {
        return messageMapper.selectByConversationIdAndType(conversationId, type);
    }

    /**
     * 获取指定 Agent 的所有会话 ID。
     */
    public List<String> getConversationIdsByAgentId(String agentId) {
        return messageMapper.selectConversationIdsByAgentId(agentId);
    }

    /**
     * 获取指定会话的消息数量。
     */
    public int countByConversationId(String conversationId) {
        return messageMapper.countByConversationId(conversationId);
    }

    // ==================== 转换查询 ====================

    /**
     * 获取指定会话的消息并转换为 Spring AI 消息列表。
     */
    public List<org.springframework.ai.chat.messages.Message> getSpringAiMessages(String conversationId) {
        List<Message> messages = getByConversationId(conversationId);
        return MessageConverter.toSpringAiList(messages);
    }

    /**
     * 获取指定会话的最近 N 条消息并转换为 Spring AI 消息列表。
     */
    public List<org.springframework.ai.chat.messages.Message> getRecentSpringAiMessages(String conversationId, int limit) {
        List<Message> messages = getRecentByConversationId(conversationId, limit);
        return MessageConverter.toSpringAiList(messages);
    }

    // ==================== 删除消息 ====================

    /**
     * 删除指定消息。
     */
    @Transactional
    public boolean delete(Long id) {
        return messageMapper.deleteById(id) > 0;
    }

    /**
     * 删除指定会话的所有消息。
     */
    @Transactional
    public int deleteByConversationId(String conversationId) {
        int count = messageMapper.deleteByConversationId(conversationId);
        log.info("[MessageService] 删除会话 {} 的 {} 条消息", conversationId, count);
        return count;
    }

    /**
     * 删除指定 Agent 的所有消息。
     */
    @Transactional
    public int deleteByAgentId(String agentId) {
        int count = messageMapper.deleteByAgentId(agentId);
        log.info("[MessageService] 删除 Agent {} 的 {} 条消息", agentId, count);
        return count;
    }

    /**
     * 删除会话及其所有消息。
     *
     * @param conversationId 会话 ID
     * @return 是否删除成功
     */
    @Transactional
    public boolean deleteConversationWithMessages(String conversationId) {
        // 先删除消息
        deleteByConversationId(conversationId);
        // 再删除会话
        return conversationService.delete(conversationId);
    }

    /**
     * 删除 Agent 的所有会话及其消息。
     *
     * @param agentId Agent ID
     * @return 删除的会话数量
     */
    @Transactional
    public int deleteAgentConversationsWithMessages(String agentId) {
        // 先删除消息
        deleteByAgentId(agentId);
        // 再删除会话
        return conversationService.deleteByAgentId(agentId);
    }
}

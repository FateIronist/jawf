package com.fateironist.jawf.service;

import com.fateironist.jawf.mapper.ConversationMapper;
import com.fateironist.jawf.model.Conversation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 会话服务层。
 * <p>
 * 管理会话与 Graph 的绑定关系，支持断点续执行。
 */
@Slf4j
@Service
public class ConversationService {

    private final ConversationMapper conversationMapper;

    public ConversationService(ConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
    }

    /**
     * 初始化表结构。
     */
    public void initTable() {
        conversationMapper.createTable();
        log.info("[ConversationService] conversations 表初始化完成");
    }

    /**
     * 创建新会话。
     *
     * @param agentId Agent ID
     * @param title   会话标题（可选）
     * @return 创建的会话
     */
    @Transactional
    public Conversation create(String agentId, String title) {
        Conversation conversation = new Conversation();
        conversation.setConversationId(UUID.randomUUID().toString());
        conversation.setAgentId(agentId);
        conversation.setTitle(title != null ? title : "新会话");
        conversation.setStatus(Conversation.Status.ACTIVE.getValue());

        conversationMapper.insert(conversation);
        log.info("[ConversationService] 创建会话: {} (agent={})", conversation.getConversationId(), agentId);
        return conversation;
    }

    /**
     * 根据会话 ID 获取会话。
     */
    public Conversation getByConversationId(String conversationId) {
        return conversationMapper.selectByConversationId(conversationId);
    }

    /**
     * 获取 Agent 的活跃会话列表。
     */
    public List<Conversation> getActiveByAgentId(String agentId) {
        return conversationMapper.selectActiveByAgentId(agentId);
    }

    /**
     * 获取 Agent 的所有会话列表。
     */
    public List<Conversation> getAllByAgentId(String agentId) {
        return conversationMapper.selectAllByAgentId(agentId);
    }

    /**
     * 绑定会话到 Graph 线程。
     * <p>
     * 当会话开始执行 Graph 时调用。
     *
     * @param conversationId 会话 ID
     * @param graphThreadId  Graph 线程 ID
     */
    @Transactional
    public void bindGraphThread(String conversationId, String graphThreadId) {
        conversationMapper.updateGraphThread(conversationId, graphThreadId, Conversation.Status.ACTIVE.getValue());
        log.info("[ConversationService] 绑定会话 {} 到 Graph 线程 {}", conversationId, graphThreadId);
    }

    /**
     * 解绑会话与 Graph 线程。
     * <p>
     * 当 Graph 执行完成（无论成功与否）时调用。
     *
     * @param conversationId 会话 ID
     */
    @Transactional
    public void unbindGraphThread(String conversationId) {
        conversationMapper.updateGraphThread(conversationId, null, Conversation.Status.COMPLETED.getValue());
        log.info("[ConversationService] 解绑会话 {} 的 Graph 线程", conversationId);
    }

    /**
     * 获取会话绑定的 Graph 线程 ID。
     *
     * @param conversationId 会话 ID
     * @return Graph 线程 ID，如果未绑定返回 null
     */
    public String getGraphThreadId(String conversationId) {
        Conversation conversation = conversationMapper.selectByConversationId(conversationId);
        return conversation != null ? conversation.getGraphThreadId() : null;
    }

    /**
     * 检查会话是否有绑定的 Graph。
     *
     * @param conversationId 会话 ID
     * @return 是否有绑定的 Graph
     */
    public boolean hasActiveGraph(String conversationId) {
        Conversation conversation = conversationMapper.selectByConversationId(conversationId);
        return conversation != null
                && conversation.getGraphThreadId() != null
                && !conversation.getGraphThreadId().isBlank()
                && Conversation.Status.ACTIVE.getValue().equals(conversation.getStatus());
    }

    /**
     * 更新会话标题。
     */
    @Transactional
    public void updateTitle(String conversationId, String title) {
        conversationMapper.updateTitle(conversationId, title);
    }

    /**
     * 归档会话。
     */
    @Transactional
    public void archive(String conversationId) {
        conversationMapper.updateStatus(conversationId, Conversation.Status.ARCHIVED.getValue());
        log.info("[ConversationService] 归档会话: {}", conversationId);
    }

    /**
     * 删除会话。
     */
    @Transactional
    public boolean delete(String conversationId) {
        int rows = conversationMapper.deleteByConversationId(conversationId);
        if (rows > 0) {
            log.info("[ConversationService] 删除会话: {}", conversationId);
            return true;
        }
        return false;
    }

    /**
     * 删除 Agent 的所有会话。
     */
    @Transactional
    public int deleteByAgentId(String agentId) {
        int count = conversationMapper.deleteByAgentId(agentId);
        log.info("[ConversationService] 删除 Agent {} 的 {} 个会话", agentId, count);
        return count;
    }
}

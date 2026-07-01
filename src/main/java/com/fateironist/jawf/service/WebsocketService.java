package com.fateironist.jawf.service;

import com.fateironist.jawf.websocket.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * WebSocket 消息服务。
 * <p>
 * 专职负责 WebSocket 消息的发送，提供统一的消息推送接口。
 * <p>
 * <b>职责</b>：
 * <ul>
 *   <li>发送流式 token 事件</li>
 *   <li>发送完成事件</li>
 *   <li>发送错误事件</li>
 *   <li>向指定会话推送消息</li>
 *   <li>广播消息</li>
 * </ul>
 */
@Slf4j
@Service
public class WebsocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public WebsocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 发送流式 token 事件。
     *
     * @param conversationId 会话 ID
     * @param agentId        Agent ID
     * @param token          本次 token
     * @param accumulated    累积内容
     */
    public void sendStreamToken(String conversationId, String agentId, String token, String accumulated) {
        Event<StreamToken> event = new Event<>();
        event.setType(Event.TYPE_CHAT_STREAM);
        event.setData(new StreamToken(token, accumulated));
        event.setTimestamp(LocalDateTime.now());
        event.setSource("server");
        event.setConversationId(conversationId);
        event.setAgentId(agentId);
        event.setEventId(UUID.randomUUID().toString());

        sendToConversation(conversationId, event);
    }

    /**
     * 发送流式完成事件（将完整消息作为一次流式输出）。
     * <p>
     * 用于非流式场景，将完整消息作为一次流式输出发送。
     *
     * @param conversationId 会话 ID
     * @param agentId        Agent ID
     * @param content        完整内容
     */
    public void sendStreamComplete(String conversationId, String agentId, String content) {
        sendStreamToken(conversationId, agentId, content, content);
    }

    /**
     * 发送完成事件。
     *
     * @param conversationId 会话 ID
     * @param agentId        Agent ID
     */
    public void sendDone(String conversationId, String agentId) {
        Event<Void> event = new Event<>();
        event.setType(Event.TYPE_CHAT_DONE);
        event.setData(null);
        event.setTimestamp(LocalDateTime.now());
        event.setSource("server");
        event.setConversationId(conversationId);
        event.setAgentId(agentId);
        event.setEventId(UUID.randomUUID().toString());

        sendToConversation(conversationId, event);
        log.debug("[WebsocketService] 发送 chat.done: conversation={}", conversationId);
    }

    /**
     * 发送错误事件。
     *
     * @param conversationId 会话 ID
     * @param agentId        Agent ID
     * @param code           错误代码
     * @param message        错误消息
     */
    public void sendError(String conversationId, String agentId, String code, String message) {
        Event<ErrorInfo> event = new Event<>();
        event.setType(Event.TYPE_CHAT_ERROR);
        event.setData(new ErrorInfo(code, message));
        event.setTimestamp(LocalDateTime.now());
        event.setSource("server");
        event.setConversationId(conversationId);
        event.setAgentId(agentId);
        event.setEventId(UUID.randomUUID().toString());

        sendToConversation(conversationId, event);
        log.debug("[WebsocketService] 发送 chat.error: conversation={}, code={}", conversationId, code);
    }

    /**
     * 向指定会话推送消息。
     *
     * @param conversationId 会话 ID
     * @param event          事件对象
     */
    public void sendToConversation(String conversationId, Event<?> event) {
        messagingTemplate.convertAndSend("/topic/chat/" + conversationId, event);
    }

    /**
     * 向所有客户端广播消息。
     *
     * @param event 事件对象
     */
    public void broadcast(Event<?> event) {
        messagingTemplate.convertAndSend("/topic/broadcast", event);
    }

    /**
     * 发送需求文档更新事件。
     *
     * @param conversationId 会话 ID
     * @param agentId        Agent ID
     * @param content        需求文档内容（Markdown 格式）
     */
    public void sendRequirementDocUpdate(String conversationId, String agentId, String content) {
        Event<RequirementDocUpdate> event = new Event<>();
        event.setType(Event.TYPE_REQUIREMENT_DOC_UPDATED);
        event.setData(new RequirementDocUpdate(content));
        event.setTimestamp(LocalDateTime.now());
        event.setSource("server");
        event.setConversationId(conversationId);
        event.setAgentId(agentId);
        event.setEventId(UUID.randomUUID().toString());

        sendToConversation(conversationId, event);
        log.info("[WebsocketService] 发送需求文档更新: conversation={}, length={}",
                conversationId, content != null ? content.length() : 0);
    }

    // ==================== DTOs ====================

    /**
     * 流式 token。
     */
    public record StreamToken(
            String token,
            String accumulated
    ) {}

    /**
     * 需求文档更新。
     */
    public record RequirementDocUpdate(
            String content
    ) {}

    /**
     * 错误信息。
     */
    public record ErrorInfo(
            String code,
            String message
    ) {}
}

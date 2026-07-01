package com.fateironist.jawf.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WebSocket 通信事件。
 * <p>
 * 泛型事件类，用于前后端 WebSocket 通信。
 * 所有 WebSocket 消息都封装为 Event 对象进行传输。
 *
 * @param <T> 事件数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Event<T> {

    /**
     * 事件类型。
     */
    private String type;

    /**
     * 事件数据。
     */
    private T data;

    /**
     * 时间戳。
     */
    private LocalDateTime timestamp;

    /**
     * 会话 ID。
     */
    private String conversationId;

    /**
     * Agent ID。
     */
    private String agentId;

    /**
     * 事件来源（client/server）。
     */
    private String source;

    /**
     * 事件 ID（用于消息追踪）。
     */
    private String eventId;

    /**
     * 创建事件（自动设置时间戳）。
     */
    public static <T> Event<T> of(String type, T data) {
        Event<T> event = new Event<>();
        event.setType(type);
        event.setData(data);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }

    /**
     * 创建事件（带会话信息）。
     */
    public static <T> Event<T> of(String type, T data, String conversationId, String agentId) {
        Event<T> event = of(type, data);
        event.setConversationId(conversationId);
        event.setAgentId(agentId);
        return event;
    }

    /**
     * 创建服务端事件。
     */
    public static <T> Event<T> server(String type, T data) {
        Event<T> event = of(type, data);
        event.setSource("server");
        return event;
    }

    /**
     * 创建客户端事件。
     */
    public static <T> Event<T> client(String type, T data) {
        Event<T> event = of(type, data);
        event.setSource("client");
        return event;
    }

    // ==================== 事件类型常量 ====================

    /** 客户端发送聊天消息 */
    public static final String TYPE_CHAT_SEND = "chat.send";

    /** 服务端返回聊天消息 */
    public static final String TYPE_CHAT_RECEIVE = "chat.receive";

    /** 服务端返回流式消息（逐字输出） */
    public static final String TYPE_CHAT_STREAM = "chat.stream";

    /** 聊天消息完成 */
    public static final String TYPE_CHAT_DONE = "chat.done";

    /** 聊天错误 */
    public static final String TYPE_CHAT_ERROR = "chat.error";

    /** 需求文档更新 */
    public static final String TYPE_REQUIREMENT_DOC_UPDATED = "requirement.doc.updated";

    /** 会话创建 */
    public static final String TYPE_CONVERSATION_CREATED = "conversation.created";

    /** 会话删除 */
    public static final String TYPE_CONVERSATION_DELETED = "conversation.deleted";

    /** Agent 更新 */
    public static final String TYPE_AGENT_UPDATED = "agent.updated";

    /** 连接建立 */
    public static final String TYPE_CONNECTED = "connected";

    /** 心跳 */
    public static final String TYPE_HEARTBEAT = "heartbeat";

    /** 错误 */
    public static final String TYPE_ERROR = "error";
}

package com.fateironist.jawf.websocket;

import com.fateironist.jawf.graph.RequirementClarificationService;
import com.fateironist.jawf.graph.RequirementClarificationService.ClarificationResult;
import com.fateironist.jawf.service.MessageService;
import com.fateironist.jawf.service.WebsocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * WebSocket 事件控制器。
 * <p>
 * 处理客户端发送的 WebSocket 消息。
 * <p>
 * <b>聊天机制</b>：
 * <ul>
 *   <li>接收客户端消息后，通过 {@link RequirementClarificationService} 执行 Graph</li>
 *   <li>若当前 Conversation 的 graphThreadId 为空，则新建 Graph</li>
 *   <li>若 graphThreadId 不为空，则继续执行旧 Graph</li>
 *   <li>当 Graph 走到 END 时，将 Conversation 的 graphThreadId 置为空</li>
 *   <li>Graph 中断时，保持 graphThreadId，等待用户输入继续执行</li>
 * </ul>
 */
@Slf4j
@Controller
public class EventController {

    private final MessageService messageService;
    private final RequirementClarificationService clarificationService;
    private final WebsocketService websocketService;

    public EventController(MessageService messageService,
                           RequirementClarificationService clarificationService,
                           WebsocketService websocketService) {
        this.messageService = messageService;
        this.clarificationService = clarificationService;
        this.websocketService = websocketService;
    }

    /**
     * 处理客户端发送的聊天消息。
     * <p>
     * 客户端发送到: /app/chat.send
     * 服务端推送到: /topic/chat/{conversationId}
     * <p>
     * <b>消息持久化</b>：用户消息在 EventController 保存，助手消息由 Graph 节点保存。
     */
    @MessageMapping("/chat.send")
    public void handleChatSend(@Payload Event<ChatMessage> event) {
        String conversationId = event.getConversationId();
        String agentId = event.getAgentId();
        String content = event.getData() != null ? event.getData().content() : "";

        log.info("[WebSocket] 收到聊天消息: conversation={}, agent={}, content={}",
                conversationId, agentId, content);

        // 保存用户消息到数据库
        messageService.saveUserMessage(conversationId, agentId, content);

        // 异步执行 Graph（避免阻塞 WebSocket 线程）
        CompletableFuture.runAsync(() -> {
            try {
                // 调用 RequirementClarificationService 执行 Graph
                ClarificationResult result = clarificationService.start(conversationId, content);

                // Graph 执行完成后，发送 done 事件
                websocketService.sendDone(conversationId, agentId);
                log.info("[WebSocket] Graph 执行完成: conversation={}, complete={}",
                        conversationId, result.isComplete());

            } catch (Exception e) {
                log.error("[WebSocket] Graph 执行异常: conversation={}", conversationId, e);
                websocketService.sendError(conversationId, agentId, "GRAPH_ERROR", e.getMessage());
                websocketService.sendDone(conversationId, agentId);
            }
        });
    }

    /**
     * 处理心跳消息。
     */
    @MessageMapping("/heartbeat")
    @SendTo("/topic/heartbeat")
    public Event<Void> handleHeartbeat(@Payload Event<Void> event) {
        return Event.server(Event.TYPE_HEARTBEAT, null);
    }

    // ==================== DTOs ====================

    /**
     * 聊天消息。
     */
    public record ChatMessage(
            String content,
            String role,
            LocalDateTime timestamp
    ) {}
}

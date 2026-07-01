package com.fateironist.jawf.controller;

import com.fateironist.jawf.model.Conversation;
import com.fateironist.jawf.model.GraphExecution;
import com.fateironist.jawf.model.Message;
import com.fateironist.jawf.service.ConversationService;
import com.fateironist.jawf.service.GraphExecutionService;
import com.fateironist.jawf.service.MessageService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息相关 API。
 */
@Slf4j
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final GraphExecutionService graphExecutionService;

    public MessageController(MessageService messageService,
                             ConversationService conversationService,
                             GraphExecutionService graphExecutionService) {
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.graphExecutionService = graphExecutionService;
    }

    /**
     * 获取会话列表（指定 Agent）。
     */
    @GetMapping("/conversations")
    public ResponseEntity<List<Conversation>> listConversations(@RequestParam String agentId) {
        return ResponseEntity.ok(conversationService.getAllByAgentId(agentId));
    }

    /**
     * 创建新会话（返回 conversationId）。
     * <p>
     * 先创建 Conversation，再保存系统消息。
     */
    @PostMapping("/conversations")
    public ResponseEntity<CreateConversationResponse> createConversation(
            @RequestBody CreateConversationRequest request) {
        // 1. 创建会话
        Conversation conversation = conversationService.create(request.getAgentId(), "新会话");
        String conversationId = conversation.getConversationId();

        // 2. 保存系统消息（如果有）
        String systemPrompt = request.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messageService.saveSystemMessage(conversationId, request.getAgentId(), systemPrompt);
        }

        log.info("[MessageController] 创建会话: {}", conversationId);
        return ResponseEntity.ok(new CreateConversationResponse(conversationId));
    }

    /**
     * 获取会话消息列表。
     */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<List<Message>> getMessages(@PathVariable String conversationId) {
        return ResponseEntity.ok(messageService.getByConversationId(conversationId));
    }

    /**
     * 获取会话最近 N 条消息。
     */
    @GetMapping("/conversations/{conversationId}/recent")
    public ResponseEntity<List<Message>> getRecentMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(messageService.getRecentByConversationId(conversationId, limit));
    }

    /**
     * 发送消息（保存用户消息，暂不调用 LLM）。
     */
    @PostMapping("/conversations/{conversationId}/send")
    public ResponseEntity<Message> sendMessage(
            @PathVariable String conversationId,
            @RequestBody SendMessageRequest request) {
        Message message = messageService.saveUserMessage(conversationId, request.getAgentId(), request.getContent());
        return ResponseEntity.ok(message);
    }

    /**
     * 删除会话及其所有消息。
     */
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String conversationId) {
        messageService.deleteConversationWithMessages(conversationId);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取会话消息数量。
     */
    @GetMapping("/conversations/{conversationId}/count")
    public ResponseEntity<MessageCountResponse> countMessages(@PathVariable String conversationId) {
        int count = messageService.countByConversationId(conversationId);
        return ResponseEntity.ok(new MessageCountResponse(count));
    }

    /**
     * 获取会话最新的 Graph 执行状态。
     */
    @GetMapping("/conversations/{conversationId}/graph-status")
    public ResponseEntity<GraphExecution> getGraphStatus(@PathVariable String conversationId) {
        GraphExecution execution = graphExecutionService.getLatestByConversationId(conversationId);
        if (execution == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(execution);
    }

    /**
     * 获取会话的需求文档内容。
     * <p>
     * 如果 Graph 执行到了需求澄清及以后的阶段，返回需求文档内容。
     */
    @GetMapping("/conversations/{conversationId}/requirement-doc")
    public ResponseEntity<RequirementDocResponse> getRequirementDoc(@PathVariable String conversationId) {
        GraphExecution execution = graphExecutionService.getLatestByConversationId(conversationId);
        if (execution == null || execution.getRequirementDocPath() == null
                || execution.getRequirementDocPath().isBlank()) {
            return ResponseEntity.noContent().build();
        }

        // 读取需求文档内容
        try {
            java.nio.file.Path docPath = java.nio.file.Path.of(execution.getRequirementDocPath());
            if (!java.nio.file.Files.exists(docPath)) {
                return ResponseEntity.noContent().build();
            }
            String content = java.nio.file.Files.readString(docPath, java.nio.charset.StandardCharsets.UTF_8);
            return ResponseEntity.ok(new RequirementDocResponse(content));
        } catch (Exception e) {
            log.warn("[MessageController] 读取需求文档失败: {}", execution.getRequirementDocPath(), e);
            return ResponseEntity.noContent().build();
        }
    }

    /**
     * 更新会话的需求文档内容。
     * <p>
     * 将用户编辑后的需求文档保存到文件。
     */
    @PutMapping("/conversations/{conversationId}/requirement-doc")
    public ResponseEntity<Void> updateRequirementDoc(
            @PathVariable String conversationId,
            @RequestBody UpdateRequirementDocRequest request) {
        GraphExecution execution = graphExecutionService.getLatestByConversationId(conversationId);
        if (execution == null || execution.getRequirementDocPath() == null
                || execution.getRequirementDocPath().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {
            java.nio.file.Path docPath = java.nio.file.Path.of(execution.getRequirementDocPath());
            // 确保父目录存在
            if (!java.nio.file.Files.exists(docPath.getParent())) {
                java.nio.file.Files.createDirectories(docPath.getParent());
            }
            java.nio.file.Files.writeString(docPath, request.getContent(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            log.info("[MessageController] 需求文档已更新: {}", docPath);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("[MessageController] 更新需求文档失败: {}", execution.getRequirementDocPath(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==================== Request/Response DTOs ====================

    @Data
    public static class CreateConversationRequest {
        private String agentId;
        private String systemPrompt;
    }

    @Data
    public static class CreateConversationResponse {
        private final String conversationId;
    }

    @Data
    public static class SendMessageRequest {
        private String agentId;
        private String content;
    }

    @Data
    public static class MessageCountResponse {
        private final int count;
    }

    @Data
    public static class RequirementDocResponse {
        private final String content;
    }

    @Data
    public static class UpdateRequirementDocRequest {
        private String content;
    }
}

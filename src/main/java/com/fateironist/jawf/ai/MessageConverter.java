package com.fateironist.jawf.ai;

import com.fateironist.jawf.model.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 消息转换器。
 * <p>
 * 负责在持久化层的 {@link Message} 和 Spring AI 的消息类型之间进行转换。
 * <p>
 * <b>支持的转换</b>：
 * <ul>
 *   <li>{@code Message.Type.SYSTEM} ↔ {@link SystemMessage}</li>
 *   <li>{@code Message.Type.USER} ↔ {@link UserMessage}</li>
 *   <li>{@code Message.Type.ASSISTANT} ↔ {@link AssistantMessage}</li>
 *   <li>{@code Message.Type.TOOL} ↔ {@link ToolResponseMessage}</li>
 * </ul>
 */
@Slf4j
public class MessageConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将持久化消息转换为 Spring AI 消息。
     *
     * @param message 持久化消息
     * @return Spring AI 消息
     */
    public static org.springframework.ai.chat.messages.Message toSpringAi(Message message) {
        if (message == null) {
            return null;
        }

        Message.Type type = Message.Type.fromValue(message.getType());
        return switch (type) {
            case SYSTEM -> new SystemMessage(message.getContent());
            case USER -> new UserMessage(message.getContent());
            case ASSISTANT -> new AssistantMessage(message.getContent());
            case TOOL -> {
                // 创建一个包含工具响应内容的 AssistantMessage
                // 因为 Spring AI 1.1.2 的 ToolResponseMessage 构造函数是 protected
                String content = String.format("[Tool Response] id=%s, name=%s, content=%s",
                        message.getToolCallId(), message.getToolName(), message.getContent());
                yield new AssistantMessage(content);
            }
        };
    }

    /**
     * 将 Spring AI 消息转换为持久化消息。
     *
     * @param springAiMessage Spring AI 消息
     * @param conversationId  会话 ID
     * @param agentId         Agent ID
     * @param sequence        序号
     * @return 持久化消息
     */
    public static Message fromSpringAi(org.springframework.ai.chat.messages.Message springAiMessage,
                                       String conversationId, String agentId, int sequence) {
        if (springAiMessage == null) {
            return null;
        }

        Message message = new Message();
        message.setConversationId(conversationId);
        message.setAgentId(agentId);
        message.setSequence(sequence);

        MessageType messageType = springAiMessage.getMessageType();

        switch (messageType) {
            case SYSTEM -> {
                message.setType(Message.Type.SYSTEM.getValue());
                message.setContent(((SystemMessage) springAiMessage).getText());
            }
            case USER -> {
                message.setType(Message.Type.USER.getValue());
                message.setContent(((UserMessage) springAiMessage).getText());
            }
            case ASSISTANT -> {
                AssistantMessage assistantMessage = (AssistantMessage) springAiMessage;
                message.setType(Message.Type.ASSISTANT.getValue());
                message.setContent(assistantMessage.getText());
                // 序列化工具调用
                if (assistantMessage.getToolCalls() != null && !assistantMessage.getToolCalls().isEmpty()) {
                    message.setToolCallsJson(serializeToolCalls(assistantMessage.getToolCalls()));
                }
            }
            case TOOL -> {
                ToolResponseMessage toolResponseMessage = (ToolResponseMessage) springAiMessage;
                message.setType(Message.Type.TOOL.getValue());
                // ToolResponseMessage 可能包含多个响应，取第一个
                if (toolResponseMessage.getResponses() != null && !toolResponseMessage.getResponses().isEmpty()) {
                    ToolResponseMessage.ToolResponse response = toolResponseMessage.getResponses().get(0);
                    message.setContent(response.responseData());
                    message.setToolCallId(response.id());
                    message.setToolName(response.name());
                }
            }
            default -> throw new IllegalArgumentException("不支持的消息类型: " + messageType);
        }

        return message;
    }

    /**
     * 批量将持久化消息转换为 Spring AI 消息列表。
     */
    public static List<org.springframework.ai.chat.messages.Message> toSpringAiList(List<Message> messages) {
        if (messages == null) {
            return List.of();
        }
        List<org.springframework.ai.chat.messages.Message> result = new ArrayList<>();
        for (Message message : messages) {
            org.springframework.ai.chat.messages.Message springAiMessage = toSpringAi(message);
            if (springAiMessage != null) {
                result.add(springAiMessage);
            }
        }
        return result;
    }

    /**
     * 批量将 Spring AI 消息转换为持久化消息列表。
     */
    public static List<Message> fromSpringAiList(List<org.springframework.ai.chat.messages.Message> springAiMessages,
                                                  String conversationId, String agentId) {
        if (springAiMessages == null) {
            return List.of();
        }
        List<Message> result = new ArrayList<>();
        int sequence = 0;
        for (org.springframework.ai.chat.messages.Message springAiMessage : springAiMessages) {
            Message message = fromSpringAi(springAiMessage, conversationId, agentId, sequence++);
            if (message != null) {
                result.add(message);
            }
        }
        return result;
    }

    // ==================== 内部方法 ====================

    /**
     * 解析工具调用 JSON。
     */
    private static List<AssistantMessage.ToolCall> parseToolCalls(String toolCallsJson) {
        if (toolCallsJson == null || toolCallsJson.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> toolCallsList = objectMapper.readValue(
                    toolCallsJson, new TypeReference<>() {});
            List<AssistantMessage.ToolCall> result = new ArrayList<>();
            for (Map<String, Object> toolCall : toolCallsList) {
                String id = (String) toolCall.get("id");
                String name = (String) toolCall.get("name");
                String arguments = objectMapper.writeValueAsString(toolCall.get("arguments"));
                String toolType = toolCall.containsKey("type") ? (String) toolCall.get("type") : "function";
                result.add(new AssistantMessage.ToolCall(id, toolType, name, arguments));
            }
            return result;
        } catch (JsonProcessingException e) {
            log.warn("[MessageConverter] 解析工具调用 JSON 失败: {}", toolCallsJson, e);
            return List.of();
        }
    }

    /**
     * 序列化工具调用为 JSON。
     */
    private static String serializeToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }
        try {
            List<Map<String, Object>> toolCallsList = new ArrayList<>();
            for (AssistantMessage.ToolCall toolCall : toolCalls) {
                toolCallsList.add(Map.of(
                        "id", toolCall.id(),
                        "type", toolCall.type() != null ? toolCall.type() : "function",
                        "name", toolCall.name(),
                        "arguments", objectMapper.readValue(toolCall.arguments(), new TypeReference<Map<String, Object>>() {})
                ));
            }
            return objectMapper.writeValueAsString(toolCallsList);
        } catch (JsonProcessingException e) {
            log.warn("[MessageConverter] 序列化工具调用失败", e);
            return null;
        }
    }
}

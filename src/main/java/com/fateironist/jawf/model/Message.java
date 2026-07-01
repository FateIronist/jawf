package com.fateironist.jawf.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息实体类。
 * <p>
 * 用于持久化存储聊天历史，支持与 Spring AI 消息类型的相互转换。
 * <p>
 * <b>消息类型</b>：
 * <ul>
 *   <li>{@code system} - 系统提示词</li>
 *   <li>{@code user} - 用户消息</li>
 *   <li>{@code assistant} - 助手回复</li>
 *   <li>{@code tool} - 工具调用结果</li>
 * </ul>
 */
@Data
public class Message {

    /** 自增主键 */
    private Long id;

    /** 会话 ID（用于关联同一轮对话） */
    private String conversationId;

    /** Agent ID（关联 Agent 表） */
    private String agentId;

    /** 消息类型：system / user / assistant / tool */
    private String type;

    /** 消息内容 */
    private String content;

    /** 工具调用 ID（type=tool 时必填） */
    private String toolCallId;

    /** 工具名称（type=tool 时可选） */
    private String toolName;

    /** 工具调用请求（type=assistant 且包含工具调用时，JSON 格式） */
    private String toolCallsJson;

    /** 序号（同一会话内的消息顺序） */
    private Integer sequence;

    /** 扩展元数据（JSON 格式） */
    private String metadataJson;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /**
     * 消息类型枚举。
     */
    public enum Type {
        SYSTEM("system"),
        USER("user"),
        ASSISTANT("assistant"),
        TOOL("tool");

        private final String value;

        Type(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Type fromValue(String value) {
            for (Type type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("未知的消息类型: " + value);
        }
    }
}

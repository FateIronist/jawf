package com.fateironist.jawf.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话实体类。
 * <p>
 * 用于跟踪会话与 Graph 的绑定关系，支持断点续执行。
 * 每个会话对应一个 Graph 实例（通过 threadId 关联）。
 */
@Data
public class Conversation {

    /** 自增主键 */
    private Long id;

    /** 会话 ID（UUID 格式） */
    private String conversationId;

    /** 关联的 Agent ID */
    private String agentId;

    /** 绑定的 Graph 线程 ID（用于断点续执行） */
    private String graphThreadId;

    /** 会话标题 */
    private String title;

    /** 会话状态：active / completed / archived */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 会话状态枚举。
     */
    public enum Status {
        /** 活跃状态（有绑定的 Graph） */
        ACTIVE("active"),
        /** 已完成（Graph 执行完毕） */
        COMPLETED("completed"),
        /** 已归档 */
        ARCHIVED("archived");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}

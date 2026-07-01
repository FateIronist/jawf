package com.fateironist.jawf.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 实体类。
 * <p>
 * 表示一个可配置的 AI Agent，包含名称、模型、并行能力等属性。
 * 用于持久化存储 Agent 配置信息，支持后续扩展记忆、工具等能力。
 */
@Data
public class Agent {

    /** 自增主键 */
    private Long id;

    /** Agent 唯一标识（UUID 格式） */
    private String agentId;

    /** Agent 名称 */
    private String name;

    /** Agent 描述 */
    private String description;

    /** 默认模型标识（格式：厂商名_模型名，如 dashscope_deepseek-v4-flash） */
    private String defaultModel;

    /** 系统提示词 */
    private String systemPrompt;

    /** 是否支持并行执行 */
    private Boolean parallelEnabled;

    /** 最大并行数（parallelEnabled=true 时有效） */
    private Integer maxParallel;

    /** 最大重试次数 */
    private Integer maxRetry;

    /** 超时时间（秒） */
    private Integer timeoutSeconds;

    /** Agent 类型（如 llm、tool、workflow 等） */
    private String agentType;

    /** 扩展配置（JSON 格式） */
    private String configJson;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 是否启用 */
    private Boolean enabled;
}

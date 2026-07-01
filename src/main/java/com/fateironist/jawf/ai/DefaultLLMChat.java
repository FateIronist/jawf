package com.fateironist.jawf.ai;

import org.springframework.ai.chat.model.ChatModel;

/**
 * 通用 LLM 适配器，基于配置驱动的 {@link Model} 包装类创建。
 * <p>
 * 任何符合 Spring AI {@link ChatModel} 规范的模型都可由本类包装，
 * 统一走 {@link LLMChat} 上层抽象。
 */
public class DefaultLLMChat extends LLMChat {

    private final Model model;
    private final String systemPrompt;

    public DefaultLLMChat(Model model) {
        this(model, null);
    }

    public DefaultLLMChat(Model model, String systemPrompt) {
        this.model = model;
        this.systemPrompt = systemPrompt;
    }

    @Override
    protected ChatModel getChatModel() {
        return model.getChatModel();
    }

    @Override
    protected String systemPrompt() {
        return systemPrompt;
    }

    public Model getModelInfo() {
        return model;
    }
}

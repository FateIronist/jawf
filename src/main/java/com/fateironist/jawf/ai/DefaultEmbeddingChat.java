package com.fateironist.jawf.ai;

import org.springframework.ai.embedding.EmbeddingModel;

/**
 * 通用 Embedding 适配器，基于配置驱动的 {@link Model} 包装类创建。
 * <p>
 * 任何符合 Spring AI {@link EmbeddingModel} 规范的模型都可由本类包装，
 * 统一走 {@link EmbeddingChat} 上层抽象。
 */
public class DefaultEmbeddingChat extends EmbeddingChat {

    private final Model model;

    public DefaultEmbeddingChat(Model model) {
        this.model = model;
    }

    @Override
    protected EmbeddingModel getEmbeddingModel() {
        return model.getEmbeddingModel();
    }

    public Model getModelInfo() {
        return model;
    }
}

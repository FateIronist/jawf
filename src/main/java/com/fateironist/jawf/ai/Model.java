package com.fateironist.jawf.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * 模型包装类。
 * <p>
 * 统一包装 {@link ChatModel} 或 {@link EmbeddingModel}，并持有厂商名、模型名、模型类型
 * 等身份信息，注册为 Bean 后供 {@link ModelFactory} 按 {@link ModelProvider} 检索。
 */
public class Model {

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final String vendor;
    private final String modelName;
    private final ModelType type;

    public Model(ChatModel chatModel, String vendor, String modelName) {
        this.chatModel = chatModel;
        this.embeddingModel = null;
        this.vendor = vendor;
        this.modelName = modelName;
        this.type = ModelType.CHAT;
    }

    public Model(EmbeddingModel embeddingModel, String vendor, String modelName) {
        this.chatModel = null;
        this.embeddingModel = embeddingModel;
        this.vendor = vendor;
        this.modelName = modelName;
        this.type = ModelType.EMBEDDING;
    }

    public ChatModel getChatModel() {
        return chatModel;
    }

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    public String getVendor() {
        return vendor;
    }

    public String getModelName() {
        return modelName;
    }

    public ModelType getType() {
        return type;
    }

    public boolean isChatModel() {
        return type == ModelType.CHAT;
    }

    public boolean isEmbeddingModel() {
        return type == ModelType.EMBEDDING;
    }

    /**
     * 唯一标识，格式 {@code 厂商名_模型名}。
     */
    public String getIdentifier() {
        return vendor + "_" + modelName;
    }

    @Override
    public String toString() {
        return "Model{" +
                "vendor='" + vendor + '\'' +
                ", modelName='" + modelName + '\'' +
                ", type=" + type +
                '}';
    }

    public enum ModelType {
        CHAT, EMBEDDING
    }
}

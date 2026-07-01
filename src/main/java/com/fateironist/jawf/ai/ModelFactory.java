package com.fateironist.jawf.ai;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 模型工厂。
 * <p>
 * 自动注入 {@code List<Model>} 类型的 chatModels / embeddingModels（配置驱动的模型包装类），
 * 并分别组织为 {@code Map<厂商名, List<Model>>}。
 * 创建上层抽象时，先根据 {@link ModelProvider#vendor()} 路由到对应厂商列表，
 * 再遍历列表按 {@link ModelProvider#modelName()} 匹配具体模型。
 */
@Component
public class ModelFactory {

    private final Map<String, List<Model>> chatModels;
    private final Map<String, List<Model>> embeddingModels;

    public ModelFactory(List<Model> chatModels, List<Model> embeddingModels) {
        this.chatModels = indexByVendor(chatModels);
        this.embeddingModels = indexByVendor(embeddingModels);
    }

    private static Map<String, List<Model>> indexByVendor(List<Model> models) {
        if (models == null || models.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<Model>> result = new HashMap<>();
        for (Model model : models) {
            result.computeIfAbsent(model.getVendor(), k -> new ArrayList<>()).add(model);
        }
        return result;
    }

    /**
     * 创建 LLM 对话客户端。
     *
     * @param provider 厂商名 + 模型名
     * @return {@link LLMChat} 实例；未找到对应 ChatModel 时返回 null
     */
    public LLMChat createLLMChat(ModelProvider provider) {
        return createLLMChat(provider, null);
    }

    /**
     * 创建带系统提示词的 LLM 对话客户端。
     */
    public LLMChat createLLMChat(ModelProvider provider, String systemPrompt) {
        Model model = findModel(chatModels, provider);
        if (model == null) {
            return null;
        }
        return new DefaultLLMChat(model, systemPrompt);
    }

    /**
     * 创建向量嵌入客户端。
     *
     * @param provider 厂商名 + 模型名
     * @return {@link EmbeddingChat} 实例；未找到对应 EmbeddingModel 时返回 null
     */
    public EmbeddingChat createEmbeddingChat(ModelProvider provider) {
        Model model = findModel(embeddingModels, provider);
        if (model == null) {
            return null;
        }
        return new DefaultEmbeddingChat(model);
    }

    /**
     * 在 {@code Map<厂商名, List<Model>>} 中按 vendor + modelName 查找模型。
     */
    private static Model findModel(Map<String, List<Model>> vendorMap, ModelProvider provider) {
        List<Model> list = vendorMap.get(provider.vendor());
        if (list == null) {
            return null;
        }
        for (Model model : list) {
            if (model.getModelName().equals(provider.modelName())) {
                return model;
            }
        }
        return null;
    }

    /**
     * 当前已配置的所有 ChatModel 标识（厂商名_模型名）。
     */
    public Set<String> availableChatModels() {
        return collectIdentifiers(chatModels);
    }

    /**
     * 当前已配置的所有 EmbeddingModel 标识（厂商名_模型名）。
     */
    public Set<String> availableEmbeddingModels() {
        return collectIdentifiers(embeddingModels);
    }

    private static Set<String> collectIdentifiers(Map<String, List<Model>> vendorMap) {
        Set<String> result = new HashSet<>();
        for (List<Model> list : vendorMap.values()) {
            for (Model model : list) {
                result.add(model.getIdentifier());
            }
        }
        return result;
    }

    /**
     * 是否存在指定厂商 + 模型名的 ChatModel。
     */
    public boolean isChatModelAvailable(ModelProvider provider) {
        return findModel(chatModels, provider) != null;
    }

    /**
     * 是否存在指定厂商 + 模型名的 EmbeddingModel。
     */
    public boolean isEmbeddingModelAvailable(ModelProvider provider) {
        return findModel(embeddingModels, provider) != null;
    }
}

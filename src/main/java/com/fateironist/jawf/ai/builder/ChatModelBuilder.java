package com.fateironist.jawf.ai.builder;

import org.springframework.ai.chat.model.ChatModel;

/**
 * ChatModel 厂商构建器 SPI。
 * <p>
 * 每个支持的厂商提供一个实现，{@link com.fateironist.jawf.ai.config.ModelConfiguration}
 * 根据厂商名选择对应构建器来创建 {@link ChatModel}。
 */
public interface ChatModelBuilder {

    /**
     * 是否支持指定厂商。
     *
     * @param vendor 厂商名（来自配置）
     */
    boolean supports(String vendor);

    /**
     * 创建 ChatModel。
     *
     * @param baseUrl   API 基地址
     * @param apiKey    API Key
     * @param modelName 模型名
     * @return 该厂商的 ChatModel 实例
     */
    ChatModel build(String baseUrl, String apiKey, String modelName);
}

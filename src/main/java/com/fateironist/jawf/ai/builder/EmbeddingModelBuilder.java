package com.fateironist.jawf.ai.builder;

import org.springframework.ai.embedding.EmbeddingModel;

/**
 * EmbeddingModel 厂商构建器 SPI。
 */
public interface EmbeddingModelBuilder {

    boolean supports(String vendor);

    EmbeddingModel build(String baseUrl, String apiKey, String modelName);
}

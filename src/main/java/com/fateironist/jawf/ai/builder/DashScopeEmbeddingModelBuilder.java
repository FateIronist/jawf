package com.fateironist.jawf.ai.builder;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.observation.ObservationRegistry;

/**
 * DashScope 厂商 EmbeddingModel 构建器。
 */
@Component
public class DashScopeEmbeddingModelBuilder implements EmbeddingModelBuilder {

    @Override
    public boolean supports(String vendor) {
        return "dashscope".equalsIgnoreCase(vendor);
    }

    @Override
    public EmbeddingModel build(String baseUrl, String apiKey, String modelName) {
        DashScopeApi.Builder apiBuilder = new DashScopeApi.Builder().apiKey(apiKey);
        if (baseUrl != null && !baseUrl.isBlank()) {
            apiBuilder.baseUrl(baseUrl);
        }
        DashScopeApi dashScopeApi = apiBuilder.build();

        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .withModel(modelName)
                .build();

        return new DashScopeEmbeddingModel(
                dashScopeApi,
                MetadataMode.EMBED,
                options,
                new RetryTemplate(),
                ObservationRegistry.NOOP
        );
    }
}

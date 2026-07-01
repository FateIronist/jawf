package com.fateironist.jawf.ai.config;

import com.fateironist.jawf.ai.Model;
import com.fateironist.jawf.ai.builder.ChatModelBuilder;
import com.fateironist.jawf.ai.builder.EmbeddingModelBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 根据 {@link ModelVendorProperties} 自动创建并注册 ChatModel / EmbeddingModel。
 * <p>
 * 1. 解析配置中的模型名称（{@code 厂商名_模型名}）为 {@code Map<厂商名, List<模型名>>}。
 * 2. 对每个非 embedding 模型，使用对应厂商的 {@link ChatModelBuilder} 创建 {@link ChatModel}，
 *    包装为 {@link Model} 后注册到 Spring 容器（List<Model> Bean，名称为 chatModels）。
 * 3. 对每个包含 "embedding" 的模型，使用对应厂商的 {@link EmbeddingModelBuilder} 创建
 *    {@link EmbeddingModel}，同样包装为 {@link Model} 后注册（List<Model> Bean，名称为 embeddingModels）。
 */
@Configuration
@EnableConfigurationProperties(ModelVendorProperties.class)
public class ModelConfiguration {

    private static final String EMBEDDING_MARKER = "embedding";

    @Bean
    public List<Model> chatModels(ModelVendorProperties properties,
                                   List<ChatModelBuilder> chatBuilders) {
        properties.validate();
        Map<String, List<String>> vendorModels = properties.parseVendorModels();
        List<Model> models = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : vendorModels.entrySet()) {
            String vendor = entry.getKey();
            ModelVendorProperties.VendorCredentials credentials = properties.getCredentials(vendor);
            if (credentials == null) {
                throw new IllegalArgumentException("未找到厂商 [" + vendor + "] 的 base-url/api-key 配置");
            }

            ChatModelBuilder builder = chatBuilders.stream()
                    .filter(b -> b.supports(vendor))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "不支持厂商 [" + vendor + "] 的 ChatModel 构建"));

            for (String modelName : entry.getValue()) {
                if (isEmbeddingModel(modelName)) {
                    continue;
                }
                models.add(new Model(
                        builder.build(credentials.baseUrl(), credentials.apiKey(), modelName),
                        vendor,
                        modelName));
            }
        }
        return Collections.unmodifiableList(models);
    }

    @Bean
    public List<Model> embeddingModels(ModelVendorProperties properties,
                                       List<EmbeddingModelBuilder> embeddingBuilders) {
        Map<String, List<String>> vendorModels = properties.parseVendorModels();
        List<Model> models = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : vendorModels.entrySet()) {
            String vendor = entry.getKey();
            ModelVendorProperties.VendorCredentials credentials = properties.getCredentials(vendor);
            if (credentials == null) {
                continue;
            }

            EmbeddingModelBuilder builder = embeddingBuilders.stream()
                    .filter(b -> b.supports(vendor))
                    .findFirst()
                    .orElse(null);
            if (builder == null) {
                continue;
            }

            for (String modelName : entry.getValue()) {
                if (!isEmbeddingModel(modelName)) {
                    continue;
                }
                models.add(new Model(
                        builder.build(credentials.baseUrl(), credentials.apiKey(), modelName),
                        vendor,
                        modelName));
            }
        }
        return Collections.unmodifiableList(models);
    }

    private static boolean isEmbeddingModel(String modelName) {
        return modelName != null && modelName.toLowerCase().contains(EMBEDDING_MARKER);
    }
}

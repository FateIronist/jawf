package com.fateironist.jawf.ai.builder;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.observation.ObservationRegistry;

/**
 * DashScope 厂商 ChatModel 构建器。
 */
@Component
public class DashScopeChatModelBuilder implements ChatModelBuilder {

    @Override
    public boolean supports(String vendor) {
        return "dashscope".equalsIgnoreCase(vendor);
    }

    @Override
    public ChatModel build(String baseUrl, String apiKey, String modelName) {
        DashScopeApi.Builder apiBuilder = new DashScopeApi.Builder().apiKey(apiKey);
        if (baseUrl != null && !baseUrl.isBlank()) {
            apiBuilder.baseUrl(baseUrl);
        }
        DashScopeApi dashScopeApi = apiBuilder.build();

        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(modelName)
                .build();

        return new DashScopeChatModel(
                dashScopeApi,
                options,
                ToolCallingManager.builder().build(),
                new RetryTemplate(),
                ObservationRegistry.NOOP
        );
    }
}

package com.fateironist.jawf.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * LLM 对话抽象类。
 * <p>
 * 屏蔽底层不同模型厂商（DashScope / OpenAI 等）的差异，
 * 对外提供统一的文本对话、带工具对话、流式对话能力。
 * <p>
 * 子类只需实现 {@link #getChatModel()} 返回具体的 {@link ChatModel}，
 * 本类负责基于它构建 {@link ChatClient} 并完成调用。
 */
@Slf4j
public abstract class LLMChat {

    private volatile ChatClient chatClient;

    /**
     * 返回底层 {@link ChatModel}，由具体实现类提供。
     */
    protected abstract ChatModel getChatModel();

    /**
     * 可重写的系统提示词，默认为空。
     */
    protected String systemPrompt() {
        return null;
    }

    /**
     * 懒加载构建 {@link ChatClient}。
     */
    protected ChatClient getChatClient() {
        if (chatClient == null) {
            synchronized (this) {
                if (chatClient == null) {
                    ChatClient.Builder builder = ChatClient.builder(getChatModel());
                    if (systemPrompt() != null && !systemPrompt().isBlank()) {
                        builder.defaultSystem(systemPrompt());
                    }
                    chatClient = builder.build();
                }
            }
        }
        return chatClient;
    }

    /**
     * 简单文本对话。
     *
     * @param message 用户输入
     * @return 模型回复文本
     */
    public String chat(String message) {
        return getChatClient().prompt()
                .user(message)
                .call()
                .content();
    }

    /**
     * 带工具的对话。传入标注了 {@link org.springframework.ai.tool.annotation.Tool} 的 Bean 或
     * {@link ToolCallback} 数组，模型可自主决定是否调用。
     *
     * @param message 用户输入
     * @param tools   工具对象（@Tool 注解方法所在 Bean）或 ToolCallback
     * @return 模型回复文本
     */
    public String chat(String message, Object... tools) {
        return getChatClient().prompt()
                .user(message)
                .tools(tools)
                .call()
                .content();
    }

    /**
     * 基于完整 {@link Prompt} 的对话，返回完整响应（含 metadata）。
     */
    public ChatResponse chat(Prompt prompt) {
        return getChatModel().call(prompt);
    }

    /**
     * 流式对话（返回 Flux）。
     *
     * @param message 用户输入
     * @return 流式响应
     */
    public Flux<ChatResponse> stream(String message) {
        return getChatClient().prompt()
                .user(message)
                .stream()
                .chatResponse();
    }

    /**
     * 流式对话（带回调）。
     * <p>
     * 逐 token 回调，适用于 WebSocket 等实时推送场景。
     *
     * @param message  用户输入
     * @param callback 流式回调
     */
    public void stream(String message, StreamCallback callback) {
        stream(message, callback, null);
    }

    /**
     * 流式对话（带回调和完成处理）。
     *
     * @param message    用户输入
     * @param callback   流式回调
     * @param onComplete 完成后的处理（可选）
     */
    public void stream(String message, StreamCallback callback, Consumer<String> onComplete) {
        AtomicReference<StringBuilder> fullResponse = new AtomicReference<>(new StringBuilder());

        stream(message)
                .doOnNext(response -> {
                    String delta = response.getResult() != null ? response.getResult().getOutput().getText() : "";
                    if (delta != null && !delta.isEmpty()) {
                        fullResponse.get().append(delta);
                        try {
                            callback.onToken(delta);
                        } catch (Exception e) {
                            log.warn("[LLMChat] 回调处理异常", e);
                        }
                    }
                })
                .doOnComplete(() -> {
                    String result = fullResponse.get().toString();
                    try {
                        callback.onComplete(result);
                    } catch (Exception e) {
                        log.warn("[LLMChat] 完成回调异常", e);
                    }
                    if (onComplete != null) {
                        try {
                            onComplete.accept(result);
                        } catch (Exception e) {
                            log.warn("[LLMChat] 完成处理异常", e);
                        }
                    }
                })
                .doOnError(error -> {
                    log.error("[LLMChat] 流式对话异常", error);
                    try {
                        callback.onError(error);
                    } catch (Exception e) {
                        log.warn("[LLMChat] 错误回调异常", e);
                    }
                })
                .subscribe();
    }

    /**
     * 流式对话（带工具和回调）。
     *
     * @param message  用户输入
     * @param callback 流式回调
     * @param tools    工具对象
     */
    public void streamWithTools(String message, StreamCallback callback, Object... tools) {
        AtomicReference<StringBuilder> fullResponse = new AtomicReference<>(new StringBuilder());

        getChatClient().prompt()
                .user(message)
                .tools(tools)
                .stream()
                .chatResponse()
                .doOnNext(response -> {
                    String delta = response.getResult() != null ? response.getResult().getOutput().getText() : "";
                    if (delta != null && !delta.isEmpty()) {
                        fullResponse.get().append(delta);
                        try {
                            callback.onToken(delta);
                        } catch (Exception e) {
                            log.warn("[LLMChat] 回调处理异常", e);
                        }
                    }
                })
                .doOnComplete(() -> {
                    String result = fullResponse.get().toString();
                    try {
                        callback.onComplete(result);
                    } catch (Exception e) {
                        log.warn("[LLMChat] 完成回调异常", e);
                    }
                })
                .doOnError(error -> {
                    log.error("[LLMChat] 流式对话异常", error);
                    try {
                        callback.onError(error);
                    } catch (Exception e) {
                        log.warn("[LLMChat] 错误回调异常", e);
                    }
                })
                .subscribe();
    }

    /**
     * 获取底层 ChatModel，便于需要直接操作模型的场景。
     */
    public ChatModel getModel() {
        return getChatModel();
    }
}

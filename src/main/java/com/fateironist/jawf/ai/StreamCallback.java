package com.fateironist.jawf.ai;

/**
 * 流式对话回调接口。
 * <p>
 * 用于处理 LLM 流式输出的逐 token 回调，
 * 适用于 WebSocket 等场景下的实时推送。
 */
@FunctionalInterface
public interface StreamCallback {

    /**
     * 当收到新的 token 时调用。
     *
     * @param token 本次收到的文本片段
     */
    void onToken(String token);

    /**
     * 当流式输出完成时调用（可选重写）。
     *
     * @param fullResponse 完整的响应文本
     */
    default void onComplete(String fullResponse) {
        // 默认空实现
    }

    /**
     * 当流式输出发生错误时调用（可选重写）。
     *
     * @param error 异常信息
     */
    default void onError(Throwable error) {
        // 默认空实现
    }
}

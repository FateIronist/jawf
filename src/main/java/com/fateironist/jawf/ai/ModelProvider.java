package com.fateironist.jawf.ai;

/**
 * 模型供应商标识（厂商名 + 模型名）。
 * <p>
 * 与配置中 {@code 厂商名_模型名} 的格式一一对应，
 * {@link ModelFactory} 通过它从 {@link List<Model>} 中定位具体 ChatModel。
 */
public record ModelProvider(String vendor, String modelName) {

    /**
     * 解析 {@code 厂商名_模型名} 字符串。
     *
     * @param identifier 例如 {@code dashscope_qwen-plus}
     * @return ModelProvider
     */
    public static ModelProvider from(String identifier) {
        int idx = identifier.indexOf('_');
        if (idx < 0 || idx == identifier.length() - 1) {
            throw new IllegalArgumentException(
                    "模型标识格式错误，应为 厂商名_模型名: " + identifier);
        }
        return new ModelProvider(identifier.substring(0, idx), identifier.substring(idx + 1));
    }

    /**
     * 返回 {@code 厂商名_模型名} 格式字符串。
     */
    public String identifier() {
        return vendor + "_" + modelName;
    }
}

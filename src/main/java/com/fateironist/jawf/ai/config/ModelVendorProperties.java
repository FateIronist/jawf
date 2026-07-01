package com.fateironist.jawf.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多厂商模型统一配置。
 * <p>
 * 通过 4 个 List 按索引位置描述厂商连接信息，再通过 {@link #parseVendorModels()} 把
 * 模型名称（格式：{@code 厂商名_模型名}）拆分为 {@code Map<厂商名, List<模型名>>}。
 */
@Data
@ConfigurationProperties(prefix = "model")
public class ModelVendorProperties {

    /** 厂商标识，例如 dashscope / openai / deepseek。 */
    private List<String> vendors = new ArrayList<>();

    /** 各厂商 API 基地址，与 {@link #vendors} 按索引一一对应。 */
    private List<String> baseUrls = new ArrayList<>();

    /** 各厂商 API Key，与 {@link #vendors} 按索引一一对应。 */
    private List<String> apiKeys = new ArrayList<>();

    /**
     * 模型名称列表，格式 {@code 厂商名_模型名}。
     * 同一个厂商可配置多个模型，例如：
     * <pre>
     * - dashscope_qwen-plus
     * - dashscope_deepseek-v4-flash
     * - openai_gpt-4
     * </pre>
     */
    private List<String> models = new ArrayList<>();

    private static final Pattern MODEL_PATTERN = Pattern.compile("^([^_]+)_(.+)$");

    /**
     * 按厂商名解析模型列表。
     *
     * @return Map<厂商名, 该厂商下的模型名列表>
     */
    public Map<String, List<String>> parseVendorModels() {
        Map<String, List<String>> result = new HashMap<>();
        for (String model : models) {
            Matcher matcher = MODEL_PATTERN.matcher(model);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                        "模型名称格式错误，应为 厂商名_模型名: " + model);
            }
            String vendor = matcher.group(1);
            String modelName = matcher.group(2);
            result.computeIfAbsent(vendor, k -> new ArrayList<>()).add(modelName);
        }
        return result;
    }

    /**
     * 获取指定厂商的配置三元组。
     *
     * @param vendor 厂商名
     * @return [baseUrl, apiKey]；找不到返回 null
     */
    public VendorCredentials getCredentials(String vendor) {
        int idx = vendors.indexOf(vendor);
        if (idx < 0 || idx >= baseUrls.size() || idx >= apiKeys.size()) {
            return null;
        }
        return new VendorCredentials(baseUrls.get(idx), apiKeys.get(idx));
    }

    /**
     * 校验配置是否合法（索引长度一致、模型名格式正确、厂商存在）。
     */
    public void validate() {
        if (vendors.size() != baseUrls.size() || vendors.size() != apiKeys.size()) {
            throw new IllegalArgumentException(
                    "vendors、base-urls、api-keys 三个列表长度必须一致");
        }
        parseVendorModels();
    }

    public record VendorCredentials(String baseUrl, String apiKey) {
    }
}

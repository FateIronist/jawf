package com.fateironist.jawf.functool;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 天气查询工具（示例 FuncTool）。
 * <p>
 * 通过 Spring AI 的 {@link Tool} 注解暴露给 LLM，模型可自主决定调用
 * {@link #getWeather(String, String)} 获取某城市天气。
 * <p>
 * 这里用随机数据模拟真实天气接口，便于在没有外部依赖时直接测试 Tool Calling 流程。
 * 接入真实接口时，把方法体替换为 HTTP 调用即可。
 */
@Slf4j
@Component
public class WeatherTool {

    @Tool(name = "getWeather", description = "查询指定城市当前天气，支持按温度单位返回")
    public Weather getWeather(
            @ToolParam(description = "城市名称，例如：北京、上海") String city,
            @ToolParam(description = "温度单位：CELSIUS（摄氏度）或 FAHRENHEIT（华氏度）", required = false)
            String unit) {

        log.info("[WeatherTool] 查询天气: city={}, unit={}", city, unit);

        // 模拟天气数据
        int tempC = ThreadLocalRandom.current().nextInt(-10, 40);
        String[] conditions = {"晴", "多云", "阴", "小雨", "大雨", "雷阵雨", "雪"};
        String condition = conditions[ThreadLocalRandom.current().nextInt(conditions.length)];
        int humidity = ThreadLocalRandom.current().nextInt(20, 100);

        double displayTemp = "FAHRENHEIT".equalsIgnoreCase(unit)
                ? tempC * 9.0 / 5.0 + 32
                : tempC;

        return new Weather(
                city,
                displayTemp,
                "FAHRENHEIT".equalsIgnoreCase(unit) ? "°F" : "°C",
                condition,
                humidity
        );
    }

    /**
     * 天气返回结构。作为 {@link Tool} 方法的返回值，会被序列化为 JSON 回传给模型。
     */
    public record Weather(
            @JsonProperty("city") String city,
            @JsonProperty("temperature") double temperature,
            @JsonProperty("unit") String unit,
            @JsonProperty("condition") String condition,
            @JsonProperty("humidity") int humidity
    ) {
    }
}

package com.fateironist.jawf.workflow.expression;

import com.alibaba.cloud.ai.graph.OverAllState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 表达式求值上下文。
 * <p>
 * 从 {@link OverAllState} 中解析变量，并缓存已解析的值。
 */
public class ExpressionContext {

    private final OverAllState state;
    private final Map<String, Object> cache = new HashMap<>();

    public ExpressionContext(OverAllState state) {
        this.state = state;
    }

    /**
     * 获取变量值。
     * <p>
     * 优先从缓存中获取，缓存未命中时从 OverallState 中查找。
     *
     * @param key 变量名（可以是完整的 state key，如 {@code llm_1.response}）
     * @return 变量值，不存在时返回 {@link Optional#empty()}
     */
    public Optional<Object> getVariable(String key) {
        if (cache.containsKey(key)) {
            return Optional.ofNullable(cache.get(key));
        }
        Optional<Object> value = state.value(key);
        value.ifPresent(v -> cache.put(key, v));
        return value;
    }

    /**
     * 设置变量值（用于临时变量或计算结果）。
     */
    public void setVariable(String key, Object value) {
        cache.put(key, value);
    }

    /**
     * 获取 OverallState 原始引用。
     */
    public OverAllState getState() {
        return state;
    }
}

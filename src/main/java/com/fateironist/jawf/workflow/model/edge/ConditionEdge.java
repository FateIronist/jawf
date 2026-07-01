package com.fateironist.jawf.workflow.model.edge;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fateironist.jawf.workflow.expression.ExpressionEngine;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 条件边。
 * <p>
 * 根据条件表达式决定是否激活。条件表达式支持：
 * <ul>
 *   <li>比较运算符：{@code >}, {@code <}, {@code ==}, {@code !=}</li>
 *   <li>逻辑运算符：{@code &&}, {@code ||}</li>
 *   <li>算术运算符：{@code +}, {@code -}, {@code *}, {@code /}, {@code %}</li>
 *   <li>位运算符：{@code >>}, {@code >>>}, {@code <<}</li>
 *   <li>括号：{@code ()}</li>
 * </ul>
 * <p>
 * 条件表达式中的 {@code ${...}} 需要先从 {@link OverAllState} 解析为实际值，再进行求值。
 * <p>
 * 示例：
 * <pre>
 *   ${input.value} > 10
 *   ${input.value} < 20
 *   ${input.value} == ${output.value}
 *   ${input.score} >= 60 && ${input.attendance} >= 80
 * </pre>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConditionEdge extends Edge {

    /** 条件表达式 */
    private String condition;

    public ConditionEdge() {
    }

    public ConditionEdge(String id, String name, String fromNodeId, String toNodeId, String condition) {
        this.id = id;
        this.name = name;
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.condition = condition;
    }

    @Override
    public boolean evaluate(OverAllState state) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        return ExpressionEngine.evaluateCondition(condition, state);
    }
}

package com.fateironist.jawf.workflow.expression;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExpressionEngine 单元测试。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExpressionEngineTest {

    // ==================== 模板解析测试 ====================

    @Test
    @Order(1)
    @DisplayName("模板解析 - 简单引用")
    void testResolveTemplateSimpleRef() {
        OverAllState state = new OverAllState();
        state.updateState(Map.of("start.input", "Hello World"));

        String template = "用户输入：${start.input}";
        String result = ExpressionEngine.resolveTemplate(template, state);
        assertEquals("用户输入：Hello World", result);
    }

    @Test
    @Order(2)
    @DisplayName("模板解析 - 多个引用")
    void testResolveTemplateMultipleRefs() {
        OverAllState state = new OverAllState();
        state.updateState(Map.of(
                "start.name", "张三",
                "start.age", "25"
        ));

        String template = "姓名：${start.name}，年龄：${start.age}";
        String result = ExpressionEngine.resolveTemplate(template, state);
        assertEquals("姓名：张三，年龄：25", result);
    }

    @Test
    @Order(3)
    @DisplayName("模板解析 - 无引用")
    void testResolveTemplateNoRef() {
        OverAllState state = new OverAllState();
        String template = "这是一个普通字符串";
        String result = ExpressionEngine.resolveTemplate(template, state);
        assertEquals("这是一个普通字符串", result);
    }

    @Test
    @Order(4)
    @DisplayName("模板解析 - 空模板")
    void testResolveTemplateNull() {
        OverAllState state = new OverAllState();
        assertNull(ExpressionEngine.resolveTemplate(null, state));
        assertEquals("", ExpressionEngine.resolveTemplate("", state));
    }

    @Test
    @Order(5)
    @DisplayName("模板解析 - 引用不存在的变量")
    void testResolveTemplateMissingRef() {
        OverAllState state = new OverAllState();
        String template = "值：${nonexistent.key}";
        String result = ExpressionEngine.resolveTemplate(template, state);
        assertEquals("值：", result);
    }

    // ==================== 值解析测试 ====================

    @Test
    @Order(10)
    @DisplayName("值解析 - 引用解析")
    void testResolveValueReference() {
        OverAllState state = new OverAllState();
        state.updateState(Map.of("llm_1.response", "AI 回复内容"));

        Object result = ExpressionEngine.resolveValue("${llm_1.response}", state);
        assertEquals("AI 回复内容", result);
    }

    @Test
    @Order(11)
    @DisplayName("值解析 - 普通值")
    void testResolveValuePlain() {
        OverAllState state = new OverAllState();
        Object result = ExpressionEngine.resolveValue("普通值", state);
        assertEquals("普通值", result);
    }

    @Test
    @Order(12)
    @DisplayName("值解析 - 非字符串值")
    void testResolveValueNonString() {
        OverAllState state = new OverAllState();
        Object result = ExpressionEngine.resolveValue(42, state);
        assertEquals(42, result);
    }

    // ==================== 条件表达式测试 ====================

    @Test
    @Order(20)
    @DisplayName("条件表达式 - 大于")
    void testConditionGreaterThan() {
        OverAllState state = new OverAllState();
        state.updateState(Map.of("input.value", "15"));

        assertTrue(ExpressionEngine.evaluateCondition("${input.value} > 10", state));
        assertFalse(ExpressionEngine.evaluateCondition("${input.value} > 20", state));
    }

    @Test
    @Order(21)
    @DisplayName("条件表达式 - 小于")
    void testConditionLessThan() {
        OverAllState state = new OverAllState();
        state.updateState(Map.of("input.value", "15"));

        assertTrue(ExpressionEngine.evaluateCondition("${input.value} < 20", state));
        assertFalse(ExpressionEngine.evaluateCondition("${input.value} < 10", state));
    }

    @Test
    @Order(22)
    @DisplayName("条件表达式 - 等于")
    void testConditionEquals() {
        OverAllState state = new OverAllState();
        state.updateState(Map.of("input.value", "15"));

        assertTrue(ExpressionEngine.evaluateCondition("${input.value} == 15", state));
        assertFalse(ExpressionEngine.evaluateCondition("${input.value} == 20", state));
    }

    @Test
    @Order(23)
    @DisplayName("条件表达式 - 不等于")
    void testConditionNotEquals() {
        OverAllState state = new OverAllState();
        state.updateState(Map.of("input.value", "15"));

        assertTrue(ExpressionEngine.evaluateCondition("${input.value} != 20", state));
        assertFalse(ExpressionEngine.evaluateCondition("${input.value} != 15", state));
    }

    @Test
    @Order(24)
    @DisplayName("条件表达式 - 逻辑与")
    void testConditionLogicalAnd() {
        OverAllState state = new OverAllState();
        state.updateState(Map.of(
                "input.score", "85",
                "input.attendance", "90"
        ));

        assertTrue(ExpressionEngine.evaluateCondition(
                "${input.score} >= 60 && ${input.attendance} >= 80", state));
        assertFalse(ExpressionEngine.evaluateCondition(
                "${input.score} >= 90 && ${input.attendance} >= 80", state));
    }

    @Test
    @Order(25)
    @DisplayName("条件表达式 - 逻辑或")
    void testConditionLogicalOr() {
        OverAllState state = new OverAllState();
        state.updateState(Map.of(
                "input.vip", "0",
                "input.score", "95"
        ));

        assertTrue(ExpressionEngine.evaluateCondition(
                "${input.vip} == 1 || ${input.score} >= 90", state));
        assertFalse(ExpressionEngine.evaluateCondition(
                "${input.vip} == 1 || ${input.score} >= 99", state));
    }

    @Test
    @Order(26)
    @DisplayName("条件表达式 - 复杂表达式")
    void testConditionComplex() {
        OverAllState state = new OverAllState();
        state.updateState(Map.of(
                "input.a", "10",
                "input.b", "20",
                "input.c", "30"
        ));

        assertTrue(ExpressionEngine.evaluateCondition(
                "(${input.a} + ${input.b}) == ${input.c}", state));
        assertTrue(ExpressionEngine.evaluateCondition(
                "${input.a} > 5 && ${input.b} < 25 || ${input.c} == 30", state));
    }

    @Test
    @Order(27)
    @DisplayName("条件表达式 - 空条件")
    void testConditionEmpty() {
        OverAllState state = new OverAllState();
        assertTrue(ExpressionEngine.evaluateCondition(null, state));
        assertTrue(ExpressionEngine.evaluateCondition("", state));
        assertTrue(ExpressionEngine.evaluateCondition("  ", state));
    }

    @Test
    @Order(28)
    @DisplayName("条件表达式 - 两个变量比较")
    void testConditionTwoVariables() {
        OverAllState state = new OverAllState();
        state.updateState(Map.of(
                "node1.output", "100",
                "node2.output", "100"
        ));

        assertTrue(ExpressionEngine.evaluateCondition(
                "${node1.output} == ${node2.output}", state));
    }

    // ==================== 算术运算测试 ====================

    @Test
    @Order(30)
    @DisplayName("算术运算 - 加减乘除")
    void testArithmetic() {
        OverAllState state = new OverAllState();
        state.updateState(Map.of("a", "10", "b", "3"));

        assertTrue(ExpressionEngine.evaluateCondition("${a} + ${b} == 13", state));
        assertTrue(ExpressionEngine.evaluateCondition("${a} - ${b} == 7", state));
        assertTrue(ExpressionEngine.evaluateCondition("${a} * ${b} == 30", state));
    }

    @Test
    @Order(31)
    @DisplayName("算术运算 - 括号优先级")
    void testArithmeticParentheses() {
        OverAllState state = new OverAllState();
        state.updateState(Map.of("a", "2", "b", "3", "c", "4"));

        // (2 + 3) * 4 = 20
        assertTrue(ExpressionEngine.evaluateCondition("(${a} + ${b}) * ${c} == 20", state));
        // 2 + 3 * 4 = 14
        assertTrue(ExpressionEngine.evaluateCondition("${a} + ${b} * ${c} == 14", state));
    }

    // ==================== 解析器边界测试 ====================

    @Test
    @Order(40)
    @DisplayName("解析器 - 布尔值")
    void testParserBoolean() {
        OverAllState state = new OverAllState();
        assertTrue(ExpressionEngine.evaluateCondition("true", state));
        assertFalse(ExpressionEngine.evaluateCondition("false", state));
    }

    @Test
    @Order(41)
    @DisplayName("解析器 - 逻辑非")
    void testParserNot() {
        OverAllState state = new OverAllState();
        state.updateState(Map.of("flag", "0"));
        assertTrue(ExpressionEngine.evaluateCondition("!${flag}", state));
    }
}

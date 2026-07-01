package com.fateironist.jawf.workflow.expression;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表达式引擎。
 * <p>
 * 提供两大核心能力：
 * <ol>
 *   <li>解析模板中的 {@code ${...}} 引用，从 {@link OverAllState} 获取实际值</li>
 *   <li>对条件表达式求值，支持比较、逻辑、算术、位运算等运算符</li>
 * </ol>
 *
 * <h3>支持的运算符（按优先级从低到高）</h3>
 * <pre>
 *   ||                        逻辑或
 *   &&                        逻辑与
 *   ==  !=                    等于/不等于
 *   >  >=  <  <=              比较
 *   +  -                      加减
 *   *  /  %                   乘除模
 *   >>  >>>  <<               位移
 *   !  -                      逻辑非/取负（一元）
 * </pre>
 *
 * <h3>引用语法</h3>
 * <pre>
 *   ${variableName}           引用 OverallState 中的变量
 *   ${nodeId.fieldName}       引用节点输出字段
 * </pre>
 */
@Slf4j
public class ExpressionEngine {

    private static final Pattern REFERENCE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * 解析模板字符串，将其中的 {@code ${...}} 引用替换为 OverallState 中的实际值。
     * <p>
     * 对于条件表达式中的引用，非数值类型的值会被包裹在单引号中，以支持字符串比较。
     *
     * @param template 模板字符串，如 {@code "用户输入：${start.input}"}
     * @param state    工作流全局状态
     * @return 解析后的字符串
     */
    public static String resolveTemplate(String template, OverAllState state) {
        return resolveTemplate(template, state, false);
    }

    /**
     * 解析模板字符串。
     *
     * @param template       模板字符串
     * @param state          工作流全局状态
     * @param forCondition   是否用于条件表达式（如果是，非数值类型会包裹单引号）
     * @return 解析后的字符串
     */
    public static String resolveTemplate(String template, OverAllState state, boolean forCondition) {
        if (template == null || template.isBlank()) {
            return template;
        }
        ExpressionContext context = new ExpressionContext(state);
        Matcher matcher = REFERENCE_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String refKey = matcher.group(1).trim();
            Optional<Object> value = context.getVariable(refKey);
            String replacement;
            if (value.isPresent()) {
                Object val = value.get();
                if (forCondition && !isNumeric(val.toString())) {
                    // 对于条件表达式，非数值类型包裹单引号
                    replacement = "'" + val.toString().replace("'", "\\'") + "'";
                } else {
                    replacement = val.toString();
                }
            } else {
                replacement = forCondition ? "''" : "";
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 判断字符串是否为数值类型。
     */
    private static boolean isNumeric(String str) {
        if (str == null || str.isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 解析值：如果值是 {@code ${...}} 引用，则从 OverallState 解析；
     * 否则原样返回。
     *
     * @param value 值（可以是字符串引用或普通值）
     * @param state 工作流全局状态
     * @return 解析后的实际值
     */
    public static Object resolveValue(Object value, OverAllState state) {
        if (value instanceof String str) {
            Matcher matcher = REFERENCE_PATTERN.matcher(str);
            if (matcher.matches()) {
                String refKey = matcher.group(1).trim();
                ExpressionContext context = new ExpressionContext(state);
                return context.getVariable(refKey).orElse(null);
            }
        }
        return value;
    }

    /**
     * 对条件表达式求值。
     * <p>
     * 先将表达式中的 {@code ${...}} 引用替换为实际值，再进行布尔求值。
     *
     * @param condition 条件表达式，如 {@code "${input.value} > 10"}
     * @param state     工作流全局状态
     * @return 条件是否成立
     */
    public static boolean evaluateCondition(String condition, OverAllState state) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        // 使用 forCondition=true 来解析模板，非数值类型会包裹单引号
        String resolved = resolveTemplate(condition, state, true);
        try {
            ExpressionParser parser = new ExpressionParser(resolved);
            double result = parser.parseExpression();
            return result != 0;
        } catch (Exception e) {
            log.warn("[ExpressionEngine] 条件表达式求值失败: {} -> {}", condition, resolved, e);
            return false;
        }
    }

    // ==================== 递归下降解析器 ====================

    /**
     * 内部递归下降解析器，用于解析和求值数学/逻辑表达式。
     */
    static class ExpressionParser {
        private final String input;
        private int pos;

        ExpressionParser(String input) {
            this.input = input.trim();
            this.pos = 0;
        }

        double parseExpression() {
            double result = parseLogicalOr();
            return result;
        }

        // logicalOr → logicalAnd ( "||" logicalAnd )*
        private double parseLogicalOr() {
            double left = parseLogicalAnd();
            while (match("||")) {
                double right = parseLogicalAnd();
                left = (left != 0 || right != 0) ? 1 : 0;
            }
            return left;
        }

        // logicalAnd → equality ( "&&" equality )*
        private double parseLogicalAnd() {
            double left = parseEquality();
            while (match("&&")) {
                double right = parseEquality();
                left = (left != 0 && right != 0) ? 1 : 0;
            }
            return left;
        }

        // equality → comparison ( ( "==" | "!=" ) comparison )*
        private double parseEquality() {
            double left = parseComparison();
            while (true) {
                if (match("==")) {
                    double right = parseComparison();
                    left = Math.abs(left - right) < 1e-9 ? 1 : 0;
                } else if (match("!=")) {
                    double right = parseComparison();
                    left = Math.abs(left - right) >= 1e-9 ? 1 : 0;
                } else {
                    break;
                }
            }
            return left;
        }

        // comparison → shift ( ( ">" | ">=" | "<" | "<=" ) shift )*
        private double parseComparison() {
            double left = parseShift();
            while (true) {
                if (match(">=")) {
                    double right = parseShift();
                    left = left >= right ? 1 : 0;
                } else if (match(">")) {
                    double right = parseShift();
                    left = left > right ? 1 : 0;
                } else if (match("<=")) {
                    double right = parseShift();
                    left = left <= right ? 1 : 0;
                } else if (match("<")) {
                    double right = parseShift();
                    left = left < right ? 1 : 0;
                } else {
                    break;
                }
            }
            return left;
        }

        // shift → addition ( ( ">>" | ">>>" | "<<" ) addition )*
        private double parseShift() {
            double left = parseAddition();
            while (true) {
                if (match(">>>")) {
                    double right = parseAddition();
                    left = ((long) left) >>> (int) right;
                } else if (match(">>")) {
                    double right = parseAddition();
                    left = ((long) left) >> (int) right;
                } else if (match("<<")) {
                    double right = parseAddition();
                    left = ((long) left) << (int) right;
                } else {
                    break;
                }
            }
            return left;
        }

        // addition → multiplication ( ( "+" | "-" ) multiplication )*
        private double parseAddition() {
            double left = parseMultiplication();
            while (true) {
                if (match("+")) {
                    left += parseMultiplication();
                } else if (match("-")) {
                    left -= parseMultiplication();
                } else {
                    break;
                }
            }
            return left;
        }

        // multiplication → unary ( ( "*" | "/" | "%" ) unary )*
        private double parseMultiplication() {
            double left = parseUnary();
            while (true) {
                if (match("*")) {
                    left *= parseUnary();
                } else if (match("/")) {
                    double divisor = parseUnary();
                    if (divisor == 0) throw new ArithmeticException("除零错误");
                    left /= divisor;
                } else if (match("%")) {
                    double divisor = parseUnary();
                    if (divisor == 0) throw new ArithmeticException("除零错误");
                    left %= divisor;
                } else {
                    break;
                }
            }
            return left;
        }

        // unary → ( "!" | "-" ) unary | primary
        private double parseUnary() {
            if (match("!")) {
                double val = parseUnary();
                return val == 0 ? 1 : 0;
            }
            if (match("-")) {
                return -parseUnary();
            }
            return parsePrimary();
        }

        // primary → NUMBER | BOOLEAN | "(" expression ")"
        private double parsePrimary() {
            skipWhitespace();

            if (pos >= input.length()) {
                throw new RuntimeException("表达式意外结束");
            }

            // 括号
            if (input.charAt(pos) == '(') {
                pos++; // skip '('
                double result = parseExpression();
                skipWhitespace();
                if (pos < input.length() && input.charAt(pos) == ')') {
                    pos++; // skip ')'
                }
                return result;
            }

            // 布尔值
            if (input.startsWith("true", pos)) {
                pos += 4;
                return 1;
            }
            if (input.startsWith("false", pos)) {
                pos += 5;
                return 0;
            }

            // 字符串字面量（比较时转为 hashCode）
            if (input.charAt(pos) == '\'' || input.charAt(pos) == '"') {
                return parseStringLiteral();
            }

            // 数字
            return parseNumber();
        }

        private double parseStringLiteral() {
            char quote = input.charAt(pos);
            pos++; // skip opening quote
            int start = pos;
            while (pos < input.length() && input.charAt(pos) != quote) {
                pos++;
            }
            String str = input.substring(start, pos);
            if (pos < input.length()) pos++; // skip closing quote

            // 尝试解析为数字
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                // 字符串比较时使用 hashCode 的绝对值作为数值表示
                return Math.abs(str.hashCode()) % 1000000;
            }
        }

        private double parseNumber() {
            int start = pos;
            while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) {
                throw new RuntimeException("期望数字，位置: " + pos + ", 输入: " + input);
            }
            return Double.parseDouble(input.substring(start, pos));
        }

        private boolean match(String op) {
            skipWhitespace();
            if (pos + op.length() <= input.length() && input.substring(pos, pos + op.length()).equals(op)) {
                // 避免 > 匹配到 >=，>> 匹配到 >>>
                if (op.equals(">") && pos + 1 < input.length() && input.charAt(pos + 1) == '=') return false;
                if (op.equals(">") && pos + 1 < input.length() && input.charAt(pos + 1) == '>') return false;
                if (op.equals(">>") && pos + 2 < input.length() && input.charAt(pos + 2) == '>') return false;
                if (op.equals("<") && pos + 1 < input.length() && input.charAt(pos + 1) == '=') return false;
                if (op.equals("<") && pos + 1 < input.length() && input.charAt(pos + 1) == '<') return false;
                if (op.equals("=") && pos + 1 < input.length() && input.charAt(pos + 1) == '=') return false;
                if (op.equals("!") && pos + 1 < input.length() && input.charAt(pos + 1) == '=') return false;
                pos += op.length();
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }
    }
}

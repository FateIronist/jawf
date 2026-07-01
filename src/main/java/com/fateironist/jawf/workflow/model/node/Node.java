package com.fateironist.jawf.workflow.model.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fateironist.jawf.workflow.model.NodeStatus;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流节点抽象基类。
 * <p>
 * 所有工作流节点都应继承此类，并实现 {@link #execute(OverAllState)} 方法。
 * <p>
 * <b>输入输出引用</b>：{@code input} 和 {@code output} 中的字段可以是普通值，
 * 也可以使用 {@code ${...}} 表示对其他节点输入或输出的引用。
 * 引用在执行前需要从 {@link OverAllState} 中解析为实际值。
 * <p>
 * <b>重试机制</b>：节点执行失败时会自动重试，超过 {@code maxRetry} 次后
 * 节点状态变为 {@link NodeStatus#SKIPPED}。
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = LLMNode.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StartNode.class, name = "start"),
        @JsonSubTypes.Type(value = EndNode.class, name = "end"),
        @JsonSubTypes.Type(value = LLMNode.class, name = "llm")
})
public abstract class Node {

    /** 全局唯一 ID */
    protected String id;

    /** 节点名称 */
    protected String name;

    /** 输入引用，字段值可以是普通值或 ${...} 引用 */
    protected Map<String, Object> input = new HashMap<>();

    /** 输出数据定义，字段值可以是普通值或 ${...} 引用 */
    protected Map<String, Object> output = new HashMap<>();

    /** 执行状态 */
    protected NodeStatus status = NodeStatus.PENDING;

    /** 执行进度 (0.0 ~ 1.0) */
    protected double progress = 0.0;

    /** 当前重试次数 */
    protected int retry = 0;

    /** 最大重试次数 */
    protected int maxRetry = 3;

    /** 错误信息 */
    protected String error;

    // ==================== UI 元数据 ====================

    /** 布局 X 坐标 */
    protected Double x;

    /** 布局 Y 坐标 */
    protected Double y;

    /** 节点图标 */
    protected String icon;

    /** 节点颜色 */
    protected String color;

    /** 节点类型标识（用于前端展示） */
    protected String typeIdentifier;

    /** 展开状态（前端 UI 用） */
    protected Boolean expanded;

    /**
     * 执行节点逻辑。
     * <p>
     * 实现类应：
     * <ol>
     *   <li>从 {@code state} 中获取解析后的输入值</li>
     *   <li>执行具体业务逻辑</li>
     *   <li>返回需要写入 {@link OverAllState} 的 key-value 结果</li>
     * </ol>
     *
     * @param state 当前工作流全局状态
     * @return 需要更新到 OverallState 的数据
     * @throws Exception 节点执行异常
     */
    public abstract Map<String, Object> execute(OverAllState state) throws Exception;

    /**
     * 返回此节点输出到 OverallState 时使用的 key 前缀。
     * <p>
     * 默认使用 {@code nodeId.}，例如节点 id 为 {@code llm_1} 时，
     * 输出字段 {@code result} 对应的 OverallState key 为 {@code llm_1.result}。
     */
    public String getStateKeyPrefix() {
        return id + ".";
    }

    /**
     * 重置节点状态为初始值（用于重新执行）。
     */
    public void reset() {
        this.status = NodeStatus.PENDING;
        this.progress = 0.0;
        this.retry = 0;
        this.error = null;
    }
}

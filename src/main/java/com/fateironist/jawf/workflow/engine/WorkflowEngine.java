package com.fateironist.jawf.workflow.engine;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.fateironist.jawf.workflow.model.Workflow;
import com.fateironist.jawf.workflow.model.WorkflowStatus;
import com.fateironist.jawf.workflow.model.node.LLMNode;
import com.fateironist.jawf.ai.ModelFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流执行引擎。
 * <p>
 * 负责：
 * <ul>
 *   <li>编译 {@link Workflow} 为 {@link CompiledGraph}</li>
 *   <li>启动工作流执行</li>
 *   <li>恢复中断的工作流</li>
 *   <li>跟踪执行状态和进度</li>
 * </ul>
 * <p>
 * <b>执行流程</b>：
 * <ol>
 *   <li>校验工作流（拓扑排序检测环）</li>
 *   <li>构建 {@link StateGraph}</li>
 *   <li>编译图</li>
 *   <li>执行（支持 checkpoint）</li>
 *   <li>处理节点失败和重试</li>
 * </ol>
 */
@Slf4j
@Component
public class WorkflowEngine {

    private final ModelFactory modelFactory;

    /** 已编译的图缓存：workflowId -> CompiledGraph */
    private final Map<String, CompiledGraph> compiledGraphs = new ConcurrentHashMap<>();

    /** 工作流实例缓存：workflowId -> Workflow */
    private final Map<String, Workflow> workflowInstances = new ConcurrentHashMap<>();

    public WorkflowEngine(ModelFactory modelFactory) {
        this.modelFactory = modelFactory;
        // 注入 ModelFactory 到 LLMNode
        LLMNode.setModelFactory(modelFactory);
    }

    /**
     * 编译工作流。
     *
     * @param workflow 工作流对象
     * @return 编译后的图
     * @throws GraphStateException 如果编译失败
     */
    public CompiledGraph compile(Workflow workflow) throws GraphStateException {
        log.info("[WorkflowEngine] 编译工作流: {} ({})", workflow.getName(), workflow.getId());

        // 构建 StateGraph
        StateGraph graph = GraphBuilder.build(workflow);

        // 编译图
        CompiledGraph compiled = graph.compile();

        // 缓存
        compiledGraphs.put(workflow.getId(), compiled);
        workflowInstances.put(workflow.getId(), workflow);

        log.info("[WorkflowEngine] 工作流编译成功: {}", workflow.getId());
        return compiled;
    }

    /**
     * 启动工作流执行。
     *
     * @param workflowId 工作流 ID
     * @param inputs     初始输入参数
     * @return 执行结果
     * @throws Exception 如果执行失败
     */
    public WorkflowResult start(String workflowId, Map<String, Object> inputs) throws Exception {
        CompiledGraph compiled = compiledGraphs.get(workflowId);
        if (compiled == null) {
            throw new IllegalStateException("工作流未编译: " + workflowId);
        }

        Workflow workflow = workflowInstances.get(workflowId);
        workflow.setStatus(WorkflowStatus.RUNNING);

        log.info("[WorkflowEngine] 启动工作流: {}", workflowId);

        // 构建初始状态
        OverAllState initialState = buildInitialState(inputs);

        // 执行图
        RunnableConfig config = RunnableConfig.builder()
                .threadId(UUID.randomUUID().toString())
                .build();

        try {
            Optional<OverAllState> resultOpt = compiled.invoke(initialState, config);
            workflow.setStatus(WorkflowStatus.COMPLETED);

            String threadId = config.threadId().orElse(UUID.randomUUID().toString());

            if (resultOpt.isEmpty()) {
                log.warn("[WorkflowEngine] 工作流执行返回空结果: {}", workflowId);
                return new WorkflowResult(
                        workflowId,
                        threadId,
                        true,
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        null
                );
            }

            OverAllState result = resultOpt.get();
            // 提取最终输出
            Map<String, Object> outputs = extractOutputs(result, workflow);
            log.info("[WorkflowEngine] 工作流执行完成: {}", workflowId);

            return new WorkflowResult(
                    workflowId,
                    threadId,
                    true,
                    outputs,
                    result.data(),
                    null
            );

        } catch (Exception e) {
            workflow.setStatus(WorkflowStatus.FAILED);
            log.error("[WorkflowEngine] 工作流执行失败: {}", workflowId, e);
            throw e;
        }
    }

    /**
     * 恢复中断的工作流。
     *
     * @param workflowId 工作流 ID
     * @param threadId   线程 ID（用于关联之前的执行）
     * @param inputs     新的输入参数
     * @return 执行结果
     * @throws Exception 如果执行失败
     */
    public WorkflowResult resume(String workflowId, String threadId, Map<String, Object> inputs) throws Exception {
        CompiledGraph compiled = compiledGraphs.get(workflowId);
        if (compiled == null) {
            throw new IllegalStateException("工作流未编译: " + workflowId);
        }

        Workflow workflow = workflowInstances.get(workflowId);
        workflow.setStatus(WorkflowStatus.RUNNING);

        log.info("[WorkflowEngine] 恢复工作流: {} (threadId: {})", workflowId, threadId);

        // 构建状态更新
        OverAllState stateUpdate = buildInitialState(inputs);

        // 恢复执行
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        try {
            Optional<OverAllState> resultOpt = compiled.invoke(stateUpdate, config);
            workflow.setStatus(WorkflowStatus.COMPLETED);

            if (resultOpt.isEmpty()) {
                log.warn("[WorkflowEngine] 工作流恢复返回空结果: {}", workflowId);
                return new WorkflowResult(
                        workflowId,
                        threadId,
                        true,
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        null
                );
            }

            OverAllState result = resultOpt.get();
            Map<String, Object> outputs = extractOutputs(result, workflow);
            log.info("[WorkflowEngine] 工作流恢复完成: {}", workflowId);

            return new WorkflowResult(
                    workflowId,
                    threadId,
                    true,
                    outputs,
                    result.data(),
                    null
            );

        } catch (Exception e) {
            workflow.setStatus(WorkflowStatus.FAILED);
            log.error("[WorkflowEngine] 工作流恢复失败: {}", workflowId, e);
            throw e;
        }
    }

    /**
     * 获取已编译的工作流。
     */
    public Optional<CompiledGraph> getCompiledGraph(String workflowId) {
        return Optional.ofNullable(compiledGraphs.get(workflowId));
    }

    /**
     * 获取工作流实例。
     */
    public Optional<Workflow> getWorkflow(String workflowId) {
        return Optional.ofNullable(workflowInstances.get(workflowId));
    }

    /**
     * 移除已编译的工作流。
     */
    public void removeWorkflow(String workflowId) {
        compiledGraphs.remove(workflowId);
        workflowInstances.remove(workflowId);
    }

    /**
     * 构建初始状态。
     */
    private OverAllState buildInitialState(Map<String, Object> inputs) {
        OverAllState state = new OverAllState();
        if (inputs != null) {
            for (Map.Entry<String, Object> entry : inputs.entrySet()) {
                state.updateState(Map.of(entry.getKey(), entry.getValue()));
            }
        }
        return state;
    }

    /**
     * 从执行结果中提取最终输出。
     */
    private Map<String, Object> extractOutputs(OverAllState result, Workflow workflow) {
        Map<String, Object> outputs = new HashMap<>();
        // 提取以 "end." 开头的输出
        String prefix = "end.";
        for (Map.Entry<String, Object> entry : result.data().entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String key = entry.getKey().substring(prefix.length());
                outputs.put(key, entry.getValue());
            }
        }
        return outputs;
    }

    /**
     * 工作流执行结果。
     */
    public record WorkflowResult(
            String workflowId,
            String threadId,
            boolean completed,
            Map<String, Object> outputs,
            Map<String, Object> state,
            String error
    ) {
        /**
         * 获取状态中的指定 key。
         */
        public Optional<Object> getStateValue(String key) {
            return Optional.ofNullable(state.get(key));
        }
    }
}

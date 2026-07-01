package com.fateironist.jawf.workflow.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.fateironist.jawf.workflow.engine.WorkflowEngine;
import com.fateironist.jawf.workflow.model.Workflow;
import com.fateironist.jawf.workflow.serialization.WorkflowJsonDeserializer;
import com.fateironist.jawf.workflow.serialization.WorkflowJsonSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * 工作流服务层。
 * <p>
 * 提供工作流的高层 API，包括：
 * <ul>
 *   <li>编译工作流</li>
 *   <li>启动工作流执行</li>
 *   <li>恢复中断的工作流</li>
 *   <li>获取工作流状态</li>
 *   <li>JSON 序列化/反序列化</li>
 * </ul>
 */
@Slf4j
@Service
public class WorkflowService {

    private final WorkflowEngine workflowEngine;

    public WorkflowService(WorkflowEngine workflowEngine) {
        this.workflowEngine = workflowEngine;
    }

    /**
     * 编译工作流。
     *
     * @param workflow 工作流对象
     * @throws GraphStateException 如果编译失败
     */
    public void compile(Workflow workflow) throws GraphStateException {
        workflowEngine.compile(workflow);
    }

    /**
     * 从 JSON 编译工作流。
     *
     * @param json 工作流 JSON 字符串
     * @throws GraphStateException 如果编译失败
     */
    public CompiledGraph compileFromJson(String json) throws GraphStateException {
        Workflow workflow = WorkflowJsonDeserializer.deserialize(json);
        return workflowEngine.compile(workflow);
    }

    /**
     * 启动工作流执行。
     *
     * @param workflowId 工作流 ID
     * @param inputs     初始输入参数
     * @return 执行结果
     * @throws Exception 如果执行失败
     */
    public WorkflowEngine.WorkflowResult start(String workflowId, Map<String, Object> inputs) throws Exception {
        return workflowEngine.start(workflowId, inputs);
    }

    /**
     * 恢复中断的工作流。
     *
     * @param workflowId 工作流 ID
     * @param threadId   线程 ID
     * @param inputs     新的输入参数
     * @return 执行结果
     * @throws Exception 如果执行失败
     */
    public WorkflowEngine.WorkflowResult resume(String workflowId, String threadId,
                                                 Map<String, Object> inputs) throws Exception {
        return workflowEngine.resume(workflowId, threadId, inputs);
    }

    /**
     * 获取工作流实例。
     */
    public Optional<Workflow> getWorkflow(String workflowId) {
        return workflowEngine.getWorkflow(workflowId);
    }

    /**
     * 将工作流序列化为 JSON。
     *
     * @param workflowId 工作流 ID
     * @return JSON 字符串
     */
    public String toJson(String workflowId) {
        Optional<Workflow> workflow = workflowEngine.getWorkflow(workflowId);
        return workflow.map(WorkflowJsonSerializer::serialize)
                .orElseThrow(() -> new IllegalArgumentException("工作流不存在: " + workflowId));
    }

    /**
     * 从 JSON 反序列化工作流。
     *
     * @param json JSON 字符串
     * @return Workflow 对象
     */
    public Workflow fromJson(String json) {
        return WorkflowJsonDeserializer.deserialize(json);
    }

    /**
     * 移除工作流。
     */
    public void removeWorkflow(String workflowId) {
        workflowEngine.removeWorkflow(workflowId);
    }

    /**
     * 检查工作流是否已编译。
     */
    public boolean isCompiled(String workflowId) {
        return workflowEngine.getCompiledGraph(workflowId).isPresent();
    }
}

package com.fateironist.jawf.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.exception.SubGraphInterruptionException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.fateironist.jawf.model.GraphExecution;
import com.fateironist.jawf.service.ConversationService;
import com.fateironist.jawf.service.GraphExecutionService;
import com.fateironist.jawf.service.WebsocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.fateironist.jawf.graph.IntentRecognitionGraph.*;

/**
 * 需求澄清人机循环服务。
 * <p>
 * 封装 {@link IntentRecognitionGraph} 的中断 / 恢复调用，外部只需调用：
 * <ol>
 *   <li>{@link #start(String, String)} 发起一次需求澄清。若返回 {@link ClarificationResult#needsMoreInfo()}，
 *       则获取 {@link ClarificationResult#getFollowUpQuestion()} 和 {@link ClarificationResult#getThreadId()}。</li>
 *   <li>收集到用户补充回答后，调用 {@link #continueClarification(String, String)} 恢复 Graph。</li>
 *   <li>重复步骤 2 直到 {@link ClarificationResult#isComplete()} 为 true。</li>
 * </ol>
 * <p>
 * <b>会话与 Graph 绑定</b>：
 * <ul>
 *   <li>每个会话（conversationId）对应一个 Graph 实例（通过 threadId 关联）。</li>
 *   <li>会话创建时绑定 Graph 线程 ID。</li>
 *   <li>Graph 执行完成（无论成功与否）后，解绑会话并发送 chat.done 事件。</li>
 *   <li>Graph 中断时，保持绑定，等待用户输入继续执行。</li>
 * </ul>
 * <p>
 * <b>state 持久化关键点</b>：resume 时必须从 checkpoint 加载完整 state（包含 requirementDocPath、
 * reviewRound 等字段），仅更新 input 后传入 {@code invoke(OverAllState, config)}，
 * 而非用 {@code invoke(Map, config)} 创建全新 state 导致 checkpoint 数据丢失。
 */
@Slf4j
@Service
public class RequirementClarificationService {

    private final IntentRecognitionGraph intentGraph;
    private final ConversationService conversationService;
    private final GraphExecutionService graphExecutionService;
    private final WebsocketService websocketService;

    public RequirementClarificationService(IntentRecognitionGraph intentGraph,
                                           ConversationService conversationService,
                                           GraphExecutionService graphExecutionService,
                                           WebsocketService websocketService) {
        this.intentGraph = intentGraph;
        this.conversationService = conversationService;
        this.graphExecutionService = graphExecutionService;
        this.websocketService = websocketService;
    }

    /**
     * 开始新的需求澄清流程。
     * <p>
     * 如果会话已有绑定的 Graph，则恢复执行；否则新建。
     *
     * @param conversationId 会话 ID
     * @param input          用户输入
     * @return 澄清结果
     */
    public ClarificationResult start(String conversationId, String input) {
        // 获取会话信息
        var conversation = conversationService.getByConversationId(conversationId);
        String agentId = conversation != null ? conversation.getAgentId() : "unknown";

        // 检查会话是否已有绑定的 Graph
        String graphThreadId = conversationService.getGraphThreadId(conversationId);
        if (graphThreadId != null && !graphThreadId.isBlank()
                && conversationService.hasActiveGraph(conversationId)) {
            log.info("[start] 会话 {} 有绑定的 Graph={}，尝试恢复", conversationId, graphThreadId);
            try {
                ClarificationResult result = continueClarification(conversationId, input);
                return result;
            } catch (Exception e) {
                log.warn("[start] 恢复失败，新建 Graph: {}", e.getMessage());
            }
        }

        // 新建 Graph
        CompiledGraph graph = intentGraph.getCompiledGraph();
        RunnableConfig config = intentGraph.newConfig(null);
        String threadId = config.threadId().orElse(UUID.randomUUID().toString());

        // 创建 GraphExecution 记录
        graphExecutionService.create(threadId, conversationId, agentId);

        try {
            ClarificationResult result = runFromScratch(graph, config, conversationId, agentId, input, threadId);

            // 如果中断了，绑定会话到 Graph 线程
            if (!result.isComplete()) {
                conversationService.bindGraphThread(conversationId, result.getThreadId());
            } else {
                // 执行完成，发送 done 事件
                sendDoneEvent(conversationId);
            }

            return result;
        } catch (SubGraphInterruptionException e) {
            ClarificationResult result = handleInterruption(config, e);
            // 中断时绑定会话到 Graph 线程
            conversationService.bindGraphThread(conversationId, result.getThreadId());
            return result;
        }
    }

    /**
     * 使用会话绑定的 Graph 恢复需求澄清流程。
     *
     * @param conversationId 会话 ID
     * @param userInput      用户输入
     * @return 澄清结果
     */
    public ClarificationResult continueClarification(String conversationId, String userInput) {
        // 获取会话信息
        var conversation = conversationService.getByConversationId(conversationId);
        String agentId = conversation != null ? conversation.getAgentId() : "unknown";

        // 获取会话绑定的 Graph 线程 ID
        String threadId = conversationService.getGraphThreadId(conversationId);
        if (threadId == null || threadId.isBlank()) {
            log.warn("[continueClarification] 会话 {} 无绑定的 Graph，作为新流程处理", conversationId);
            return start(conversationId, userInput);
        }

        CompiledGraph graph = intentGraph.getCompiledGraph();
        RunnableConfig config = intentGraph.newConfig(threadId);

        try {
            // 从 checkpoint 加载完整 state（包含 requirementDocPath、reviewRound 等）
            StateSnapshot snapshot = graph.getState(config);
            if (snapshot == null || snapshot.state() == null) {
                log.warn("[continueClarification] thread={} 无 checkpoint，作为新流程处理", threadId);
                try {
                    ClarificationResult result = runFromScratch(graph, config, conversationId, agentId, userInput);
                    if (result.isComplete()) {
                        conversationService.unbindGraphThread(conversationId);
                        sendDoneEvent(conversationId);
                    } else {
                        conversationService.bindGraphThread(conversationId, result.getThreadId());
                    }
                    return result;
                } catch (SubGraphInterruptionException e) {
                    ClarificationResult result = handleInterruption(config, e);
                    conversationService.bindGraphThread(conversationId, result.getThreadId());
                    return result;
                }
            }

            // 复制 checkpoint state，仅更新 input
            OverAllState resumeState = cloneAndUpdateInput(snapshot.state(), userInput);
            RunnableConfig resumeConfig = config.withResume();

            ClarificationResult result = runWithResume(graph, resumeConfig, resumeState);

            // 如果执行完成（无论成功失败），解绑会话并发送 done 事件
            if (result.isComplete()) {
                conversationService.unbindGraphThread(conversationId);
                sendDoneEvent(conversationId);
            } else {
                // 中断时更新绑定
                conversationService.bindGraphThread(conversationId, result.getThreadId());
            }

            return result;
        } catch (SubGraphInterruptionException e) {
            ClarificationResult result = handleInterruption(config, e);
            // 中断时更新绑定
            conversationService.bindGraphThread(conversationId, result.getThreadId());
            return result;
        } catch (Exception e) {
            log.error("[continueClarification] thread={} 恢复异常，作为新流程处理", threadId, e);
            try {
                ClarificationResult result = runFromScratch(graph, intentGraph.newConfig(threadId), conversationId, agentId, userInput);
                if (result.isComplete()) {
                    conversationService.unbindGraphThread(conversationId);
                    sendDoneEvent(conversationId);
                } else {
                    conversationService.bindGraphThread(conversationId, result.getThreadId());
                }
                return result;
            } catch (SubGraphInterruptionException ex) {
                ClarificationResult result = handleInterruption(config, ex);
                conversationService.bindGraphThread(conversationId, result.getThreadId());
                return result;
            }
        }
    }

    /**
     * 提交用户修改后的工作流图 JSON 进行校验。
     * <p>
     * 在 confirm_graph_node 中断后调用，将用户修改后的图 JSON 写入 state 并恢复执行。
     * 会清除之前的校验状态（graphValid、graphValidationError），以便重新校验。
     *
     * @param conversationId 会话 ID
     * @param graphJson      用户修改后的工作流图 JSON
     * @return 校验结果，若校验通过则执行工作流
     */
    public ClarificationResult submitGraphJson(String conversationId, String graphJson) {
        // 获取会话绑定的 Graph 线程 ID
        String threadId = conversationService.getGraphThreadId(conversationId);
        if (threadId == null || threadId.isBlank()) {
            log.warn("[submitGraphJson] 会话 {} 无绑定的 Graph", conversationId);
            return ClarificationResult.complete(null, "无绑定的 Graph 状态", Map.of());
        }

        CompiledGraph graph = intentGraph.getCompiledGraph();
        RunnableConfig config = intentGraph.newConfig(threadId);

        try {
            // 从 checkpoint 加载完整 state
            StateSnapshot snapshot = graph.getState(config);
            if (snapshot == null || snapshot.state() == null) {
                log.warn("[submitGraphJson] thread={} 无 checkpoint", threadId);
                return ClarificationResult.complete(threadId, "无 checkpoint 状态", Map.of());
            }

            // 更新图 JSON 到 state，清除之前的校验状态
            Map<String, Object> data = new HashMap<>(snapshot.state().data());
            data.put(KEY_WORKFLOW_GRAPH_JSON, graphJson);
            data.put(KEY_IS_REENTRY, false);
            data.put(KEY_GRAPH_VALID, null);  // 清除之前的校验结果
            data.put(KEY_GRAPH_VALIDATION_ERROR, "");  // 清除之前的校验错误
            data.put(KEY_GRAPH_VALIDATION_ROUND, 0);
            data.put(KEY_HISTORY, IntentRecognitionGraph.appendHistory(
                    snapshot.state(), "user", "[提交图 JSON] 用户修改后的工作流图"));

            OverAllState resumeState = new OverAllState(data);
            RunnableConfig resumeConfig = config.withResume();

            ClarificationResult result = runWithResume(graph, resumeConfig, resumeState);

            // 如果执行完成（无论成功失败），解绑会话并发送 done 事件
            if (result.isComplete()) {
                conversationService.unbindGraphThread(conversationId);
                sendDoneEvent(conversationId);
            } else {
                // 中断时更新绑定
                conversationService.bindGraphThread(conversationId, result.getThreadId());
            }

            return result;
        } catch (SubGraphInterruptionException e) {
            ClarificationResult result = handleInterruption(config, e);
            conversationService.bindGraphThread(conversationId, result.getThreadId());
            return result;
        } catch (Exception e) {
            log.error("[submitGraphJson] thread={} 提交异常", threadId, e);
            // 发生异常时也发送 done 事件
            sendDoneEvent(conversationId);
            return ClarificationResult.complete(threadId, "提交图 JSON 失败: " + e.getMessage(), Map.of());
        }
    }

    /**
     * 发送 chat.done 事件到 WebSocket。
     */
    private void sendDoneEvent(String conversationId) {
        try {
            // 从会话获取 agentId
            var conversation = conversationService.getByConversationId(conversationId);
            String agentId = conversation != null ? conversation.getAgentId() : "unknown";
            websocketService.sendDone(conversationId, agentId);
            log.info("[RequirementClarificationService] 发送 chat.done 事件: conversation={}", conversationId);
        } catch (Exception e) {
            log.warn("[RequirementClarificationService] 发送 chat.done 事件失败", e);
        }
    }

    /**
     * 全新执行：用 input map 创建全新 state。
     */
    private ClarificationResult runFromScratch(CompiledGraph graph, RunnableConfig config,
                                               String conversationId, String agentId, String input)
            throws SubGraphInterruptionException {
        return runFromScratch(graph, config, conversationId, agentId, input, null);
    }

    /**
     * 全新执行：用 input map 创建全新 state（指定 threadId）。
     */
    private ClarificationResult runFromScratch(CompiledGraph graph, RunnableConfig config,
                                               String conversationId, String agentId, String input,
                                               String threadId)
            throws SubGraphInterruptionException {
        if (threadId == null) {
            threadId = config.threadId().orElse(UUID.randomUUID().toString());
        }
        Map<String, Object> initialState = new HashMap<>();
        initialState.put(KEY_INPUT, input);
        initialState.put(KEY_CONVERSATION_ID, conversationId);
        initialState.put(KEY_AGENT_ID, agentId);
        initialState.put(KEY_THREAD_ID, threadId);
        Optional<OverAllState> finalState = graph.invoke(initialState, config);
        return resolveResult(config, finalState);
    }

    /**
     * 恢复执行：用 checkpoint state + withResume。
     */
    private ClarificationResult runWithResume(CompiledGraph graph, RunnableConfig config, OverAllState resumeState)
            throws SubGraphInterruptionException {
        Optional<OverAllState> finalState = graph.invoke(resumeState, config);
        return resolveResult(config, finalState);
    }

    /**
     * 统一结果判定：基于 state 语义内容判断图是否真正结束。
     */
    private ClarificationResult resolveResult(RunnableConfig config, Optional<OverAllState> finalState) {
        if (finalState.isEmpty()) {
            return ClarificationResult.complete(
                    config.threadId().orElse(null),
                    "无返回状态", Map.of());
        }

        OverAllState state = finalState.get();
        String threadId = config.threadId().orElse(null);

        // 检查是否在 plan_node 中断（等待用户确认图 JSON）
        String workflowGraphJson = state.value(KEY_WORKFLOW_GRAPH_JSON, String.class).orElse(null);
        if (workflowGraphJson != null && !workflowGraphJson.isBlank()) {
            Boolean graphValid = state.value(KEY_GRAPH_VALID, Boolean.class).orElse(null);
            // 如果图已生成但未校验，返回图 JSON 等待用户确认
            if (graphValid == null) {
                return ClarificationResult.withGraphJson(threadId, workflowGraphJson, stateData(state));
            }
            // 如果图校验失败，返回错误信息
            if (!graphValid) {
                String validationError = state.value(KEY_GRAPH_VALIDATION_ERROR, String.class).orElse("未知错误");
                return ClarificationResult.graphInvalid(threadId, workflowGraphJson, validationError, stateData(state));
            }
        }

        Boolean complete = state.value(KEY_REQUIREMENT_COMPLETE, Boolean.class).orElse(false);
        String followUp = state.value(KEY_FOLLOW_UP_QUESTION, String.class).orElse("");

        if (!complete && !followUp.isBlank()) {
            return ClarificationResult.needsMoreInfo(threadId, followUp, stateData(state));
        }

        String answer = state.value(KEY_ANSWER, String.class)
                .orElseGet(() -> state.value(KEY_EXECUTION_RESULT, String.class)
                        .orElse("处理完成"));
        return ClarificationResult.complete(threadId, answer, stateData(state));
    }

    private ClarificationResult handleInterruption(RunnableConfig config, SubGraphInterruptionException e) {
        String threadId = config.threadId().orElse(null);
        OverAllState state = new OverAllState(e.state());
        String question = extractFollowUpQuestion(state).orElse("能否补充更多细节？");
        log.info("[RequirementClarificationService] thread={} 被中断，追问: {}", threadId, question);
        return ClarificationResult.needsMoreInfo(threadId, question, stateData(state));
    }

    /**
     * 复制 checkpoint state，更新 input、标记 reentry、追加用户消息到 history。
     * 保留 requirementDocPath、reviewRound 等全部历史字段。
     */
    private static OverAllState cloneAndUpdateInput(OverAllState checkpointState, String newInput) {
        Map<String, Object> data = new HashMap<>(checkpointState.data());
        data.put(KEY_INPUT, newInput);
        data.put(KEY_IS_REENTRY, true);
        data.put(KEY_HISTORY, IntentRecognitionGraph.appendHistory(checkpointState, "user", newInput));
        return new OverAllState(data);
    }

    private static Map<String, Object> stateData(OverAllState state) {
        return state == null ? Map.of() : new HashMap<>(state.data());
    }

    /**
     * 需求澄清结果。
     */
    public static class ClarificationResult {
        private final String threadId;
        private final boolean complete;
        private final String answer;
        private final String followUpQuestion;
        private final String workflowGraphJson;
        private final String graphValidationError;
        private final Map<String, Object> state;

        private ClarificationResult(String threadId, boolean complete, String answer,
                                    String followUpQuestion, String workflowGraphJson,
                                    String graphValidationError, Map<String, Object> state) {
            this.threadId = threadId;
            this.complete = complete;
            this.answer = answer;
            this.followUpQuestion = followUpQuestion;
            this.workflowGraphJson = workflowGraphJson;
            this.graphValidationError = graphValidationError;
            this.state = state;
        }

        public static ClarificationResult complete(String threadId, String answer,
                                                   Map<String, Object> state) {
            return new ClarificationResult(threadId, true, answer, null, null, null, state);
        }

        public static ClarificationResult needsMoreInfo(String threadId, String followUpQuestion,
                                                        Map<String, Object> state) {
            return new ClarificationResult(threadId, false, null, followUpQuestion, null, null, state);
        }

        /**
         * 返回图 JSON，等待用户确认或修改。
         */
        public static ClarificationResult withGraphJson(String threadId, String workflowGraphJson,
                                                        Map<String, Object> state) {
            return new ClarificationResult(threadId, false, null, null, workflowGraphJson, null, state);
        }

        /**
         * 图 JSON 校验失败，返回错误信息。
         */
        public static ClarificationResult graphInvalid(String threadId, String workflowGraphJson,
                                                       String validationError, Map<String, Object> state) {
            return new ClarificationResult(threadId, false, null, null, workflowGraphJson, validationError, state);
        }

        public String getThreadId() {
            return threadId;
        }

        public boolean isComplete() {
            return complete;
        }

        public boolean needsMoreInfo() {
            return !complete;
        }

        public String getAnswer() {
            return answer;
        }

        public String getFollowUpQuestion() {
            return followUpQuestion;
        }

        /**
         * 获取生成的工作流图 JSON。
         * <p>
         * 在 plan_node 中断后返回，前端可用于展示和编辑。
         */
        public String getWorkflowGraphJson() {
            return workflowGraphJson;
        }

        /**
         * 获取图 JSON 校验错误信息。
         * <p>
         * 在 validate_graph_node 校验失败后返回，前端可用于提示用户修改。
         */
        public String getGraphValidationError() {
            return graphValidationError;
        }

        /**
         * 是否有工作流图 JSON（等待用户确认）。
         */
        public boolean hasWorkflowGraph() {
            return workflowGraphJson != null && !workflowGraphJson.isBlank();
        }

        /**
         * 图 JSON 是否校验失败。
         */
        public boolean isGraphInvalid() {
            return graphValidationError != null && !graphValidationError.isBlank();
        }

        public Map<String, Object> getState() {
            return state;
        }
    }
}

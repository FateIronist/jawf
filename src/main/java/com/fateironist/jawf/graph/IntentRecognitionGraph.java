package com.fateironist.jawf.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.file.FileSystemSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.fateironist.jawf.ai.LLMChat;
import com.fateironist.jawf.ai.ModelFactory;
import com.fateironist.jawf.ai.ModelProvider;
import com.fateironist.jawf.workflow.engine.WorkflowEngine;
import com.fateironist.jawf.workflow.model.Workflow;
import com.fateironist.jawf.workflow.serialization.WorkflowJsonDeserializer;
import com.fateironist.jawf.workflow.serialization.WorkflowJsonSerializer;
import com.fateironist.jawf.workflow.validation.WorkflowValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * 用户需求处理 Graph。
 * <p>
 * 流程：
 * <pre>
 * START → input_node → intent_node
 *                          │
 *          ┌────────────────┴────────────────┐
 *          │(QA)                              │(LONG_TASK)
 *          ▼                                  ▼
 *    qa_node → END                    requirement_node
 *                                            │
 *                                            ▼
 *                                     review_node ←───────┐
 *                                            │            │
 *                    ┌───────────────────────┴──────┐     │
 *                    │(INCOMPLETE)                    │     │
 *                    ▼                                │     │
 *             followup_node ──[INTERRUPT]─────────────┘     │
 *                    │                                      │
 *                    │(COMPLETE / MAX_ROUND)                 │
 *                    ▼                                      │
 *                  plan_node                                │
 *                    │                                      │
 *                    ▼                                      │
 *           confirm_graph_node                               │
 *                    │                                      │
 *              [INTERRUPT] ← 用户确认/修改图 JSON            │
 *                    │                                      │
 *                    ▼                                      │
 *           validate_graph_node ─────────────────────────────┘
 *                    │
 *          ┌────────┴────────┐
 *          │(VALID)          │(INVALID)
 *          ▼                 ▼
 *    execute_node    confirm_graph_node ──[INTERRUPT]
 *          │                 │
 *          ▼                 ▼
 *        END         validate_graph_node (循环)
 * </pre>
 * <p>
 * <b>聊天记录</b>：每个节点执行时将角色和内容追加到 {@link #KEY_HISTORY}（{@code List<Map<String,String>>}）。
 * <p>
 * <b>需求澄清循环</b>：
 * <ol>
 *   <li>{@code followup_node} 设置 {@link #KEY_IS_REENTRY}={@code true}，中断等待用户补充。</li>
 *   <li>外部 resume 时将用户新输入写入 {@link #KEY_INPUT}，并追加到 history。</li>
 *   <li>{@code review_node} 检测到 reentry 后，在提示词中加入用户新输入，
 *       让 LLM 修改追加需求文档并判断完整度，返回包含 {@code updatedDoc} 的 JSON。</li>
 *   <li>最多 {@link #MAX_REVIEW_ROUNDS} 轮后强制进入计划节点。</li>
 * </ol>
 * <p>
 * <b>工作流图生成</b>：
 * <ol>
 *   <li>{@code plan_node} 使用提示词模板生成工作流图 JSON。</li>
 *   <li>反序列化并校验图 JSON，若失败则重试（最多 {@link #MAX_GRAPH_GENERATION_ROUNDS} 轮）。</li>
 *   <li>生成完成后进入 {@code confirm_graph_node}，中断等待用户确认/修改。</li>
 *   <li>{@code validate_graph_node} 校验图 JSON 合法性。</li>
 *   <li>若不合法，将错误信息存入 state，回到 {@code confirm_graph_node} 中断，提示用户修改。</li>
 *   <li>若合法，进入 {@code execute_node} 执行工作流。</li>
 * </ol>
 */
@Slf4j
@Component
public class IntentRecognitionGraph {

    public static final String KEY_INPUT = "input";
    public static final String KEY_INTENT = "intent";
    public static final String KEY_ANSWER = "answer";
    public static final String KEY_REQUIREMENT_DOC_PATH = "requirementDocPath";
    public static final String KEY_REQUIREMENT_COMPLETE = "requirementComplete";
    public static final String KEY_REASON = "reason";
    public static final String KEY_FOLLOW_UP_QUESTION = "followUpQuestion";
    public static final String KEY_PLAN_DOC_PATH = "planDocPath";
    public static final String KEY_EXECUTION_RESULT = "executionResult";
    public static final String KEY_REVIEW_ROUND = "reviewRound";
    public static final String KEY_HISTORY = "history";
    public static final String KEY_IS_REENTRY = "isReentry";

    // 会话相关状态键
    public static final String KEY_CONVERSATION_ID = "conversationId";
    public static final String KEY_AGENT_ID = "agentId";
    public static final String KEY_THREAD_ID = "threadId";

    // 工作流图相关状态键
    public static final String KEY_WORKFLOW_GRAPH_JSON = "workflowGraphJson";
    public static final String KEY_PLAN_JSON_PATH = "planJsonPath";
    public static final String KEY_GRAPH_VALID = "graphValid";
    public static final String KEY_GRAPH_VALIDATION_ERROR = "graphValidationError";
    public static final String KEY_GRAPH_GENERATION_ROUND = "graphGenerationRound";
    public static final String KEY_GRAPH_VALIDATION_ROUND = "graphValidationRound";

    public static final String INTENT_QA = "QA";
    public static final String INTENT_LONG_TASK = "LONG_TASK";
    public static final String REVIEW_INCOMPLETE = "INCOMPLETE";
    public static final String REVIEW_COMPLETE = "COMPLETE";
    public static final String GRAPH_VALID = "VALID";
    public static final String GRAPH_INVALID = "INVALID";

    public static final int MAX_REVIEW_ROUNDS = 5;
    public static final int MAX_GRAPH_GENERATION_ROUNDS = 3;
    public static final int MAX_GRAPH_VALIDATION_ROUNDS = 5;

    private final LLMChat llmChat;
    private final WorkflowEngine workflowEngine;
    private final com.fateironist.jawf.service.WebsocketService websocketService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CompiledGraph compiledGraph;

    public IntentRecognitionGraph(ModelFactory modelFactory, WorkflowEngine workflowEngine,
                                   com.fateironist.jawf.service.WebsocketService websocketService) throws GraphStateException {
        this.llmChat = modelFactory.createLLMChat(
                new ModelProvider("dashscope", "deepseek-v4-flash"),
                "你是一个需求分析与任务规划助手，请根据用户输入进行意图识别、问答或生成结构化文档。");
        this.workflowEngine = workflowEngine;
        this.websocketService = websocketService;
        this.compiledGraph = build();
    }

    public CompiledGraph getCompiledGraph() {
        return compiledGraph;
    }

    public RunnableConfig newConfig(String threadId) {
        return RunnableConfig.builder()
                .threadId(threadId == null ? UUID.randomUUID().toString() : threadId)
                .build();
    }

    public static Optional<String> extractFollowUpQuestion(OverAllState state) {
        return state == null ? Optional.empty() : state.value(KEY_FOLLOW_UP_QUESTION, String.class);
    }

    public static Optional<String> extractAnswer(OverAllState state) {
        return state == null ? Optional.empty() : state.value(KEY_ANSWER, String.class);
    }

    // ==================== Graph 构建 ====================

    private CompiledGraph build() throws GraphStateException {
        StateGraph graph = new StateGraph();

        graph.addNode("input_node", node_async(this::inputNode))
                .addNode("intent_node", node_async(this::intentRecognitionNode))
                .addNode("qa_node", node_async(this::qaNode))
                .addNode("requirement_node", node_async(this::requirementNode))
                .addNode("review_node", node_async(this::reviewNode))
                .addNode("followup_node", node_async(this::followUpNode))
                .addNode("plan_node", node_async(this::planNode))
                .addNode("confirm_graph_node", node_async(this::confirmGraphNode))
                .addNode("validate_graph_node", node_async(this::validateGraphNode))
                .addNode("execute_node", node_async(this::executeNode));

        graph.addEdge(StateGraph.START, "input_node")
                .addEdge("input_node", "intent_node")
                .addConditionalEdges("intent_node", edge_async(this::routeByIntent),
                        Map.of(INTENT_QA, "qa_node", INTENT_LONG_TASK, "requirement_node"))
                .addEdge("qa_node", StateGraph.END)
                .addEdge("requirement_node", "review_node")
                .addConditionalEdges("review_node", edge_async(this::routeByReview),
                        Map.of(REVIEW_INCOMPLETE, "followup_node", REVIEW_COMPLETE, "plan_node"))
                .addEdge("followup_node", "review_node")
                // plan_node 生成图后，进入 confirm_graph_node 等待用户确认
                .addEdge("plan_node", "confirm_graph_node")
                // confirm_graph_node 中断后，进入 validate_graph_node 校验
                .addEdge("confirm_graph_node", "validate_graph_node")
                // validate_graph_node 校验图合法性
                .addConditionalEdges("validate_graph_node", edge_async(this::routeByGraphValidation),
                        Map.of(GRAPH_VALID, "execute_node", GRAPH_INVALID, "confirm_graph_node"))
                .addEdge("execute_node", StateGraph.END);

        Path checkpointDir = getTmpPath("checkpoints").resolve("intent_graph");
        FileSystemSaver saver = FileSystemSaver.builder()
                .targetFolder(checkpointDir)
                .build();

        return graph.compile(
                com.alibaba.cloud.ai.graph.CompileConfig.builder()
                        .interruptAfter("followup_node", "confirm_graph_node")
                        .saverConfig(SaverConfig.builder().register(saver).build())
                        .build()
        );
    }

    // ==================== 节点实现 ====================

    private Map<String, Object> inputNode(OverAllState state) {
        String input = state.value(KEY_INPUT, String.class).orElse("");
        String threadId = state.value(KEY_CONVERSATION_ID, String.class).orElse("unknown");
        log.info("[input_node] 用户输入: {}", input);

        // 更新执行状态
        updateExecutionStatus(state, com.fateironist.jawf.model.GraphExecution.Status.INITIALIZING, "input_node");

        Map<String, Object> result = new HashMap<>();
        result.put(KEY_INPUT, input);
        result.put(KEY_HISTORY, appendHistory(state, "user", input));
        return result;
    }

    private Map<String, Object> intentRecognitionNode(OverAllState state) {
        String input = state.value(KEY_INPUT, String.class).orElse("");
        log.info("[intent_node] 意图识别");

        // 更新执行状态
        updateExecutionStatus(state, com.fateironist.jawf.model.GraphExecution.Status.INTENT_RECOGNITION, "intent_node");

        String prompt = String.format("""
                请判断以下用户输入的意图，只回复 QA 或 LONG_TASK 之一：
                QA 表示普通问答；LONG_TASK 表示用户有一个需要多步骤执行的复杂任务或需求。

                用户输入：%s
                意图：
                """, input);
        String intent = Optional.ofNullable(llmChat.chat(prompt))
                .map(String::trim)
                .map(s -> s.contains("LONG_TASK") ? INTENT_LONG_TASK : INTENT_QA)
                .orElse(INTENT_QA);
        log.info("[intent_node] 意图识别结果: {}", intent);

        Map<String, Object> result = new HashMap<>();
        result.put(KEY_INTENT, intent);
        result.put(KEY_HISTORY, appendHistory(state, "assistant", "[意图识别] " + intent));
        return result;
    }

    private Map<String, Object> qaNode(OverAllState state) {
        String input = state.value(KEY_INPUT, String.class).orElse("");
        String answer = llmChat.chat(input);
        log.info("[qa_node] 已生成回答");

        Map<String, Object> result = new HashMap<>();
        result.put(KEY_ANSWER, answer);
        result.put(KEY_HISTORY, appendHistory(state, "assistant", answer));
        return result;}

    private Map<String, Object> requirementNode(OverAllState state) {
        String input = state.value(KEY_INPUT, String.class).orElse("");
        String conversationId = state.value(KEY_CONVERSATION_ID, String.class).orElse("unknown");
        String agentId = state.value(KEY_AGENT_ID, String.class).orElse("unknown");
        String threadId = state.value(KEY_THREAD_ID, String.class).orElse(null);

        Path docPath = getTmpPath("docs").resolve("requirement_" + System.currentTimeMillis() + ".md");

        String prompt = String.format("""
                请将以下用户需求整理成一份结构化的需求文档（Markdown 格式）。
                要求包含：背景、目标、功能需求、非功能需求、约束条件。
                如果信息不足，请在文档末尾明确列出需要进一步澄清的问题。

                用户需求：%s
                """, input);
        String content = llmChat.chat(prompt);

        writeFile(docPath, content);
        log.info("[requirement_node] 需求文档已生成: {}", docPath);

        // 保存需求文档路径到 GraphExecution
        saveRequirementDocPath(threadId, docPath.toString());

        // 发送需求文档到前端实时渲染
        websocketService.sendRequirementDocUpdate(conversationId, agentId, content);

        Map<String, Object> result = new HashMap<>();
        result.put(KEY_REQUIREMENT_DOC_PATH, docPath.toString());
        result.put(KEY_REVIEW_ROUND, 0);
        result.put(KEY_IS_REENTRY, false);
        result.put(KEY_HISTORY, appendHistory(state, "assistant",
                "[需求文档已生成] " + docPath.getFileName()));
        return result;
    }

    /**
     * 审阅节点。
     * <p>
     * 首次进入：审阅现有需求文档，返回 JSON。
     * <p>
     * 重新进入（{@code isReentry=true}）：用户已补充新信息，
     * 在提示词中加入用户新输入，让 LLM 修改追加需求文档后再判断完整度。
     * 返回 JSON 中额外包含 {@code updatedDoc} 字段。
     * <p>
     * <b>消息保存</b>：用户消息由 EventController 保存，助手消息（followUpQuestion）由本节点保存。
     */
    private Map<String, Object> reviewNode(OverAllState state) {
        log.info("[review_node] 审阅节点");

        // 更新执行状态
        updateExecutionStatus(state, com.fateironist.jawf.model.GraphExecution.Status.REQUIREMENT_CLARIFICATION, "review_node");

        // 从 state 获取需求文档路径，实时读取文件内容
        String docPath = state.value(KEY_REQUIREMENT_DOC_PATH, String.class).orElse("");
        String docContent = readFile(Path.of(docPath));
        int round = state.value(KEY_REVIEW_ROUND, Integer.class).orElse(0);
        boolean isReentry = state.value(KEY_IS_REENTRY, Boolean.class).orElse(false);
        String threadId = state.value(KEY_THREAD_ID, String.class).orElse(null);

        // 从 state 获取 conversationId 和 agentId
        String conversationId = state.value(KEY_CONVERSATION_ID, String.class).orElse("unknown");
        String agentId = state.value(KEY_AGENT_ID, String.class).orElse("unknown");

        // 达到最大轮数，强制视为完整
        if (round >= MAX_REVIEW_ROUNDS) {
            log.warn("[review_node] 已达最大追问轮数 {}，强制视为完整", MAX_REVIEW_ROUNDS);
            Map<String, Object> result = new HashMap<>();
            result.put(KEY_REQUIREMENT_COMPLETE, true);
            result.put(KEY_REASON, "达到最大追问轮数，强制进入计划阶段");
            result.put(KEY_FOLLOW_UP_QUESTION, "");
            result.put(KEY_REVIEW_ROUND, round);
            result.put(KEY_IS_REENTRY, false);
            result.put(KEY_HISTORY, appendHistory(state, "assistant",
                    "[review] 达到最大轮数，强制进入计划阶段"));
            return result;
        }

        ReviewResult reviewResult;
        if (isReentry) {
            // 用户补充了新信息，让 LLM 修改需求文档并判断完整度
            String newInput = state.value(KEY_INPUT, String.class).orElse("");
            reviewResult = reviewWithNewInput(state, docContent, newInput);
            // 将修改后的需求文档写回文件
            if (reviewResult.updatedDoc != null && !reviewResult.updatedDoc.isBlank()) {
                writeFile(Path.of(docPath), reviewResult.updatedDoc);
                log.info("[review_node] 需求文档已更新（第 {} 轮）", round);
                // 更新 GraphExecution 中的需求文档路径
                saveRequirementDocPath(threadId, docPath);
            }
        } else {
            // 首次审阅，仅判断现有文档完整度
            reviewResult = reviewFirstTime(state, docContent);
        }

        Map<String, Object> result = new HashMap<>();
        result.put(KEY_REQUIREMENT_COMPLETE, reviewResult.complete);
        result.put(KEY_REASON, reviewResult.reason);
        result.put(KEY_FOLLOW_UP_QUESTION, reviewResult.followUpQuestion);
        result.put(KEY_REVIEW_ROUND, round);
        result.put(KEY_IS_REENTRY, false); // 重置标志
        result.put(KEY_HISTORY, appendHistory(state, "assistant",
                String.format("[review] %s | 原因: %s",
                        reviewResult.complete ? "COMPLETE" : "INCOMPLETE",
                        reviewResult.reason)));
        log.info("[review_node] 第 {} 轮审阅(reentry={})，完整度: {}, 原因: {}",
                round, isReentry, reviewResult.complete ? "COMPLETE" : "INCOMPLETE", reviewResult.reason);
        return result;
    }

    /**
     * 首次审阅：仅判断现有需求文档是否完整。
     * <p>
     * 使用非流式输出，获取格式化 JSON 后提取 followUpQuestion，只向前端传回 followUpQuestion。
     * 同时将需求文档发送给前端实时渲染。
     */
    private ReviewResult reviewFirstTime(OverAllState state, String docContent) {
        String prompt = String.format("""
                请审阅以下需求文档，判断需求是否已经足够完整、清晰，可以进入计划制定阶段。
                必须返回且仅返回如下 JSON 格式，不要包含任何额外文本：
                {
                  "complete": true or false,
                  "reason": "判断原因",
                  "followUpQuestion": "若需求不完整，请给出追问用户的具体问题；若完整则填空字符串"
                }

                需求文档：
                %s
                """, docContent);

        // 从 state 获取 conversationId 和 agentId
        String conversationId = state.value(KEY_CONVERSATION_ID, String.class).orElse("unknown");
        String agentId = state.value(KEY_AGENT_ID, String.class).orElse("unknown");

        // 发送需求文档到前端实时渲染
        websocketService.sendRequirementDocUpdate(conversationId, agentId, docContent);

        // 使用非流式输出
        String reply = llmChat.chat(prompt);
        log.info("[review_node] 审阅完成，长度: {}", reply.length());

        // 解析 JSON
        ReviewResult reviewResult = parseReviewJson(reply);

        // 如果有 followUpQuestion，保存到数据库并发送给前端
        if (!reviewResult.complete && reviewResult.followUpQuestion != null
                && !reviewResult.followUpQuestion.isBlank()) {
            // 保存助手消息到数据库
            saveAssistantMessage(conversationId, agentId, reviewResult.followUpQuestion);
            // 发送给前端
            websocketService.sendStreamComplete(conversationId, agentId, reviewResult.followUpQuestion);
        }

        return reviewResult;
    }

    /**
     * 重新进入审阅：用户补充了新信息，让 LLM 修改需求文档并判断完整度。
     * LLM 返回的 JSON 包含 {@code updatedDoc} 字段。
     * <p>
     * 使用非流式输出，获取格式化 JSON 后提取 followUpQuestion，只向前端传回 followUpQuestion。
     * 如果 LLM 修改了需求文档，会将更新后的文档发送给前端实时渲染。
     */
    private ReviewResult reviewWithNewInput(OverAllState state, String docContent, String newInput) {
        String prompt = String.format("""
                以下是一份已有的需求文档，用户补充了新的信息。请根据新信息修改完善需求文档，
                然后判断修改后的需求是否已足够完整、可以进入计划制定阶段。

                必须返回且仅返回如下 JSON 格式，不要包含任何额外文本：
                {
                  "complete": true or false,
                  "reason": "判断原因",
                  "followUpQuestion": "若需求不完整，请给出追问用户的具体问题；若完整则填空字符串",
                  "updatedDoc": "修改后的完整需求文档内容（Markdown 格式）"
                }

                原需求文档：
                %s

                用户补充的新信息：
                %s
                """, docContent, newInput);

        // 从 state 获取 conversationId 和 agentId
        String conversationId = state.value(KEY_CONVERSATION_ID, String.class).orElse("unknown");
        String agentId = state.value(KEY_AGENT_ID, String.class).orElse("unknown");

        // 使用非流式输出
        String reply = llmChat.chat(prompt);
        log.info("[review_node] 审阅完成（含用户补充），长度: {}", reply.length());

        // 解析 JSON
        ReviewResult reviewResult = parseReviewJsonWithDoc(reply);

        // 如果 LLM 修改了需求文档，发送更新后的文档到前端
        if (reviewResult.updatedDoc != null && !reviewResult.updatedDoc.isBlank()) {
            websocketService.sendRequirementDocUpdate(conversationId, agentId, reviewResult.updatedDoc);
            log.info("[review_node] 需求文档已更新并发送到前端");
        }

        // 如果有 followUpQuestion，保存到数据库并发送给前端
        if (!reviewResult.complete && reviewResult.followUpQuestion != null
                && !reviewResult.followUpQuestion.isBlank()) {
            // 保存助手消息到数据库
            saveAssistantMessage(conversationId, agentId, reviewResult.followUpQuestion);
            // 发送给前端
            websocketService.sendStreamComplete(conversationId, agentId, reviewResult.followUpQuestion);
        }

        return reviewResult;
    }

    /**
     * 发送流式 token 到 WebSocket。
     */
    private void sendStreamToken(String conversationId, String agentId, String token, String accumulated) {
        try {
            websocketService.sendStreamToken(conversationId, agentId, token, accumulated);
        } catch (Exception e) {
            log.warn("[review_node] 发送流式 token 失败", e);
        }
    }

    /**
     * 保存助手消息到数据库。
     * <p>
     * 通过 Spring 上下文获取 MessageService，避免循环依赖。
     */
    private void saveAssistantMessage(String conversationId, String agentId, String content) {
        try {
            com.fateironist.jawf.service.MessageService messageService = getMessageService();
            if (messageService != null) {
                messageService.saveAssistantMessage(conversationId, agentId, content);
                log.debug("[IntentRecognitionGraph] 保存助手消息: conversation={}", conversationId);
            }
        } catch (Exception e) {
            log.warn("[IntentRecognitionGraph] 保存助手消息失败", e);
        }
    }

    /**
     * 保存用户消息到数据库。
     * <p>
     * 通过 Spring 上下文获取 MessageService，避免循环依赖。
     */
    private void saveUserMessage(String conversationId, String agentId, String content) {
        try {
            com.fateironist.jawf.service.MessageService messageService = getMessageService();
            if (messageService != null) {
                messageService.saveUserMessage(conversationId, agentId, content);
                log.debug("[IntentRecognitionGraph] 保存用户消息: conversation={}", conversationId);
            }
        } catch (Exception e) {
            log.warn("[IntentRecognitionGraph] 保存用户消息失败", e);
        }
    }

    /**
     * 获取 MessageService。
     */
    private com.fateironist.jawf.service.MessageService getMessageService() {
        org.springframework.context.ApplicationContext ctx = org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext();
        if (ctx == null) {
            ctx = staticApplicationContext;
        }
        if (ctx != null) {
            return ctx.getBean(com.fateironist.jawf.service.MessageService.class);
        }
        log.warn("[IntentRecognitionGraph] 无法获取 ApplicationContext");
        return null;
    }

    /**
     * 更新 Graph 执行状态。
     * <p>
     * 通过 GraphExecutionService 更新数据库状态并通知前端。
     */
    private void updateExecutionStatus(OverAllState state,
                                        com.fateironist.jawf.model.GraphExecution.Status status,
                                        String currentNode) {
        try {
            String threadId = state.value(KEY_THREAD_ID, String.class).orElse(null);
            if (threadId == null) {
                log.debug("[IntentRecognitionGraph] 无 threadId，跳过状态更新");
                return;
            }
            org.springframework.context.ApplicationContext ctx = org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext();
            if (ctx == null) {
                ctx = staticApplicationContext;
            }
            if (ctx != null) {
                com.fateironist.jawf.service.GraphExecutionService executionService =
                        ctx.getBean(com.fateironist.jawf.service.GraphExecutionService.class);
                executionService.updateStatusAndNotify(threadId, status, currentNode);
            }
        } catch (Exception e) {
            log.warn("[IntentRecognitionGraph] 更新执行状态失败", e);
        }
    }

    /**
     * 记录 Graph 执行错误。
     */
    private void recordExecutionError(OverAllState state, String errorMessage) {
        try {
            String threadId = state.value(KEY_THREAD_ID, String.class).orElse(null);
            if (threadId == null) {
                log.debug("[IntentRecognitionGraph] 无 threadId，跳过错误记录");
                return;
            }
            org.springframework.context.ApplicationContext ctx = org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext();
            if (ctx == null) {
                ctx = staticApplicationContext;
            }
            if (ctx != null) {
                com.fateironist.jawf.service.GraphExecutionService executionService =
                        ctx.getBean(com.fateironist.jawf.service.GraphExecutionService.class);
                executionService.recordError(threadId, errorMessage);
            }
        } catch (Exception e) {
            log.warn("[IntentRecognitionGraph] 记录执行错误失败", e);
        }
    }

    /**
     * 保存需求文档路径到 GraphExecution。
     */
    private void saveRequirementDocPath(String threadId, String docPath) {
        try {
            if (threadId == null) {
                log.debug("[IntentRecognitionGraph] 无 threadId，跳过保存需求文档路径");
                return;
            }
            org.springframework.context.ApplicationContext ctx = org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext();
            if (ctx == null) {
                ctx = staticApplicationContext;
            }
            if (ctx != null) {
                com.fateironist.jawf.service.GraphExecutionService executionService =
                        ctx.getBean(com.fateironist.jawf.service.GraphExecutionService.class);
                executionService.updateRequirementDocPath(threadId, docPath);
            }
        } catch (Exception e) {
            log.warn("[IntentRecognitionGraph] 保存需求文档路径失败", e);
        }
    }

    /**
     * 保存计划 JSON 到 GraphExecution。
     */
    private void savePlanJson(String threadId, String planJson) {
        try {
            if (threadId == null) {
                log.debug("[IntentRecognitionGraph] 无 threadId，跳过保存计划 JSON");
                return;
            }
            org.springframework.context.ApplicationContext ctx = org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext();
            if (ctx == null) {
                ctx = staticApplicationContext;
            }
            if (ctx != null) {
                com.fateironist.jawf.service.GraphExecutionService executionService =
                        ctx.getBean(com.fateironist.jawf.service.GraphExecutionService.class);
                executionService.updatePlanJson(threadId, planJson);
            }
        } catch (Exception e) {
            log.warn("[IntentRecognitionGraph] 保存计划 JSON 失败", e);
        }
    }

    /**
     * 保存计划 JSON 文件路径到 GraphExecution。
     */
    private void savePlanJsonPath(String threadId, String planJsonPath) {
        try {
            if (threadId == null) {
                log.debug("[IntentRecognitionGraph] 无 threadId，跳过保存计划 JSON 路径");
                return;
            }
            org.springframework.context.ApplicationContext ctx = org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext();
            if (ctx == null) {
                ctx = staticApplicationContext;
            }
            if (ctx != null) {
                com.fateironist.jawf.service.GraphExecutionService executionService =
                        ctx.getBean(com.fateironist.jawf.service.GraphExecutionService.class);
                executionService.updatePlanJsonPath(threadId, planJsonPath);
            }
        } catch (Exception e) {
            log.warn("[IntentRecognitionGraph] 保存计划 JSON 路径失败", e);
        }
    }

    /** 静态持有的 ApplicationContext，由 Spring 初始化时设置 */
    private static org.springframework.context.ApplicationContext staticApplicationContext;

    @org.springframework.beans.factory.annotation.Autowired
    public void setApplicationContext(org.springframework.context.ApplicationContext ctx) {
        staticApplicationContext = ctx;
    }

    private Map<String, Object> followUpNode(OverAllState state) {
        log.info("[followup_node] 追问节点");

        String question = state.value(KEY_FOLLOW_UP_QUESTION, String.class)
                .orElse("能否补充更多细节，以便我准确理解您的需求？");
        int round = state.value(KEY_REVIEW_ROUND, Integer.class).orElse(0);
        String conversationId = state.value(KEY_CONVERSATION_ID, String.class).orElse("unknown");
        String agentId = state.value(KEY_AGENT_ID, String.class).orElse("unknown");

        log.info("[followup_node] 第 {} 轮追问: {}", round, question);

        // 追问消息已由 review_node 保存并发送，这里不需要重复操作

        Map<String, Object> result = new HashMap<>();
        result.put(KEY_ANSWER, question);
        result.put(KEY_REVIEW_ROUND, round + 1);
        result.put(KEY_IS_REENTRY, true); // 标记下次进入 review_node 为重新进入
        result.put(KEY_HISTORY, appendHistory(state, "assistant", "[追问] " + question));
        return result;
    }

    private Map<String, Object> planNode(OverAllState state) {
        log.info("[plan_node] 计划节点 - 生成工作流图 JSON");

        // 更新执行状态
        updateExecutionStatus(state, com.fateironist.jawf.model.GraphExecution.Status.WORKFLOW_GENERATION, "plan_node");

        // 从 state 获取需求文档路径，实时读取文件内容
        String docPath = state.value(KEY_REQUIREMENT_DOC_PATH, String.class).orElse("");
        String docContent = readFile(Path.of(docPath));
        int round = state.value(KEY_GRAPH_GENERATION_ROUND, Integer.class).orElse(0);
        String threadId = state.value(KEY_THREAD_ID, String.class).orElse(null);

        // 读取提示词模板
        String promptTemplate = readFile(Path.of("src/main/resources/static/prompt/plan/planGraph.md"));

        // 构建完整提示词
        String prompt = String.format("""
                %s

                ---
                用户需求文档：
                %s
                """, promptTemplate, docContent);

        // 达到最大生成轮数，跳过
        if (round >= MAX_GRAPH_GENERATION_ROUNDS) {
            log.warn("[plan_node] 已达最大生成轮数 {}，跳过图生成", MAX_GRAPH_GENERATION_ROUNDS);
            Map<String, Object> result = new HashMap<>();
            result.put(KEY_GRAPH_GENERATION_ROUND, round);
            result.put(KEY_HISTORY, appendHistory(state, "assistant",
                    "[plan] 达到最大生成轮数，跳过图生成"));
            return result;
        }

        // 调用 LLM 生成图 JSON
        String graphJson = llmChat.chat(prompt);
        graphJson = sanitizeJson(graphJson);
        log.info("[plan_node] LLM 生成的图 JSON 长度: {}", graphJson.length());

        // 尝试反序列化和校验
        String validationError = null;
        try {
            Workflow workflow = WorkflowJsonDeserializer.deserialize(graphJson);
            WorkflowValidator.validate(workflow);
            log.info("[plan_node] 图 JSON 校验通过");
        } catch (Exception e) {
            validationError = e.getMessage();
            log.warn("[plan_node] 图 JSON 校验失败: {}", validationError);
        }

        // 如果校验失败且未超过最大轮数，重新生成
        if (validationError != null && round < MAX_GRAPH_GENERATION_ROUNDS - 1) {
            log.info("[plan_node] 重新生成图 JSON (第 {} 轮)", round + 1);
            Map<String, Object> result = new HashMap<>();
            result.put(KEY_GRAPH_GENERATION_ROUND, round + 1);
            result.put(KEY_IS_REENTRY, true);
            result.put(KEY_HISTORY, appendHistory(state, "assistant",
                    "[plan] 图 JSON 校验失败，重新生成: " + validationError));
            return result;
        }

        // 存储生成的图 JSON 到文件
        Path graphPath = getTmpPath("plans").resolve("plan_" + threadId + ".json");
        writeFile(graphPath, graphJson);
        log.info("[plan_node] 图 JSON 已保存到文件: {}", graphPath);

        // 保存计划 JSON 到 GraphExecution（同时保存文件路径）
        savePlanJson(threadId, graphJson);
        savePlanJsonPath(threadId, graphPath.toString());

        // 只存储文件路径到 state，不存储完整 JSON
        Map<String, Object> result = new HashMap<>();
        result.put(KEY_PLAN_JSON_PATH, graphPath.toString());
        result.put(KEY_GRAPH_GENERATION_ROUND, round);
        result.put(KEY_IS_REENTRY, false);
        // 清除之前的校验错误（首次生成）
        result.put(KEY_GRAPH_VALID, null);
        result.put(KEY_GRAPH_VALIDATION_ERROR, "");
        result.put(KEY_GRAPH_VALIDATION_ROUND, 0);
        result.put(KEY_HISTORY, appendHistory(state, "assistant",
                "[plan] 工作流图 JSON 已生成，等待用户确认"));

        log.info("[plan_node] 工作流图 JSON 已生成并存储");
        return result;
    }

    /**
     * 用户确认/修改图 JSON 节点。
     * <p>
     * 此节点执行后中断，等待用户确认或修改图 JSON。
     * <ul>
     *   <li>首次从 plan_node 进入：展示生成的图 JSON，无校验错误。</li>
     *   <li>从 validate_graph_node 校验失败后进入：展示图 JSON 和校验错误信息。</li>
     * </ul>
     * 用户确认后，resume 时会将修改后的图 JSON 写入 state 的 {@link #KEY_WORKFLOW_GRAPH_JSON}。
     */
    private Map<String, Object> confirmGraphNode(OverAllState state) {
        log.info("[confirm_graph_node] 用户确认/修改图 JSON");

        // 更新执行状态
        updateExecutionStatus(state, com.fateironist.jawf.model.GraphExecution.Status.WORKFLOW_CONFIRMATION, "confirm_graph_node");

        // 从文件路径实时读取图 JSON
        String planJsonPath = state.value(KEY_PLAN_JSON_PATH, String.class).orElse("");
        String graphJson = readFile(Path.of(planJsonPath));
        String validationError = state.value(KEY_GRAPH_VALIDATION_ERROR, String.class).orElse("");
        int validationRound = state.value(KEY_GRAPH_VALIDATION_ROUND, Integer.class).orElse(0);

        Map<String, Object> result = new HashMap<>();
        result.put(KEY_WORKFLOW_GRAPH_JSON, graphJson);
        result.put(KEY_GRAPH_VALIDATION_ERROR, validationError);
        result.put(KEY_GRAPH_VALIDATION_ROUND, validationRound);

        if (validationError.isBlank()) {
            result.put(KEY_HISTORY, appendHistory(state, "assistant",
                    "[confirm] 工作流图 JSON 已生成，请确认或修改"));
        } else {
            result.put(KEY_HISTORY, appendHistory(state, "assistant",
                    "[confirm] 图 JSON 校验失败: " + validationError + "，请修改后重新提交"));
        }

        log.info("[confirm_graph_node] 等待用户确认，校验错误: {}",
                validationError.isBlank() ? "无" : validationError);
        return result;
    }

    /**
     * 校验图 JSON 合法性节点。
     * <p>
     * 读取 state 中的图 JSON，进行校验：
     * - 若合法，进入执行节点
     * - 若不合法，将错误信息存入 state，回到 confirm_graph_node 等待用户修改
     */
    private Map<String, Object> validateGraphNode(OverAllState state) {
        log.info("[validate_graph_node] 校验图 JSON 合法性");

        // 更新执行状态
        updateExecutionStatus(state, com.fateironist.jawf.model.GraphExecution.Status.WORKFLOW_VALIDATION, "validate_graph_node");

        String graphJson = state.value(KEY_WORKFLOW_GRAPH_JSON, String.class).orElse("");
        int round = state.value(KEY_GRAPH_VALIDATION_ROUND, Integer.class).orElse(0);

        if (graphJson.isBlank()) {
            log.warn("[validate_graph_node] 图 JSON 为空");
            Map<String, Object> result = new HashMap<>();
            result.put(KEY_GRAPH_VALID, false);
            result.put(KEY_GRAPH_VALIDATION_ERROR, "图 JSON 为空，请提供有效的工作流图");
            result.put(KEY_GRAPH_VALIDATION_ROUND, round + 1);
            result.put(KEY_HISTORY, appendHistory(state, "assistant",
                    "[validate] 图 JSON 为空"));
            return result;
        }

        // 校验图 JSON
        String validationError = null;
        try {
            Workflow workflow = WorkflowJsonDeserializer.deserialize(graphJson);
            WorkflowValidator.validate(workflow);
            log.info("[validate_graph_node] 图 JSON 校验通过");
        } catch (Exception e) {
            validationError = e.getMessage();
            log.warn("[validate_graph_node] 图 JSON 校验失败: {}", validationError);
        }

        Map<String, Object> result = new HashMap<>();
        result.put(KEY_GRAPH_VALID, validationError == null);
        result.put(KEY_GRAPH_VALIDATION_ERROR, validationError != null ? validationError : "");
        result.put(KEY_GRAPH_VALIDATION_ROUND, round + 1);
        result.put(KEY_IS_REENTRY, false);

        if (validationError == null) {
            result.put(KEY_HISTORY, appendHistory(state, "assistant",
                    "[validate] 图 JSON 校验通过，准备执行"));
        } else {
            // 校验失败，错误信息存入 state，由路由回到 confirm_graph_node
            result.put(KEY_HISTORY, appendHistory(state, "assistant",
                    "[validate] 图 JSON 校验失败: " + validationError));
        }

        return result;
    }

    private Map<String, Object> executeNode(OverAllState state) {
        // 从文件路径实时读取图 JSON（获取最新版本）
        String planJsonPath = state.value(KEY_PLAN_JSON_PATH, String.class).orElse("");
        String graphJson = readFile(Path.of(planJsonPath));
        String conversationId = state.value(KEY_CONVERSATION_ID, String.class).orElse("unknown");
        String agentId = state.value(KEY_AGENT_ID, String.class).orElse("unknown");
        log.info("[execute_node] 执行工作流图，文件路径: {}", planJsonPath);

        // 更新执行状态
        updateExecutionStatus(state, com.fateironist.jawf.model.GraphExecution.Status.WORKFLOW_EXECUTION, "execute_node");

        try {
            // 反序列化并编译工作流
            Workflow workflow = WorkflowJsonDeserializer.deserialize(graphJson);
            workflowEngine.compile(workflow);

            // 执行工作流
            Map<String, Object> inputs = new HashMap<>();
            // 从 state 中提取输入参数
            String userInput = state.value(KEY_INPUT, String.class).orElse("");
            inputs.put("start.userQuery", userInput);

            WorkflowEngine.WorkflowResult workflowResult = workflowEngine.start(workflow.getId(), inputs);

            // 获取执行结果
            String resultText = workflowResult.outputs().toString();

            // 使用 LLM 生成执行总结
            String summaryPrompt = String.format("""
                    请根据以下工作流执行结果，为用户生成一份简洁友好的执行总结报告。
                    要求：
                    1. 用简洁明了的语言描述执行了什么
                    2. 列出关键的执行步骤和结果
                    3. 如果有生成的文件或输出，明确告知用户在哪里
                    4. 如果有任何问题或注意事项，也要提及

                    工作流名称：%s
                    工作流描述：%s
                    执行结果：
                    %s
                    """, workflow.getName(), workflow.getDescription(), resultText);

            String summary = llmChat.chat(summaryPrompt);
            log.info("[execute_node] LLM 生成执行总结，长度: {}", summary.length());

            // 更新执行状态为完成
            updateExecutionStatus(state, com.fateironist.jawf.model.GraphExecution.Status.COMPLETED, "end");

            // 保存 LLM 总结到数据库
            saveAssistantMessage(conversationId, agentId, summary);

            // 发送 LLM 总结到前端
            websocketService.sendStreamComplete(conversationId, agentId, summary);

            Map<String, Object> result = new HashMap<>();
            result.put(KEY_EXECUTION_RESULT, summary);
            result.put(KEY_HISTORY, appendHistory(state, "assistant",
                    "[执行] 工作流执行完成"));
            return result;

        } catch (Exception e) {
            log.error("[execute_node] 工作流执行失败", e);

            String errorMessage = "执行失败: " + e.getMessage();

            // 更新执行状态为失败
            recordExecutionError(state, errorMessage);

            // 保存错误消息到数据库
            saveAssistantMessage(conversationId, agentId, errorMessage);

            // 发送错误消息到前端
            websocketService.sendStreamComplete(conversationId, agentId, errorMessage);

            Map<String, Object> result = new HashMap<>();
            result.put(KEY_EXECUTION_RESULT, errorMessage);
            result.put(KEY_HISTORY, appendHistory(state, "assistant",
                    "[执行] " + errorMessage));
            return result;
        }
    }

    // ==================== 条件路由 ====================

    private String routeByIntent(OverAllState state) {
        return state.value(KEY_INTENT, String.class).orElse(INTENT_QA);
    }

    private String routeByReview(OverAllState state) {
        Boolean complete = state.value(KEY_REQUIREMENT_COMPLETE, Boolean.class).orElse(false);
        return Boolean.TRUE.equals(complete) ? REVIEW_COMPLETE : REVIEW_INCOMPLETE;
    }

    private String routeByGraphValidation(OverAllState state) {
        Boolean valid = state.value(KEY_GRAPH_VALID, Boolean.class).orElse(false);
        return Boolean.TRUE.equals(valid) ? GRAPH_VALID : GRAPH_INVALID;
    }

    // ==================== 聊天记录工具 ====================

    /**
     * 将新消息追加到 history 列表末尾，返回新列表。
     */
    @SuppressWarnings("unchecked")
    static List<Map<String, String>> appendHistory(OverAllState state, String role, String content) {
        List<Map<String, String>> history;
        try {
            Object raw = state.data().get(KEY_HISTORY);
            history = (raw instanceof List) ? (List<Map<String, String>>) raw : new ArrayList<>();
        } catch (Exception e) {
            history = new ArrayList<>();
        }
        // 防御性复制，避免修改 checkpoint 中的列表
        history = new ArrayList<>(history);
        history.add(Map.of("role", role, "content", content == null ? "" : content));
        return history;
    }

    // ==================== JSON 解析 ====================

    private ReviewResult parseReviewJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(sanitizeJson(json));
            boolean complete = node.path("complete").asBoolean(false);
            String reason = node.path("reason").asText("");
            String followUp = node.path("followUpQuestion").asText("");
            return new ReviewResult(complete, reason, followUp, null);
        } catch (IOException e) {
            log.warn("[review_node] JSON 解析失败，按不完整处理。原始回复: {}", json, e);
            return new ReviewResult(false, "JSON 解析失败", "能否补充更多细节？", null);
        }
    }

    /**
     * 解析包含 updatedDoc 字段的 JSON。
     */
    private ReviewResult parseReviewJsonWithDoc(String json) {
        try {
            JsonNode node = objectMapper.readTree(sanitizeJson(json));
            boolean complete = node.path("complete").asBoolean(false);
            String reason = node.path("reason").asText("");
            String followUp = node.path("followUpQuestion").asText("");
            String updatedDoc = node.path("updatedDoc").asText(null);
            return new ReviewResult(complete, reason, followUp, updatedDoc);
        } catch (IOException e) {
            log.warn("[review_node] JSON 解析失败，按不完整处理。原始回复: {}", json, e);
            return new ReviewResult(false, "JSON 解析失败", "能否补充更多细节？", null);
        }
    }

    private static String sanitizeJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        return s.trim();
    }

    // ==================== 文件工具 ====================

    private static Path getTmpPath(String fileName) {
        try {
            Path base = Path.of(System.getProperty("java.io.tmpdir"), "jawf_graph");
            Path path = (fileName == null || fileName.isBlank()) ? base : base.resolve(fileName);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            return path;
        } catch (IOException e) {
            throw new RuntimeException("创建临时目录失败", e);
        }
    }

    private static void writeFile(Path path, String content) {
        try {
            if (!Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("写入文件失败: " + path, e);
        }
    }

    private static String readFile(Path path) {
        try {
            if (path == null || !Files.exists(path)) {
                return "";
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[readFile] 读取文件失败: {}", path, e);
            return "";
        }
    }

    private static AsyncNodeAction node_async(NodeAction action) {
        return AsyncNodeAction.node_async(action);
    }

    private static AsyncEdgeAction edge_async(EdgeAction action) {
        return AsyncEdgeAction.edge_async(action);
    }

    private record ReviewResult(boolean complete, String reason, String followUpQuestion, String updatedDoc) {
    }
}

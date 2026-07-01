package com.fateironist.jawf.service;

import com.fateironist.jawf.mapper.GraphExecutionMapper;
import com.fateironist.jawf.model.GraphExecution;
import com.fateironist.jawf.model.GraphExecution.Status;
import com.fateironist.jawf.websocket.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Graph 执行实例服务。
 * <p>
 * 管理 Graph 执行状态的持久化和 WebSocket 通知。
 */
@Slf4j
@Service
public class GraphExecutionService {

    /** 状态变更事件类型 */
    public static final String TYPE_GRAPH_STATUS_CHANGED = "graph.status.changed";

    private final GraphExecutionMapper graphExecutionMapper;
    private final WebsocketService websocketService;

    public GraphExecutionService(GraphExecutionMapper graphExecutionMapper,
                                  WebsocketService websocketService) {
        this.graphExecutionMapper = graphExecutionMapper;
        this.websocketService = websocketService;
    }

    /**
     * 初始化表结构。
     */
    public void initTable() {
        graphExecutionMapper.createTable();
        // 尝试添加新列（如果表已存在但缺少该列）
        try {
            graphExecutionMapper.addRequirementDocPathColumn();
            log.info("[GraphExecutionService] 添加 requirement_doc_path 列");
        } catch (Exception e) {
            // 列已存在，忽略错误
            log.debug("[GraphExecutionService] requirement_doc_path 列已存在");
        }
        try {
            graphExecutionMapper.addPlanJsonColumn();
            log.info("[GraphExecutionService] 添加 plan_json 列");
        } catch (Exception e) {
            // 列已存在，忽略错误
            log.debug("[GraphExecutionService] plan_json 列已存在");
        }
        try {
            graphExecutionMapper.addPlanJsonPathColumn();
            log.info("[GraphExecutionService] 添加 plan_json_path 列");
        } catch (Exception e) {
            // 列已存在，忽略错误
            log.debug("[GraphExecutionService] plan_json_path 列已存在");
        }
        log.info("[GraphExecutionService] graph_executions 表初始化完成");
    }

    /**
     * 创建新的 Graph 执行实例。
     *
     * @param threadId       线程 ID
     * @param conversationId 会话 ID
     * @param agentId        Agent ID
     * @return 创建的执行实例
     */
    @Transactional
    public GraphExecution create(String threadId, String conversationId, String agentId) {
        GraphExecution execution = new GraphExecution();
        execution.setThreadId(threadId);
        execution.setConversationId(conversationId);
        execution.setAgentId(agentId);
        execution.setStatus(Status.INITIALIZING.getValue());
        execution.setCurrentNode("input_node");
        execution.setProgress(Status.INITIALIZING.getProgressValue());

        graphExecutionMapper.insert(execution);
        log.info("[GraphExecutionService] 创建执行实例: thread={}", threadId);

        // 通知前端
        notifyStatusChange(conversationId, agentId, execution);

        return execution;
    }

    /**
     * 更新执行状态。
     *
     * @param threadId    线程 ID
     * @param status      新状态
     * @param currentNode 当前节点名称
     */
    @Transactional
    public void updateStatus(String threadId, Status status, String currentNode) {
        graphExecutionMapper.updateStatus(threadId, status.getValue(), currentNode, status.getProgressValue());
        log.debug("[GraphExecutionService] 更新状态: thread={}, status={}, node={}",
                threadId, status.getValue(), currentNode);

        // 通知前端
        GraphExecution execution = graphExecutionMapper.selectByThreadId(threadId);
        if (execution != null) {
            notifyStatusChange(execution.getConversationId(), execution.getAgentId(), execution);
        }
    }

    /**
     * 更新执行状态（自动从 GraphExecution 获取 conversationId 和 agentId）。
     *
     * @param threadId    线程 ID
     * @param status      新状态
     * @param currentNode 当前节点名称
     */
    @Transactional
    public void updateStatusAndNotify(String threadId, Status status, String currentNode) {
        graphExecutionMapper.updateStatus(threadId, status.getValue(), currentNode, status.getProgressValue());

        // 通知前端
        GraphExecution execution = graphExecutionMapper.selectByThreadId(threadId);
        if (execution != null) {
            notifyStatusChange(execution.getConversationId(), execution.getAgentId(), execution);
        }
    }

    /**
     * 记录执行错误。
     *
     * @param threadId     线程 ID
     * @param errorMessage 错误信息
     */
    @Transactional
    public void recordError(String threadId, String errorMessage) {
        graphExecutionMapper.updateError(threadId, errorMessage);
        log.warn("[GraphExecutionService] 记录错误: thread={}, error={}", threadId, errorMessage);

        // 通知前端
        GraphExecution execution = graphExecutionMapper.selectByThreadId(threadId);
        if (execution != null) {
            notifyStatusChange(execution.getConversationId(), execution.getAgentId(), execution);
        }
    }

    /**
     * 更新扩展数据。
     *
     * @param threadId  线程 ID
     * @param extraData 扩展数据（JSON 格式）
     */
    @Transactional
    public void updateExtraData(String threadId, String extraData) {
        graphExecutionMapper.updateExtraData(threadId, extraData);
    }

    /**
     * 更新需求文档路径。
     *
     * @param threadId 线程 ID
     * @param docPath  需求文档路径
     */
    @Transactional
    public void updateRequirementDocPath(String threadId, String docPath) {
        graphExecutionMapper.updateRequirementDocPath(threadId, docPath);
        log.debug("[GraphExecutionService] 更新需求文档路径: thread={}, path={}", threadId, docPath);
    }

    /**
     * 更新计划 JSON 文件路径。
     *
     * @param threadId     线程 ID
     * @param planJsonPath 计划 JSON 文件路径
     */
    @Transactional
    public void updatePlanJsonPath(String threadId, String planJsonPath) {
        graphExecutionMapper.updatePlanJsonPath(threadId, planJsonPath);
        log.debug("[GraphExecutionService] 更新计划 JSON 路径: thread={}, path={}", threadId, planJsonPath);
    }

    /**
     * 更新计划 JSON。
     * <p>
     * 同时将 JSON 写入本地文件，并更新数据库中的路径和内容。
     *
     * @param threadId 线程 ID
     * @param planJson 计划 JSON（工作流图定义）
     */
    @Transactional
    public void updatePlanJson(String threadId, String planJson) {
        // 获取执行实例
        GraphExecution execution = graphExecutionMapper.selectByThreadId(threadId);
        if (execution == null) {
            log.warn("[GraphExecutionService] 更新计划 JSON 失败: 未找到执行实例 thread={}", threadId);
            return;
        }

        // 保存到本地文件
        String planJsonPath = execution.getPlanJsonPath();
        if (planJsonPath == null || planJsonPath.isBlank()) {
            // 首次保存，创建新文件
            Path planPath = getTmpPath("plans").resolve("plan_" + threadId + ".json");
            planJsonPath = planPath.toString();
            graphExecutionMapper.updatePlanJsonPath(threadId, planJsonPath);
        }

        try {
            Path filePath = Path.of(planJsonPath);
            if (!Files.exists(filePath.getParent())) {
                Files.createDirectories(filePath.getParent());
            }
            Files.writeString(filePath, planJson, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("[GraphExecutionService] 计划 JSON 已保存到文件: {}", planJsonPath);
        } catch (IOException e) {
            log.error("[GraphExecutionService] 保存计划 JSON 到文件失败: {}", planJsonPath, e);
        }

        // 更新数据库
        graphExecutionMapper.updatePlanJson(threadId, planJson);
        log.debug("[GraphExecutionService] 更新计划 JSON: thread={}, length={}", threadId,
                planJson != null ? planJson.length() : 0);

        // 通知前端计划 JSON 已更新
        notifyStatusChange(execution.getConversationId(), execution.getAgentId(), execution);
    }

    /**
     * 获取临时目录路径。
     */
    private static Path getTmpPath(String subDir) {
        Path base = Path.of(System.getProperty("java.io.tmpdir"), "jawf_graph");
        Path path = (subDir == null || subDir.isBlank()) ? base : base.resolve(subDir);
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            log.warn("[GraphExecutionService] 创建临时目录失败: {}", path, e);
        }
        return path;
    }

    /**
     * 根据 threadId 获取执行实例。
     */
    public GraphExecution getByThreadId(String threadId) {
        return graphExecutionMapper.selectByThreadId(threadId);
    }

    /**
     * 获取会话最新的执行实例。
     */
    public GraphExecution getLatestByConversationId(String conversationId) {
        return graphExecutionMapper.selectLatestByConversationId(conversationId);
    }

    /**
     * 获取 Agent 的所有执行实例。
     */
    public List<GraphExecution> getByAgentId(String agentId) {
        return graphExecutionMapper.selectByAgentId(agentId);
    }

    /**
     * 删除执行实例。
     */
    @Transactional
    public boolean delete(String threadId) {
        return graphExecutionMapper.deleteByThreadId(threadId) > 0;
    }

    /**
     * 删除会话的所有执行实例。
     */
    @Transactional
    public int deleteByConversationId(String conversationId) {
        return graphExecutionMapper.deleteByConversationId(conversationId);
    }

    /**
     * 通过 WebSocket 通知前端状态变更。
     */
    private void notifyStatusChange(String conversationId, String agentId, GraphExecution execution) {
        try {
            Event<GraphStatusEvent> event = new Event<>();
            event.setType(TYPE_GRAPH_STATUS_CHANGED);
            event.setData(new GraphStatusEvent(
                    execution.getThreadId(),
                    execution.getStatus(),
                    execution.getCurrentNode(),
                    execution.getProgress(),
                    execution.getErrorMessage()
            ));
            event.setConversationId(conversationId);
            event.setAgentId(agentId);

            websocketService.sendToConversation(conversationId, event);
        } catch (Exception e) {
            log.warn("[GraphExecutionService] 发送状态变更通知失败", e);
        }
    }

    /**
     * Graph 状态变更事件 DTO。
     */
    public record GraphStatusEvent(
            String threadId,
            String status,
            String currentNode,
            int progress,
            String errorMessage
    ) {}
}

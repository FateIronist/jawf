package com.fateironist.jawf.controller;

import com.fateironist.jawf.model.GraphExecution;
import com.fateironist.jawf.service.GraphExecutionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Graph 执行实例 REST 控制器。
 */
@Slf4j
@RestController
@RequestMapping("/api/graph-executions")
public class GraphExecutionController {

    private final GraphExecutionService graphExecutionService;

    public GraphExecutionController(GraphExecutionService graphExecutionService) {
        this.graphExecutionService = graphExecutionService;
    }

    /**
     * 获取会话最新的执行实例。
     */
    @GetMapping("/by-conversation/{conversationId}")
    public ResponseEntity<GraphExecution> getByConversationId(@PathVariable String conversationId) {
        GraphExecution execution = graphExecutionService.getLatestByConversationId(conversationId);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(execution);
    }

    /**
     * 获取执行实例。
     */
    @GetMapping("/{threadId}")
    public ResponseEntity<GraphExecution> getByThreadId(@PathVariable String threadId) {
        GraphExecution execution = graphExecutionService.getByThreadId(threadId);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(execution);
    }

    /**
     * 更新计划 JSON。
     */
    @PutMapping("/{threadId}/plan")
    public ResponseEntity<Void> updatePlanJson(
            @PathVariable String threadId,
            @RequestBody PlanJsonRequest request) {
        graphExecutionService.updatePlanJson(threadId, request.getPlanJson());
        return ResponseEntity.ok().build();
    }

    /**
     * 更新需求文档内容。
     */
    @PutMapping("/{threadId}/requirement-doc")
    public ResponseEntity<Void> updateRequirementDoc(
            @PathVariable String threadId,
            @RequestBody RequirementDocRequest request) {
        // 获取执行实例以获取文档路径
        GraphExecution execution = graphExecutionService.getByThreadId(threadId);
        if (execution == null || execution.getRequirementDocPath() == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            java.nio.file.Path docPath = java.nio.file.Path.of(execution.getRequirementDocPath());
            java.nio.file.Files.writeString(docPath, request.getContent(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("更新需求文档失败: threadId={}", threadId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Data
    public static class PlanJsonRequest {
        private String planJson;
    }

    @Data
    public static class RequirementDocRequest {
        private String content;
    }
}

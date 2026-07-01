package com.fateironist.jawf.controller;

import com.fateironist.jawf.model.Agent;
import com.fateironist.jawf.service.AgentService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent 相关 API。
 */
@Slf4j
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 获取所有启用的 Agent。
     */
    @GetMapping
    public ResponseEntity<List<Agent>> listAll() {
        return ResponseEntity.ok(agentService.listAllEnabled());
    }

    /**
     * 根据 agentId 获取 Agent。
     */
    @GetMapping("/{agentId}")
    public ResponseEntity<Agent> getByAgentId(@PathVariable String agentId) {
        Agent agent = agentService.getByAgentId(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(agent);
    }

    /**
     * 创建 Agent。
     */
    @PostMapping
    public ResponseEntity<Agent> create(@RequestBody CreateAgentRequest request) {
        Agent agent = new Agent();
        agent.setName(request.getName());
        agent.setDescription(request.getDescription());
        agent.setDefaultModel(request.getDefaultModel());
        agent.setSystemPrompt(request.getSystemPrompt());
        agent.setParallelEnabled(request.getParallelEnabled());
        agent.setMaxParallel(request.getMaxParallel());
        agent.setAgentType(request.getAgentType());

        Agent created = agentService.create(agent);
        return ResponseEntity.ok(created);
    }

    /**
     * 更新 Agent。
     */
    @PutMapping("/{agentId}")
    public ResponseEntity<Agent> update(@PathVariable String agentId,
                                         @RequestBody UpdateAgentRequest request) {
        Agent agent = agentService.getByAgentId(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }

        if (request.getName() != null) agent.setName(request.getName());
        if (request.getDescription() != null) agent.setDescription(request.getDescription());
        if (request.getDefaultModel() != null) agent.setDefaultModel(request.getDefaultModel());
        if (request.getSystemPrompt() != null) agent.setSystemPrompt(request.getSystemPrompt());
        if (request.getParallelEnabled() != null) agent.setParallelEnabled(request.getParallelEnabled());
        if (request.getMaxParallel() != null) agent.setMaxParallel(request.getMaxParallel());
        if (request.getAgentType() != null) agent.setAgentType(request.getAgentType());

        Agent updated = agentService.update(agent);
        return ResponseEntity.ok(updated);
    }

    /**
     * 删除 Agent。
     */
    @DeleteMapping("/{agentId}")
    public ResponseEntity<Void> delete(@PathVariable String agentId) {
        boolean deleted = agentService.delete(agentId);
        if (deleted) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 启用/禁用 Agent。
     */
    @PatchMapping("/{agentId}/enabled")
    public ResponseEntity<Void> setEnabled(@PathVariable String agentId,
                                            @RequestBody EnabledRequest request) {
        agentService.setEnabled(agentId, request.getEnabled());
        return ResponseEntity.ok().build();
    }

    // ==================== Request DTOs ====================

    @Data
    public static class CreateAgentRequest {
        private String name;
        private String description;
        private String defaultModel;
        private String systemPrompt;
        private Boolean parallelEnabled;
        private Integer maxParallel;
        private String agentType;
    }

    @Data
    public static class UpdateAgentRequest {
        private String name;
        private String description;
        private String defaultModel;
        private String systemPrompt;
        private Boolean parallelEnabled;
        private Integer maxParallel;
        private String agentType;
    }

    @Data
    public static class EnabledRequest {
        private Boolean enabled;
    }
}

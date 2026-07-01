package com.fateironist.jawf.service;

import com.fateironist.jawf.mapper.AgentMapper;
import com.fateironist.jawf.model.Agent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Agent 服务层。
 * <p>
 * 提供 Agent 的业务操作，包括创建、查询、更新、删除等。
 */
@Slf4j
@Service
public class AgentService {

    private final AgentMapper agentMapper;

    public AgentService(AgentMapper agentMapper) {
        this.agentMapper = agentMapper;
    }

    /**
     * 初始化表结构。
     */
    public void initTable() {
        agentMapper.createTable();
        log.info("[AgentService] agents 表初始化完成");
    }

    /**
     * 创建新的 Agent。
     *
     * @param agent Agent 信息（agentId 可为空，会自动生成 UUID）
     * @return 创建后的 Agent（包含生成的 id 和 agentId）
     */
    @Transactional
    public Agent create(Agent agent) {
        // 自动生成 agentId
        if (agent.getAgentId() == null || agent.getAgentId().isBlank()) {
            agent.setAgentId(UUID.randomUUID().toString());
        }

        // 设置默认值
        if (agent.getParallelEnabled() == null) {
            agent.setParallelEnabled(false);
        }
        if (agent.getMaxParallel() == null) {
            agent.setMaxParallel(1);
        }
        if (agent.getMaxRetry() == null) {
            agent.setMaxRetry(3);
        }
        if (agent.getTimeoutSeconds() == null) {
            agent.setTimeoutSeconds(60);
        }
        if (agent.getAgentType() == null || agent.getAgentType().isBlank()) {
            agent.setAgentType("llm");
        }
        if (agent.getEnabled() == null) {
            agent.setEnabled(true);
        }

        agentMapper.insert(agent);
        log.info("[AgentService] 创建 Agent: {} ({})", agent.getName(), agent.getAgentId());
        return agent;
    }

    /**
     * 根据 agentId 查询 Agent。
     */
    public Agent getByAgentId(String agentId) {
        return agentMapper.selectByAgentId(agentId);
    }

    /**
     * 根据 id 查询 Agent。
     */
    public Agent getById(Long id) {
        return agentMapper.selectById(id);
    }

    /**
     * 根据名称查询 Agent。
     */
    public Agent getByName(String name) {
        return agentMapper.selectByName(name);
    }

    /**
     * 获取所有启用的 Agent。
     */
    public List<Agent> listAllEnabled() {
        return agentMapper.selectAllEnabled();
    }

    /**
     * 获取所有 Agent。
     */
    public List<Agent> listAll() {
        return agentMapper.selectAll();
    }

    /**
     * 根据类型查询启用的 Agent。
     */
    public List<Agent> listByType(String agentType) {
        return agentMapper.selectByType(agentType);
    }

    /**
     * 根据模型查询启用的 Agent。
     */
    public List<Agent> listByModel(String model) {
        return agentMapper.selectByModel(model);
    }

    /**
     * 更新 Agent 信息。
     */
    @Transactional
    public Agent update(Agent agent) {
        int rows = agentMapper.update(agent);
        if (rows == 0) {
            throw new RuntimeException("Agent 不存在: " + agent.getAgentId());
        }
        log.info("[AgentService] 更新 Agent: {} ({})", agent.getName(), agent.getAgentId());
        return agent;
    }

    /**
     * 启用/禁用 Agent。
     */
    @Transactional
    public void setEnabled(String agentId, boolean enabled) {
        agentMapper.updateEnabled(agentId, enabled);
        log.info("[AgentService] {} Agent: {}", enabled ? "启用" : "禁用", agentId);
    }

    /**
     * 更新 Agent 默认模型。
     */
    @Transactional
    public void updateModel(String agentId, String model) {
        agentMapper.updateModel(agentId, model);
        log.info("[AgentService] 更新 Agent {} 模型为: {}", agentId, model);
    }

    /**
     * 删除 Agent。
     */
    @Transactional
    public boolean delete(String agentId) {
        int rows = agentMapper.deleteByAgentId(agentId);
        if (rows > 0) {
            log.info("[AgentService] 删除 Agent: {}", agentId);
            return true;
        }
        return false;
    }

    /**
     * 获取启用的 Agent 数量。
     */
    public int countEnabled() {
        return agentMapper.countEnabled();
    }

    /**
     * 获取指定类型的 Agent 数量。
     */
    public int countByType(String agentType) {
        return agentMapper.countByType(agentType);
    }
}

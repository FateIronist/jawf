package com.fateironist.jawf.mapper;

import com.fateironist.jawf.model.Agent;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Agent 数据访问层。
 * <p>
 * 提供 Agent 实体的 CRUD 操作，使用 MyBatis 注解方式定义 SQL。
 */
@Mapper
public interface AgentMapper {

    // ==================== 表管理 ====================

    @Update("DROP TABLE IF EXISTS agents")
    void dropTable();

    @Update("""
            CREATE TABLE IF NOT EXISTS agents (
                id                INTEGER PRIMARY KEY AUTOINCREMENT,
                agent_id          TEXT    NOT NULL UNIQUE,
                name              TEXT    NOT NULL,
                description       TEXT,
                default_model     TEXT    NOT NULL,
                system_prompt     TEXT,
                parallel_enabled  INTEGER NOT NULL DEFAULT 0,
                max_parallel      INTEGER DEFAULT 1,
                max_retry         INTEGER DEFAULT 3,
                timeout_seconds   INTEGER DEFAULT 60,
                agent_type        TEXT    NOT NULL DEFAULT 'llm',
                config_json       TEXT,
                created_at        TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
                updated_at        TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
                enabled           INTEGER NOT NULL DEFAULT 1
            )
            """)
    void createTable();

    // ==================== 插入 ====================

    @Insert("""
            INSERT INTO agents (agent_id, name, description, default_model, system_prompt,
                                parallel_enabled, max_parallel, max_retry, timeout_seconds,
                                agent_type, config_json, enabled)
            VALUES (#{agentId}, #{name}, #{description}, #{defaultModel}, #{systemPrompt},
                    #{parallelEnabled}, #{maxParallel}, #{maxRetry}, #{timeoutSeconds},
                    #{agentType}, #{configJson}, #{enabled})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Agent agent);

    // ==================== 查询 ====================

    @Select("SELECT * FROM agents WHERE id = #{id}")
    Agent selectById(Long id);

    @Select("SELECT * FROM agents WHERE agent_id = #{agentId}")
    Agent selectByAgentId(String agentId);

    @Select("SELECT * FROM agents WHERE name = #{name}")
    Agent selectByName(String name);

    @Select("SELECT * FROM agents WHERE agent_type = #{agentType} AND enabled = 1 ORDER BY name")
    List<Agent> selectByType(String agentType);

    @Select("SELECT * FROM agents WHERE enabled = 1 ORDER BY name")
    List<Agent> selectAllEnabled();

    @Select("SELECT * FROM agents ORDER BY id")
    List<Agent> selectAll();

    @Select("SELECT * FROM agents WHERE default_model = #{model} AND enabled = 1")
    List<Agent> selectByModel(String model);

    // ==================== 更新 ====================

    @Update("""
            UPDATE agents
            SET name = #{name}, description = #{description}, default_model = #{defaultModel},
                system_prompt = #{systemPrompt}, parallel_enabled = #{parallelEnabled},
                max_parallel = #{maxParallel}, max_retry = #{maxRetry},
                timeout_seconds = #{timeoutSeconds}, agent_type = #{agentType},
                config_json = #{configJson}, enabled = #{enabled},
                updated_at = datetime('now', 'localtime')
            WHERE id = #{id}
            """)
    int update(Agent agent);

    @Update("UPDATE agents SET enabled = #{enabled}, updated_at = datetime('now', 'localtime') WHERE agent_id = #{agentId}")
    int updateEnabled(@Param("agentId") String agentId, @Param("enabled") Boolean enabled);

    @Update("UPDATE agents SET default_model = #{model}, updated_at = datetime('now', 'localtime') WHERE agent_id = #{agentId}")
    int updateModel(@Param("agentId") String agentId, @Param("model") String model);

    // ==================== 删除 ====================

    @Delete("DELETE FROM agents WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("DELETE FROM agents WHERE agent_id = #{agentId}")
    int deleteByAgentId(String agentId);

    @Delete("DELETE FROM agents")
    int deleteAll();

    // ==================== 统计 ====================

    @Select("SELECT COUNT(*) FROM agents WHERE enabled = 1")
    int countEnabled();

    @Select("SELECT COUNT(*) FROM agents WHERE agent_type = #{agentType} AND enabled = 1")
    int countByType(String agentType);
}

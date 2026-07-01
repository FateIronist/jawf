package com.fateironist.jawf.mapper;

import com.fateironist.jawf.model.GraphExecution;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Graph 执行实例数据访问层。
 */
@Mapper
public interface GraphExecutionMapper {

    // ==================== 表管理 ====================

    @Update("DROP TABLE IF EXISTS graph_executions")
    void dropTable();

    @Update("""
            CREATE TABLE IF NOT EXISTS graph_executions (
                id                   INTEGER PRIMARY KEY AUTOINCREMENT,
                thread_id            TEXT    NOT NULL UNIQUE,
                conversation_id      TEXT    NOT NULL,
                agent_id             TEXT    NOT NULL,
                status               TEXT    NOT NULL DEFAULT 'initializing',
                current_node         TEXT,
                progress             INTEGER NOT NULL DEFAULT 0,
                error_message        TEXT,
                requirement_doc_path TEXT,
                plan_json_path       TEXT,
                plan_json            TEXT,
                extra_data           TEXT,
                created_at           TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
                updated_at           TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
            )
            """)
    void createTable();

    /**
     * 添加 requirement_doc_path 列（如果不存在）。
     * <p>
     * SQLite 不支持 ALTER TABLE ADD COLUMN IF NOT EXISTS，
     * 所以通过捕获异常来处理列已存在的情况。
     */
    @Update("ALTER TABLE graph_executions ADD COLUMN requirement_doc_path TEXT")
    void addRequirementDocPathColumn();

    /**
     * 添加 plan_json 列（如果不存在）。
     */
    @Update("ALTER TABLE graph_executions ADD COLUMN plan_json TEXT")
    void addPlanJsonColumn();

    /**
     * 添加 plan_json_path 列（如果不存在）。
     */
    @Update("ALTER TABLE graph_executions ADD COLUMN plan_json_path TEXT")
    void addPlanJsonPathColumn();

    // ==================== 插入 ====================

    @Insert("""
            INSERT INTO graph_executions (thread_id, conversation_id, agent_id, status, current_node, progress)
            VALUES (#{threadId}, #{conversationId}, #{agentId}, #{status}, #{currentNode}, #{progress})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GraphExecution execution);

    // ==================== 查询 ====================

    @Select("SELECT * FROM graph_executions WHERE thread_id = #{threadId}")
    GraphExecution selectByThreadId(String threadId);

    @Select("SELECT * FROM graph_executions WHERE conversation_id = #{conversationId} ORDER BY updated_at DESC LIMIT 1")
    GraphExecution selectLatestByConversationId(String conversationId);

    @Select("SELECT * FROM graph_executions WHERE agent_id = #{agentId} ORDER BY updated_at DESC")
    List<GraphExecution> selectByAgentId(String agentId);

    @Select("SELECT * FROM graph_executions WHERE status = #{status} ORDER BY updated_at DESC")
    List<GraphExecution> selectByStatus(String status);

    // ==================== 更新 ====================

    @Update("UPDATE graph_executions SET status = #{status}, current_node = #{currentNode}, progress = #{progress}, updated_at = datetime('now', 'localtime') WHERE thread_id = #{threadId}")
    int updateStatus(@Param("threadId") String threadId, @Param("status") String status,
                     @Param("currentNode") String currentNode, @Param("progress") int progress);

    @Update("UPDATE graph_executions SET error_message = #{errorMessage}, status = 'failed', updated_at = datetime('now', 'localtime') WHERE thread_id = #{threadId}")
    int updateError(@Param("threadId") String threadId, @Param("errorMessage") String errorMessage);

    @Update("UPDATE graph_executions SET extra_data = #{extraData}, updated_at = datetime('now', 'localtime') WHERE thread_id = #{threadId}")
    int updateExtraData(@Param("threadId") String threadId, @Param("extraData") String extraData);

    @Update("UPDATE graph_executions SET requirement_doc_path = #{docPath}, updated_at = datetime('now', 'localtime') WHERE thread_id = #{threadId}")
    int updateRequirementDocPath(@Param("threadId") String threadId, @Param("docPath") String docPath);

    @Update("UPDATE graph_executions SET plan_json = #{planJson}, updated_at = datetime('now', 'localtime') WHERE thread_id = #{threadId}")
    int updatePlanJson(@Param("threadId") String threadId, @Param("planJson") String planJson);

    @Update("UPDATE graph_executions SET plan_json_path = #{planJsonPath}, updated_at = datetime('now', 'localtime') WHERE thread_id = #{threadId}")
    int updatePlanJsonPath(@Param("threadId") String threadId, @Param("planJsonPath") String planJsonPath);

    // ==================== 删除 ====================

    @Delete("DELETE FROM graph_executions WHERE thread_id = #{threadId}")
    int deleteByThreadId(String threadId);

    @Delete("DELETE FROM graph_executions WHERE conversation_id = #{conversationId}")
    int deleteByConversationId(String conversationId);

    @Delete("DELETE FROM graph_executions")
    int deleteAll();
}

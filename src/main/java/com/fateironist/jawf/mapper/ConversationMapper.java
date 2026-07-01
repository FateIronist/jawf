package com.fateironist.jawf.mapper;

import com.fateironist.jawf.model.Conversation;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 会话数据访问层。
 * <p>
 * 提供会话与 Graph 绑定关系的 CRUD 操作。
 */
@Mapper
public interface ConversationMapper {

    // ==================== 表管理 ====================

    @Update("DROP TABLE IF EXISTS conversations")
    void dropTable();

    @Update("""
            CREATE TABLE IF NOT EXISTS conversations (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id  TEXT    NOT NULL UNIQUE,
                agent_id         TEXT    NOT NULL,
                graph_thread_id  TEXT,
                title            TEXT,
                status           TEXT    NOT NULL DEFAULT 'active',
                created_at       TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
                updated_at       TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
            )
            """)
    void createTable();

    // ==================== 插入 ====================

    @Insert("""
            INSERT INTO conversations (conversation_id, agent_id, graph_thread_id, title, status)
            VALUES (#{conversationId}, #{agentId}, #{graphThreadId}, #{title}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Conversation conversation);

    // ==================== 查询 ====================

    @Select("SELECT * FROM conversations WHERE conversation_id = #{conversationId}")
    Conversation selectByConversationId(String conversationId);

    @Select("SELECT * FROM conversations WHERE agent_id = #{agentId} AND status = 'active' ORDER BY updated_at DESC")
    List<Conversation> selectActiveByAgentId(String agentId);

    @Select("SELECT * FROM conversations WHERE agent_id = #{agentId} ORDER BY updated_at DESC")
    List<Conversation> selectAllByAgentId(String agentId);

    @Select("SELECT * FROM conversations WHERE graph_thread_id = #{graphThreadId}")
    Conversation selectByGraphThreadId(String graphThreadId);

    // ==================== 更新 ====================

    @Update("UPDATE conversations SET graph_thread_id = #{graphThreadId}, status = #{status}, updated_at = datetime('now', 'localtime') WHERE conversation_id = #{conversationId}")
    int updateGraphThread(@Param("conversationId") String conversationId,
                          @Param("graphThreadId") String graphThreadId,
                          @Param("status") String status);

    @Update("UPDATE conversations SET title = #{title}, updated_at = datetime('now', 'localtime') WHERE conversation_id = #{conversationId}")
    int updateTitle(@Param("conversationId") String conversationId, @Param("title") String title);

    @Update("UPDATE conversations SET status = #{status}, updated_at = datetime('now', 'localtime') WHERE conversation_id = #{conversationId}")
    int updateStatus(@Param("conversationId") String conversationId, @Param("status") String status);

    // ==================== 删除 ====================

    @Delete("DELETE FROM conversations WHERE conversation_id = #{conversationId}")
    int deleteByConversationId(String conversationId);

    @Delete("DELETE FROM conversations WHERE agent_id = #{agentId}")
    int deleteByAgentId(String agentId);
}

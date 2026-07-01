package com.fateironist.jawf.mapper;

import com.fateironist.jawf.model.Message;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 消息数据访问层。
 * <p>
 * 提供聊天消息的 CRUD 操作，用于持久化聊天历史。
 */
@Mapper
public interface MessageMapper {

    // ==================== 表管理 ====================

    @Update("DROP TABLE IF EXISTS messages")
    void dropTable();

    @Update("""
            CREATE TABLE IF NOT EXISTS messages (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id  TEXT    NOT NULL,
                agent_id         TEXT,
                type             TEXT    NOT NULL,
                content          TEXT    NOT NULL,
                tool_call_id     TEXT,
                tool_name        TEXT,
                tool_calls_json  TEXT,
                sequence         INTEGER NOT NULL DEFAULT 0,
                metadata_json    TEXT,
                created_at       TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
            )
            """)
    void createTable();

    @Update("CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(conversation_id)")
    void createIndex();

    // ==================== 插入 ====================

    @Insert("""
            INSERT INTO messages (conversation_id, agent_id, type, content,
                                  tool_call_id, tool_name, tool_calls_json,
                                  sequence, metadata_json)
            VALUES (#{conversationId}, #{agentId}, #{type}, #{content},
                    #{toolCallId}, #{toolName}, #{toolCallsJson},
                    #{sequence}, #{metadataJson})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Message message);

    @Insert("""
            INSERT INTO messages (conversation_id, agent_id, type, content,
                                  tool_call_id, tool_name, tool_calls_json,
                                  sequence, metadata_json)
            VALUES (#{conversationId}, #{agentId}, #{type}, #{content},
                    #{toolCallId}, #{toolName}, #{toolCallsJson},
                    #{sequence}, #{metadataJson})
            """)
    int batchInsert(@Param("list") List<Message> messages);

    // ==================== 查询 ====================

    @Select("SELECT * FROM messages WHERE id = #{id}")
    Message selectById(Long id);

    /**
     * 查询指定会话的所有消息，按序号排序。
     */
    @Select("SELECT * FROM messages WHERE conversation_id = #{conversationId} ORDER BY sequence")
    List<Message> selectByConversationId(String conversationId);

    /**
     * 查询指定会话的最近 N 条消息。
     */
    @Select("""
            SELECT * FROM messages
            WHERE conversation_id = #{conversationId}
            ORDER BY sequence DESC
            LIMIT #{limit}
            """)
    List<Message> selectRecentByConversationId(
            @Param("conversationId") String conversationId,
            @Param("limit") int limit);

    /**
     * 查询指定会话的指定类型消息。
     */
    @Select("""
            SELECT * FROM messages
            WHERE conversation_id = #{conversationId} AND type = #{type}
            ORDER BY sequence
            """)
    List<Message> selectByConversationIdAndType(
            @Param("conversationId") String conversationId,
            @Param("type") String type);

    /**
     * 查询指定 Agent 的所有会话 ID（去重）。
     */
    @Select("SELECT DISTINCT conversation_id FROM messages WHERE agent_id = #{agentId} ORDER BY created_at DESC")
    List<String> selectConversationIdsByAgentId(String agentId);

    /**
     * 获取指定会话的消息数量。
     */
    @Select("SELECT COUNT(*) FROM messages WHERE conversation_id = #{conversationId}")
    int countByConversationId(String conversationId);

    /**
     * 获取指定会话的最大序号。
     */
    @Select("SELECT COALESCE(MAX(sequence), 0) FROM messages WHERE conversation_id = #{conversationId}")
    int maxSequenceByConversationId(String conversationId);

    // ==================== 更新 ====================

    @Update("UPDATE messages SET content = #{content}, metadata_json = #{metadataJson} WHERE id = #{id}")
    int updateContent(Message message);

    // ==================== 删除 ====================

    @Delete("DELETE FROM messages WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("DELETE FROM messages WHERE conversation_id = #{conversationId}")
    int deleteByConversationId(String conversationId);

    @Delete("DELETE FROM messages WHERE agent_id = #{agentId}")
    int deleteByAgentId(String agentId);

    @Delete("DELETE FROM messages")
    int deleteAll();
}

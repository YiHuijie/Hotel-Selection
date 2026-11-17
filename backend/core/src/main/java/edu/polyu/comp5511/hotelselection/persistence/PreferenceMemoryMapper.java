package edu.polyu.comp5511.hotelselection.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PreferenceMemoryMapper {
    record MemoryRow(String userId, String preferencesJson) {}
    record SourceEvent(String sourceType, long sourceId, String content, LocalDateTime occurredAt) {}
    record StoredEvent(String sourceType, long sourceId, LocalDateTime occurredAt, String preferencesJson) {}

    @Insert("""
            INSERT INTO user_preference_memory (user_id, preferences_json, updated_at)
            VALUES (#{userId}, '[]', CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
            """)
    void ensureMemory(@Param("userId") String userId);

    @Select("""
            SELECT user_id AS userId, preferences_json AS preferencesJson
            FROM user_preference_memory WHERE user_id = #{userId} FOR UPDATE
            """)
    Optional<MemoryRow> lockMemory(@Param("userId") String userId);

    @Select("""
            SELECT 'MESSAGE' AS sourceType, m.id AS sourceId, m.content, m.created_at AS occurredAt
            FROM agent_message m JOIN agent_session s ON s.id = m.session_id
            WHERE s.user_id = #{userId} AND m.role = 'user'
            UNION ALL
            SELECT 'REVIEW' AS sourceType, id AS sourceId, content, created_at AS occurredAt
            FROM review WHERE user_id = #{userId}
            """)
    List<SourceEvent> sourceEvents(@Param("userId") String userId);

    @Select("""
            SELECT source_type AS sourceType, source_id AS sourceId, occurred_at AS occurredAt,
                preferences_json AS preferencesJson
            FROM user_preference_event WHERE user_id = #{userId}
            """)
    List<StoredEvent> storedEvents(@Param("userId") String userId);

    @Insert("""
            INSERT INTO user_preference_event (user_id, source_type, source_id, occurred_at, preferences_json)
            VALUES (#{userId}, #{event.sourceType}, #{event.sourceId}, #{event.occurredAt}, #{preferencesJson})
            """)
    void insertEvent(@Param("userId") String userId, @Param("event") SourceEvent event,
                     @Param("preferencesJson") String preferencesJson);

    @Update("""
            UPDATE user_preference_memory SET preferences_json = #{preferencesJson}, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = #{userId}
            """)
    void updateMemory(@Param("userId") String userId, @Param("preferencesJson") String preferencesJson);
}

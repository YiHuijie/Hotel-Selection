package edu.polyu.comp5511.hotelselection.persistence;

import edu.polyu.comp5511.hotelselection.api.ConversationMessage;
import edu.polyu.comp5511.hotelselection.api.Evaluation;
import edu.polyu.comp5511.hotelselection.api.SessionSummary;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AgentSessionMapper {
    @Insert("""
            INSERT INTO agent_session (id, user_id, title, state, pending_relaxation,
                requirements_json, latest_reply_json, created_at, updated_at, workflow_version)
            VALUES (#{id}, #{userId}, NULL, #{state}, NULL, #{requirementsJson}, #{replyJson},
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 2)
            """)
    void insertSession(@Param("id") String id, @Param("userId") String userId,
                       @Param("state") String state, @Param("requirementsJson") String requirementsJson,
                       @Param("replyJson") String replyJson);

    @Select("""
            SELECT id, user_id, title, state, pending_relaxation, requirements_json, latest_reply_json,
                created_at, updated_at, workflow_version
            FROM agent_session WHERE id = #{id} AND user_id = #{userId}
            """)
    Optional<AgentSessionRow> find(@Param("id") String id, @Param("userId") String userId);

    @Select("""
            SELECT id, user_id, title, state, pending_relaxation, requirements_json, latest_reply_json,
                created_at, updated_at, workflow_version
            FROM agent_session WHERE id = #{id} AND user_id = #{userId} FOR UPDATE
            """)
    Optional<AgentSessionRow> findForUpdate(@Param("id") String id, @Param("userId") String userId);

    @Select("""
            SELECT id AS session_id, title, created_at, updated_at FROM agent_session
            WHERE user_id = #{userId} ORDER BY updated_at DESC, id DESC
            """)
    List<SessionSummary> list(@Param("userId") String userId);

    @Update("""
            UPDATE agent_session SET state = #{state}, pending_relaxation = #{pending},
                requirements_json = #{requirementsJson}, latest_reply_json = #{replyJson},
                title = COALESCE(title, #{title}), updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND user_id = #{userId}
            """)
    void updateSession(@Param("id") String id, @Param("userId") String userId,
                       @Param("state") String state, @Param("pending") String pending,
                       @Param("requirementsJson") String requirementsJson, @Param("replyJson") String replyJson,
                       @Param("title") String title);

    @Update("UPDATE agent_session SET updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND user_id = #{userId}")
    void touch(@Param("id") String id, @Param("userId") String userId);

    @Insert("""
            INSERT INTO agent_message (session_id, role, content, created_at)
            VALUES (#{sessionId}, #{role}, #{text}, CURRENT_TIMESTAMP)
            """)
    void insertMessage(@Param("sessionId") String sessionId, @Param("role") String role, @Param("text") String text);

    @Select("""
            SELECT id, role, content AS text, created_at FROM agent_message
            WHERE session_id = #{sessionId} ORDER BY id
            """)
    List<ConversationMessage> messages(@Param("sessionId") String sessionId);

    @Insert("""
            INSERT INTO session_evaluation (session_id, history_complete) VALUES (#{sessionId}, #{historyComplete})
            ON DUPLICATE KEY UPDATE session_id = VALUES(session_id)
            """)
    void ensureEvaluation(@Param("sessionId") String sessionId, @Param("historyComplete") boolean historyComplete);

    @Update("""
            UPDATE session_evaluation SET request_count = request_count + 1,
                error_count = error_count + #{failed},
                duration_millis = COALESCE(duration_millis, 0) + #{evaluation.durationMillis},
                model_calls = COALESCE(model_calls, 0) + #{evaluation.modelCalls},
                hard_satisfied = CASE WHEN #{evaluation.hardSatisfied} IS NULL THEN hard_satisfied
                    ELSE COALESCE(hard_satisfied, 0) + #{evaluation.hardSatisfied} END,
                hard_total = CASE WHEN #{evaluation.hardTotal} IS NULL THEN hard_total
                    ELSE COALESCE(hard_total, 0) + #{evaluation.hardTotal} END,
                preference_satisfied = CASE WHEN #{evaluation.preferenceSatisfied} IS NULL THEN preference_satisfied
                    ELSE COALESCE(preference_satisfied, 0) + #{evaluation.preferenceSatisfied} END,
                preference_total = CASE WHEN #{evaluation.preferenceTotal} IS NULL THEN preference_total
                    ELSE COALESCE(preference_total, 0) + #{evaluation.preferenceTotal} END,
                preference_unknown = CASE WHEN #{evaluation.preferenceUnknown} IS NULL THEN preference_unknown
                    ELSE COALESCE(preference_unknown, 0) + #{evaluation.preferenceUnknown} END
            WHERE session_id = #{sessionId}
            """)
    void recordEvaluation(@Param("sessionId") String sessionId, @Param("failed") int failed,
                          @Param("evaluation") Evaluation evaluation);

    @Select("""
            SELECT duration_millis AS durationMillis, model_calls AS modelCalls,
                hard_satisfied AS hardSatisfied, hard_total AS hardTotal,
                preference_satisfied AS preferenceSatisfied, preference_total AS preferenceTotal,
                preference_unknown AS preferenceUnknown, request_count AS requestCount, error_count AS errorCount,
                history_complete AS historyComplete
            FROM session_evaluation WHERE session_id = #{sessionId} AND request_count > 0
            """)
    Optional<Evaluation> evaluation(@Param("sessionId") String sessionId);

    @Delete("DELETE FROM agent_session WHERE id = #{id} AND user_id = #{userId}")
    int delete(@Param("id") String id, @Param("userId") String userId);
}

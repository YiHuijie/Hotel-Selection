package edu.polyu.comp5511.hotelselection.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.polyu.comp5511.hotelselection.api.AgentReply;
import edu.polyu.comp5511.hotelselection.api.Evaluation;
import edu.polyu.comp5511.hotelselection.api.SessionHistory;
import edu.polyu.comp5511.hotelselection.api.SessionSummary;
import edu.polyu.comp5511.hotelselection.domain.AgentSession;
import edu.polyu.comp5511.hotelselection.domain.AgentState;
import edu.polyu.comp5511.hotelselection.domain.Requirements;
import edu.polyu.comp5511.hotelselection.agent.WorkflowAgentMessageService;
import edu.polyu.comp5511.hotelselection.agent.UserPreferenceMemory;
import edu.polyu.comp5511.hotelselection.agent.ModelCallCounter;
import edu.polyu.comp5511.hotelselection.persistence.AgentSessionMapper;
import edu.polyu.comp5511.hotelselection.persistence.AgentSessionRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SessionHistoryService {
    private static final String FEEDBACK_MESSAGE = "感谢反馈！评价已保存，并可用于改进此演示用户后续收到的推荐。";
    private static final String INITIAL_MESSAGE = "请告诉我目的地、入住日期和退房日期，日期格式为 YYYY-MM-DD。";
    private final HotelSkillService skills;
    private final WorkflowAgentMessageService workflow;
    private final UserPreferenceMemory memory;
    private final AgentSessionMapper sessions;
    private final ObjectMapper json;

    public SessionHistoryService(
            HotelSkillService skills,
            WorkflowAgentMessageService workflow,
            UserPreferenceMemory memory,
            AgentSessionMapper sessions,
            ObjectMapper json) {
        this.skills = skills;
        this.workflow = workflow;
        this.memory = memory;
        this.sessions = sessions;
        this.json = json;
    }

    @Transactional
    public AgentReply create(String userId) {
        AgentSession session = new AgentSession(userId);
        AgentReply reply = new AgentReply(session.getId(), session.getState(), INITIAL_MESSAGE,
                session.getRequirements(), List.of(), List.of(), List.of());
        sessions.insertSession(session.getId(), userId, session.getState().name(),
                write(session.getRequirements()), write(reply));
        sessions.ensureEvaluation(session.getId(), true);
        sessions.insertMessage(session.getId(), "agent", reply.message());
        return reply;
    }

    @Transactional
    public AgentReply send(String id, String userId, String text) {
        return send(id, userId, text, new ModelCallCounter(), System.nanoTime());
    }

    @Transactional
    public AgentReply send(String id, String userId, String text, ModelCallCounter counter, long startNanos) {
        AgentSessionRow row = locked(id, userId);
        requireCurrentWorkflow(row);
        AgentSession session = restore(row);
        AgentReply previousReply = read(row.latestReplyJson(), AgentReply.class);
        AgentReply reply = workflow.handleMessage(
                session, text, memory.refresh(userId, counter), counter, previousReply.message());
        sessions.insertMessage(id, "user", text);
        sessions.insertMessage(id, "agent", reply.message());
        memory.refresh(userId, counter);
        Evaluation evaluation = recordEvaluation(id, false, counter, startNanos, reply.evaluation());
        reply = reply.withEvaluation(evaluation);
        String title = text.strip().substring(0, Math.min(text.strip().length(), 120));
        sessions.updateSession(id, userId, session.getState().name(), session.getPendingRelaxation(),
                write(session.getRequirements()), write(reply), title);
        return reply;
    }

    @Transactional
    public void recordFailedMessage(String id, String userId, ModelCallCounter counter, long startNanos) {
        if (sessions.findForUpdate(id, userId).isPresent()) {
            recordEvaluation(id, true, counter, startNanos, null);
        }
    }

    private Evaluation recordEvaluation(String id, boolean failed, ModelCallCounter counter,
            long startNanos, Evaluation quality) {
        Evaluation current = new Evaluation(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
                counter.count(), quality == null ? null : quality.hardSatisfied(),
                quality == null ? null : quality.hardTotal(),
                quality == null ? null : quality.preferenceSatisfied(),
                quality == null ? null : quality.preferenceTotal(),
                quality == null ? null : quality.preferenceUnknown(), 0, 0, true);
        sessions.ensureEvaluation(id, false);
        sessions.recordEvaluation(id, failed ? 1 : 0, current);
        return sessions.evaluation(id).orElseThrow(() -> new IllegalStateException("无法读取会话评估结果。"));
    }

    @Transactional
    public void feedback(String id, String userId, long hotelId, int rating, String comment) {
        AgentSessionRow row = locked(id, userId);
        requireCurrentWorkflow(row);
        AgentSession session = restore(row);
        skills.saveFeedback(session, hotelId, rating, comment);
        sessions.insertMessage(id, "agent", FEEDBACK_MESSAGE);
        sessions.touch(id, userId);
        memory.refresh(userId);
    }

    @Transactional(readOnly = true)
    public List<SessionSummary> list(String userId) {
        return sessions.list(userId);
    }

    @Transactional(readOnly = true)
    public SessionHistory get(String id, String userId) {
        AgentSessionRow row = sessions.find(id, userId)
                .orElseThrow(SessionHistoryService::notFound);
        AgentReply reply = read(row.latestReplyJson(), AgentReply.class);
        return new SessionHistory(reply.withEvaluation(sessions.evaluation(id).orElse(null)), sessions.messages(id));
    }

    @Transactional
    public void delete(String id, String userId) {
        locked(id, userId);
        memory.refresh(userId);
        if (sessions.delete(id, userId) == 0) {
            throw notFound();
        }
    }

    private AgentSessionRow locked(String id, String userId) {
        return sessions.findForUpdate(id, userId).orElseThrow(SessionHistoryService::notFound);
    }

    private void requireCurrentWorkflow(AgentSessionRow row) {
        if (row.workflowVersion() != 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "旧会话只支持查看和删除，请创建新会话。");
        }
    }

    private AgentSession restore(AgentSessionRow row) {
        return new AgentSession(row.id(), row.userId(), read(row.requirementsJson(), Requirements.class),
                AgentState.valueOf(row.state()), row.pendingRelaxation());
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize agent session data", e);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to read stored agent session data", e);
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该会话。");
    }
}

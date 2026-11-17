package edu.polyu.comp5511.hotelselection.api;

import edu.polyu.comp5511.hotelselection.service.SessionHistoryService;
import edu.polyu.comp5511.hotelselection.agent.ModelCallCounter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/agent/sessions")
public class AgentController {
    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private final SessionHistoryService history;

    public AgentController(SessionHistoryService history) {
        this.history = history;
    }

    @PostMapping
    public AgentReply create(@Valid @RequestBody CreateSessionRequest request) {
        return history.create(request.userId());
    }

    @GetMapping
    public List<SessionSummary> list(@RequestParam @NotBlank String userId) {
        return history.list(userId);
    }

    @GetMapping("/{sessionId}")
    public SessionHistory get(@PathVariable String sessionId, @RequestParam @NotBlank String userId) {
        return history.get(sessionId, userId);
    }

    @DeleteMapping("/{sessionId}")
    public void delete(@PathVariable String sessionId, @RequestParam @NotBlank String userId) {
        history.delete(sessionId, userId);
    }

    @PostMapping("/{sessionId}/messages")
    public AgentReply message(
            @PathVariable String sessionId,
            @Valid @RequestBody AgentMessageRequest request) {
        ModelCallCounter counter = new ModelCallCounter();
        long startNanos = System.nanoTime();
        try {
            return history.send(sessionId, request.userId(), request.text(), counter, startNanos);
        } catch (RuntimeException exception) {
            try {
                history.recordFailedMessage(sessionId, request.userId(), counter, startNanos);
            } catch (RuntimeException recordingFailure) {
                exception.addSuppressed(recordingFailure);
                log.error("无法记录会话消息失败指标。", recordingFailure);
            }
            throw exception;
        }
    }

    @PostMapping("/{sessionId}/feedback")
    public Map<String, Boolean> feedback(
            @PathVariable String sessionId,
            @Valid @RequestBody FeedbackRequest request) {
        history.feedback(sessionId, request.userId(), request.hotelId(), request.rating(), request.comment());
        return Map.of("saved", true);
    }
}

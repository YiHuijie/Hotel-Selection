package edu.polyu.comp5511.hotelselection.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.polyu.comp5511.hotelselection.domain.MemoryPreference;
import edu.polyu.comp5511.hotelselection.service.SessionHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:preference_memory_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "hotel.llm.base-url=http://localhost:1234/v1",
        "hotel.llm.api-key=test-key",
        "hotel.llm.model=test-model"
})
class PreferenceMemoryIntegrationTest {
    @Autowired SessionHistoryService sessions;
    @Autowired UserPreferenceMemory memory;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockBean ChatModel model;
    private final AtomicInteger extractionCalls = new AtomicInteger();

    @BeforeEach
    void resetModel() {
        reset(model);
        extractionCalls.set(0);
    }

    @Test
    void countsMessagesAndReviewsUpdatesTopTwoAndRetainsDeletedHistory() throws Exception {
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            if (!prompt.getInstructions().get(0).getText().contains("从每条历史用户需求")) {
                return response("{\"patch\":{},\"decision\":\"NONE\"}");
            }
            extractionCalls.incrementAndGet();
            JsonNode input = json.readTree(prompt.getInstructions().get(1).getText());
            var result = json.createObjectNode();
            var events = result.putArray("events");
            input.path("events").forEach(event -> {
                String content = event.path("content").asText();
                var output = events.addObject().put("eventId", event.path("eventId").asText());
                var preferences = output.putArray("preferences");
                if (content.contains("安静")) preferences.addObject().put("type", "REVIEW").put("value", "安静");
                if (content.contains("卫生")) preferences.addObject().put("type", "REVIEW").put("value", "卫生");
                if (content.contains("洗衣房")) preferences.addObject().put("type", "AMENITY").put("value", "洗衣房");
            });
            return response(json.writeValueAsString(result));
        });
        String user = "memory-" + UUID.randomUUID();
        String first = sessions.create(user).sessionId();
        sessions.send(first, user, "安静安静");
        sessions.send(first, user, "希望安静");
        sessions.feedback(first, user, 1, 5, "卫生很好");
        String second = sessions.create(user).sessionId();
        sessions.send(second, user, "有洗衣房");
        sessions.send(second, user, "希望有洗衣房");

        assertEquals(Set.of("安静", "洗衣房"), values(memory.refresh(user)));
        int calls = extractionCalls.get();
        assertEquals(Set.of("安静", "洗衣房"), values(memory.refresh(user)));
        assertEquals(calls, extractionCalls.get());
        sessions.delete(first, user);
        assertEquals(Set.of("安静", "洗衣房"), values(memory.refresh(user)));

        sessions.send(second, user, "卫生好");
        sessions.send(second, user, "卫生要好");
        sessions.send(second, user, "卫生整洁");
        assertEquals(Set.of("卫生", "洗衣房"), values(memory.refresh(user)));
        assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM user_preference_event WHERE user_id = ?",
                Integer.class, user));
        assertEquals(2, json.readTree(jdbc.queryForObject(
                "SELECT preferences_json FROM user_preference_memory WHERE user_id = ?", String.class, user)).size());
        String other = "memory-" + UUID.randomUUID();
        assertTrue(memory.refresh(other).isEmpty());
    }

    @Test
    void invalidExtractionRollsBackMessageAndMemory() {
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            return response(prompt.getInstructions().get(0).getText().contains("从每条历史用户需求")
                    ? "{\"events\":[]}" : "{\"patch\":{},\"decision\":\"NONE\"}");
        });
        String user = "memory-invalid-" + UUID.randomUUID();
        String id = sessions.create(user).sessionId();

        assertThrows(ResponseStatusException.class, () -> sessions.send(id, user, "希望安静"));
        assertEquals(1, sessions.get(id, user).messages().size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_preference_memory WHERE user_id = ?",
                Integer.class, user));
    }

    @Test
    void processesAllExistingHistoryInBatchesWithoutRecounting() {
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            extractionCalls.incrementAndGet();
            Prompt prompt = invocation.getArgument(0);
            JsonNode input = json.readTree(prompt.getInstructions().get(1).getText());
            var result = json.createObjectNode();
            var events = result.putArray("events");
            input.path("events").forEach(event -> events.addObject()
                    .put("eventId", event.path("eventId").asText())
                    .putArray("preferences").addObject().put("type", "REVIEW").put("value", "安静"));
            return response(json.writeValueAsString(result));
        });
        String user = "memory-batches-" + UUID.randomUUID();
        String id = sessions.create(user).sessionId();
        for (int i = 0; i < 45; i++) {
            jdbc.update("INSERT INTO agent_message (session_id, role, content, created_at) "
                    + "VALUES (?, 'user', '安静', CURRENT_TIMESTAMP)", id);
        }

        assertEquals(Set.of("安静"), values(memory.refresh(user)));
        assertEquals(3, extractionCalls.get());
        assertEquals(45, jdbc.queryForObject("SELECT COUNT(*) FROM user_preference_event WHERE user_id = ?",
                Integer.class, user));
        memory.refresh(user);
        assertEquals(3, extractionCalls.get());
    }

    @Test
    void usesPersistedMemoryOnNextRecommendationAndExcludesCurrentConflict() {
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            String system = prompt.getInstructions().get(0).getText();
            JsonNode input = json.readTree(prompt.getInstructions().get(1).getText());
            if (system.contains("从用户本轮原文")) {
                return response(input.path("text").asText().contains("杭州")
                        ? "{\"patch\":{\"city\":\"杭州市\",\"checkIn\":\"2026-11-20\","
                            + "\"checkOut\":\"2026-11-21\"},\"decision\":\"NONE\"}"
                        : "{\"patch\":{},\"decision\":\"NONE\"}");
            }
            if (system.contains("从每条历史用户需求")) {
                var result = json.createObjectNode();
                var events = result.putArray("events");
                input.path("events").forEach(event -> {
                    var preferences = events.addObject().put("eventId", event.path("eventId").asText())
                            .putArray("preferences");
                    if (event.path("content").asText().contains("我喜欢洗衣房")) {
                        preferences.addObject().put("type", "AMENITY").put("value", "洗衣房");
                    }
                });
                return response(json.writeValueAsString(result));
            }
            if (system.contains("判断每条长期偏好")) {
                boolean apply = !input.path("currentMessage").asText().contains("不需要洗衣房");
                return response("{\"decisions\":[{\"type\":\"AMENITY\",\"value\":\"洗衣房\",\"apply\":"
                        + apply + "}]}");
            }
            if (system.contains("根据输入中的候选酒店")) {
                int score = input.path("longTermPreferences").isEmpty() ? 80 : 91;
                var result = json.createObjectNode();
                var ranked = result.putArray("rankedHotels");
                JsonNode candidates = input.path("candidates");
                for (int i = 0; i < Math.min(3, candidates.size()); i++) {
                    JsonNode candidate = candidates.get(i);
                    ranked.addObject().put("hotelId", candidate.path("hotel").path("id").asLong())
                            .put("roomOfferId", candidate.path("roomOffers").get(0).path("id").asLong())
                            .put("score", score).putArray("reasons").add("有房");
                }
                return response(json.writeValueAsString(result));
            }
            throw new IllegalStateException("Unexpected model prompt: " + system);
        });

        String user = "memory-ranking-" + UUID.randomUUID();
        String id = sessions.create(user).sessionId();
        var first = sessions.send(id, user, "我喜欢洗衣房");
        assertEquals(2, first.evaluation().modelCalls());
        assertEquals(Set.of("洗衣房"), values(memory.refresh(user)));
        var second = sessions.send(id, user, "杭州 2026-11-20 到 2026-11-21");
        assertEquals(91, second.recommendations().get(0).score());
        assertEquals(first.evaluation().modelCalls() + 4, second.evaluation().modelCalls());
        assertEquals(second.hotelCards().size(), second.evaluation().preferenceTotal());
        var laundry = second.hotelCards().stream().map(card -> jdbc.queryForObject(
                "SELECT laundry FROM hotel_detail WHERE hotel_id = ?", Boolean.class, card.hotelId())).toList();
        assertEquals(laundry.stream().filter(Boolean.TRUE::equals).count(),
                second.evaluation().preferenceSatisfied().longValue());
        assertEquals(laundry.stream().filter(java.util.Objects::isNull).count(),
                second.evaluation().preferenceUnknown().longValue());
        var third = sessions.send(id, user, "这次不需要洗衣房");
        assertEquals(80, third.recommendations().get(0).score());
        assertEquals(second.evaluation().preferenceTotal(), third.evaluation().preferenceTotal());
        assertEquals(second.evaluation().hardTotal() + third.hotelCards().size(), third.evaluation().hardTotal());
    }

    private Set<String> values(List<MemoryPreference> preferences) {
        return preferences.stream().map(MemoryPreference::value).collect(java.util.stream.Collectors.toSet());
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}

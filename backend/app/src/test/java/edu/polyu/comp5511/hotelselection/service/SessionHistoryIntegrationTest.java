package edu.polyu.comp5511.hotelselection.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.polyu.comp5511.hotelselection.api.AgentReply;
import edu.polyu.comp5511.hotelselection.api.Evaluation;
import edu.polyu.comp5511.hotelselection.api.SessionHistory;
import edu.polyu.comp5511.hotelselection.agent.UserPreferenceMemory;
import edu.polyu.comp5511.hotelselection.domain.AgentSession;
import edu.polyu.comp5511.hotelselection.domain.AgentState;
import edu.polyu.comp5511.hotelselection.domain.Hotel;
import edu.polyu.comp5511.hotelselection.domain.HotelCandidate;
import edu.polyu.comp5511.hotelselection.domain.HotelReview;
import edu.polyu.comp5511.hotelselection.domain.ReviewForMatching;
import edu.polyu.comp5511.hotelselection.domain.RoomOffer;
import edu.polyu.comp5511.hotelselection.persistence.HotelMapper;
import edu.polyu.comp5511.hotelselection.persistence.AgentSessionMapper;
import edu.polyu.comp5511.hotelselection.persistence.ReviewMapper;
import edu.polyu.comp5511.hotelselection.persistence.RoomOfferMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:history_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "hotel.llm.base-url=http://localhost:1234/v1",
        "hotel.llm.api-key=test-key",
        "hotel.llm.model=test-model"
})
@AutoConfigureMockMvc
class SessionHistoryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SessionHistoryService history;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReviewMapper reviews;
    @Autowired HotelMapper hotels;
    @Autowired RoomOfferMapper roomOffers;
    @Autowired AgentSessionMapper sessions;
    @Autowired HotelSkillService skills;
    @MockBean ChatModel chatModel;
    @MockBean UserPreferenceMemory memory;
    private final AtomicInteger toolCallIds = new AtomicInteger();

    @BeforeEach
    void resetModel() {
        reset(chatModel);
        when(memory.refresh(any())).thenReturn(List.of());
        when(memory.refresh(any(), any())).thenReturn(List.of());
    }

    @Test
    void normalizesHangzhouBeforeSearchingForLaundryHotels() throws Exception {
        AgentSession fixture = new AgentSession("fixture");
        skills.applyRequirementAnalysis(fixture, new RequirementAnalysisInput(
                "杭州", "2026-11-20", "2026-11-21", null, null, null,
                List.of("laundry"), null, null, null));
        List<HotelCandidate> eligible = skills.filterWorkflowCandidates(
                fixture.getRequirements(), skills.listAvailable(fixture)).candidates();
        assertTrue(eligible.stream().anyMatch(candidate -> candidate.hotel().id() == 2));
        String ranking = eligible.stream().limit(3)
                .map(candidate -> "{\"hotelId\":" + candidate.hotel().id()
                        + ",\"roomOfferId\":" + candidate.roomOffers().get(0).id()
                        + ",\"score\":90,\"reasons\":[\"符合已验证候选\"]}")
                .collect(java.util.stream.Collectors.joining(","));
        when(chatModel.call(any(Prompt.class)))
                .thenAnswer(invocation -> {
                    Thread.sleep(50);
                    return textResponse("```json\n{\"patch\":{}}\n```");
                })
                .thenReturn(textResponse("""
                        {"patch":{"city":"杭州","checkIn":"2026-11-20","checkOut":"2026-11-21",
                        "priorityAmenities":["laundry"]},"decision":"NONE"}
                        """))
                .thenReturn(textResponse("{\"rankedHotels\":"))
                .thenReturn(textResponse("{\"rankedHotels\":[" + ranking + "]}"));
        String id = create("hangzhou-laundry");

        JsonNode reply = send(id, "hangzhou-laundry", "杭州，11-20到11-21，有洗衣房");

        assertEquals("杭州市", reply.path("requirements").path("city").asText());
        assertTrue(reply.path("hotelCards").size() > 0);
        assertTrue(!reply.path("message").asText().contains("没有完整入住区间的库存"));
        assertEquals(4, reply.path("evaluation").path("modelCalls").asInt());
        assertEquals(reply.path("hotelCards").size(), reply.path("evaluation").path("hardSatisfied").asInt());
        assertEquals(reply.path("hotelCards").size(), reply.path("evaluation").path("hardTotal").asInt());
        assertEquals(0, reply.path("evaluation").path("preferenceTotal").asInt());
        assertEquals(1, reply.path("evaluation").path("requestCount").asInt());
        assertEquals(0, reply.path("evaluation").path("errorCount").asInt());
        assertEquals("杭州市", history.get(id, "hangzhou-laundry").latestReply().requirements().getCity());
        List<JsonNode> durations = new java.util.ArrayList<>();
        reply.path("observations").forEach(observation -> {
            if ("recommendation_duration".equals(observation.path("toolName").asText())) durations.add(observation);
        });
        assertEquals(1, durations.size());
        String duration = durations.get(0).path("summary").asText();
        assertEquals("SUCCESS", durations.get(0).path("status").asText());
        assertTrue(duration.matches("\\d+ 毫秒（后端工作流）。"));
        assertTrue(Long.parseLong(duration.substring(0, duration.indexOf(' '))) >= 50);
        assertTrue(history.get(id, "hangzhou-laundry").latestReply().observations().stream()
                .anyMatch(item -> item.toolName().equals("recommendation_duration") && item.summary().equals(duration)));
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(4)).call(prompts.capture());
        assertTrue(prompts.getAllValues().get(1).getInstructions().get(0).getText().contains("上次输出不是有效"));
        assertTrue(prompts.getAllValues().get(3).getInstructions().get(0).getText().contains("上次输出不是有效"));
        assertEquals(prompts.getAllValues().get(0).getInstructions().get(1).getText(),
                prompts.getAllValues().get(1).getInstructions().get(1).getText());
        assertEquals(prompts.getAllValues().get(2).getInstructions().get(1).getText(),
                prompts.getAllValues().get(3).getInstructions().get(1).getText());
    }

    @Test
    void unknownCityIsClarifiedAndCanBeCorrectedInTheNextTurn() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(textResponse("""
                        {"patch":{"city":"不存在的城市","checkIn":"2026-11-20","checkOut":"2026-11-22"},
                        "decision":"NONE"}
                        """))
                .thenReturn(textResponse("{\"patch\":{\"city\":\"杭州市\"},\"decision\":\"NONE\"}"));
        String id = create("city-clarification");
        JsonNode first = send(id, "city-clarification", "不存在的城市");
        assertEquals("CLARIFICATION_REQUIRED", first.path("state").asText());
        assertTrue(first.path("message").asText().contains("无法识别目的地城市"));
        assertTrue(!first.path("observations").toString().contains("hotel_list"));
        assertNull(history.get(id, "city-clarification").latestReply().requirements().getCity());
        JsonNode second = send(id, "city-clarification", "杭州市");
        assertEquals("杭州市", second.path("requirements").path("city").asText());
        assertTrue(second.path("message").asText().contains("没有完整入住区间的库存"));
    }

    @Test
    void validCityWithoutHotelRecordsReturnsNoInventoryRatherThanCityClarification() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(textResponse("""
                        {"patch":{"city":"深圳","checkIn":"2026-11-20","checkOut":"2026-11-21"},
                        "decision":"NONE"}
                        """));
        String id = create("valid-city-without-hotels");
        JsonNode reply = send(id, "valid-city-without-hotels", "深圳，11月20日到21日");
        assertEquals("深圳市", reply.path("requirements").path("city").asText());
        assertTrue(reply.path("message").asText().contains("没有完整入住区间的库存"));
        assertTrue(reply.path("observations").toString().contains("NO_INVENTORY"));
    }

    @Test
    void tieredFieldsRetainTheirMeaningAcrossStoredTurns() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(textResponse("""
                        {"patch":{"city":"杭州","priorityMaxPricePerNight":500,"maxPricePerNight":400,
                        "priorityAmenities":["gym"],"amenities":["laundry"],
                        "secondaryAmenities":["parking"],"priorityReviewPreferences":["安静"],
                        "reviewPreferences":["卫生"],"secondaryReviewPreferences":["服务"]},"decision":"NONE"}
                        """))
                .thenReturn(textResponse("{\"patch\":{\"clearFields\":[\"maxPricePerNight\"]},\"decision\":\"NONE\"}"));
        String id = create("tiered-fields");
        send(id, "tiered-fields", "条件");
        JsonNode second = send(id, "tiered-fields", "取消一般预算");
        assertEquals(500, second.path("requirements").path("maxPricePerNight").asInt());
        assertTrue(second.path("requirements").path("preferredMaxPricePerNight").isNull());
        var stored = history.get(id, "tiered-fields").latestReply().requirements();
        assertEquals(List.of("gym"), stored.getRequiredAmenities());
        assertEquals(List.of("laundry"), stored.getPreferredAmenities());
        assertEquals(List.of("parking"), stored.getSecondaryAmenities());
        assertEquals(List.of("安静"), stored.getPriorityReviewPreferences());
        assertEquals(List.of("卫生"), stored.getReviewPreferences());
        assertEquals(List.of("服务"), stored.getSecondaryReviewPreferences());
    }

    @Test
    void legacySessionsRemainReadableAndDeletableButCannotBeChanged() throws Exception {
        String id = create("legacy-user");
        jdbc.update("UPDATE agent_session SET workflow_version = 1 WHERE id = ?", id);
        mvc.perform(get("/api/agent/sessions/{id}", id).param("userId", "legacy-user"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/agent/sessions/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", "legacy-user", "text", "你好"))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/agent/sessions/{id}/feedback", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", "legacy-user", "hotelId", 1,
                                "rating", 5, "comment", "很好"))))
                .andExpect(status().isConflict());
        assertEquals(1, history.get(id, "legacy-user").messages().size());
        mvc.perform(delete("/api/agent/sessions/{id}", id).param("userId", "legacy-user"))
                .andExpect(status().isOk());
    }

    @Test
    void unverifiedFacilityPersistsUntilExplicitlyCleared() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(textResponse("""
                        {"patch":{"city":"杭州","checkIn":"2026-11-20","checkOut":"2026-11-22",
                        "priorityAmenities":["gym","pool"]},"decision":"NONE"}
                        """))
                .thenReturn(textResponse("{\"patch\":{\"clearFields\":[\"unverifiedAmenities\"]},\"decision\":\"NONE\"}"));
        String id = create("facility-user");

        JsonNode first = send(id, "facility-user", "要求健身房和泳池");
        assertEquals("pool", first.path("requirements").path("unverifiedAmenities").get(0).asText());
        assertEquals("pool", history.get(id, "facility-user").latestReply().requirements()
                .getUnverifiedAmenities().get(0));
        JsonNode second = send(id, "facility-user", "取消泳池");
        assertEquals(0, second.path("requirements").path("unverifiedAmenities").size());
        assertEquals("gym", second.path("requirements").path("requiredAmenities").get(0).asText());
        assertTrue(second.path("message").asText().contains("没有完整入住区间的库存"));
    }

    @Test
    void invalidModelResponseDoesNotPersistMessageOrRequirements() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse(
                "{\"patch\":{\"city\":\"杭州市\"},\"decision\":\"NONE\"} {\"unexpected\":true}"));
        String id = create("invalid-model-user");

        mvc.perform(post("/api/agent/sessions/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", "invalid-model-user", "text", "杭州市"))))
                .andExpect(status().isBadGateway());
        SessionHistory restored = history.get(id, "invalid-model-user");
        assertEquals(1, restored.messages().size());
        assertNull(restored.latestReply().requirements().getCity());
        assertEquals(1, restored.latestReply().evaluation().requestCount());
        assertEquals(1, restored.latestReply().evaluation().errorCount());
        assertEquals(2, restored.latestReply().evaluation().modelCalls());
        assertNull(restored.latestReply().evaluation().hardTotal());
        verify(chatModel, times(2)).call(any(Prompt.class));
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("{\"patch\":{},\"decision\":\"NONE\"}"));
        JsonNode success = send(id, "invalid-model-user", "继续");
        assertEquals(2, success.path("evaluation").path("requestCount").asInt());
        assertEquals(1, success.path("evaluation").path("errorCount").asInt());
        assertEquals(3, success.path("evaluation").path("modelCalls").asInt());
        assertTrue(success.path("evaluation").path("historyComplete").asBoolean());
        assertTrue(success.path("evaluation").path("hardTotal").isNull());
    }

    @Test
    void evaluationAccumulatesAcrossRecommendationsClarificationsAndFailures() throws Exception {
        String id = create("cumulative-evaluation");
        Evaluation recommendation = new Evaluation(100L, 3, 2, 2, 2, 4, 1, 0, 0, true);
        Evaluation clarification = new Evaluation(20L, 1, null, null, null, null, null, 0, 0, true);
        Evaluation failure = new Evaluation(30L, 2, null, null, null, null, null, 0, 0, true);
        sessions.recordEvaluation(id, 0, recommendation);
        sessions.recordEvaluation(id, 0, clarification);
        sessions.recordEvaluation(id, 0, recommendation);
        sessions.recordEvaluation(id, 1, failure);

        Evaluation evaluation = history.get(id, "cumulative-evaluation").latestReply().evaluation();
        assertEquals(250L, evaluation.durationMillis());
        assertEquals(9, evaluation.modelCalls());
        assertEquals(4, evaluation.hardSatisfied());
        assertEquals(4, evaluation.hardTotal());
        assertEquals(4, evaluation.preferenceSatisfied());
        assertEquals(8, evaluation.preferenceTotal());
        assertEquals(2, evaluation.preferenceUnknown());
        assertEquals(4, evaluation.requestCount());
        assertEquals(1, evaluation.errorCount());
        assertTrue(evaluation.historyComplete());

        jdbc.update("UPDATE session_evaluation SET history_complete = FALSE WHERE session_id = ?", id);
        sessions.recordEvaluation(id, 0, clarification);
        Evaluation legacy = history.get(id, "cumulative-evaluation").latestReply().evaluation();
        assertTrue(!legacy.historyComplete());
        assertEquals(270L, legacy.durationMillis());
        assertEquals(4, legacy.hardTotal());
    }

    @Test
    void sessionsWithoutPriorEvaluationAreMarkedIncomplete() throws Exception {
        String id = create("missing-legacy-evaluation");
        jdbc.update("DELETE FROM session_evaluation WHERE session_id = ?", id);
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("{\"patch\":{},\"decision\":\"NONE\"}"));

        JsonNode reply = send(id, "missing-legacy-evaluation", "继续");

        assertTrue(!reply.path("evaluation").path("historyComplete").asBoolean());
        assertEquals(1, reply.path("evaluation").path("requestCount").asInt());
    }

    @Test
    void modelServiceFailureIsReportedWithoutPersistingTheTurn() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenThrow(new NonTransientAiException("model unavailable"));
        String id = create("model-failure-user");

        mvc.perform(post("/api/agent/sessions/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", "model-failure-user", "text", "杭州市"))))
                .andExpect(status().isServiceUnavailable());
        assertEquals(1, history.get(id, "model-failure-user").messages().size());
    }

    @Test
    void hotelDetailEndpointReturnsAllDisplayableHotelRoomAndReviewFields() throws Exception {
        String response = mvc.perform(get("/api/hotels/1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode detail = json.readTree(response);
        assertTrue(detail.has("hotel"));
        assertTrue(detail.path("hotel").has("longitude"));
        assertEquals("浙江省", detail.path("hotel").path("province").asText());
        assertEquals("上城区", detail.path("hotel").path("district").asText());
        assertTrue(detail.path("hotel").has("accessibility"));
        assertTrue(detail.has("amenities"));
        assertTrue(detail.has("roomOffers"));
        assertTrue(detail.has("reviews"));
        if (!detail.path("reviews").isEmpty()) {
            assertTrue(detail.path("reviews").get(0).has("reviewId"));
            assertTrue(detail.path("reviews").get(0).has("content"));
            assertTrue(!detail.path("reviews").get(0).has("userId"));
        }
    }

    @Test
    @Transactional
    void hotelLocationComesFromHotelEvenWithoutDetailRow() {
        LocalDate checkIn = LocalDate.parse("2026-11-20");
        LocalDate checkOut = LocalDate.parse("2026-11-21");
        Hotel available = hotels.findAvailable("杭州市", checkIn, checkOut, 1L).get(0);
        assertNotNull(available.province());
        assertNotNull(available.district());
        jdbc.update("DELETE FROM hotel_detail WHERE hotel_id = ?", available.id());

        Hotel byId = hotels.findById(available.id()).orElseThrow();
        Hotel byAvailability = hotels.findAvailable("杭州市", checkIn, checkOut, 1L).stream()
                .filter(hotel -> hotel.id() == available.id()).findFirst().orElseThrow();
        assertEquals(available.province(), byId.province());
        assertEquals(available.district(), byId.district());
        assertEquals(available.province(), byAvailability.province());
        assertEquals(available.district(), byAvailability.district());
    }

    @Test
    void migratesLegacyHotelLocationsAndCanRunAgain() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:location_migration_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        JdbcTemplate legacy = new JdbcTemplate(dataSource);
        legacy.execute("""
                CREATE TABLE hotel (id BIGINT PRIMARY KEY, name VARCHAR(255) NOT NULL,
                city VARCHAR(80) NOT NULL, rating DECIMAL(3, 1))
                """);
        legacy.execute("""
                CREATE TABLE hotel_detail (hotel_id BIGINT PRIMARY KEY, province VARCHAR(80), district VARCHAR(80))
                """);
        legacy.update("INSERT INTO hotel (id, name, city) VALUES (1, 'hotel', 'city'), (2, 'other', 'city')");
        legacy.update("INSERT INTO hotel_detail (hotel_id, province, district) VALUES (1, 'province', 'district')");
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));

        schema.execute(dataSource);
        schema.execute(dataSource);

        assertEquals("province", legacy.queryForObject("SELECT province FROM hotel WHERE id = 1", String.class));
        assertEquals("district", legacy.queryForObject("SELECT district FROM hotel WHERE id = 1", String.class));
        assertNull(legacy.queryForObject("SELECT province FROM hotel WHERE id = 2", String.class));
        assertEquals(0, legacy.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'HOTEL_DETAIL' AND COLUMN_NAME IN ('PROVINCE', 'DISTRICT')
                """, Integer.class));

        legacy.execute("ALTER TABLE hotel_detail ADD COLUMN province VARCHAR(80)");
        legacy.execute("ALTER TABLE hotel_detail ADD COLUMN district VARCHAR(80)");
        legacy.update("UPDATE hotel_detail SET province = 'old', district = 'recovered' WHERE hotel_id = 1");
        legacy.update("UPDATE hotel SET province = 'current', district = NULL WHERE id = 1");
        schema.execute(dataSource);
        assertEquals("current", legacy.queryForObject("SELECT province FROM hotel WHERE id = 1", String.class));
        assertEquals("recovered", legacy.queryForObject("SELECT district FROM hotel WHERE id = 1", String.class));
    }

    @Test
    void migratesLegacyEvaluationAsIncompleteWithoutDiscardingData() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:evaluation_migration_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        JdbcTemplate legacy = new JdbcTemplate(dataSource);
        legacy.execute("""
                CREATE TABLE session_evaluation (session_id VARCHAR(36) PRIMARY KEY,
                request_count BIGINT NOT NULL DEFAULT 0, duration_millis BIGINT)
                """);
        legacy.update("INSERT INTO session_evaluation VALUES ('old', 3, 123)");
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
        schema.execute(dataSource);
        schema.execute(dataSource);
        assertEquals(false, legacy.queryForObject(
                "SELECT history_complete FROM session_evaluation WHERE session_id = 'old'", Boolean.class));
        assertEquals(123L, legacy.queryForObject(
                "SELECT duration_millis FROM session_evaluation WHERE session_id = 'old'", Long.class));
    }

    @Test
    void hotelListEndpointReturnsOnlyHotelsAvailableForTheRequestedStay() throws Exception {
        String response = mvc.perform(get("/api/hotels")
                        .param("city", "杭州市")
                        .param("checkIn", "2026-11-20")
                        .param("checkOut", "2026-11-21"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode hotels = json.readTree(response);
        assertTrue(hotels.isArray());
        assertTrue(hotels.size() > 0);
        assertEquals("杭州市", hotels.get(0).path("city").asText());
        assertTrue(hotels.get(0).has("pricePerNight"));

        String unavailable = mvc.perform(get("/api/hotels")
                        .param("city", "杭州市")
                        .param("checkIn", "2026-11-20")
                        .param("checkOut", "2026-11-22"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(0, json.readTree(unavailable).size());
    }

    @Test
    void restoresConversationAndPendingRelaxationAndIsolatesUsers() throws Exception {
        LocalDate checkIn = LocalDate.parse("2026-11-20");
        LocalDate checkOut = LocalDate.parse("2026-11-21");
        List<Hotel> availableHotels = hotels.findAvailable(
                "杭州市", checkIn, checkOut, 1L);
        List<RoomOffer> availableRooms = roomOffers.findAvailableForStay(
                availableHotels.stream().map(Hotel::id).toList(), checkIn, checkOut, 1L);
        Map<Long, RoomOffer> oneRoomPerHotel = new LinkedHashMap<>();
        availableRooms.forEach(room -> oneRoomPerHotel.putIfAbsent(room.hotelId(), room));
        List<RoomOffer> selectedRooms = oneRoomPerHotel.values().stream().limit(3).toList();
        assertEquals(3, selectedRooms.size());
        String rankedHotels = selectedRooms.stream()
                .map(room -> "{\"hotelId\":" + room.hotelId() + ",\"roomOfferId\":" + room.id()
                        + ",\"score\":90,\"reasons\":[\"符合已验证候选\"]}")
                .collect(java.util.stream.Collectors.joining(","));
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(textResponse(
                        "{\"patch\":{\"city\":\"杭州市\",\"checkIn\":\"2026-11-20\",\"checkOut\":\"2026-11-21\","
                                + "\"priorityMaxPricePerNight\":100},\"decision\":\"NONE\"}"))
                .thenReturn(textResponse("{\"patch\":{\"clearFields\":[\"priorityMaxPricePerNight\"]},\"decision\":\"CHANGE\"}"))
                .thenReturn(textResponse("{\"rankedHotels\":[" + rankedHotels + "]}"));
        String id = create("alice");
        send(id, "alice", "杭州市, 2026-11-20 to 2026-11-21, under 100");
        SessionHistory restored = history.get(id, "alice");
        assertEquals(3, restored.messages().size());
        assertEquals("user", restored.messages().get(1).role());
        assertEquals("CLARIFICATION_REQUIRED", restored.latestReply().state().name());
        assertTrue(restored.latestReply().message().contains("调整"));

        JsonNode reply = send(id, "alice", "放宽预算");
        assertEquals("RECOMMENDED", reply.get("state").asText());
        assertEquals(3, reply.path("hotelCards").size());
        assertEquals("priorityPrice", reply.path("requirements").path("appliedRelaxations").get(0)
                .path("condition").asText());
        assertEquals("100", reply.path("requirements").path("appliedRelaxations").get(0)
                .path("beforeValue").asText());
        assertTrue(reply.path("hotelCards").get(0).has("satisfiedRequiredConditions"));
        assertEquals(5, history.get(id, "alice").messages().size());
        assertTrue(history.get(id, "alice").latestReply().recommendations().size() > 0);
        assertEquals(3, history.get(id, "alice").latestReply().hotelCards().size());
        assertEquals("100", history.get(id, "alice").latestReply().requirements()
                .getAppliedRelaxations().get(0).beforeValue());

        mvc.perform(get("/api/agent/sessions/{id}", id).param("userId", "bob"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/agent/sessions/{id}", id).param("userId", "bob"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/agent/sessions/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("userId", "bob", "text", "放宽预算"))))
                .andExpect(status().isNotFound());
        assertEquals(0, history.list("bob").size());

        String second = create("alice");
        assertNotEquals(id, second);
        assertEquals(1, history.get(second, "alice").messages().size());
        assertNull(history.get(second, "alice").latestReply().requirements().getCity());
        assertEquals(2, history.list("alice").size());
        mvc.perform(delete("/api/agent/sessions/{id}", id).param("userId", "alice"))
                .andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM agent_message WHERE session_id = ?", Integer.class, id));
        assertEquals(1, history.list("alice").size());
    }

    @Test
    void invalidDateReturnsClarificationAndPersistsValidRequirements() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(textResponse(
                        "{\"patch\":{\"city\":\"杭州市\",\"checkIn\":\"2026-02-31\","
                                + "\"checkOut\":\"2026-03-02\"},\"decision\":\"NONE\"}"));
        String id = create("invalid-date-user");
        String response = mvc.perform(post("/api/agent/sessions/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("userId", "invalid-date-user",
                                "text", "The dates are invalid."))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals("CLARIFICATION_REQUIRED", json.readTree(response).path("state").asText());
        assertTrue(json.readTree(response).path("message").asText().contains("入住日期"));
        SessionHistory restored = history.get(id, "invalid-date-user");
        assertEquals(3, restored.messages().size());
        assertEquals("杭州市", restored.latestReply().requirements().getCity());
        assertNull(restored.latestReply().requirements().getCheckIn());
        assertEquals(LocalDate.parse("2026-03-02"), restored.latestReply().requirements().getCheckOut());
    }

    @Test
    void reviewPreferencesSurviveDateClarificationAndSessionRestore() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(textResponse("""
                        {"patch":{"city":"杭州市","reviewPreferences":["安静","卫生"],
                         "clarificationQuestion":"请问入住年份和退房日期？"},"decision":"NONE"}
                        """))
                .thenReturn(textResponse("{\"patch\":{\"checkIn\":\"2026-11-20\","
                        + "\"checkOut\":\"2026-11-22\"},\"decision\":\"NONE\"}"));

        String id = create("review-preference-user");
        JsonNode first = send(id, "review-preference-user", "杭州11月20日，安静卫生的酒店");
        assertEquals("CLARIFICATION_REQUIRED", first.path("state").asText());
        assertEquals("安静", first.path("requirements").path("reviewPreferences").get(0).asText());
        assertEquals("卫生", first.path("requirements").path("reviewPreferences").get(1).asText());
        assertEquals(0, first.path("requirements").path("preferredAmenities").size());

        JsonNode second = send(id, "review-preference-user", "2026年11月20日入住，11月22日退房");
        assertEquals(2, second.path("requirements").path("reviewPreferences").size());
        assertEquals(List.of("安静", "卫生"),
                history.get(id, "review-preference-user").latestReply().requirements().getReviewPreferences());
        assertTrue(second.path("observations").toString().contains("NO_INVENTORY"));
    }

    @Test
    void asksOnlyForCheckoutAndRetainsOtherRequirementsOnNextTurn() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(textResponse("""
                        {"patch":{"city":"杭州","checkIn":"2026-11-20","origin":"西湖",
                        "reviewPreferences":["卫生","安静"]},"decision":"NONE"}
                        """))
                .thenReturn(textResponse("{\"patch\":{\"checkOut\":\"2026-11-22\"},\"decision\":\"NONE\"}"));
        String id = create("checkout-clarification");

        JsonNode first = send(id, "checkout-clarification", "找一家离杭州西湖近的，11月20日入住的卫生安静的酒店");
        assertEquals("请提供退房日期。", first.path("message").asText());
        assertEquals("杭州市", first.path("requirements").path("city").asText());
        assertEquals("2026-11-20", first.path("requirements").path("checkIn").asText());
        assertTrue(first.path("requirements").path("checkOut").isNull());

        JsonNode second = send(id, "checkout-clarification", "11月22日退房");
        assertEquals("2026-11-22", second.path("requirements").path("checkOut").asText());
        assertEquals("西湖", second.path("requirements").path("origin").asText());
        assertEquals(List.of("卫生", "安静"),
                history.get(id, "checkout-clarification").latestReply().requirements().getReviewPreferences());
        assertTrue(second.path("observations").toString().contains("NO_INVENTORY"));
    }

    @Test
    void misclassifiedQuietAndDateOnlyReplyDoNotBlockConversation() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(textResponse("""
                        {"patch":{"city":"杭州","checkIn":"2026-11-20","origin":"西湖",
                        "amenities":["quiet"],"reviewPreferences":["卫生"]},"decision":"NONE"}
                        """))
                .thenReturn(textResponse("{\"patch\":{},\"decision\":\"NONE\"}"));
        String user = "quiet-followup";
        String id = create(user);

        JsonNode first = send(id, user, "找一家离杭州西湖近的，11月20日入住的卫生安静的酒店");
        assertEquals("请提供退房日期。", first.path("message").asText());
        assertEquals(0, first.path("requirements").path("unverifiedAmenities").size());
        assertEquals(List.of("卫生", "安静"), history.get(id, user).latestReply().requirements().getReviewPreferences());

        JsonNode second = send(id, user, "11月22日");
        assertEquals("2026-11-20", second.path("requirements").path("checkIn").asText());
        assertEquals("2026-11-22", second.path("requirements").path("checkOut").asText());
        assertTrue(second.path("observations").toString().contains("NO_INVENTORY"));
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompts.capture());
        assertEquals("请提供退房日期。", json.readTree(
                prompts.getAllValues().get(1).getInstructions().get(1).getText()).path("lastAssistantMessage").asText());
    }

    @Test
    void repairsPersistedQuietAndAcceptsDateOnlyCheckout() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("{\"patch\":{},\"decision\":\"NONE\"}"));
        String user = "legacy-quiet-followup";
        String id = create(user);
        var requirements = history.get(id, user).latestReply().requirements();
        requirements.setCity("杭州市");
        requirements.setCheckIn(LocalDate.parse("2026-11-20"));
        requirements.setMaxPricePerNight(new java.math.BigDecimal("0.01"));
        requirements.getUnverifiedAmenities().add("quiet");
        var previous = new AgentReply(
                id, AgentState.CLARIFICATION_REQUIRED,
                "请提供退房日期。 无法核验设施：quiet。请确认是否调整这些要求。",
                requirements, List.of(), List.of(), List.of());
        jdbc.update("UPDATE agent_session SET state = ?, requirements_json = ?, latest_reply_json = ? WHERE id = ?",
                "CLARIFICATION_REQUIRED", json.writeValueAsString(requirements), json.writeValueAsString(previous), id);

        JsonNode reply = send(id, user, "11月21日");

        assertEquals("2026-11-21", reply.path("requirements").path("checkOut").asText());
        assertEquals(0, reply.path("requirements").path("unverifiedAmenities").size());
        assertEquals("安静", reply.path("requirements").path("reviewPreferences").get(0).asText());
        assertTrue(reply.path("observations").toString().contains("NO_FILTER_MATCH"));
        assertEquals(List.of("安静"), history.get(id, user).latestReply().requirements().getReviewPreferences());
    }

    @Test
    void failedTurnDoesNotPersistPartialMessagesOrChangedRequirements() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(textResponse(
                        "{\"patch\":{\"city\":\"Hong Kong\",\"clearFields\":[\"unsupported\"]},\"decision\":\"NONE\"}"));
        String id = create("failure-user");
        mvc.perform(post("/api/agent/sessions/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("userId", "failure-user",
                                "text", "Invalid request."))))
                .andExpect(status().isBadRequest());
        SessionHistory restored = history.get(id, "failure-user");
        assertEquals(1, restored.messages().size());
        assertNull(restored.latestReply().requirements().getCity());
    }

    @Test
    void preservesFeedbackMessageAndKeepsHotelReviewAfterSessionDeletion() throws Exception {
        String id = create("review-user");
        mvc.perform(post("/api/agent/sessions/{id}/feedback", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "userId", "review-user", "hotelId", 1, "rating", 5, "comment", "Great stay"))))
                .andExpect(status().isOk());
        assertEquals(2, history.get(id, "review-user").messages().size());
        assertTrue(history.get(id, "review-user").messages().get(1).text().contains("评价已保存"));
        mvc.perform(delete("/api/agent/sessions/{id}", id).param("userId", "review-user"))
                .andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM review WHERE user_id = ?", Integer.class, "review-user"));
    }

    @Test
    void loadsImportedReviewEvidenceWithUnknownFieldsPreservedAsNull() {
        List<HotelReview> hotelReviews = reviews.findReviewsForHotels(List.of(2L));

        assertTrue(hotelReviews.size() > 0);
        assertEquals(2, hotelReviews.get(0).hotelId());
        assertNotNull(hotelReviews.get(0).reviewId());
        assertNull(hotelReviews.get(0).rating());
    }

    @Test
    @Transactional
    void loadsAllReviewMatchingRowsAndIdentifiesCurrentUser() {
        List<Object[]> rows = java.util.stream.IntStream.range(0, 105)
                .mapToObj(index -> new Object[]{2L, index == 104 ? "matching-owner" : "another-user",
                        "matching-test-" + index})
                .toList();
        jdbc.batchUpdate("INSERT INTO review (hotel_id, user_id, content, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)", rows);

        List<ReviewForMatching> matching = reviews.findReviewsForMatching(List.of(2L), "matching-owner")
                .stream().filter(review -> review.content() != null && review.content().startsWith("matching-test-"))
                .toList();
        assertEquals(105, matching.size());
        assertEquals(1, matching.stream().filter(ReviewForMatching::own).count());
        assertEquals("matching-test-104", matching.stream().filter(ReviewForMatching::own).findFirst().orElseThrow().content());
        assertEquals(105, matching.stream().map(ReviewForMatching::id).distinct().count());
    }

    @Test
    @Transactional
    void persistsReviewPercentagesAndPersonalOverrideInRecommendations() throws Exception {
        AgentSession fixture = new AgentSession("matching-integration");
        skills.applyRequirementAnalysis(fixture, new RequirementAnalysisInput(
                "杭州", "2026-11-20", "2026-11-21", null, null, null,
                List.of("laundry"), null, null, null));
        List<HotelCandidate> eligible = skills.filterWorkflowCandidates(
                fixture.getRequirements(), skills.listAvailable(fixture)).candidates();
        long hotelId = eligible.get(0).hotel().id();
        for (int i = 0; i < 3; i++) {
            jdbc.update("INSERT INTO review (hotel_id, user_id, content, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                    hotelId, "another-user", "matching-integration-support");
        }
        jdbc.update("INSERT INTO review (hotel_id, user_id, content, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                hotelId, "matching-integration", "matching-integration-oppose");
        String ranking = eligible.stream().limit(3)
                .map(candidate -> "{\"hotelId\":" + candidate.hotel().id()
                        + ",\"roomOfferId\":" + candidate.roomOffers().get(0).id()
                        + ",\"score\":90,\"reasons\":[\"符合已验证候选\"]}")
                .collect(java.util.stream.Collectors.joining(","));
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            String system = prompt.getInstructions().get(0).getText();
            if (system.contains("从用户本轮原文")) {
                return textResponse("""
                        {"patch":{"city":"杭州","checkIn":"2026-11-20","checkOut":"2026-11-21",
                        "priorityAmenities":["laundry"],"reviewPreferences":["安静"]},"decision":"NONE"}
                        """);
            }
            if (system.contains("针对输入的单个评价偏好")) {
                JsonNode input = json.readTree(prompt.getInstructions().get(1).getText());
                var result = json.createObjectNode();
                var matches = result.putArray("matches");
                input.path("reviews").forEach(review -> {
                    String content = review.path("content").asText();
                    String status = content.equals("matching-integration-support") ? "MATCHED"
                            : content.equals("matching-integration-oppose") ? "CONTRADICTED" : "UNKNOWN";
                    matches.addObject().put("reviewId", review.path("reviewId").asLong()).put("result", status);
                });
                return textResponse(json.writeValueAsString(result));
            }
            return textResponse("{\"rankedHotels\":[" + ranking + "]}");
        });
        String id = create("matching-integration");

        JsonNode reply = send(id, "matching-integration", "杭州，11-20到11-21，有洗衣房，要安静");
        JsonNode card = reply.path("hotelCards").get(0);
        assertEquals(hotelId, card.path("hotelId").asLong());
        assertTrue(card.path("reasons").toString().contains("75.0% 同意、25.0% 反对"));
        assertTrue(card.path("reasons").toString().contains("不符合，以本用户最新相关评价为准"));
        assertTrue(history.get(id, "matching-integration").latestReply().hotelCards().get(0).reasons().stream()
                .anyMatch(reason -> reason.contains("75.0% 同意、25.0% 反对")));
    }

    @Test
    void doesNotTreatSeparateSingleNightRowsAsMultiNightAvailability() {
        List<Hotel> hotelsWithTwoNightInventory = hotels.findAvailable(
                "杭州市", LocalDate.parse("2026-11-20"), LocalDate.parse("2026-11-22"), 2L);

        assertTrue(hotelsWithTwoNightInventory.isEmpty());
    }

    @Test
    void seedUsesFourEvenlyDistributedRequestedInventoryDates() {
        List<LocalDate> dates = jdbc.query(
                "SELECT DISTINCT available_on FROM room_offer ORDER BY available_on",
                (resultSet, rowNumber) -> resultSet.getDate(1).toLocalDate());
        List<Integer> inventoryCounts = dates.stream()
                .map(date -> jdbc.queryForObject(
                        "SELECT COUNT(*) FROM room_offer WHERE available_on = ?", Integer.class, date))
                .toList();

        assertEquals(List.of(
                LocalDate.parse("2026-11-20"), LocalDate.parse("2026-11-25"),
                LocalDate.parse("2026-11-28"), LocalDate.parse("2026-11-30")), dates);
        assertEquals(300, jdbc.queryForObject("SELECT COUNT(*) FROM room_offer", Integer.class));
        assertEquals(List.of(75, 75, 75, 75), inventoryCounts);
    }

    private String create(String user) throws Exception {
        String response = mvc.perform(post("/api/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("userId", user))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("sessionId").asText();
    }

    private JsonNode send(String id, String user, String text) throws Exception {
        String response = mvc.perform(post("/api/agent/sessions/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("userId", user, "text", text))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private ChatResponse requirementTool(String text) throws Exception {
        return toolCallResponse("requirement_analysis", json.writeValueAsString(Map.of("text", text)));
    }

    private ChatResponse toolCallResponse(String name, String arguments) {
        AssistantMessage assistant = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "integration-tool-" + toolCallIds.incrementAndGet(), "function", name, arguments)))
                .build();
        return new ChatResponse(List.of(new Generation(assistant)));
    }

    private ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}

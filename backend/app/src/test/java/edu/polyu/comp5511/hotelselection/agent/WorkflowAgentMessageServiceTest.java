package edu.polyu.comp5511.hotelselection.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.polyu.comp5511.hotelselection.api.AgentReply;
import edu.polyu.comp5511.hotelselection.domain.AgentSession;
import edu.polyu.comp5511.hotelselection.domain.AgentState;
import edu.polyu.comp5511.hotelselection.domain.DistanceEstimate;
import edu.polyu.comp5511.hotelselection.domain.Hotel;
import edu.polyu.comp5511.hotelselection.domain.MemoryPreference;
import edu.polyu.comp5511.hotelselection.domain.ReviewForMatching;
import edu.polyu.comp5511.hotelselection.domain.RoomOffer;
import edu.polyu.comp5511.hotelselection.persistence.HotelMapper;
import edu.polyu.comp5511.hotelselection.persistence.ReviewMapper;
import edu.polyu.comp5511.hotelselection.persistence.RoomOfferMapper;
import edu.polyu.comp5511.hotelselection.service.DistanceProvider;
import edu.polyu.comp5511.hotelselection.service.HotelSkillService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowAgentMessageServiceTest {
    private final ChatModel model = mock(ChatModel.class);
    private final HotelMapper hotels = mock(HotelMapper.class);
    private final RoomOfferMapper rooms = mock(RoomOfferMapper.class);
    private final ReviewMapper reviews = mock(ReviewMapper.class);
    private final DistanceProvider distances = mock(DistanceProvider.class);
    private final HotelSkillService skills = new HotelSkillService(hotels, rooms, reviews, distances);
    private final WorkflowAgentMessageService workflow = new WorkflowAgentMessageService(
            model, skills, new ObjectMapper().findAndRegisterModules(),
            Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void mapsTieredModelFieldsAndClearFieldsWithoutChangingStoredFieldSemantics() throws Exception {
        when(model.call(any(Prompt.class)))
                .thenReturn(response("""
                        {"patch":{"city":"杭州市","priorityMaxPricePerNight":500,"maxPricePerNight":400,
                        "priorityMinPricePerNight":100,"minPricePerNight":150,"secondaryMinPricePerNight":200,
                        "secondaryMaxPricePerNight":350,"priorityAmenities":["gym"],"amenities":["laundry"],
                        "secondaryAmenities":["parking"],"priorityReviewPreferences":["安静"],
                        "reviewPreferences":["卫生"],"secondaryReviewPreferences":["服务"]},"decision":"NONE"}
                        """))
                .thenReturn(response("""
                        {"patch":{"clearFields":["priorityMaxPricePerNight","minPricePerNight",
                        "priorityAmenities","amenities"]},"decision":"NONE"}
                        """));
        AgentSession session = new AgentSession("user");
        AgentReply first = workflow.handleMessage(session, "条件");
        assertEquals(new BigDecimal("500"), first.requirements().getMaxPricePerNight());
        assertEquals(new BigDecimal("400"), first.requirements().getPreferredMaxPricePerNight());
        assertEquals(new BigDecimal("100"), first.requirements().getMinPricePerNight());
        assertEquals(new BigDecimal("150"), first.requirements().getPreferredMinPricePerNight());
        assertEquals(new BigDecimal("200"), first.requirements().getSecondaryMinPricePerNight());
        assertEquals(new BigDecimal("350"), first.requirements().getSecondaryMaxPricePerNight());
        assertEquals(List.of("gym"), first.requirements().getRequiredAmenities());
        assertEquals(List.of("laundry"), first.requirements().getPreferredAmenities());
        assertEquals(List.of("parking"), first.requirements().getSecondaryAmenities());
        assertEquals(List.of("安静"), first.requirements().getPriorityReviewPreferences());
        assertEquals(List.of("卫生"), first.requirements().getReviewPreferences());
        assertEquals(List.of("服务"), first.requirements().getSecondaryReviewPreferences());

        AgentReply second = workflow.handleMessage(session, "取消部分条件");
        assertEquals(null, second.requirements().getMaxPricePerNight());
        assertEquals(new BigDecimal("400"), second.requirements().getPreferredMaxPricePerNight());
        assertEquals(new BigDecimal("100"), second.requirements().getMinPricePerNight());
        assertEquals(null, second.requirements().getPreferredMinPricePerNight());
        assertTrue(second.requirements().getRequiredAmenities().isEmpty());
        assertTrue(second.requirements().getPreferredAmenities().isEmpty());
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        String context = prompts.getAllValues().get(1).getInstructions().get(1).getText();
        var requirements = new ObjectMapper().readTree(context).path("currentRequirements");
        assertEquals(500, requirements.path("priorityMaxPricePerNight").asInt());
        assertEquals(400, requirements.path("maxPricePerNight").asInt());
        assertEquals("gym", requirements.path("priorityAmenities").get(0).asText());
        assertTrue(!requirements.has("preferredMaxPricePerNight"));
    }

    @Test
    void rejectsLegacyModelFieldsInsteadOfSilentlyChangingTheirMeaning() {
        when(model.call(any(Prompt.class))).thenReturn(response(
                "{\"patch\":{\"preferredMaxPricePerNight\":200},\"decision\":\"NONE\"}"));
        assertThrows(RuntimeException.class, () -> workflow.handleMessage(new AgentSession("user"), "预算"));
    }

    @Test
    void asksForMissingDatesWithoutSearching() {
        when(model.call(any(Prompt.class))).thenReturn(response(
                "{\"patch\":{\"city\":\"杭州市\",\"clarificationQuestion\":\"请提供入住日期。\"},\"decision\":\"NONE\"}"));

        AgentReply reply = workflow.handleMessage(new AgentSession("user"), "杭州市");

        assertEquals(AgentState.CLARIFICATION_REQUIRED, reply.state());
        assertEquals("请提供入住日期。", reply.message());
        assertTrue(reply.observations().stream().anyMatch(item -> item.toolName().equals("recommendation_duration")
                && item.summary().matches("\\d+ 毫秒（后端工作流）。")));
        verify(hotels, times(0)).findAvailable(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void asksOnlyForMissingRequiredFields() {
        List<String[]> cases = List.of(
                new String[] {"{}", "请提供目的地、入住日期和退房日期。"},
                new String[] {"{\"city\":\"杭州\"}", "请提供入住日期和退房日期。"},
                new String[] {"{\"checkIn\":\"2026-11-20\"}", "请提供目的地和退房日期。"},
                new String[] {"{\"checkOut\":\"2026-11-21\"}", "请提供目的地和入住日期。"},
                new String[] {"{\"city\":\"杭州\",\"checkIn\":\"2026-11-20\"}", "请提供退房日期。"},
                new String[] {"{\"city\":\"杭州\",\"checkOut\":\"2026-11-21\"}", "请提供入住日期。"},
                new String[] {"{\"checkIn\":\"2026-11-20\",\"checkOut\":\"2026-11-21\"}", "请提供目的地。"}
        );
        for (String[] testCase : cases) {
            when(model.call(any(Prompt.class))).thenReturn(response(
                    "{\"patch\":" + testCase[0] + ",\"decision\":\"NONE\"}"));
            AgentReply reply = workflow.handleMessage(new AgentSession("user"), "查找酒店");
            assertEquals(AgentState.CLARIFICATION_REQUIRED, reply.state());
            assertEquals(testCase[1], reply.message());
        }
        verify(hotels, times(0)).findAvailable(any(), any(), any(), anyLong());
    }

    @Test
    void displaysEligibleHotelAndAsksOnlyWhenRelaxationCouldHelp() {
        LocalDate checkIn = LocalDate.parse("2026-12-10");
        LocalDate checkOut = LocalDate.parse("2026-12-12");
        Hotel first = hotel(1, "First");
        Hotel second = hotel(2, "Second");
        when(hotels.findAvailable("杭州市", checkIn, checkOut, 2L)).thenReturn(List.of(first, second));
        when(rooms.findAvailableForStay(List.of(1L, 2L), checkIn, checkOut, 2L))
                .thenReturn(List.of(room(10, 1, "180"), room(20, 2, "250")));
        when(hotels.findById(1)).thenReturn(Optional.of(first));
        when(model.call(any(Prompt.class)))
                .thenReturn(response("""
                        {"patch":{"city":"杭州市","checkIn":"2026-12-10","checkOut":"2026-12-12",
                        "maxPricePerNight":200},"decision":"NONE"}
                        """))
                .thenReturn(response("""
                        {"rankedHotels":[{"hotelId":1,"roomOfferId":10,"score":90,"reasons":["价格符合条件"]}]}
                        """));
        AgentSession session = new AgentSession("user");

        AgentReply reply = workflow.handleMessage(session, "杭州市 dates and price");

        assertEquals(AgentState.CLARIFICATION_REQUIRED, reply.state());
        assertEquals(1, reply.hotelCards().size());
        assertTrue(reply.message().contains("可能增加候选"));
        assertEquals("generalPrice", session.getPendingRelaxation());
        assertEquals(new BigDecimal("200"), reply.requirements().getPreferredMaxPricePerNight());
    }

    @Test
    void refusalPreservesPriceAndDoesNotRepeatSameQuestion() {
        LocalDate checkIn = LocalDate.parse("2026-12-10");
        LocalDate checkOut = LocalDate.parse("2026-12-12");
        Hotel first = hotel(1, "First");
        Hotel second = hotel(2, "Second");
        when(hotels.findAvailable("杭州市", checkIn, checkOut, 2L)).thenReturn(List.of(first, second));
        when(rooms.findAvailableForStay(List.of(1L, 2L), checkIn, checkOut, 2L))
                .thenReturn(List.of(room(10, 1, "180"), room(20, 2, "250")));
        when(hotels.findById(1)).thenReturn(Optional.of(first));
        String ranking = """
                {"rankedHotels":[{"hotelId":1,"roomOfferId":10,"score":90,"reasons":["价格符合条件"]}]}
                """;
        when(model.call(any(Prompt.class)))
                .thenReturn(response("""
                        {"patch":{"city":"杭州市","checkIn":"2026-12-10","checkOut":"2026-12-12",
                        "maxPricePerNight":200},"decision":"NONE"}
                        """))
                .thenReturn(response(ranking))
                .thenReturn(response("{\"patch\":{},\"decision\":\"REJECT\"}"))
                .thenReturn(response(ranking));
        AgentSession session = new AgentSession("user");
        workflow.handleMessage(session, "杭州市 dates and price");

        AgentReply reply = workflow.handleMessage(session, "不调整");

        assertEquals(AgentState.RECOMMENDED, reply.state());
        assertEquals(1, reply.hotelCards().size());
        assertEquals(new BigDecimal("200"), reply.requirements().getPreferredMaxPricePerNight());
        assertEquals(null, session.getPendingRelaxation());
        assertTrue(reply.observations().stream().noneMatch(o -> o.toolName().equals("condition_relax")));
        verify(hotels, times(2)).findAvailable("杭州市", checkIn, checkOut, 2L);
    }

    @Test
    void reportsNoInventoryWithoutAskingToRelax() {
        LocalDate checkIn = LocalDate.parse("2026-12-10");
        LocalDate checkOut = LocalDate.parse("2026-12-12");
        when(hotels.findAvailable("杭州市", checkIn, checkOut, 2L)).thenReturn(List.of());
        when(model.call(any(Prompt.class))).thenReturn(response("""
                {"patch":{"city":"杭州市","checkIn":"2026-12-10","checkOut":"2026-12-12",
                "priorityMaxPricePerNight":200},"decision":"NONE"}
                """));
        AgentSession session = new AgentSession("user");

        AgentReply reply = workflow.handleMessage(session, "杭州市 dates");

        assertTrue(reply.message().contains("没有完整入住区间的库存"));
        assertEquals(null, session.getPendingRelaxation());
        assertTrue(reply.observations().stream().noneMatch(o -> o.toolName().equals("condition_relax")));
    }

    @Test
    void preservesOtherRequirementsWhenFacilityCannotBeVerified() {
        when(model.call(any(Prompt.class)))
                .thenReturn(response("""
                        {"patch":{"city":"杭州市","checkIn":"2026-12-10","checkOut":"2026-12-12",
                        "priorityAmenities":["gym","pool"]},"decision":"NONE"}
                        """))
                .thenReturn(response("{\"patch\":{\"clearFields\":[\"unverifiedAmenities\"]},\"decision\":\"NONE\"}"));
        AgentSession session = new AgentSession("user");

        AgentReply first = workflow.handleMessage(session, "杭州市 dates, must have a pool");
        assertEquals(AgentState.CLARIFICATION_REQUIRED, first.state());
        assertEquals("杭州市", first.requirements().getCity());
        assertEquals(List.of("pool"), first.requirements().getUnverifiedAmenities());
        assertEquals(List.of("gym"), first.requirements().getRequiredAmenities());
        assertTrue(first.message().contains("pool"));

        AgentReply second = workflow.handleMessage(session, "取消泳池要求");
        assertTrue(second.requirements().getUnverifiedAmenities().isEmpty());
        assertTrue(second.message().contains("没有完整入住区间的库存"));
    }

    @Test
    void movesMisclassifiedExperiencePreferencesToTheirReviewTiers() {
        when(model.call(any(Prompt.class))).thenReturn(response("""
                {"patch":{"city":"杭州","checkIn":"2026-11-20",
                "priorityAmenities":["gym","quiet"],"amenities":["laundry","卫生","pool"],
                "secondaryAmenities":["parking","安静"],"reviewPreferences":["服务"]},"decision":"NONE"}
                """));
        AgentSession session = new AgentSession("user");
        session.getRequirements().getPriorityReviewPreferences().add("隔音");

        AgentReply reply = workflow.handleMessage(session, "需要健身房，安静卫生");

        assertEquals(List.of("gym"), reply.requirements().getRequiredAmenities());
        assertEquals(List.of("laundry"), reply.requirements().getPreferredAmenities());
        assertEquals(List.of("parking"), reply.requirements().getSecondaryAmenities());
        assertEquals(List.of("隔音", "安静"), reply.requirements().getPriorityReviewPreferences());
        assertEquals(List.of("服务", "卫生"), reply.requirements().getReviewPreferences());
        assertEquals(List.of("安静"), reply.requirements().getSecondaryReviewPreferences());
        assertEquals(List.of("pool"), reply.requirements().getUnverifiedAmenities());
        assertTrue(reply.message().contains("pool"));
    }

    @Test
    void resolvesDateOnlyAnswerAndRepairsPreviouslyUnverifiedQuiet() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(response(
                "{\"patch\":{\"checkIn\":\"2026-11-21\"},\"decision\":\"NONE\"}"));
        AgentSession session = new AgentSession("user");
        session.getRequirements().setCity("杭州市");
        session.getRequirements().setCheckIn(LocalDate.parse("2026-11-20"));
        session.getRequirements().getReviewPreferences().add("卫生");
        session.getRequirements().getUnverifiedAmenities().addAll(List.of("quiet", "pool"));
        String previous = "请提供退房日期。 无法核验设施：quiet、pool。请确认是否调整这些要求。";

        AgentReply reply = workflow.handleMessage(session, "11月21日", List.of(), new ModelCallCounter(), previous);

        assertEquals(LocalDate.parse("2026-11-20"), reply.requirements().getCheckIn());
        assertEquals(LocalDate.parse("2026-11-21"), reply.requirements().getCheckOut());
        assertEquals(List.of("卫生", "安静"), reply.requirements().getReviewPreferences());
        assertEquals(List.of("pool"), reply.requirements().getUnverifiedAmenities());
        assertTrue(reply.message().contains("pool"));
        assertTrue(!reply.message().contains("退房日期"));
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompts.capture());
        var input = new ObjectMapper().readTree(prompts.getValue().getInstructions().get(1).getText());
        assertEquals(previous, input.path("lastAssistantMessage").asText());
        assertTrue(input.path("currentRequirements").path("reviewPreferences").toString().contains("安静"));
    }

    @Test
    void dateOnlyAnswerDoesNotGuessWhenThePriorQuestionIsNotAboutCheckout() {
        when(model.call(any(Prompt.class))).thenReturn(response("{\"patch\":{},\"decision\":\"NONE\"}"));
        AgentSession session = new AgentSession("user");
        session.getRequirements().setCity("杭州市");
        session.getRequirements().setCheckIn(LocalDate.parse("2026-11-20"));

        AgentReply reply = workflow.handleMessage(
                session, "11月21日", List.of(), new ModelCallCounter(), "请确认行程安排。");

        assertEquals(null, reply.requirements().getCheckOut());
        assertEquals("请提供退房日期。", reply.message());
    }

    @Test
    void invalidDateOnlyAnswerStillRequiresAValidDate() {
        when(model.call(any(Prompt.class))).thenReturn(response("{\"patch\":{},\"decision\":\"NONE\"}"));
        AgentSession session = new AgentSession("user");
        session.getRequirements().setCity("杭州市");
        session.getRequirements().setCheckIn(LocalDate.parse("2026-11-20"));

        AgentReply reply = workflow.handleMessage(
                session, "11月31日", List.of(), new ModelCallCounter(), "请提供退房日期。");

        assertEquals(null, reply.requirements().getCheckOut());
        assertTrue(reply.message().contains("有效的退房日期"));
        assertEquals(AgentState.CLARIFICATION_REQUIRED, reply.state());
    }

    @Test
    void repairsLegacyQuietBeforeProcessingAnExplicitCancellation() {
        when(model.call(any(Prompt.class))).thenReturn(response(
                "{\"patch\":{\"clearFields\":[\"reviewPreferences\"]},\"decision\":\"NONE\"}"));
        AgentSession session = new AgentSession("user");
        session.getRequirements().getUnverifiedAmenities().add("quiet");

        AgentReply reply = workflow.handleMessage(session, "不需要安静");

        assertTrue(reply.requirements().getUnverifiedAmenities().isEmpty());
        assertTrue(reply.requirements().getReviewPreferences().isEmpty());
    }

    @Test
    void confirmsActualRelaxationAndRecordsIt() {
        LocalDate checkIn = LocalDate.parse("2026-12-10");
        LocalDate checkOut = LocalDate.parse("2026-12-12");
        Hotel first = hotel(1, "First");
        Hotel second = hotel(2, "Second");
        when(hotels.findAvailable("杭州市", checkIn, checkOut, 2L)).thenReturn(List.of(first, second));
        when(rooms.findAvailableForStay(List.of(1L, 2L), checkIn, checkOut, 2L))
                .thenReturn(List.of(room(10, 1, "180"), room(20, 2, "250")));
        when(hotels.findById(1)).thenReturn(Optional.of(first));
        when(hotels.findById(2)).thenReturn(Optional.of(second));
        when(model.call(any(Prompt.class)))
                .thenReturn(response("""
                        {"patch":{"city":"杭州市","checkIn":"2026-12-10","checkOut":"2026-12-12",
                        "maxPricePerNight":200,"priorityMinPricePerNight":100,
                        "minPricePerNight":120,"secondaryMaxPricePerNight":150},"decision":"NONE"}
                        """))
                .thenReturn(response("""
                        {"rankedHotels":[{"hotelId":1,"roomOfferId":10,"score":90,"reasons":["价格符合条件"]}]}
                        """))
                .thenReturn(response("{\"patch\":{\"maxPricePerNight\":300},\"decision\":\"CHANGE\"}"))
                .thenReturn(response("""
                        {"rankedHotels":[{"hotelId":1,"roomOfferId":10,"score":90,"reasons":["价格符合条件"]},
                        {"hotelId":2,"roomOfferId":20,"score":80,"reasons":["价格符合条件"]}]}
                        """));
        AgentSession session = new AgentSession("user");
        workflow.handleMessage(session, "杭州市 dates and price");

        AgentReply reply = workflow.handleMessage(session, "预算改为300");

        assertEquals(2, reply.hotelCards().size());
        assertEquals(new BigDecimal("300"), reply.requirements().getPreferredMaxPricePerNight());
        assertEquals("generalPrice", reply.requirements().getAppliedRelaxations().get(0).condition());
        assertEquals("200", reply.requirements().getAppliedRelaxations().get(0).beforeValue());
        assertEquals("300", reply.requirements().getAppliedRelaxations().get(0).afterValue());
        assertTrue(reply.hotelCards().get(0).satisfiedRequiredConditions().stream()
                .anyMatch(value -> value.contains("每晚最低预算：100")));
        assertTrue(reply.hotelCards().get(0).satisfiedPreferences().stream()
                .anyMatch(value -> value.contains("一般最低价格：120")));
        assertTrue(reply.hotelCards().get(0).unmetPreferences().stream()
                .anyMatch(value -> value.contains("次要最高价格：150")));
        assertEquals(null, session.getPendingRelaxation());
        verify(hotels, times(2)).findAvailable("杭州市", checkIn, checkOut, 2L);
    }

    @Test
    void asksForRouteOriginBeforeSearching() {
        when(model.call(any(Prompt.class))).thenReturn(response("""
                {"patch":{"city":"杭州市","checkIn":"2026-12-10","checkOut":"2026-12-12",
                "transportModes":["walking"]},"decision":"NONE"}
                """));
        AgentReply reply = workflow.handleMessage(new AgentSession("user"), "查步行路线");

        assertEquals(AgentState.CLARIFICATION_REQUIRED, reply.state());
        assertTrue(reply.message().contains("出发地点"));
        verify(hotels, times(0)).findAvailable(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void requestsOnlyEligibleRoutesAndDisclosesPartialResults() {
        LocalDate checkIn = LocalDate.parse("2026-12-10");
        LocalDate checkOut = LocalDate.parse("2026-12-12");
        Hotel eligible = hotel(1, "First");
        Hotel excluded = hotel(2, "Second");
        when(hotels.findAvailable("杭州市", checkIn, checkOut, 2L)).thenReturn(List.of(eligible, excluded));
        when(rooms.findAvailableForStay(List.of(1L, 2L), checkIn, checkOut, 2L))
                .thenReturn(List.of(room(10, 1, "180"), room(20, 2, "250")));
        when(hotels.findById(1)).thenReturn(Optional.of(eligible));
        when(distances.getRoutes("Central", "杭州市", List.of("walking", "driving", "transit"), List.of(eligible)))
                .thenReturn(List.of(new DistanceEstimate(1, "walking", 100, 120, List.of())));
        when(model.call(any(Prompt.class)))
                .thenReturn(response("""
                        {"patch":{"city":"杭州市","checkIn":"2026-12-10","checkOut":"2026-12-12",
                        "priorityMaxPricePerNight":200,"origin":"Central"},"decision":"NONE"}
                        """))
                .thenReturn(response("""
                        {"rankedHotels":[{"hotelId":1,"roomOfferId":10,"score":90,"reasons":["价格符合条件"]}]}
                        """));

        AgentReply reply = workflow.handleMessage(new AgentSession("user"), "从Central出发");

        assertTrue(reply.message().contains("部分路线数据不可用或未知"));
        verify(distances).getRoutes("Central", "杭州市", List.of("walking", "driving", "transit"),
                List.of(eligible));
    }

    @Test
    void matchesEveryReviewAndAppliesThresholdsAndLatestPersonalOpinion() throws Exception {
        LocalDate checkIn = LocalDate.parse("2026-12-10");
        LocalDate checkOut = LocalDate.parse("2026-12-12");
        Hotel first = hotel(1, "First");
        Hotel second = hotel(2, "Second");
        when(hotels.findAvailable("杭州市", checkIn, checkOut, 2L)).thenReturn(List.of(first, second));
        when(rooms.findAvailableForStay(List.of(1L, 2L), checkIn, checkOut, 2L))
                .thenReturn(List.of(room(10, 1, "180"), room(20, 2, "200")));
        when(hotels.findById(1)).thenReturn(Optional.of(first));
        when(hotels.findById(2)).thenReturn(Optional.of(second));
        when(reviews.findReviewsForMatching(List.of(1L, 2L), "user")).thenReturn(List.of(
                new ReviewForMatching(1, 1, "评价1", null, false),
                new ReviewForMatching(2, 1, "评价2", null, false),
                new ReviewForMatching(3, 1, "评价3", null, false),
                new ReviewForMatching(4, 1, "之前的评价", LocalDateTime.parse("2026-01-01T10:00:00"), true),
                new ReviewForMatching(5, 1, "最新的评价", LocalDateTime.parse("2026-02-01T10:00:00"), true)));
        when(model.call(any(Prompt.class)))
                .thenReturn(response("""
                {"patch":{"city":"杭州市","checkIn":"2026-12-10","checkOut":"2026-12-12",
                "priorityReviewPreferences":["安静"],"reviewPreferences":["卫生"],
                "secondaryReviewPreferences":["服务"]},"decision":"NONE"}
                """))
                .thenReturn(response("""
                        {"matches":[{"reviewId":1,"result":"MATCHED"},{"reviewId":2,"result":"MATCHED"},
                        {"reviewId":3,"result":"MATCHED"},{"reviewId":4,"result":"MATCHED"},
                        {"reviewId":5,"result":"CONTRADICTED"}]}
                        """))
                .thenReturn(response("""
                        {"matches":[{"reviewId":1,"result":"MATCHED"},{"reviewId":2,"result":"MATCHED"},
                        {"reviewId":3,"result":"CONTRADICTED"},{"reviewId":4,"result":"UNKNOWN"},
                        {"reviewId":5,"result":"UNKNOWN"}]}
                        """))
                .thenReturn(response("""
                        {"matches":[{"reviewId":1,"result":"MATCHED"},{"reviewId":2,"result":"CONTRADICTED"},
                        {"reviewId":3,"result":"UNKNOWN"},{"reviewId":4,"result":"UNKNOWN"},
                        {"reviewId":5,"result":"UNKNOWN"}]}
                        """))
                .thenReturn(response("""
                        {"rankedHotels":[{"hotelId":1,"roomOfferId":10,"score":90,"reasons":["有房"]},
                        {"hotelId":2,"roomOfferId":20,"score":85,"reasons":["有房"]}]}
                        """));

        AgentReply reply = workflow.handleMessage(new AgentSession("user"), "需要安静");
        assertEquals(2, reply.evaluation().hardSatisfied());
        assertEquals(2, reply.evaluation().hardTotal());
        assertEquals(1, reply.evaluation().preferenceSatisfied());
        assertEquals(6, reply.evaluation().preferenceTotal());
        assertEquals(3, reply.evaluation().preferenceUnknown());
        List<String> reasons = reply.hotelCards().get(0).reasons();
        assertTrue(reasons.stream().anyMatch(reason -> reason.contains("安静")
                && reason.contains("80.0% 同意、20.0% 反对") && reason.contains("不符合")
                && reason.contains("本用户最新相关评价")));
        assertTrue(reasons.stream().anyMatch(reason -> reason.contains("卫生")
                && reason.contains("66.7% 同意、33.3% 反对") && reason.contains("符合")));
        assertTrue(reasons.stream().anyMatch(reason -> reason.contains("服务")
                && reason.contains("50.0% 同意、50.0% 反对") && reason.contains("不符合")));
        assertEquals(3, reply.hotelCards().get(1).reasons().stream()
                .filter(reason -> reason.contains("暂无相关评价")).count());
        assertTrue(reply.hotelCards().get(0).unknownPreferences().stream().noneMatch(value -> value.contains("评价偏好")));
        assertTrue(reply.observations().stream().anyMatch(observation ->
                observation.toolName().equals("review_matching") && observation.summary().contains("5 条")));
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(5)).call(prompts.capture());
        var assessments = new ObjectMapper().readTree(prompts.getAllValues().get(4).getInstructions().get(1).getText())
                .path("reviewAssessments");
        assertEquals("NOT_MET", assessments.get(0).path("result").asText());
        assertTrue(assessments.get(0).path("personalOverride").asBoolean());
        verify(reviews, times(0)).insert(anyLong(), any(), anyInt(), any());
    }

    @Test
    void rejectsMissingOrInventedReviewMatches() {
        LocalDate checkIn = LocalDate.parse("2026-12-10");
        LocalDate checkOut = LocalDate.parse("2026-12-12");
        when(hotels.findAvailable("杭州市", checkIn, checkOut, 2L)).thenReturn(List.of(hotel(1, "First")));
        when(rooms.findAvailableForStay(List.of(1L), checkIn, checkOut, 2L))
                .thenReturn(List.of(room(10, 1, "180")));
        when(reviews.findReviewsForMatching(List.of(1L), "user"))
                .thenReturn(List.of(new ReviewForMatching(1, 1, "安静", null, false)));
        String requirement = """
                {"patch":{"city":"杭州市","checkIn":"2026-12-10","checkOut":"2026-12-12",
                "reviewPreferences":["安静"]},"decision":"NONE"}
                """;
        when(model.call(any(Prompt.class)))
                .thenReturn(response(requirement))
                .thenReturn(response("{\"matches\":[]}"))
                .thenReturn(response(requirement))
                .thenReturn(response("{\"matches\":[{\"reviewId\":999,\"result\":\"MATCHED\"}]}"));
        assertThrows(RuntimeException.class, () -> workflow.handleMessage(new AgentSession("user"), "安静"));
        assertThrows(RuntimeException.class, () -> workflow.handleMessage(new AgentSession("user"), "安静"));
    }

    @Test
    void includesApplicableLongTermPreferencesAndExcludesConflictsBeforeRanking() throws Exception {
        LocalDate checkIn = LocalDate.parse("2026-12-10");
        LocalDate checkOut = LocalDate.parse("2026-12-12");
        Hotel eligible = hotel(1, "First");
        when(hotels.findAvailable("杭州市", checkIn, checkOut, 2L)).thenReturn(List.of(eligible));
        when(rooms.findAvailableForStay(List.of(1L), checkIn, checkOut, 2L))
                .thenReturn(List.of(room(10, 1, "180")));
        when(hotels.findById(1)).thenReturn(Optional.of(eligible));
        when(reviews.findReviewsForMatching(List.of(1L), "user"))
                .thenReturn(List.of(new ReviewForMatching(1, 1, "很安静", null, false)));
        when(model.call(any(Prompt.class)))
                .thenReturn(response("""
                        {"patch":{"city":"杭州市","checkIn":"2026-12-10","checkOut":"2026-12-12"},"decision":"NONE"}
                        """))
                .thenReturn(response("""
                        {"decisions":[{"type":"REVIEW","value":"安静","apply":true},
                        {"type":"AMENITY","value":"洗衣房","apply":false}]}
                        """))
                .thenReturn(response("{\"matches\":[{\"reviewId\":1,\"result\":\"MATCHED\"}]}"))
                .thenReturn(response("""
                        {"rankedHotels":[{"hotelId":1,"roomOfferId":10,"score":90,"reasons":["有房"]}]}
                        """));

        AgentReply reply = workflow.handleMessage(new AgentSession("user"), "这次不需要洗衣房",
                List.of(new MemoryPreference("REVIEW", "安静"), new MemoryPreference("AMENITY", "洗衣房")));

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(4)).call(prompts.capture());
        var input = new ObjectMapper().readTree(prompts.getAllValues().get(3).getInstructions().get(1).getText());
        assertEquals(1, input.path("longTermPreferences").size());
        assertEquals("安静", input.path("longTermPreferences").get(0).path("value").asText());
        assertEquals("MATCHED", input.path("reviewAssessments").get(0).path("result").asText());
        assertTrue(reply.hotelCards().get(0).reasons().stream().anyMatch(reason -> reason.contains("100.0% 同意")));
        assertTrue(reply.observations().stream().anyMatch(item -> item.toolName().equals("memory_fetch")
                && item.summary().contains("1 项")));
    }

    @Test
    void matchesAllBatchesAndPersonalSupportOverridesOpposition() throws Exception {
        LocalDate checkIn = LocalDate.parse("2026-12-10");
        LocalDate checkOut = LocalDate.parse("2026-12-12");
        Hotel eligible = hotel(1, "First");
        when(hotels.findAvailable("杭州市", checkIn, checkOut, 2L)).thenReturn(List.of(eligible));
        when(rooms.findAvailableForStay(List.of(1L), checkIn, checkOut, 2L))
                .thenReturn(List.of(room(10, 1, "180")));
        when(hotels.findById(1)).thenReturn(Optional.of(eligible));
        when(reviews.findReviewsForMatching(List.of(1L), "user")).thenReturn(
                java.util.stream.LongStream.rangeClosed(1, 21)
                        .mapToObj(id -> new ReviewForMatching(id, 1, "评价" + id,
                                LocalDateTime.parse("2026-02-01T10:00:00"), id == 1))
                        .toList());
        String firstBatch = java.util.stream.LongStream.rangeClosed(1, 20)
                .mapToObj(id -> "{\"reviewId\":" + id + ",\"result\":\""
                        + (id == 1 ? "MATCHED" : "CONTRADICTED") + "\"}")
                .collect(java.util.stream.Collectors.joining(","));
        when(model.call(any(Prompt.class)))
                .thenReturn(response("""
                        {"patch":{"city":"杭州市","checkIn":"2026-12-10","checkOut":"2026-12-12",
                        "reviewPreferences":["安静"]},"decision":"NONE"}
                        """))
                .thenReturn(response("{\"matches\":[" + firstBatch + "]}"))
                .thenReturn(response("{\"matches\":[{\"reviewId\":21,\"result\":\"UNKNOWN\"}]}"))
                .thenReturn(response("""
                        {"rankedHotels":[{"hotelId":1,"roomOfferId":10,"score":90,"reasons":["有房"]}]}
                        """));

        AgentReply reply = workflow.handleMessage(new AgentSession("user"), "安静");
        assertTrue(reply.hotelCards().get(0).reasons().stream().anyMatch(reason ->
                reason.contains("5.0% 同意、95.0% 反对") && reason.contains("符合，以本用户最新相关评价为准")));
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(4)).call(prompts.capture());
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(20, mapper.readTree(prompts.getAllValues().get(1).getInstructions().get(1).getText())
                .path("reviews").size());
        assertEquals(1, mapper.readTree(prompts.getAllValues().get(2).getInstructions().get(1).getText())
                .path("reviews").size());
    }

    @Test
    void rejectsRankingThatSelectsAFilteredOutRoom() {
        LocalDate checkIn = LocalDate.parse("2026-12-10");
        LocalDate checkOut = LocalDate.parse("2026-12-12");
        Hotel eligible = hotel(1, "First");
        when(hotels.findAvailable("杭州市", checkIn, checkOut, 2L)).thenReturn(List.of(eligible));
        when(rooms.findAvailableForStay(List.of(1L), checkIn, checkOut, 2L))
                .thenReturn(List.of(room(10, 1, "180"), room(11, 1, "250")));
        when(model.call(any(Prompt.class)))
                .thenReturn(response("""
                        {"patch":{"city":"杭州市","checkIn":"2026-12-10","checkOut":"2026-12-12",
                        "priorityMaxPricePerNight":200},"decision":"NONE"}
                        """))
                .thenReturn(response("""
                        {"rankedHotels":[{"hotelId":1,"roomOfferId":11,"score":90,"reasons":["不合格房型"]}]}
                        """));

        assertThrows(RuntimeException.class,
                () -> workflow.handleMessage(new AgentSession("user"), "每晚最高200"));
    }

    @Test
    void unconfirmedAndTightenedPriceChangesAreNotRecordedAsRelaxations() {
        LocalDate checkIn = LocalDate.parse("2026-12-10");
        LocalDate checkOut = LocalDate.parse("2026-12-12");
        Hotel first = hotel(1, "First");
        Hotel second = hotel(2, "Second");
        when(hotels.findAvailable("杭州市", checkIn, checkOut, 2L)).thenReturn(List.of(first, second));
        when(rooms.findAvailableForStay(List.of(1L, 2L), checkIn, checkOut, 2L))
                .thenReturn(List.of(room(10, 1, "80"), room(20, 2, "150")));
        when(hotels.findById(1)).thenReturn(Optional.of(first));
        when(model.call(any(Prompt.class)))
                .thenReturn(response("""
                        {"patch":{"city":"杭州市","checkIn":"2026-12-10","checkOut":"2026-12-12",
                        "priorityMaxPricePerNight":100},"decision":"NONE"}
                        """))
                .thenReturn(response("""
                        {"rankedHotels":[{"hotelId":1,"roomOfferId":10,"score":90,"reasons":["符合预算"]}]}
                        """))
                .thenReturn(response("{\"patch\":{},\"decision\":\"NONE\"}"))
                .thenReturn(response("{\"patch\":{\"priorityMaxPricePerNight\":90},\"decision\":\"CHANGE\"}"))
                .thenReturn(response("""
                        {"rankedHotels":[{"hotelId":1,"roomOfferId":10,"score":90,"reasons":["符合预算"]}]}
                        """));
        AgentSession session = new AgentSession("user");
        workflow.handleMessage(session, "预算100");

        AgentReply unconfirmed = workflow.handleMessage(session, "还没决定");
        assertEquals("priorityPrice", session.getPendingRelaxation());
        assertTrue(unconfirmed.requirements().getAppliedRelaxations().isEmpty());
        AgentReply tightened = workflow.handleMessage(session, "改成90");
        assertEquals(new BigDecimal("90"), tightened.requirements().getMaxPricePerNight());
        assertTrue(tightened.requirements().getAppliedRelaxations().isEmpty());
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private Hotel hotel(long id, String name) {
        return new Hotel(id, name, "杭州市", "Central", 4.5,
                null, null, "", null, new BigDecimal("114.1"), new BigDecimal("22.3"),
                null, "杭州市", "Central", 2010, 2019, null,
                true, false, false, null, null, null, null, null, null);
    }

    private RoomOffer room(long id, long hotelId, String price) {
        return new RoomOffer(id, hotelId, "King", new BigDecimal(price), true,
                LocalDate.parse("2026-12-10"), LocalDate.parse("2026-12-11"), true, true);
    }
}

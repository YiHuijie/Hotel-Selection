package edu.polyu.comp5511.hotelselection.service;

import edu.polyu.comp5511.hotelselection.domain.AgentSession;
import edu.polyu.comp5511.hotelselection.domain.AgentState;
import edu.polyu.comp5511.hotelselection.domain.Hotel;
import edu.polyu.comp5511.hotelselection.domain.HotelCandidate;
import edu.polyu.comp5511.hotelselection.domain.HotelRecommendation;
import edu.polyu.comp5511.hotelselection.domain.RoomOffer;
import edu.polyu.comp5511.hotelselection.persistence.HotelMapper;
import edu.polyu.comp5511.hotelselection.persistence.ReviewMapper;
import edu.polyu.comp5511.hotelselection.persistence.RoomOfferMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HotelSkillServiceTest {
    private final HotelMapper hotelMapper = mock(HotelMapper.class);
    private final RoomOfferMapper roomOfferMapper = mock(RoomOfferMapper.class);
    private final ReviewMapper reviewMapper = mock(ReviewMapper.class);
    private final HotelSkillService skills = new HotelSkillService(
            hotelMapper, roomOfferMapper, reviewMapper, mock(DistanceProvider.class));

    @Test
    void normalizesCitiesAndClarifiesUnsupportedNamesWithoutKeepingTheOldCity() {
        AgentSession session = new AgentSession("demo-user");
        for (String city : List.of("杭州", "杭州市")) {
            RequirementUpdateResult result = skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                    city, null, null, null, null, null, null, null, null, null));
            assertEquals("杭州市", session.getRequirements().getCity());
            assertEquals(null, result.clarificationQuestion());
        }
        for (String city : List.of("不存在", "恩施土家族苗族自治州", "杭州市市")) {
            session.getRequirements().setCity("杭州市");
            RequirementUpdateResult result = skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                    city, null, null, null, null, null, null, null, null, null));
            assertEquals(null, session.getRequirements().getCity());
            assertTrue(result.clarificationQuestion().contains(city));
            assertEquals(AgentState.CLARIFICATION_REQUIRED, session.getState());
        }
        RequirementUpdateResult blank = skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                "  ", null, null, null, null, null, null, null, null, null));
        assertTrue(blank.clarificationQuestion().contains("目的地城市"));
    }

    @Test
    void revalidatesStoredCityWithoutAHotelDatabaseLookup() {
        AgentSession session = new AgentSession("demo-user");
        session.getRequirements().setCity("杭州");
        RequirementAnalysisInput unchanged = new RequirementAnalysisInput(
                null, null, null, null, null, null, null, null, null, null);
        skills.applyRequirementAnalysis(session, unchanged);
        assertEquals("杭州市", session.getRequirements().getCity());

    }

    @Test
    void recognizesMainlandCitiesAndHongKongMacaoAndTaiwanWithoutHotelRecords() {
        Map<String, String> examples = Map.ofEntries(
                Map.entry("深圳", "深圳市"), Map.entry("昆山", "昆山市"),
                Map.entry("天津", "天津市"), Map.entry("津市", "津市市"),
                Map.entry("白杨", "白杨市"), Map.entry("芒市", "芒市"),
                Map.entry("香港特别行政区", "香港"), Map.entry("hong kong", "香港"),
                Map.entry("澳门", "澳门"), Map.entry("Macau", "澳门"),
                Map.entry("澳門特別行政區", "澳门"),
                Map.entry("臺北", "台北市"), Map.entry("桃園市", "桃园市"),
                Map.entry("花莲", "花莲市"), Map.entry("馬公", "马公市"));
        examples.forEach((input, expected) -> {
            AgentSession session = new AgentSession("demo-user");
            RequirementUpdateResult result = skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                    input, null, null, null, null, null, null, null, null, null));
            assertEquals(expected, session.getRequirements().getCity(), input);
            assertEquals(null, result.clarificationQuestion(), input);
        });
        org.mockito.Mockito.verifyNoInteractions(hotelMapper);
    }

    @Test
    void filtersBudgetAndAmenitiesOnTheSameRoomOffer() {
        AgentSession session = new AgentSession("demo-user");
        skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                "杭州市", "2026-12-10", "2026-12-12",
                new BigDecimal("200"), new BigDecimal("240"), false,
                List.of("gym", "breakfast"), List.of(), List.of(), null));
        Hotel hotel = hotel(1, "Within budget", "gym");
        HotelCandidate candidate = new HotelCandidate(hotel, List.of(
                room(10, 1, "Budget room", "180", false),
                room(11, 1, "Breakfast room", "220", true)));

        List<HotelCandidate> eligible = skills.filterWorkflowCandidates(
                session.getRequirements(), List.of(candidate)).candidates();

        assertEquals(List.of(), eligible);
        assertEquals(new BigDecimal("240"), session.getRequirements().getPreferredMaxPricePerNight());
    }

    @Test
    void workflowFiltersGeneralConditionsOnSameRoomAndSeparatesUnknown() {
        AgentSession session = new AgentSession("demo-user");
        skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                "杭州市", "2026-12-10", "2026-12-12",
                null, new BigDecimal("200"), false,
                List.of(), List.of("breakfast"), List.of(), null));
        HotelCandidate first = new HotelCandidate(hotel(1, "First", "gym"), List.of(
                room(10, 1, "Affordable", "180", false),
                room(11, 1, "Breakfast", "250", true)));
        HotelCandidate second = new HotelCandidate(hotel(2, "Second", "gym"), List.of(
                room(20, 2, "Unknown price", null, true)));
        HotelCandidate third = new HotelCandidate(hotel(3, "Third", "gym"), List.of(
                room(30, 3, "Both", "190", true)));

        var result = skills.filterWorkflowCandidates(session.getRequirements(), List.of(first, second, third));

        assertEquals(List.of(3L), result.candidates().stream().map(c -> c.hotel().id()).toList());
        assertEquals(List.of(2L), result.unknown().stream().map(c -> c.hotel().id()).toList());
        assertEquals(List.of(
                new HotelSkillService.WorkflowRelaxationOption("generalPrice", null, 2),
                new HotelSkillService.WorkflowRelaxationOption("generalAmenity", "breakfast", 2)),
                result.relaxationOptions());
    }

    @Test
    void workflowDoesNotSuggestRelaxationBasedOnlyOnUnknownOrFewInventoryHotels() {
        AgentSession session = new AgentSession("demo-user");
        skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                "杭州市", "2026-12-10", "2026-12-12",
                new BigDecimal("200"), null, false, List.of(), List.of(), List.of(), null));
        HotelCandidate known = new HotelCandidate(hotel(1, "Known", "gym"),
                List.of(room(10, 1, "Known", "180", true)));
        HotelCandidate unknown = new HotelCandidate(hotel(2, "Unknown", "gym"),
                List.of(room(20, 2, "Unknown", null, true)));

        var result = skills.filterWorkflowCandidates(session.getRequirements(), List.of(known, unknown));

        assertEquals(1, result.candidates().size());
        assertEquals(1, result.unknown().size());
        assertTrue(result.relaxationOptions().isEmpty());
    }

    @Test
    void workflowFiltersMinimumPricesAndRestoresTieredPreferences() {
        AgentSession session = new AgentSession("demo-user");
        skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                "杭州市", "2026-12-10", "2026-12-12",
                null, null, false, List.of(), List.of(), List.of(), null));
        session.getRequirements().setPreferredMinPricePerNight(new BigDecimal("150"));
        session.getRequirements().setSecondaryMaxPricePerNight(new BigDecimal("250"));
        session.getRequirements().getPriorityReviewPreferences().add("安静");
        session.getRequirements().getSecondaryAmenities().add("gym");
        AgentSession restored = new AgentSession(session.getId(), session.getUserId(),
                session.getRequirements(), session.getState(), session.getPendingRelaxation());
        HotelCandidate cheap = new HotelCandidate(hotel(1, "Cheap", "gym"),
                List.of(room(10, 1, "Cheap", "120", true)));
        HotelCandidate eligible = new HotelCandidate(hotel(2, "Eligible", "gym"),
                List.of(room(20, 2, "Eligible", "180", true)));

        var result = skills.filterWorkflowCandidates(restored.getRequirements(), List.of(cheap, eligible));

        assertEquals(List.of(2L), result.candidates().stream().map(c -> c.hotel().id()).toList());
        assertEquals(List.of(new HotelSkillService.WorkflowRelaxationOption("generalMinPrice", null, 2)),
                result.relaxationOptions());
        assertEquals(new BigDecimal("250"), restored.getRequirements().getSecondaryMaxPricePerNight());
        assertEquals(List.of("安静"), restored.getRequirements().getPriorityReviewPreferences());
        assertEquals(List.of("gym"), restored.getRequirements().getSecondaryAmenities());
    }

    @Test
    void tieredRequirementPatchPersistsAndClarifiesConflictingPrices() {
        AgentSession session = new AgentSession("demo-user");
        RequirementUpdateResult update = skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                "杭州市", "2026-12-10", "2026-12-12",
                new BigDecimal("200"), null, false, List.of("gym"), List.of(), List.of(), null,
                null, null, List.of("卫生"), new BigDecimal("210"), null,
                null, new BigDecimal("250"), List.of("安静"), List.of("隔音"), List.of("breakfast")));

        assertTrue(update.clarificationQuestion().contains("价格范围"));
        assertEquals(AgentState.CLARIFICATION_REQUIRED, session.getState());
        AgentSession restored = new AgentSession(session.getId(), session.getUserId(),
                session.getRequirements(), session.getState(), session.getPendingRelaxation());
        assertEquals(new BigDecimal("210"), restored.getRequirements().getMinPricePerNight());
        assertEquals(List.of("安静"), restored.getRequirements().getPriorityReviewPreferences());
        assertEquals(List.of("隔音"), restored.getRequirements().getSecondaryReviewPreferences());
        assertEquals(List.of("breakfast"), restored.getRequirements().getSecondaryAmenities());
    }

    @Test
    void workflowUsesKnownFalseRatherThanUnknownAsRelaxationEvidence() {
        AgentSession session = new AgentSession("demo-user");
        skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                "杭州市", "2026-12-10", "2026-12-12",
                new BigDecimal("200"), null, false, List.of("breakfast"), List.of(), List.of(), null));
        HotelCandidate unknownBreakfast = new HotelCandidate(hotel(1, "Unknown", "gym"),
                List.of(new RoomOffer(10, 1, "Unknown", new BigDecimal("180"), null,
                        LocalDate.parse("2026-12-10"), LocalDate.parse("2026-12-11"), true, true)));
        HotelCandidate unknownPrice = new HotelCandidate(hotel(2, "Unknown price", "gym"),
                List.of(room(20, 2, "Unknown", null, true)));
        HotelCandidate eligible = new HotelCandidate(hotel(3, "Eligible", "gym"),
                List.of(room(30, 3, "Eligible", "180", true)));

        var result = skills.filterWorkflowCandidates(
                session.getRequirements(), List.of(unknownBreakfast, unknownPrice, eligible));

        assertEquals(1, result.candidates().size());
        assertEquals(2, result.unknown().size());
        assertTrue(result.relaxationOptions().isEmpty());
    }

    @Test
    void asksForClarificationWhenDateRangeIsInvalid() {
        AgentSession session = new AgentSession("demo-user");

        RequirementUpdateResult result = skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                "杭州市", "2026-12-12", "2026-12-10",
                null, null, null, null, null, null, null));

        assertEquals("退房日期必须晚于入住日期，请确认入住和退房日期。", result.clarificationQuestion());
        assertEquals(List.of("checkIn", "checkOut"), result.missingFields());
        assertEquals("杭州市", session.getRequirements().getCity());
        assertEquals(AgentState.CLARIFICATION_REQUIRED, session.getState());
    }

    @Test
    void distinguishesReviewPreferencesFromHardAmenities() {
        AgentSession session = new AgentSession("demo-user");
        skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                "杭州市", null, null, null, null, null,
                null, List.of("breakfast", "安静", "卫生"), null, null));

        assertEquals(List.of("breakfast"), session.getRequirements().getPreferredAmenities());
        assertEquals(List.of("安静", "卫生"), session.getRequirements().getReviewPreferences());
        assertThrows(ResponseStatusException.class, () -> skills.applyRequirementAnalysis(session,
                new RequirementAnalysisInput(null, null, null, null, null, null,
                        List.of("安静"), null, null, null)));
    }

    @Test
    void recommendationCardSeparatesMetUnmetAndUnknownPreferences() {
        AgentSession session = new AgentSession("demo-user");
        skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                "杭州市", "2026-12-10", "2026-12-12",
                new BigDecimal("200"), new BigDecimal("150"), true,
                List.of("towels"), List.of("breakfast", "parking", "gym"), null, null));
        Hotel hotel = new Hotel(1, "Hotel", "杭州市", "Central", 4.5,
                null, null, "gym", null, null, null, null, null, null,
                null, null, null, false, null, null, null, null,
                null, null, null, null);
        RoomOffer room = new RoomOffer(10, 1, "King", new BigDecimal("180"),
                null, LocalDate.parse("2026-12-10"), LocalDate.parse("2026-12-11"), true, true);
        HotelCandidate candidate = new HotelCandidate(hotel, List.of(room));
        HotelRecommendation recommendation = new HotelRecommendation(
                1, 10, "King", "Hotel", "杭州市", "Central", 4.5,
                new BigDecimal("180"), null, true, true, "gym", 90, 0.8, List.of(),
                List.of("符合排序证据"));
        when(hotelMapper.findById(1)).thenReturn(Optional.of(hotel));

        var card = skills.getRecommendationCards(session.getRequirements(), List.of(candidate),
                List.of(1L), List.of(recommendation)).get(0);

        assertTrue(card.satisfiedRequiredConditions().contains("必需设施：毛巾"));
        assertTrue(card.unmetPreferences().contains("偏好预算：房价 180 高于 150"));
        assertTrue(card.unmetPreferences().contains("偏好设施：健身房"));
        assertTrue(card.unknownPreferences().contains("靠近公共交通：缺少可核验的站点距离数据"));
        assertTrue(card.unknownPreferences().contains("偏好设施：早餐（数据未知）"));
        assertTrue(card.unknownPreferences().contains("偏好设施：停车场（数据未知）"));
    }

    @Test
    void rejectsRankingsThatReferenceAHotelOrRoomOutsideTheEligibleSet() {
        AgentSession session = new AgentSession("demo-user");
        skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                "杭州市", "2026-12-10", "2026-12-12",
                null, null, null, List.of("gym"), null, null, null));
        List<HotelCandidate> eligible = List.of(
                new HotelCandidate(hotel(1, "Eligible", "gym"), List.of(room(10, 1, "King", "180", false))));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> skills.validateRanking(eligible,
                        new RankingInput(List.of(new RankingInput.RankedHotelInput(2, 20, 95, List.of("good")))),
                        Map.of()));

        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void validatesHotelAndRoomAgainstCurrentCandidatesAndHardRequirements() {
        AgentSession session = new AgentSession("demo-user");
        skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                "杭州市", "2026-12-10", "2026-12-12",
                new BigDecimal("200"), null, null, List.of("gym", "breakfast"), null, null, null));
        HotelCandidate candidate = new HotelCandidate(hotel(1, "Eligible", "gym"),
                List.of(room(10, 1, "King", "180", true)));
        List<HotelRecommendation> ranked = skills.validateRanking(
                List.of(candidate),
                new RankingInput(List.of(new RankingInput.RankedHotelInput(
                        1, 10, 90, List.of("meets requirements")))),
                Map.of());

        RecommendationValidationResult result = skills.validateFinalRecommendations(
                session.getRequirements(), List.of(candidate), ranked, List.of(1L));

        assertEquals("VERIFIED", result.status());
        assertEquals(10, result.recommendations().get(0).roomOfferId());
        assertEquals(List.of("city=杭州市", "availability=2026-12-10 to 2026-12-12",
                        "pricePerNight=180 <= 200", "requiredAmenity=gym", "requiredAmenity=breakfast"),
                result.recommendations().get(0).satisfiedHardConstraints());
    }

    @Test
    void rejectsFinalRecommendationThatDiffersFromTheRankedDatabaseResults() {
        AgentSession session = new AgentSession("demo-user");
        skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                "杭州市", "2026-12-10", "2026-12-12",
                null, null, null, null, null, null, null));
        HotelCandidate candidate = new HotelCandidate(hotel(1, "Eligible", "gym"),
                List.of(room(10, 1, "King", "180", false)));
        List<HotelRecommendation> ranked = skills.validateRanking(
                List.of(candidate),
                new RankingInput(List.of(new RankingInput.RankedHotelInput(1, 10, 90, List.of("eligible")))),
                Map.of());

        RecommendationValidationResult result = skills.validateFinalRecommendations(
                session.getRequirements(), List.of(candidate), ranked, List.of(2L));

        assertEquals("REJECTED", result.status());
        assertEquals(List.of(2L), result.rejectedHotelIds());
    }

    @Test
    void finalValidationRejectsUnknownPriceWhenBudgetIsHard() {
        AgentSession session = new AgentSession("demo-user");
        skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                "杭州市", "2026-12-10", "2026-12-12",
                new BigDecimal("200"), null, null, null, null, null, null));
        HotelCandidate candidate = new HotelCandidate(hotel(2, "Unknown price", "gym"),
                List.of(room(20, 2, "King", null, false)));
        HotelRecommendation ranked = new HotelRecommendation(
                2, 20, "King", "Unknown price", "杭州市", "Central, 杭州市", 4.5,
                null, false, true, true, "gym", 90, 0.8, List.of(), List.of("price unknown"));

        RecommendationValidationResult result = skills.validateFinalRecommendations(
                session.getRequirements(), List.of(candidate), List.of(ranked), List.of(2L));

        assertEquals("REJECTED", result.status());
    }

    @Test
    void finalValidationChecksGeneralPriceAndFacilitiesOnTheSelectedRoom() {
        AgentSession session = new AgentSession("demo-user");
        skills.applyRequirementAnalysis(session, new RequirementAnalysisInput(
                "杭州市", "2026-12-10", "2026-12-12",
                null, new BigDecimal("200"), null, null, List.of("breakfast"), null, null));
        Hotel hotel = hotel(1, "Hotel", "gym");
        for (RoomOffer selected : List.of(
                room(10, 1, "Expensive", "250", true),
                room(11, 1, "No breakfast", "180", false),
                room(12, 1, "Unknown price", null, true))) {
            HotelCandidate candidate = new HotelCandidate(hotel, List.of(selected));
            List<HotelRecommendation> ranked = skills.validateRanking(List.of(candidate),
                    new RankingInput(List.of(new RankingInput.RankedHotelInput(
                            1, selected.id(), 90, List.of("test")))), Map.of());
            assertEquals("REJECTED", skills.validateFinalRecommendations(
                    session.getRequirements(), List.of(candidate), ranked, List.of(1L)).status());
        }
        HotelCandidate candidate = new HotelCandidate(hotel, List.of(room(13, 1, "Eligible", "180", true)));
        List<HotelRecommendation> ranked = skills.validateRanking(List.of(candidate),
                new RankingInput(List.of(new RankingInput.RankedHotelInput(1, 13, 90, List.of("test")))), Map.of());
        assertEquals("VERIFIED", skills.validateFinalRecommendations(
                session.getRequirements(), List.of(candidate), ranked, List.of(1L)).status());
    }

    private Hotel hotel(long id, String name, String amenities) {
        return new Hotel(id, name, "杭州市", "Central, 杭州市", 4.5,
                null, null, amenities, null, new BigDecimal("114.1"), new BigDecimal("22.3"),
                null, "杭州市", "Central", 2010, 2019, null,
                amenities.contains("gym"), false, false, null, null, null, null, null, null);
    }

    private RoomOffer room(long id, long hotelId, String name, String price, boolean breakfast) {
        return new RoomOffer(id, hotelId, name, price == null ? null : new BigDecimal(price),
                breakfast, LocalDate.parse("2026-12-10"), LocalDate.parse("2026-12-11"), true, true);
    }
}

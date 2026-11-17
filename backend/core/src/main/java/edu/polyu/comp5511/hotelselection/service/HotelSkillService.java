package edu.polyu.comp5511.hotelselection.service;

import edu.polyu.comp5511.hotelselection.api.HotelCard;
import edu.polyu.comp5511.hotelselection.domain.AgentSession;
import edu.polyu.comp5511.hotelselection.domain.AgentState;
import edu.polyu.comp5511.hotelselection.domain.DistanceEstimate;
import edu.polyu.comp5511.hotelselection.domain.Hotel;
import edu.polyu.comp5511.hotelselection.domain.HotelCandidate;
import edu.polyu.comp5511.hotelselection.domain.HotelRecommendation;
import edu.polyu.comp5511.hotelselection.domain.ReviewForMatching;
import edu.polyu.comp5511.hotelselection.domain.Requirements;
import edu.polyu.comp5511.hotelselection.domain.RoomOffer;
import edu.polyu.comp5511.hotelselection.persistence.HotelMapper;
import edu.polyu.comp5511.hotelselection.persistence.ReviewMapper;
import edu.polyu.comp5511.hotelselection.persistence.RoomOfferMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class HotelSkillService {
    private static final Set<String> AMENITIES = Set.of(
            "breakfast", "gym", "laundry", "parking", "luggage_storage", "robot_service",
            "restaurant", "taxi_service", "meeting_room", "accessibility", "hair_dryer", "towels");
    private static final Set<String> TRANSPORT_MODES = Set.of("walking", "driving", "transit", "cycling");
    private static final Set<String> CLEARABLE_FIELDS = Set.of(
            "city", "checkIn", "checkOut", "maxPricePerNight", "preferredMaxPricePerNight",
            "minPricePerNight", "preferredMinPricePerNight", "secondaryMinPricePerNight",
            "secondaryMaxPricePerNight", "requiredAmenities", "preferredAmenities",
            "secondaryAmenities", "unverifiedAmenities", "priorityReviewPreferences", "secondaryReviewPreferences",
            "origin", "transportModes", "reviewPreferences");

    private final HotelMapper hotelMapper;
    private final RoomOfferMapper roomOfferMapper;
    private final ReviewMapper reviewMapper;
    private final DistanceProvider distanceProvider;

    public HotelSkillService(
            HotelMapper hotelMapper,
            RoomOfferMapper roomOfferMapper,
            ReviewMapper reviewMapper,
            DistanceProvider distanceProvider) {
        this.hotelMapper = hotelMapper;
        this.roomOfferMapper = roomOfferMapper;
        this.reviewMapper = reviewMapper;
        this.distanceProvider = distanceProvider;
    }

    public boolean isSupportedAmenity(String value) {
        return value != null && AMENITIES.contains(value.strip().toLowerCase(Locale.ROOT));
    }

    public RequirementUpdateResult applyRequirementAnalysis(AgentSession session, RequirementAnalysisInput input) {
        if (input == null) {
            throw badRequest("需求分析结果不能为空。");
        }
        Requirements requirements = session.getRequirements();
        Set<String> clearFields = new HashSet<>(input.clearFields() == null ? List.of() : input.clearFields());
        if (!CLEARABLE_FIELDS.containsAll(clearFields)) {
            throw badRequest("需求更新包含不支持的清除字段。");
        }

        String cityQuestion = null;
        if (!clearFields.contains("city") && (input.city() != null || requirements.getCity() != null)) {
            String city = blankToNull(input.city() != null ? input.city() : requirements.getCity());
            if (city == null) {
                requirements.setCity(null);
                cityQuestion = "请提供有效的目的地城市。";
            } else {
                String normalized = ChinaCities.normalize(city).orElse(null);
                requirements.setCity(normalized);
                if (normalized == null) {
                    cityQuestion = "无法识别目的地城市“" + city + "”，请确认城市名称。";
                }
            }
        }
        if (clearFields.contains("city")) {
            requirements.setCity(null);
        }
        LocalDate checkIn = requirements.getCheckIn();
        LocalDate checkOut = requirements.getCheckOut();
        List<String> invalidDates = new ArrayList<>();
        try {
            if (input.checkIn() != null) checkIn = LocalDate.parse(input.checkIn());
        } catch (DateTimeParseException exception) {
            checkIn = null;
            invalidDates.add("入住日期");
        }
        try {
            if (input.checkOut() != null) checkOut = LocalDate.parse(input.checkOut());
        } catch (DateTimeParseException exception) {
            checkOut = null;
            invalidDates.add("退房日期");
        }
        if (clearFields.contains("checkIn")) checkIn = null;
        if (clearFields.contains("checkOut")) checkOut = null;
        String dateQuestion = invalidDates.isEmpty() ? null
                : "请提供有效的" + String.join("和", invalidDates) + "，日期格式为 YYYY-MM-DD。";
        if (checkIn != null && checkOut != null && !checkOut.isAfter(checkIn)) {
            dateQuestion = "退房日期必须晚于入住日期，请确认入住和退房日期。";
            if (input.checkIn() != null) checkIn = null;
            if (input.checkOut() != null) checkOut = null;
            if (checkIn != null && checkOut != null) {
                checkIn = null;
                checkOut = null;
            }
        }
        requirements.setCheckIn(checkIn);
        requirements.setCheckOut(checkOut);
        if (input.maxPricePerNight() != null) {
            requirements.setMaxPricePerNight(positive(input.maxPricePerNight(), "最高预算"));
        }
        if (clearFields.contains("maxPricePerNight")) {
            requirements.setMaxPricePerNight(null);
        }
        if (input.preferredMaxPricePerNight() != null) {
            requirements.setPreferredMaxPricePerNight(
                    positive(input.preferredMaxPricePerNight(), "偏好预算"));
        }
        if (clearFields.contains("preferredMaxPricePerNight")) {
            requirements.setPreferredMaxPricePerNight(null);
        }
        if (input.minPricePerNight() != null) {
            requirements.setMinPricePerNight(positive(input.minPricePerNight(), "最低预算"));
        }
        if (clearFields.contains("minPricePerNight")) requirements.setMinPricePerNight(null);
        if (input.preferredMinPricePerNight() != null) {
            requirements.setPreferredMinPricePerNight(positive(input.preferredMinPricePerNight(), "一般最低预算"));
        }
        if (clearFields.contains("preferredMinPricePerNight")) requirements.setPreferredMinPricePerNight(null);
        if (input.secondaryMinPricePerNight() != null) {
            requirements.setSecondaryMinPricePerNight(positive(input.secondaryMinPricePerNight(), "次要最低预算"));
        }
        if (clearFields.contains("secondaryMinPricePerNight")) requirements.setSecondaryMinPricePerNight(null);
        if (input.secondaryMaxPricePerNight() != null) {
            requirements.setSecondaryMaxPricePerNight(positive(input.secondaryMaxPricePerNight(), "次要最高预算"));
        }
        if (clearFields.contains("secondaryMaxPricePerNight")) requirements.setSecondaryMaxPricePerNight(null);
        if (input.preferNearTransit() != null) {
            requirements.setPreferNearTransit(input.preferNearTransit());
        }
        if (input.origin() != null) {
            requirements.setOrigin(blankToNull(input.origin()));
        }
        if (clearFields.contains("origin")) {
            requirements.setOrigin(null);
        }
        if (input.requiredAmenities() != null) {
            replaceAmenities(requirements.getRequiredAmenities(), input.requiredAmenities());
        }
        if (clearFields.contains("requiredAmenities")) requirements.getRequiredAmenities().clear();
        if (input.reviewPreferences() != null) {
            replaceReviewPreferences(requirements.getReviewPreferences(), input.reviewPreferences());
        }
        if (input.preferredAmenities() != null) {
            replacePreferredAmenities(requirements, input.preferredAmenities());
        }
        if (clearFields.contains("preferredAmenities")) requirements.getPreferredAmenities().clear();
        if (input.secondaryAmenities() != null) {
            List<String> secondary = normalizeReviewPreferences(input.secondaryAmenities());
            requirements.getSecondaryAmenities().clear();
            requirements.getSecondaryAmenities().addAll(secondary);
        }
        if (clearFields.contains("secondaryAmenities")) requirements.getSecondaryAmenities().clear();
        if (clearFields.contains("unverifiedAmenities")) requirements.getUnverifiedAmenities().clear();
        if (input.priorityReviewPreferences() != null) {
            replaceReviewPreferences(requirements.getPriorityReviewPreferences(), input.priorityReviewPreferences());
        }
        if (clearFields.contains("priorityReviewPreferences")) requirements.getPriorityReviewPreferences().clear();
        if (input.secondaryReviewPreferences() != null) {
            replaceReviewPreferences(requirements.getSecondaryReviewPreferences(), input.secondaryReviewPreferences());
        }
        if (clearFields.contains("secondaryReviewPreferences")) requirements.getSecondaryReviewPreferences().clear();
        if (clearFields.contains("reviewPreferences")) {
            requirements.getReviewPreferences().clear();
        }
        if (input.transportModes() != null) {
            replaceTransportModes(requirements.getTransportModes(), input.transportModes());
        }
        if (clearFields.contains("transportModes")) {
            requirements.getTransportModes().clear();
        }
        if (requirements.getOrigin() != null && requirements.getTransportModes().isEmpty()) {
            requirements.getTransportModes().addAll(List.of("walking", "driving", "transit"));
        }
        requirements.getPreferredAmenities().removeAll(requirements.getRequiredAmenities());

        List<String> missing = new ArrayList<>();
        if (requirements.getCity() == null) {
            missing.add("city");
        }
        if (requirements.getCheckIn() == null) {
            missing.add("checkIn");
        }
        if (requirements.getCheckOut() == null) {
            missing.add("checkOut");
        }
        String question = input.clarificationQuestion() == null || input.clarificationQuestion().isBlank()
                ? null : input.clarificationQuestion().strip();
        if (cityQuestion != null) {
            question = question == null ? cityQuestion : cityQuestion + " " + question;
        }
        if (dateQuestion != null) {
            question = question == null ? dateQuestion : dateQuestion + " " + question;
        }
        BigDecimal minimum = maximum(requirements.getMinPricePerNight(), requirements.getPreferredMinPricePerNight());
        BigDecimal maximum = minimum(requirements.getMaxPricePerNight(), requirements.getPreferredMaxPricePerNight());
        if ((minimum != null && maximum != null && minimum.compareTo(maximum) > 0)
                || (requirements.getSecondaryMinPricePerNight() != null
                && requirements.getSecondaryMaxPricePerNight() != null
                && requirements.getSecondaryMinPricePerNight().compareTo(
                        requirements.getSecondaryMaxPricePerNight()) > 0)) {
            String priceQuestion = "最低价格高于最高价格，请确认价格范围。";
            question = question == null ? priceQuestion : priceQuestion + " " + question;
        }
        boolean pendingClarification = session.getPendingRelaxation() != null;
        session.setState(missing.isEmpty() && question == null && !pendingClarification
                ? AgentState.COLLECTING_REQUIREMENTS : AgentState.CLARIFICATION_REQUIRED);
        return new RequirementUpdateResult(List.copyOf(missing), question);
    }

    public List<HotelCandidate> listAvailable(AgentSession session) {
        Requirements requirements = session.getRequirements();
        requireSearchRequirements(requirements);
        session.setState(AgentState.SEARCHING);
        try {
            long nightCount = ChronoUnit.DAYS.between(requirements.getCheckIn(), requirements.getCheckOut());
            List<Hotel> hotels = hotelMapper.findAvailable(
                    requirements.getCity(), requirements.getCheckIn(), requirements.getCheckOut(), nightCount);
            if (hotels.isEmpty()) {
                session.setState(AgentState.OBSERVING_RESULTS);
                return List.of();
            }
            List<Long> hotelIds = hotels.stream().map(Hotel::id).toList();
            List<RoomOffer> offers = roomOfferMapper.findAvailableForStay(
                    hotelIds, requirements.getCheckIn(), requirements.getCheckOut(), nightCount);
            Map<Long, List<RoomOffer>> offersByHotel = new HashMap<>();
            offers.forEach(offer -> offersByHotel.computeIfAbsent(offer.hotelId(), ignored -> new ArrayList<>())
                    .add(offer));
            List<HotelCandidate> candidates = hotels.stream()
                    .map(hotel -> new HotelCandidate(hotel, offersByHotel.getOrDefault(hotel.id(), List.of())))
                    .filter(candidate -> !candidate.roomOffers().isEmpty())
                    .toList();
            session.setState(AgentState.OBSERVING_RESULTS);
            return candidates;
        } catch (DataAccessException exception) {
            session.setState(AgentState.ERROR);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "酒店搜索失败，请稍后重试。", exception);
        }
    }

    public WorkflowFilterResult filterWorkflowCandidates(
            Requirements requirements, List<HotelCandidate> inventory) {
        requireSearchRequirements(requirements);
        List<WorkflowCondition> conditions = workflowConditions(requirements);
        List<HotelCandidate> eligible = workflowCandidates(inventory, requirements, conditions, true);
        List<HotelCandidate> unknown = workflowCandidates(inventory, requirements, conditions, null);
        List<WorkflowRelaxationOption> options = new ArrayList<>();
        if (eligible.size() < 3) {
            for (WorkflowCondition ignored : conditions) {
                List<HotelCandidate> alternate = inventory.stream()
                        .map(candidate -> new HotelCandidate(candidate.hotel(), candidate.roomOffers().stream()
                                .filter(room -> Boolean.FALSE.equals(workflowConditionMatches(
                                        candidate.hotel(), room, requirements, ignored)))
                                .filter(room -> Boolean.TRUE.equals(workflowMatch(
                                        candidate.hotel(), room, requirements, conditions, ignored)))
                                .toList()))
                        .filter(candidate -> !candidate.roomOffers().isEmpty())
                        .toList();
                Set<Long> hotelIds = new HashSet<>();
                eligible.forEach(candidate -> hotelIds.add(candidate.hotel().id()));
                alternate.forEach(candidate -> hotelIds.add(candidate.hotel().id()));
                if (hotelIds.size() > eligible.size()) {
                    options.add(new WorkflowRelaxationOption(ignored.kind(), ignored.amenity(), hotelIds.size()));
                }
            }
        }
        return new WorkflowFilterResult(eligible, unknown, List.copyOf(options));
    }

    private List<WorkflowCondition> workflowConditions(Requirements requirements) {
        List<WorkflowCondition> conditions = new ArrayList<>();
        if (requirements.getMaxPricePerNight() != null) {
            conditions.add(new WorkflowCondition("priorityPrice", null));
        }
        if (requirements.getPreferredMaxPricePerNight() != null) {
            conditions.add(new WorkflowCondition("generalPrice", null));
        }
        if (requirements.getMinPricePerNight() != null) {
            conditions.add(new WorkflowCondition("priorityMinPrice", null));
        }
        if (requirements.getPreferredMinPricePerNight() != null) {
            conditions.add(new WorkflowCondition("generalMinPrice", null));
        }
        requirements.getRequiredAmenities().forEach(amenity ->
                conditions.add(new WorkflowCondition("priorityAmenity", amenity)));
        requirements.getPreferredAmenities().forEach(amenity ->
                conditions.add(new WorkflowCondition("generalAmenity", amenity)));
        return conditions;
    }

    private List<HotelCandidate> workflowCandidates(
            List<HotelCandidate> inventory, Requirements requirements,
            List<WorkflowCondition> conditions, Boolean expected) {
        return inventory.stream()
                .map(candidate -> new HotelCandidate(candidate.hotel(), candidate.roomOffers().stream()
                        .filter(room -> Objects.equals(expected, workflowMatch(
                                candidate.hotel(), room, requirements, conditions, null)))
                        .toList()))
                .filter(candidate -> !candidate.roomOffers().isEmpty())
                .toList();
    }

    private Boolean workflowMatch(Hotel hotel, RoomOffer room, Requirements requirements,
            List<WorkflowCondition> conditions, WorkflowCondition ignored) {
        boolean unknown = false;
        for (WorkflowCondition condition : conditions) {
            if (condition.equals(ignored)) continue;
            Boolean matches = workflowConditionMatches(hotel, room, requirements, condition);
            if (Boolean.FALSE.equals(matches)) return false;
            if (matches == null) unknown = true;
        }
        return unknown ? null : true;
    }

    private Boolean workflowConditionMatches(
            Hotel hotel, RoomOffer room, Requirements requirements, WorkflowCondition condition) {
        return switch (condition.kind()) {
            case "priorityPrice", "generalPrice" -> {
                BigDecimal limit = "priorityPrice".equals(condition.kind())
                        ? requirements.getMaxPricePerNight() : requirements.getPreferredMaxPricePerNight();
                yield room.pricePerNight() == null ? null : room.pricePerNight().compareTo(limit) <= 0;
            }
            case "priorityMinPrice", "generalMinPrice" -> {
                BigDecimal limit = "priorityMinPrice".equals(condition.kind())
                        ? requirements.getMinPricePerNight() : requirements.getPreferredMinPricePerNight();
                yield room.pricePerNight() == null ? null : room.pricePerNight().compareTo(limit) >= 0;
            }
            case "priorityAmenity", "generalAmenity" -> amenityAvailability(hotel, room, condition.amenity());
            default -> throw new IllegalStateException("Unsupported workflow condition: " + condition.kind());
        };
    }

    private record WorkflowCondition(String kind, String amenity) {}

    public record WorkflowRelaxationOption(String condition, String amenity, int possibleHotelCount) {}

    public record WorkflowFilterResult(
            List<HotelCandidate> candidates, List<HotelCandidate> unknown,
            List<WorkflowRelaxationOption> relaxationOptions) {
        public WorkflowFilterResult {
            candidates = List.copyOf(candidates);
            unknown = List.copyOf(unknown);
            relaxationOptions = List.copyOf(relaxationOptions);
        }
    }

    public List<ReviewForMatching> findReviewsForMatching(List<Long> hotelIds, String userId) {
        if (hotelIds.isEmpty()) {
            return List.of();
        }
        try {
            return List.copyOf(reviewMapper.findReviewsForMatching(hotelIds, userId));
        } catch (DataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "酒店评价读取失败。", exception);
        }
    }

    public List<HotelCard> getRecommendationCards(
            Requirements requirements,
            List<HotelCandidate> candidates,
            List<Long> hotelIds,
            List<HotelRecommendation> validatedRecommendations) {
        if (validatedRecommendations == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "缺少已验证的酒店推荐，无法生成酒店卡片。");
        }
        List<Long> validatedIds = validatedRecommendations.stream()
                .map(HotelRecommendation::hotelId)
                .toList();
        if (hotelIds == null || hotelIds.isEmpty() || hotelIds.size() > 3
                || !hotelIds.equals(validatedIds)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "最终 JSON 中的酒店必须与已验证推荐及顺序一致。");
        }

        Map<Long, HotelRecommendation> recommendationById = new HashMap<>();
        validatedRecommendations.forEach(recommendation ->
                recommendationById.put(recommendation.hotelId(), recommendation));
        Map<Long, HotelCandidate> candidatesById = new HashMap<>();
        candidates.forEach(candidate -> candidatesById.put(candidate.hotel().id(), candidate));
        try {
            return hotelIds.stream()
                    .map(hotelId -> {
                        Hotel hotel = hotelMapper.findById(hotelId)
                                .orElseThrow(() -> new ResponseStatusException(
                                        HttpStatus.SERVICE_UNAVAILABLE, "推荐酒店详情暂时无法读取。"));
                        HotelRecommendation recommendation = recommendationById.get(hotelId);
                        HotelCandidate candidate = candidatesById.get(hotelId);
                        if (candidate == null) {
                            throw new ResponseStatusException(
                                    HttpStatus.BAD_GATEWAY, "推荐酒店不属于当前候选集，无法生成条件证据。");
                        }
                        RoomOffer room = candidate.roomOffers().stream()
                                .filter(offer -> offer.id() == recommendation.roomOfferId())
                                .findFirst()
                                .orElseThrow(() -> new ResponseStatusException(
                                        HttpStatus.BAD_GATEWAY, "推荐房型不属于当前候选集，无法生成条件证据。"));
                        PreferenceEvidence preferences = preferenceEvidence(requirements, candidate.hotel(), room);
                        return new HotelCard(
                                hotel.id(), hotel.name(), hotel.city(), hotel.address(), hotel.image(),
                                hotel.rating(), recommendation.roomName(), recommendation.pricePerNight(),
                                recommendation.score(), recommendation.reasons(), recommendation.routes(),
                                satisfiedRequiredConditions(requirements, candidate.hotel(), room),
                                preferences.satisfied(), preferences.unmet(), preferences.unknown());
                    })
                    .toList();
        } catch (DataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "推荐酒店详情读取失败，请稍后重试。", exception);
        }
    }

    public DistanceSkillResult getDistances(
            AgentSession session,
            DistanceCalculationInput input,
            List<HotelCandidate> candidates) {
        Requirements requirements = session.getRequirements();
        if (input == null || requirements.getOrigin() == null || requirements.getTransportModes().isEmpty()
                || !requirements.getOrigin().equals(input.origin())
                || !requirements.getTransportModes().equals(input.transportModes())) {
            throw badRequest("路线查询必须使用当前结构化需求中明确提供的起点和交通方式。");
        }
        List<Hotel> hotels = candidates.stream().map(HotelCandidate::hotel).toList();
        if (hotels.isEmpty()) {
            return new DistanceSkillResult("amap", false, List.of(), "没有可查询的候选酒店。");
        }
        try {
            List<DistanceEstimate> providerResults = distanceProvider.getRoutes(
                    requirements.getOrigin(), requirements.getCity(), requirements.getTransportModes(), hotels);
            if (providerResults == null) {
                throw new DistanceProviderException("距离服务未返回结果。");
            }
            Set<Long> candidateIds = hotels.stream().map(Hotel::id).collect(java.util.stream.Collectors.toSet());
            List<DistanceEstimate> validated = providerResults.stream()
                    .filter(Objects::nonNull)
                    .filter(estimate -> candidateIds.contains(estimate.hotelId()))
                    .filter(estimate -> requirements.getTransportModes().contains(estimate.transportMode()))
                    .filter(estimate -> Double.isFinite(estimate.distanceMeters()) && estimate.distanceMeters() >= 0)
                    .filter(estimate -> Double.isFinite(estimate.durationSeconds()) && estimate.durationSeconds() >= 0)
                    .toList();
            if (validated.size() != providerResults.size()) {
                throw new DistanceProviderException("距离服务返回了与请求候选不匹配的数据。");
            }
            return new DistanceSkillResult("amap", !validated.isEmpty(), validated,
                    validated.isEmpty() ? "高德未返回可用路线。" : "路线由高德地图 MCP 返回。");
        } catch (DistanceProviderException exception) {
            return new DistanceSkillResult("amap", false, List.of(),
                    "高德路线查询失败或不可用：" + exception.getMessage());
        }
    }

    public List<HotelRecommendation> validateRanking(
            List<HotelCandidate> eligibleHotels,
            RankingInput input,
            Map<Long, List<DistanceEstimate>> distances) {
        if (input == null || input.rankedHotels() == null || input.rankedHotels().isEmpty()) {
            throw badRequest("排序结果不能为空。");
        }
        int expectedCount = Math.min(3, eligibleHotels.size());
        if (input.rankedHotels().size() != expectedCount) {
            throw badRequest("排序结果必须包含所有可推荐酒店（最多三家）。");
        }

        Map<Long, HotelCandidate> eligibleById = new HashMap<>();
        eligibleHotels.forEach(candidate -> eligibleById.put(candidate.hotel().id(), candidate));
        Set<Long> selectedIds = new HashSet<>();
        List<HotelRecommendation> recommendations = new ArrayList<>();
        for (RankingInput.RankedHotelInput ranked : input.rankedHotels()) {
            if (ranked == null) {
                throw badRequest("排序结果包含无效酒店。");
            }
            HotelCandidate candidate = eligibleById.get(ranked.hotelId());
            if (candidate == null || !selectedIds.add(ranked.hotelId())) {
                throw badRequest("排序结果只能引用不同的硬条件合格候选酒店。");
            }
            RoomOffer room = candidate.roomOffers().stream()
                    .filter(offer -> offer.id() == ranked.roomOfferId())
                    .findFirst()
                    .orElseThrow(() -> badRequest("排序结果引用的房型不属于该酒店的可订硬条件合格房型。"));
            if (!Double.isFinite(ranked.score()) || ranked.score() < 0 || ranked.score() > 100) {
                throw badRequest("酒店评分必须介于 0 到 100。");
            }
            if (ranked.reasons() == null || ranked.reasons().isEmpty()
                    || ranked.reasons().stream().anyMatch(reason -> reason == null || reason.isBlank())) {
                throw badRequest("每家推荐酒店都必须提供非空理由。");
            }
            Hotel hotel = candidate.hotel();
            recommendations.add(new HotelRecommendation(
                    hotel.id(), room.id(), room.roomName(), hotel.name(), hotel.city(), hotel.address(),
                    hotel.rating(), room.pricePerNight(), room.breakfast(), room.hairDryer(), room.towels(),
                    hotel.amenities(), ranked.score(), dataCompleteness(hotel, room),
                    distances.getOrDefault(hotel.id(), List.of()), List.copyOf(ranked.reasons())));
        }
        return List.copyOf(recommendations);
    }

    public RecommendationValidationResult validateFinalRecommendations(
            Requirements requirements,
            List<HotelCandidate> candidates,
            List<HotelRecommendation> rankedRecommendations,
            List<Long> hotelIds) {
        List<Long> suppliedIds = hotelIds == null ? List.of()
                : hotelIds.stream().filter(Objects::nonNull).toList();
        if (hotelIds == null || hotelIds.isEmpty() || hotelIds.size() > 3
                || suppliedIds.size() != hotelIds.size()
                || new HashSet<>(suppliedIds).size() != hotelIds.size()) {
            return RecommendationValidationResult.rejected(
                    "最终推荐必须包含 1 到 3 个互不重复的有效酒店 ID。", suppliedIds);
        }
        List<Long> rankedIds = rankedRecommendations.stream().map(HotelRecommendation::hotelId).toList();
        if (!hotelIds.equals(rankedIds)) {
            return RecommendationValidationResult.rejected(
                    "最终推荐酒店 ID 必须与已通过排序的结果及顺序完全一致。", hotelIds);
        }

        Map<Long, HotelCandidate> candidatesById = new HashMap<>();
        candidates.forEach(candidate -> candidatesById.put(candidate.hotel().id(), candidate));
        Map<Long, HotelRecommendation> recommendationsById = new HashMap<>();
        rankedRecommendations.forEach(recommendation ->
                recommendationsById.put(recommendation.hotelId(), recommendation));
        List<WorkflowCondition> conditions = workflowConditions(requirements);
        List<Long> rejectedIds = hotelIds.stream()
                .filter(id -> {
                    HotelCandidate candidate = candidatesById.get(id);
                    HotelRecommendation recommendation = recommendationsById.get(id);
                    if (candidate == null || recommendation == null
                            || (requirements.getCity() != null
                            && !requirements.getCity().equalsIgnoreCase(candidate.hotel().city()))) {
                        return true;
                    }
                    return candidate.roomOffers().stream()
                            .filter(room -> room.id() == recommendation.roomOfferId())
                            .noneMatch(room -> Boolean.TRUE.equals(workflowMatch(
                                    candidate.hotel(), room, requirements, conditions, null)));
                })
                .toList();
        if (!rejectedIds.isEmpty()) {
            return RecommendationValidationResult.rejected(
                    "部分酒店或房型不是当前可订候选，或不满足当前结构化硬条件。", rejectedIds);
        }

        List<RecommendationValidationResult.VerifiedRecommendation> verified = hotelIds.stream()
                .map(id -> {
                    HotelCandidate candidate = candidatesById.get(id);
                    HotelRecommendation recommendation = recommendationsById.get(id);
                    RoomOffer room = candidate.roomOffers().stream()
                            .filter(offer -> offer.id() == recommendation.roomOfferId())
                            .findFirst()
                            .orElseThrow();
                    return new RecommendationValidationResult.VerifiedRecommendation(
                            candidate.hotel().id(), room.id(), room.roomName(), candidate.hotel().name(),
                            candidate.hotel().city(), room.pricePerNight(), room.breakfast(),
                            room.hairDryer(), room.towels(), satisfiedHardConstraints(
                                    requirements, candidate.hotel(), room));
                })
                .toList();
        return new RecommendationValidationResult(
                "VERIFIED", "所有最终推荐的酒店和房型均属于当前可订候选并满足结构化硬条件。",
                verified, List.of());
    }

    public void saveFeedback(AgentSession session, long hotelId, int rating, String comment) {
        if (hotelMapper.findById(hotelId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该酒店。");
        }
        reviewMapper.insert(hotelId, session.getUserId(), rating, comment);
    }

    private String formatDateRange(LocalDate checkIn, LocalDate checkOut) {
        return Objects.toString(checkIn, "未设置") + " 至 " + Objects.toString(checkOut, "未设置");
    }

    private void requireSearchRequirements(Requirements requirements) {
        if (requirements.getCity() == null || requirements.getCheckIn() == null
                || requirements.getCheckOut() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先补齐城市和入住日期后再搜索。");
        }
        if (!requirements.getCheckOut().isAfter(requirements.getCheckIn())) {
            throw badRequest("退房日期必须晚于入住日期。");
        }
    }

    private List<String> satisfiedHardConstraints(Requirements requirements, Hotel hotel, RoomOffer room) {
        List<String> satisfied = new ArrayList<>();
        if (requirements.getCity() != null) {
            satisfied.add("city=" + hotel.city());
        }
        satisfied.add("availability=" + requirements.getCheckIn() + " to " + requirements.getCheckOut());
        if (requirements.getMaxPricePerNight() != null) {
            satisfied.add("pricePerNight=" + room.pricePerNight()
                    + " <= " + requirements.getMaxPricePerNight());
        }
        requirements.getRequiredAmenities().stream()
                .filter(amenity -> Boolean.TRUE.equals(amenityAvailability(hotel, room, amenity)))
                .map(amenity -> "requiredAmenity=" + amenity)
                .forEach(satisfied::add);
        return List.copyOf(satisfied);
    }

    private List<String> satisfiedRequiredConditions(Requirements requirements, Hotel hotel, RoomOffer room) {
        List<String> satisfied = new ArrayList<>();
        if (requirements.getCity() != null) {
            satisfied.add("城市：" + hotel.city());
        }
        satisfied.add("入住日期：" + formatDateRange(requirements.getCheckIn(), requirements.getCheckOut()));
        if (requirements.getMaxPricePerNight() != null) {
            satisfied.add("每晚最高预算：" + requirements.getMaxPricePerNight().toPlainString()
                    + "（房价 " + room.pricePerNight().toPlainString() + "）");
        }
        if (requirements.getMinPricePerNight() != null) {
            satisfied.add("每晚最低预算：" + requirements.getMinPricePerNight().toPlainString()
                    + "（房价 " + room.pricePerNight().toPlainString() + "）");
        }
        requirements.getRequiredAmenities().stream()
                .map(amenity -> "必需设施：" + amenityLabel(amenity))
                .forEach(satisfied::add);
        return List.copyOf(satisfied);
    }

    private PreferenceEvidence preferenceEvidence(Requirements requirements, Hotel hotel, RoomOffer room) {
        List<String> satisfied = new ArrayList<>();
        List<String> unmet = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        BigDecimal preferredBudget = requirements.getPreferredMaxPricePerNight();
        if (preferredBudget != null) {
            if (room.pricePerNight() == null) {
                unknown.add("偏好预算：房价数据未知");
            } else if (room.pricePerNight().compareTo(preferredBudget) <= 0) {
                satisfied.add("偏好预算：房价 " + room.pricePerNight().toPlainString()
                        + " 不高于 " + preferredBudget.toPlainString());
            } else {
                unmet.add("偏好预算：房价 " + room.pricePerNight().toPlainString()
                        + " 高于 " + preferredBudget.toPlainString());
            }
        }
        addPricePreferenceEvidence(satisfied, unmet, unknown, "一般最低价格",
                requirements.getPreferredMinPricePerNight(), room.pricePerNight(), false);
        addPricePreferenceEvidence(satisfied, unmet, unknown, "次要最低价格",
                requirements.getSecondaryMinPricePerNight(), room.pricePerNight(), false);
        addPricePreferenceEvidence(satisfied, unmet, unknown, "次要最高价格",
                requirements.getSecondaryMaxPricePerNight(), room.pricePerNight(), true);
        if (requirements.isPreferNearTransit()) {
            unknown.add("靠近公共交通：缺少可核验的站点距离数据");
        }
        requirements.getPreferredAmenities().forEach(amenity -> {
            String label = "偏好设施：" + amenityLabel(amenity);
            Boolean available = amenityAvailability(hotel, room, amenity);
            if (available == null) {
                unknown.add(label + "（数据未知）");
            } else if (available) {
                satisfied.add(label);
            } else {
                unmet.add(label);
            }
        });
        requirements.getSecondaryAmenities().forEach(amenity -> {
            String label = "次要设施偏好：" + amenityLabel(amenity);
            Boolean available = amenityAvailability(hotel, room, amenity);
            if (available == null) unknown.add(label + "（数据未知）");
            else if (available) satisfied.add(label);
            else unmet.add(label);
        });
        return new PreferenceEvidence(List.copyOf(satisfied), List.copyOf(unmet), List.copyOf(unknown));
    }

    private void addPricePreferenceEvidence(List<String> satisfied, List<String> unmet, List<String> unknown,
            String label, BigDecimal limit, BigDecimal price, boolean maximum) {
        if (limit == null) return;
        if (price == null) {
            unknown.add(label + "：房价数据未知");
            return;
        }
        boolean matches = maximum ? price.compareTo(limit) <= 0 : price.compareTo(limit) >= 0;
        String evidence = label + "：" + limit.toPlainString() + "（房价 " + price.toPlainString() + "）";
        (matches ? satisfied : unmet).add(evidence);
    }

    private String amenityLabel(String amenity) {
        return switch (amenity) {
            case "breakfast" -> "早餐";
            case "gym" -> "健身房";
            case "laundry" -> "洗衣服务";
            case "parking" -> "停车场";
            case "luggage_storage" -> "行李寄存";
            case "robot_service" -> "机器人服务";
            case "restaurant" -> "餐厅";
            case "taxi_service" -> "出租车服务";
            case "meeting_room" -> "会议室";
            case "accessibility" -> "无障碍设施";
            case "hair_dryer" -> "吹风机";
            case "towels" -> "毛巾";
            default -> amenity;
        };
    }

    public Boolean memoryAmenityAvailability(HotelCandidate candidate, long roomId, String preference) {
        String amenity = switch (preference) {
            case "breakfast", "早餐", "含早餐" -> "breakfast";
            case "gym", "健身房" -> "gym";
            case "laundry", "洗衣房", "洗衣服务" -> "laundry";
            case "parking", "停车场" -> "parking";
            case "luggage_storage", "行李寄存" -> "luggage_storage";
            case "robot_service", "机器人服务" -> "robot_service";
            case "restaurant", "餐厅" -> "restaurant";
            case "taxi_service", "叫车服务", "出租车服务" -> "taxi_service";
            case "meeting_room", "会议室" -> "meeting_room";
            case "accessibility", "无障碍设施" -> "accessibility";
            case "hair_dryer", "吹风机" -> "hair_dryer";
            case "towels", "毛巾" -> "towels";
            default -> null;
        };
        if (amenity == null) return null;
        RoomOffer room = candidate.roomOffers().stream().filter(item -> item.id() == roomId)
                .findFirst().orElseThrow(() -> new IllegalStateException("推荐房型不在候选中。"));
        return amenityAvailability(candidate.hotel(), room, amenity);
    }

    private Boolean amenityAvailability(Hotel hotel, RoomOffer room, String amenity) {
        return switch (amenity) {
            case "breakfast" -> room.breakfast();
            case "hair_dryer" -> room.hairDryer();
            case "towels" -> room.towels();
            case "gym" -> hotel.gym();
            case "laundry" -> hotel.laundry();
            case "parking" -> hotel.parking();
            case "luggage_storage" -> hotel.luggageStorage();
            case "robot_service" -> hotel.robotService();
            case "restaurant" -> hotel.restaurant();
            case "taxi_service" -> hotel.taxiService();
            case "meeting_room" -> hotel.meetingRoom();
            case "accessibility" -> hotel.accessibility();
            default -> null;
        };
    }

    private double dataCompleteness(Hotel hotel, RoomOffer room) {
        List<Object> values = java.util.Arrays.asList(
                hotel.name(), hotel.city(), hotel.address(), hotel.rating(),
                hotel.longitude(), hotel.latitude(), hotel.province(), hotel.district(),
                hotel.openingYear(), hotel.renovationYear(), hotel.phone(),
                hotel.gym(), hotel.laundry(), hotel.parking(), hotel.luggageStorage(),
                hotel.robotService(), hotel.restaurant(), hotel.taxiService(),
                hotel.meetingRoom(), hotel.accessibility(), room.roomName(),
                room.pricePerNight(), room.breakfast(), room.hairDryer(), room.towels());
        long known = values.stream().filter(Objects::nonNull).count();
        return (double) known / values.size();
    }

    private void replaceAmenities(List<String> target, List<String> requested) {
        List<String> normalized = requested.stream()
                .map(value -> value == null ? "" : value.strip().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalized.stream().anyMatch(value -> !AMENITIES.contains(value))) {
            throw badRequest("需求包含不支持的设施名称。");
        }
        target.clear();
        target.addAll(normalized);
    }

    private void replacePreferredAmenities(Requirements requirements, List<String> requested) {
        List<String> normalized = normalizeReviewPreferences(requested);
        List<String> supported = normalized.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(AMENITIES::contains)
                .distinct()
                .toList();
        List<String> reviewPreferences = normalized.stream()
                .filter(value -> !AMENITIES.contains(value.toLowerCase(Locale.ROOT)))
                .toList();
        if (reviewPreferences.isEmpty()) {
            replaceAmenities(requirements.getPreferredAmenities(), supported);
        } else {
            supported.forEach(value -> {
                if (!requirements.getPreferredAmenities().contains(value)) {
                    requirements.getPreferredAmenities().add(value);
                }
            });
            reviewPreferences.forEach(value -> {
                if (!requirements.getReviewPreferences().contains(value)) {
                    requirements.getReviewPreferences().add(value);
                }
            });
        }
    }

    private void replaceReviewPreferences(List<String> target, List<String> requested) {
        List<String> normalized = normalizeReviewPreferences(requested);
        target.clear();
        target.addAll(normalized);
    }

    private List<String> normalizeReviewPreferences(List<String> requested) {
        if (requested.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw badRequest("评价偏好不能为空。");
        }
        return requested.stream().map(String::strip).distinct().toList();
    }

    private void replaceTransportModes(List<String> target, List<String> requested) {
        List<String> normalized = requested.stream()
                .map(value -> value == null ? "" : value.strip().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalized.stream().anyMatch(value -> !TRANSPORT_MODES.contains(value))) {
            throw badRequest("路线查询包含不支持的交通方式。");
        }
        target.clear();
        target.addAll(normalized);
    }

    private BigDecimal positive(BigDecimal value, String field) {
        if (value.signum() <= 0) {
            throw badRequest(field + "必须大于 0。");
        }
        return value;
    }

    private BigDecimal maximum(BigDecimal first, BigDecimal second) {
        if (first == null) return second;
        return second == null ? first : first.max(second);
    }

    private BigDecimal minimum(BigDecimal first, BigDecimal second) {
        if (first == null) return second;
        return second == null ? first : first.min(second);
    }

    private String blankToNull(String value) {
        return value.isBlank() ? null : value.strip();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record PreferenceEvidence(List<String> satisfied, List<String> unmet, List<String> unknown) {
    }
}

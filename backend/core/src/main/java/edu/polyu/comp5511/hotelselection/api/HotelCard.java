package edu.polyu.comp5511.hotelselection.api;

import edu.polyu.comp5511.hotelselection.domain.DistanceEstimate;

import java.math.BigDecimal;
import java.util.List;

public record HotelCard(
        long hotelId,
        String name,
        String city,
        String address,
        String image,
        Double rating,
        String roomName,
        BigDecimal pricePerNight,
        double score,
        List<String> reasons,
        List<DistanceEstimate> routes,
        List<String> satisfiedRequiredConditions,
        List<String> satisfiedPreferences,
        List<String> unmetPreferences,
        List<String> unknownPreferences) {
    public HotelCard {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        routes = routes == null ? List.of() : List.copyOf(routes);
        satisfiedRequiredConditions = satisfiedRequiredConditions == null
                ? List.of() : List.copyOf(satisfiedRequiredConditions);
        satisfiedPreferences = satisfiedPreferences == null ? List.of() : List.copyOf(satisfiedPreferences);
        unmetPreferences = unmetPreferences == null ? List.of() : List.copyOf(unmetPreferences);
        unknownPreferences = unknownPreferences == null ? List.of() : List.copyOf(unknownPreferences);
    }
}

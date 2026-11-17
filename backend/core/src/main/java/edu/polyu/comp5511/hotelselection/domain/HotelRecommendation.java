package edu.polyu.comp5511.hotelselection.domain;

import java.math.BigDecimal;
import java.util.List;

public record HotelRecommendation(
        long hotelId,
        long roomOfferId,
        String roomName,
        String name,
        String city,
        String address,
        Double rating,
        BigDecimal pricePerNight,
        Boolean breakfast,
        Boolean hairDryer,
        Boolean towels,
        String amenities,
        double score,
        double dataCompleteness,
        List<DistanceEstimate> routes,
        List<String> reasons) {
    public HotelRecommendation {
        routes = List.copyOf(routes);
        reasons = List.copyOf(reasons);
    }
}

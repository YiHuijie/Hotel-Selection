package edu.polyu.comp5511.hotelselection.service;

import java.math.BigDecimal;
import java.util.List;

public record RecommendationValidationResult(
        String status,
        String message,
        List<VerifiedRecommendation> recommendations,
        List<Long> rejectedHotelIds) {
    public RecommendationValidationResult {
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        rejectedHotelIds = rejectedHotelIds == null ? List.of() : List.copyOf(rejectedHotelIds);
    }

    public boolean verified() {
        return "VERIFIED".equals(status);
    }

    public static RecommendationValidationResult rejected(String message, List<Long> rejectedHotelIds) {
        return new RecommendationValidationResult("REJECTED", message, List.of(), rejectedHotelIds);
    }

    public record VerifiedRecommendation(
            long hotelId,
            long roomOfferId,
            String roomName,
            String name,
            String city,
            BigDecimal pricePerNight,
            Boolean breakfast,
            Boolean hairDryer,
            Boolean towels,
            List<String> satisfiedHardConstraints) {
        public VerifiedRecommendation {
            satisfiedHardConstraints = List.copyOf(satisfiedHardConstraints);
        }
    }
}

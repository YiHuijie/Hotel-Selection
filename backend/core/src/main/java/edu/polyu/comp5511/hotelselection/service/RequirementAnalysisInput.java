package edu.polyu.comp5511.hotelselection.service;

import java.math.BigDecimal;
import java.util.List;

public record RequirementAnalysisInput(
        String city,
        String checkIn,
        String checkOut,
        BigDecimal maxPricePerNight,
        BigDecimal preferredMaxPricePerNight,
        Boolean preferNearTransit,
        List<String> requiredAmenities,
        List<String> preferredAmenities,
        List<String> clearFields,
        String clarificationQuestion,
        String origin,
        List<String> transportModes,
        List<String> reviewPreferences,
        BigDecimal minPricePerNight,
        BigDecimal preferredMinPricePerNight,
        BigDecimal secondaryMinPricePerNight,
        BigDecimal secondaryMaxPricePerNight,
        List<String> priorityReviewPreferences,
        List<String> secondaryReviewPreferences,
        List<String> secondaryAmenities) {
    public RequirementAnalysisInput(
            String city, String checkIn, String checkOut,
            BigDecimal maxPricePerNight, BigDecimal preferredMaxPricePerNight, Boolean preferNearTransit,
            List<String> requiredAmenities, List<String> preferredAmenities, List<String> clearFields,
            String clarificationQuestion, String origin, List<String> transportModes,
            List<String> reviewPreferences) {
        this(city, checkIn, checkOut, maxPricePerNight, preferredMaxPricePerNight, preferNearTransit,
                requiredAmenities, preferredAmenities, clearFields, clarificationQuestion, origin,
                transportModes, reviewPreferences, null, null, null, null, null, null, null);
    }

    public RequirementAnalysisInput(
            String city,
            String checkIn,
            String checkOut,
            BigDecimal maxPricePerNight,
            BigDecimal preferredMaxPricePerNight,
            Boolean preferNearTransit,
            List<String> requiredAmenities,
            List<String> preferredAmenities,
            List<String> clearFields,
            String clarificationQuestion) {
        this(city, checkIn, checkOut, maxPricePerNight, preferredMaxPricePerNight,
                preferNearTransit, requiredAmenities, preferredAmenities, clearFields,
                clarificationQuestion, null, null, null);
    }
}

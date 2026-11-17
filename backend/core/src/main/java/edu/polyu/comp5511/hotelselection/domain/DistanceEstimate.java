package edu.polyu.comp5511.hotelselection.domain;

import java.util.List;

public record DistanceEstimate(
        long hotelId,
        String transportMode,
        double distanceMeters,
        double durationSeconds,
        List<String> routeSteps) {
    public DistanceEstimate {
        routeSteps = routeSteps == null ? List.of() : List.copyOf(routeSteps);
    }
}

package edu.polyu.comp5511.hotelselection.service;

import edu.polyu.comp5511.hotelselection.domain.DistanceEstimate;

import java.util.List;

public record DistanceSkillResult(
        String provider,
        boolean available,
        List<DistanceEstimate> estimates,
        String limitation) {
    public DistanceSkillResult {
        estimates = estimates == null ? List.of() : List.copyOf(estimates);
    }
}

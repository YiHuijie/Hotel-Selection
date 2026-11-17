package edu.polyu.comp5511.hotelselection.domain;

import java.util.UUID;

public class AgentSession {
    private final String id;
    private final String userId;
    private final Requirements requirements = new Requirements();
    private AgentState state = AgentState.COLLECTING_REQUIREMENTS;
    private String pendingRelaxation;

    public AgentSession(String userId) {
        this(UUID.randomUUID().toString(), userId, new Requirements(), AgentState.COLLECTING_REQUIREMENTS, null);
    }

    public AgentSession(String id, String userId, Requirements requirements, AgentState state, String pendingRelaxation) {
        this.id = id;
        this.userId = userId;
        this.requirements.setCity(requirements.getCity());
        this.requirements.setCheckIn(requirements.getCheckIn());
        this.requirements.setCheckOut(requirements.getCheckOut());
        this.requirements.setMaxPricePerNight(requirements.getMaxPricePerNight());
        this.requirements.setPreferredMaxPricePerNight(requirements.getPreferredMaxPricePerNight());
        this.requirements.setMinPricePerNight(requirements.getMinPricePerNight());
        this.requirements.setPreferredMinPricePerNight(requirements.getPreferredMinPricePerNight());
        this.requirements.setSecondaryMinPricePerNight(requirements.getSecondaryMinPricePerNight());
        this.requirements.setSecondaryMaxPricePerNight(requirements.getSecondaryMaxPricePerNight());
        this.requirements.setPreferNearTransit(requirements.isPreferNearTransit());
        this.requirements.setOrigin(requirements.getOrigin());
        this.requirements.setDeclinedRelaxation(requirements.getDeclinedRelaxation());
        this.requirements.getRequiredAmenities().addAll(requirements.getRequiredAmenities());
        this.requirements.getPreferredAmenities().addAll(requirements.getPreferredAmenities());
        this.requirements.getReviewPreferences().addAll(requirements.getReviewPreferences());
        this.requirements.getPriorityReviewPreferences().addAll(requirements.getPriorityReviewPreferences());
        this.requirements.getSecondaryReviewPreferences().addAll(requirements.getSecondaryReviewPreferences());
        this.requirements.getSecondaryAmenities().addAll(requirements.getSecondaryAmenities());
        this.requirements.getUnverifiedAmenities().addAll(requirements.getUnverifiedAmenities());
        this.requirements.getTransportModes().addAll(requirements.getTransportModes());
        this.requirements.getAppliedRelaxations().addAll(requirements.getAppliedRelaxations());
        this.state = state;
        this.pendingRelaxation = pendingRelaxation;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public Requirements getRequirements() { return requirements; }
    public AgentState getState() { return state; }
    public void setState(AgentState state) { this.state = state; }
    public String getPendingRelaxation() { return pendingRelaxation; }
    public void setPendingRelaxation(String pendingRelaxation) { this.pendingRelaxation = pendingRelaxation; }
}

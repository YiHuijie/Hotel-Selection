package edu.polyu.comp5511.hotelselection.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Requirements {
    private String city;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private BigDecimal maxPricePerNight;
    private BigDecimal preferredMaxPricePerNight;
    private BigDecimal minPricePerNight;
    private BigDecimal preferredMinPricePerNight;
    private BigDecimal secondaryMinPricePerNight;
    private BigDecimal secondaryMaxPricePerNight;
    private boolean preferNearTransit;
    private String origin;
    private String declinedRelaxation;
    private final List<String> requiredAmenities = new ArrayList<>();
    private final List<String> preferredAmenities = new ArrayList<>();
    private final List<String> reviewPreferences = new ArrayList<>();
    private final List<String> priorityReviewPreferences = new ArrayList<>();
    private final List<String> secondaryReviewPreferences = new ArrayList<>();
    private final List<String> secondaryAmenities = new ArrayList<>();
    private final List<String> unverifiedAmenities = new ArrayList<>();
    private final List<String> transportModes = new ArrayList<>();
    private final List<RelaxedCondition> appliedRelaxations = new ArrayList<>();

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public LocalDate getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDate checkIn) { this.checkIn = checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDate checkOut) { this.checkOut = checkOut; }
    public BigDecimal getMaxPricePerNight() { return maxPricePerNight; }
    public void setMaxPricePerNight(BigDecimal maxPricePerNight) { this.maxPricePerNight = maxPricePerNight; }
    public BigDecimal getPreferredMaxPricePerNight() { return preferredMaxPricePerNight; }
    public void setPreferredMaxPricePerNight(BigDecimal preferredMaxPricePerNight) {
        this.preferredMaxPricePerNight = preferredMaxPricePerNight;
    }
    public BigDecimal getMinPricePerNight() { return minPricePerNight; }
    public void setMinPricePerNight(BigDecimal value) { minPricePerNight = value; }
    public BigDecimal getPreferredMinPricePerNight() { return preferredMinPricePerNight; }
    public void setPreferredMinPricePerNight(BigDecimal value) { preferredMinPricePerNight = value; }
    public BigDecimal getSecondaryMinPricePerNight() { return secondaryMinPricePerNight; }
    public void setSecondaryMinPricePerNight(BigDecimal value) { secondaryMinPricePerNight = value; }
    public BigDecimal getSecondaryMaxPricePerNight() { return secondaryMaxPricePerNight; }
    public void setSecondaryMaxPricePerNight(BigDecimal value) { secondaryMaxPricePerNight = value; }
    public boolean isPreferNearTransit() { return preferNearTransit; }
    public void setPreferNearTransit(boolean preferNearTransit) { this.preferNearTransit = preferNearTransit; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getDeclinedRelaxation() { return declinedRelaxation; }
    public void setDeclinedRelaxation(String value) { declinedRelaxation = value; }
    public List<String> getRequiredAmenities() { return requiredAmenities; }
    public List<String> getPreferredAmenities() { return preferredAmenities; }
    public List<String> getReviewPreferences() { return reviewPreferences; }
    public List<String> getPriorityReviewPreferences() { return priorityReviewPreferences; }
    public List<String> getSecondaryReviewPreferences() { return secondaryReviewPreferences; }
    public List<String> getSecondaryAmenities() { return secondaryAmenities; }
    public List<String> getUnverifiedAmenities() { return unverifiedAmenities; }
    public List<String> getTransportModes() { return transportModes; }
    public List<RelaxedCondition> getAppliedRelaxations() { return appliedRelaxations; }
    public void setAppliedRelaxations(List<RelaxedCondition> values) {
        appliedRelaxations.clear();
        if (values != null) {
            appliedRelaxations.addAll(values);
        }
    }
}

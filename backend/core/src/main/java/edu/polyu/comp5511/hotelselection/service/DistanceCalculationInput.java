package edu.polyu.comp5511.hotelselection.service;

import java.util.List;

public record DistanceCalculationInput(String origin, List<String> transportModes) {
}

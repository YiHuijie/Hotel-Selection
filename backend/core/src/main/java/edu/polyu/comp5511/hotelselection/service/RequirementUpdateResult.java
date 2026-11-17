package edu.polyu.comp5511.hotelselection.service;

import java.util.List;

public record RequirementUpdateResult(
        List<String> missingFields,
        String clarificationQuestion) {
}

package edu.polyu.comp5511.hotelselection.api;

public record Evaluation(
        Long durationMillis,
        Integer modelCalls,
        Integer hardSatisfied,
        Integer hardTotal,
        Integer preferenceSatisfied,
        Integer preferenceTotal,
        Integer preferenceUnknown,
        long requestCount,
        long errorCount,
        boolean historyComplete) {
}

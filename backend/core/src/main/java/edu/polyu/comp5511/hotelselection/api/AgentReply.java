package edu.polyu.comp5511.hotelselection.api;

import edu.polyu.comp5511.hotelselection.domain.AgentState;
import edu.polyu.comp5511.hotelselection.domain.HotelRecommendation;
import edu.polyu.comp5511.hotelselection.domain.Requirements;

import java.util.List;

public record AgentReply(
        String sessionId,
        AgentState state,
        String message,
        Requirements requirements,
        List<HotelRecommendation> recommendations,
        List<HotelCard> hotelCards,
        List<ToolObservation> observations,
        Evaluation evaluation) {
    public AgentReply {
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        hotelCards = hotelCards == null ? List.of() : List.copyOf(hotelCards);
        observations = observations == null ? List.of() : List.copyOf(observations);
    }

    public AgentReply(String sessionId, AgentState state, String message, Requirements requirements,
            List<HotelRecommendation> recommendations, List<HotelCard> hotelCards,
            List<ToolObservation> observations) {
        this(sessionId, state, message, requirements, recommendations, hotelCards, observations, null);
    }

    public AgentReply withEvaluation(Evaluation value) {
        return new AgentReply(sessionId, state, message, requirements, recommendations, hotelCards, observations, value);
    }
}

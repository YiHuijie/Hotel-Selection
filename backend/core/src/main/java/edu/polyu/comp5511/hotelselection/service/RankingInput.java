package edu.polyu.comp5511.hotelselection.service;

import java.util.List;

public record RankingInput(List<RankedHotelInput> rankedHotels) {
    public record RankedHotelInput(long hotelId, long roomOfferId, double score, List<String> reasons) {}
}

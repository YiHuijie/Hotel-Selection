package edu.polyu.comp5511.hotelselection.domain;

import java.util.List;

public record HotelDetail(
        Hotel hotel,
        List<String> amenities,
        List<RoomOffer> roomOffers,
        List<HotelReview> reviews) {
}

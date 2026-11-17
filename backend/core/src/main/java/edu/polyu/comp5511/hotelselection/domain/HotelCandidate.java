package edu.polyu.comp5511.hotelselection.domain;

import java.util.List;

public record HotelCandidate(Hotel hotel, List<RoomOffer> roomOffers) {
    public HotelCandidate {
        roomOffers = List.copyOf(roomOffers);
    }
}

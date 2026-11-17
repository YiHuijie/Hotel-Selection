package edu.polyu.comp5511.hotelselection.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RoomOffer(
        long id,
        long hotelId,
        String roomName,
        BigDecimal pricePerNight,
        Boolean breakfast,
        LocalDate availableFrom,
        LocalDate availableTo,
        Boolean hairDryer,
        Boolean towels) {
}

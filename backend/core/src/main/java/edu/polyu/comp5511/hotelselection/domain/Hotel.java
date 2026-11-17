package edu.polyu.comp5511.hotelselection.domain;

import java.math.BigDecimal;

public record Hotel(
        long id,
        String name,
        String city,
        String address,
        Double rating,
        BigDecimal pricePerNight,
        Boolean breakfast,
        String amenities,
        String keywords,
        BigDecimal longitude,
        BigDecimal latitude,
        String image,
        String province,
        String district,
        Integer openingYear,
        Integer renovationYear,
        String phone,
        Boolean gym,
        Boolean laundry,
        Boolean parking,
        Boolean luggageStorage,
        Boolean robotService,
        Boolean restaurant,
        Boolean taxiService,
        Boolean meetingRoom,
        Boolean accessibility) {
}

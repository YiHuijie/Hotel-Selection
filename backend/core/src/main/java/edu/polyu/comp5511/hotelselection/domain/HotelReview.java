package edu.polyu.comp5511.hotelselection.domain;

import java.time.LocalDateTime;

public record HotelReview(
        long hotelId,
        String reviewId,
        Integer rating,
        String content,
        LocalDateTime createdAt) {
}

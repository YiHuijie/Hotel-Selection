package edu.polyu.comp5511.hotelselection.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record FeedbackRequest(
        @NotBlank String userId,
        long hotelId,
        @Min(1) @Max(5) int rating,
        @NotBlank String comment) {
}

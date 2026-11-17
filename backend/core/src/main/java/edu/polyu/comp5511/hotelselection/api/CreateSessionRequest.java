package edu.polyu.comp5511.hotelselection.api;

import jakarta.validation.constraints.NotBlank;

public record CreateSessionRequest(@NotBlank String userId) {
}

package edu.polyu.comp5511.hotelselection.api;

import jakarta.validation.constraints.NotBlank;

public record AgentMessageRequest(@NotBlank String userId, @NotBlank String text) {
}

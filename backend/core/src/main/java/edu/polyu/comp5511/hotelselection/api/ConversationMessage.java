package edu.polyu.comp5511.hotelselection.api;

import java.time.LocalDateTime;

public record ConversationMessage(long id, String role, String text, LocalDateTime createdAt) {
}

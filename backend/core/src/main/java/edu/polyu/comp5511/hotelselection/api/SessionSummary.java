package edu.polyu.comp5511.hotelselection.api;

import java.time.LocalDateTime;

public record SessionSummary(String sessionId, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
}

package edu.polyu.comp5511.hotelselection.domain;

import java.time.LocalDateTime;

public record ReviewForMatching(long id, long hotelId, String content, LocalDateTime createdAt, boolean own) {
}

package edu.polyu.comp5511.hotelselection.persistence;

import java.time.LocalDateTime;

public record AgentSessionRow(
        String id, String userId, String title, String state, String pendingRelaxation,
        String requirementsJson, String latestReplyJson, LocalDateTime createdAt, LocalDateTime updatedAt,
        int workflowVersion) {
}

package edu.polyu.comp5511.hotelselection.api;

import java.util.List;

public record SessionHistory(AgentReply latestReply, List<ConversationMessage> messages) {
}

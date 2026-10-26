package org.example.springtestci.trade.dto;

import java.time.Instant;
import org.example.springtestci.trade.entity.ChangeReviewDecision;
import tools.jackson.databind.JsonNode;

public record ChangeReviewResponse(
    long eventId,
    long factsVersion,
    JsonNode changeFacts,
    ChangeReviewDecision decision,
    Instant reviewedAt) {}

package org.example.springtestci.trade.entity;

import java.time.Instant;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import tools.jackson.databind.JsonNode;

public class ReservationChangeReview {

  private final long purchaseRequestId;
  private final long eventId;
  private final long factsVersion;
  private final JsonNode changeFacts;
  private ChangeReviewDecision decision;
  private Instant reviewedAt;

  private ReservationChangeReview(
      long purchaseRequestId,
      long eventId,
      long factsVersion,
      JsonNode changeFacts,
      ChangeReviewDecision decision,
      Instant reviewedAt) {
    this.purchaseRequestId = purchaseRequestId;
    this.eventId = eventId;
    this.factsVersion = factsVersion;
    this.changeFacts = changeFacts;
    this.decision = decision;
    this.reviewedAt = reviewedAt;
  }

  public static ReservationChangeReview pending(
      long purchaseRequestId, long eventId, long factsVersion, JsonNode changeFacts) {
    if (factsVersion < 1 || changeFacts == null || !changeFacts.isObject()) {
      throw new CommonException(ErrorCode.INVALID_INPUT);
    }
    return new ReservationChangeReview(
        purchaseRequestId, eventId, factsVersion, changeFacts.deepCopy(), null, null);
  }

  public static ReservationChangeReview restore(
      long purchaseRequestId,
      long eventId,
      long factsVersion,
      JsonNode changeFacts,
      ChangeReviewDecision decision,
      Instant reviewedAt) {
    return new ReservationChangeReview(
        purchaseRequestId, eventId, factsVersion, changeFacts, decision, reviewedAt);
  }

  public void decide(ChangeReviewDecision requestedDecision, Instant decisionTime) {
    if (decision != null) {
      if (decision != requestedDecision) {
        throw new CommonException(ErrorCode.TRADE_CONFLICT);
      }
      return;
    }
    decision = requestedDecision;
    reviewedAt = decisionTime;
  }

  public boolean sameSnapshot(long candidateFactsVersion, JsonNode candidateFacts) {
    return factsVersion == candidateFactsVersion && changeFacts.equals(candidateFacts);
  }

  public long purchaseRequestId() {
    return purchaseRequestId;
  }

  public long eventId() {
    return eventId;
  }

  public long factsVersion() {
    return factsVersion;
  }

  public JsonNode changeFacts() {
    return changeFacts.deepCopy();
  }

  public ChangeReviewDecision decision() {
    return decision;
  }

  public Instant reviewedAt() {
    return reviewedAt;
  }
}

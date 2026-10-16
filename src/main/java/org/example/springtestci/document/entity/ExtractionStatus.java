package org.example.springtestci.document.entity;

public enum ExtractionStatus {
  QUEUED,
  PROCESSING,
  EXTRACTED,
  CONFIRMED,
  REJECTED,
  FAILED;

  public boolean canTransitionTo(ExtractionStatus next) {
    return switch (this) {
      case QUEUED -> next == PROCESSING || next == FAILED;
      case PROCESSING -> next == EXTRACTED || next == FAILED;
      case EXTRACTED -> next == CONFIRMED || next == REJECTED;
      case CONFIRMED, REJECTED, FAILED -> false;
    };
  }
}

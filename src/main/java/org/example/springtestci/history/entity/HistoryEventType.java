package org.example.springtestci.history.entity;

public enum HistoryEventType {
  PURCHASE,
  INSPECTION,
  REPAIR,
  PART_REPLACEMENT,
  SPEC_CHANGE,
  TRANSFER;

  public boolean changesSpecifications() {
    return this == PART_REPLACEMENT || this == SPEC_CHANGE;
  }
}

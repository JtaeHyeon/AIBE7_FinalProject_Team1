package org.example.springtestci.history.dto;

/** Describes who created an event without exposing a member identifier. */
public enum HistoryEventSource {
  CURRENT_OWNER,
  PREVIOUS_OWNER,
  SYSTEM
}

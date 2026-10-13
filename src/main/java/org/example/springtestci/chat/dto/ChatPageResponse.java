package org.example.springtestci.chat.dto;

import java.util.List;

public record ChatPageResponse<T>(List<T> items, PageMetadata page) {

  public ChatPageResponse {
    items = List.copyOf(items);
  }

  public record PageMetadata(
      int number, int size, long totalElements, int totalPages, boolean hasNext) {}
}

package org.example.springtestci.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record SendChatMessageRequest(@NotBlank String content) {

  @Override
  public String toString() {
    return "SendChatMessageRequest[content=<redacted>]";
  }
}

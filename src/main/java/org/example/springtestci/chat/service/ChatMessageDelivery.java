package org.example.springtestci.chat.service;

import org.example.springtestci.chat.dto.ChatMessageResponse;

public interface ChatMessageDelivery {

  void publishAfterCommit(ChatMessageResponse message);
}

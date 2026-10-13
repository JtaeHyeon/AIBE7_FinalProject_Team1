package org.example.springtestci.chat.service;

import java.util.Optional;
import org.example.springtestci.chat.domain.ChatListingSnapshot;

public interface ChatListingReader {

  Optional<ChatListingSnapshot> findById(long listingId);
}

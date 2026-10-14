package org.example.springtestci.chat.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.example.springtestci.chat.domain.ChatListingSnapshot;

public interface ChatListingReader {

  Optional<ChatListingSnapshot> findById(long listingId);

  default Map<Long, ChatListingSnapshot> findByIds(Collection<Long> listingIds) {
    return listingIds.stream()
        .distinct()
        .map(this::findById)
        .flatMap(Optional::stream)
        .collect(Collectors.toMap(ChatListingSnapshot::listingId, Function.identity()));
  }
}

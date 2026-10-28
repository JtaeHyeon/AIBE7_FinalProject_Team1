package org.example.springtestci.chat.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.example.springtestci.chat.domain.ChatListingSnapshot;

/** 채팅 도메인에 필요한 공개 판매글 사본만 공급하는 읽기 포트다. */
public interface ChatListingReader {

  /** 비공개 초안이 아닌 게시 판매글을 조회한다. */
  Optional<ChatListingSnapshot> findById(long listingId);

  default Map<Long, ChatListingSnapshot> findByIds(Collection<Long> listingIds) {
    return listingIds.stream()
        .distinct()
        .map(this::findById)
        .flatMap(Optional::stream)
        .collect(Collectors.toMap(ChatListingSnapshot::listingId, Function.identity()));
  }
}

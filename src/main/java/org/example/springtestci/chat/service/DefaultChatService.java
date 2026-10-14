package org.example.springtestci.chat.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.example.springtestci.chat.domain.ChatAccessPolicy;
import org.example.springtestci.chat.domain.ChatListingSnapshot;
import org.example.springtestci.chat.domain.ChatMessage;
import org.example.springtestci.chat.domain.ChatReadPosition;
import org.example.springtestci.chat.domain.ChatRoom;
import org.example.springtestci.chat.dto.ChatMessageResponse;
import org.example.springtestci.chat.dto.ChatPageResponse;
import org.example.springtestci.chat.dto.ChatPageResponse.PageMetadata;
import org.example.springtestci.chat.dto.ChatRoomResponse;
import org.example.springtestci.chat.dto.ChatRoomSummaryResponse;
import org.example.springtestci.chat.dto.ReadPositionResponse;
import org.example.springtestci.chat.repository.ChatMessageRepository;
import org.example.springtestci.chat.repository.ChatReadPositionRepository;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.features.chat-api", name = "enabled", havingValue = "true")
@Transactional(readOnly = true)
public class DefaultChatService implements ChatService {

  private static final Sort MESSAGE_SORT =
      Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

  private final ChatRoomRepository roomRepository;
  private final ChatMessageRepository messageRepository;
  private final ChatReadPositionRepository readPositionRepository;
  private final ChatListingReader listingReader;
  private final ChatUserReader userReader;
  private final ChatAccessPolicy accessPolicy;

  public DefaultChatService(
      ChatRoomRepository roomRepository,
      ChatMessageRepository messageRepository,
      ChatReadPositionRepository readPositionRepository,
      ChatListingReader listingReader,
      ChatUserReader userReader) {
    this.roomRepository = roomRepository;
    this.messageRepository = messageRepository;
    this.readPositionRepository = readPositionRepository;
    this.listingReader = listingReader;
    this.userReader = userReader;
    this.accessPolicy = new ChatAccessPolicy();
  }

  @Override
  @Transactional
  public ChatRoomResponse openRoom(long listingId, long actorId) {
    return roomRepository
        .findByListingIdAndInquirerId(listingId, actorId)
        .map(this::toRoomResponse)
        .orElseGet(() -> createRoom(listingId, actorId));
  }

  @Override
  public ChatPageResponse<ChatRoomSummaryResponse> listMyRooms(long actorId, int page, int size) {
    Page<ChatRoom> rooms = roomRepository.findByParticipantId(actorId, PageRequest.of(page, size));
    List<Long> roomIds = rooms.stream().map(ChatRoom::roomId).toList();
    Map<Long, ChatMessage> latestMessages =
        messageRepository.findLatestByRoomIds(roomIds).stream()
            .collect(Collectors.toMap(ChatMessage::roomId, Function.identity()));
    Map<Long, ChatListingSnapshot> listings =
        listingReader.findByIds(rooms.stream().map(ChatRoom::listingId).toList());
    Set<Long> unreadRoomIds = messageRepository.findRoomIdsWithUnreadMessages(roomIds, actorId);
    Map<Long, String> nicknames =
        userReader.findNicknamesByIds(
            rooms.stream().map(room -> counterpartyId(room, actorId)).distinct().toList());

    List<ChatRoomSummaryResponse> items =
        rooms.stream()
            .map(
                room ->
                    toRoomSummary(
                        room,
                        actorId,
                        latestMessages.get(room.roomId()),
                        listings.get(room.listingId()),
                        nicknames,
                        unreadRoomIds.contains(room.roomId())))
            .toList();
    return page(items, rooms);
  }

  @Override
  public ChatPageResponse<ChatMessageResponse> listMessages(
      long roomId, long actorId, int page, int size) {
    requireParticipant(roomId, actorId);
    Page<ChatMessage> messages =
        messageRepository.findByRoomId(roomId, PageRequest.of(page, size, MESSAGE_SORT));
    return page(messages.stream().map(this::toMessageResponse).toList(), messages);
  }

  @Override
  @Transactional
  public ChatMessageResponse sendMessage(long roomId, long actorId, String content) {
    requireParticipant(roomId, actorId);
    if (content == null || content.isBlank()) {
      throw new CommonException(ErrorCode.INVALID_INPUT);
    }
    return toMessageResponse(messageRepository.append(roomId, actorId, content));
  }

  @Override
  @Transactional
  public ReadPositionResponse updateMyReadPosition(
      long roomId, long actorId, long lastReadMessageId) {
    requireParticipant(roomId, actorId);
    ChatMessage message =
        messageRepository
            .findById(lastReadMessageId)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    if (message.roomId() != roomId) {
      throw new CommonException(ErrorCode.INVALID_INPUT);
    }
    ChatReadPosition position = readPositionRepository.update(roomId, actorId, lastReadMessageId);
    return new ReadPositionResponse(position.roomId(), position.lastReadMessageId());
  }

  private ChatRoomResponse createRoom(long listingId, long actorId) {
    ChatListingSnapshot listing =
        listingReader
            .findById(listingId)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!accessPolicy.canCreateRoom(listing, actorId)) {
      throw new CommonException(ErrorCode.ACCESS_DENIED);
    }
    return toRoomResponse(roomRepository.create(listingId, listing.sellerId(), actorId));
  }

  private ChatRoom requireParticipant(long roomId, long actorId) {
    ChatRoom room =
        roomRepository
            .findById(roomId)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!accessPolicy.canAccessRoom(room, actorId)) {
      throw new CommonException(ErrorCode.ACCESS_DENIED);
    }
    return room;
  }

  private ChatRoomSummaryResponse toRoomSummary(
      ChatRoom room,
      long actorId,
      ChatMessage latestMessage,
      ChatListingSnapshot listing,
      Map<Long, String> nicknames,
      boolean hasUnread) {
    long counterpartyId = counterpartyId(room, actorId);
    return new ChatRoomSummaryResponse(
        room.roomId(),
        room.listingId(),
        nicknames.getOrDefault(counterpartyId, "사용자 #" + counterpartyId),
        listing == null ? "판매글 #" + room.listingId() : listing.title(),
        latestMessage == null ? null : latestMessage.content(),
        latestMessage == null ? null : latestMessage.createdAt(),
        hasUnread);
  }

  private long counterpartyId(ChatRoom room, long actorId) {
    return actorId == room.sellerId() ? room.inquirerId() : room.sellerId();
  }

  private ChatRoomResponse toRoomResponse(ChatRoom room) {
    return new ChatRoomResponse(room.roomId(), room.listingId());
  }

  private ChatMessageResponse toMessageResponse(ChatMessage message) {
    return new ChatMessageResponse(
        message.messageId(),
        message.roomId(),
        message.senderId(),
        message.content(),
        message.createdAt());
  }

  private <T> ChatPageResponse<T> page(List<T> items, Page<?> source) {
    return new ChatPageResponse<>(
        items,
        new PageMetadata(
            source.getNumber(),
            source.getSize(),
            source.getTotalElements(),
            source.getTotalPages(),
            source.hasNext()));
  }
}

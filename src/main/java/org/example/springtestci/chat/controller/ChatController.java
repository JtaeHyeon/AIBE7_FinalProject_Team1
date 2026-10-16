package org.example.springtestci.chat.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.example.springtestci.chat.dto.ChatMessageResponse;
import org.example.springtestci.chat.dto.ChatPageResponse;
import org.example.springtestci.chat.dto.ChatRoomResponse;
import org.example.springtestci.chat.dto.ChatRoomSummaryResponse;
import org.example.springtestci.chat.dto.ReadPositionResponse;
import org.example.springtestci.chat.dto.SendChatMessageRequest;
import org.example.springtestci.chat.dto.UpdateReadPositionRequest;
import org.example.springtestci.chat.service.ChatService;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.response.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "app.features.chat-api", name = "enabled", havingValue = "true")
public class ChatController {

  private static final String DEFAULT_PAGE = "0";
  private static final String DEFAULT_PAGE_SIZE = "20";
  private static final int MAX_PAGE_SIZE = 100;

  private final ChatService chatService;

  public ChatController(ChatService chatService) {
    this.chatService = chatService;
  }

  @PostMapping("/market/listings/{listingId}/chat-rooms")
  public ApiResponse<ChatRoomResponse> openRoom(
      @PathVariable @Positive Long listingId, Authentication authentication) {
    return ApiResponse.success(chatService.openRoom(listingId, actorId(authentication)));
  }

  @GetMapping("/chat-rooms")
  public ApiResponse<ChatPageResponse<ChatRoomSummaryResponse>> listMyRooms(
      Authentication authentication,
      @RequestParam(defaultValue = DEFAULT_PAGE) @Min(0) int page,
      @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return ApiResponse.success(chatService.listMyRooms(actorId(authentication), page, size));
  }

  @GetMapping("/chat-rooms/{roomId}/messages")
  public ApiResponse<ChatPageResponse<ChatMessageResponse>> listMessages(
      @PathVariable @Positive Long roomId,
      Authentication authentication,
      @RequestParam(defaultValue = DEFAULT_PAGE) @Min(0) int page,
      @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return ApiResponse.success(
        chatService.listMessages(roomId, actorId(authentication), page, size));
  }

  @PostMapping("/chat-rooms/{roomId}/messages")
  public ApiResponse<ChatMessageResponse> sendMessage(
      @PathVariable @Positive Long roomId,
      @Valid @RequestBody SendChatMessageRequest request,
      Authentication authentication) {
    return ApiResponse.success(
        chatService.sendMessage(roomId, actorId(authentication), request.content()));
  }

  @PutMapping("/chat-rooms/{roomId}/read-position")
  public ApiResponse<ReadPositionResponse> updateMyReadPosition(
      @PathVariable @Positive Long roomId,
      @Valid @RequestBody UpdateReadPositionRequest request,
      Authentication authentication) {
    return ApiResponse.success(
        chatService.updateMyReadPosition(
            roomId, actorId(authentication), request.lastReadMessageId()));
  }

  private long actorId(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new CommonException(ErrorCode.UNAUTHORIZED);
    }

    try {
      return Long.parseLong(authentication.getName());
    } catch (NumberFormatException exception) {
      throw new CommonException(ErrorCode.UNAUTHORIZED);
    }
  }
}

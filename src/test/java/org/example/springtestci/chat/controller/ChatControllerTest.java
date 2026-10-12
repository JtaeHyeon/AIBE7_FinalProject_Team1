package org.example.springtestci.chat.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.example.springtestci.chat.dto.ChatMessageResponse;
import org.example.springtestci.chat.dto.ChatPageResponse;
import org.example.springtestci.chat.dto.ChatPageResponse.PageMetadata;
import org.example.springtestci.chat.dto.ChatRoomResponse;
import org.example.springtestci.chat.dto.ChatRoomSummaryResponse;
import org.example.springtestci.chat.dto.ReadPositionResponse;
import org.example.springtestci.chat.service.ChatService;
import org.example.springtestci.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

  private static final long ACTOR_ID = 42L;
  private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");

  @Mock private ChatService chatService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ChatController(chatService))
            .setControllerAdvice(new GlobalExceptionHandler(clock))
            .build();
  }

  @Test
  void opensRoomWithAuthenticatedActor() throws Exception {
    when(chatService.openRoom(7L, ACTOR_ID)).thenReturn(new ChatRoomResponse(11L, 7L));

    mockMvc
        .perform(post("/api/v1/market/listings/7/chat-rooms").principal(authenticatedActor()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.roomId").value(11))
        .andExpect(jsonPath("$.data.listingId").value(7));

    verify(chatService).openRoom(7L, ACTOR_ID);
  }

  @Test
  void listsRoomsWithPagingParameters() throws Exception {
    ChatRoomSummaryResponse room =
        new ChatRoomSummaryResponse(11L, 7L, "counterparty", "laptop", "hello", NOW, true);
    when(chatService.listMyRooms(ACTOR_ID, 1, 10))
        .thenReturn(new ChatPageResponse<>(List.of(room), new PageMetadata(1, 10, 12, 2, false)));

    mockMvc
        .perform(
            get("/api/v1/chat-rooms")
                .principal(authenticatedActor())
                .param("page", "1")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].roomId").value(11))
        .andExpect(jsonPath("$.data.page.number").value(1))
        .andExpect(jsonPath("$.data.page.hasNext").value(false));

    verify(chatService).listMyRooms(ACTOR_ID, 1, 10);
  }

  @Test
  void listsMessagesForRoomAndAuthenticatedActor() throws Exception {
    ChatMessageResponse message = new ChatMessageResponse(21L, 11L, ACTOR_ID, "hello", NOW);
    when(chatService.listMessages(11L, ACTOR_ID, 0, 20))
        .thenReturn(new ChatPageResponse<>(List.of(message), new PageMetadata(0, 20, 1, 1, false)));

    mockMvc
        .perform(get("/api/v1/chat-rooms/11/messages").principal(authenticatedActor()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].messageId").value(21))
        .andExpect(jsonPath("$.data.items[0].senderId").value(ACTOR_ID));

    verify(chatService).listMessages(11L, ACTOR_ID, 0, 20);
  }

  @Test
  void sendsContentUsingAuthenticatedActorAsSender() throws Exception {
    ChatMessageResponse response = new ChatMessageResponse(21L, 11L, ACTOR_ID, "hello", NOW);
    when(chatService.sendMessage(11L, ACTOR_ID, "hello")).thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/chat-rooms/11/messages")
                .principal(authenticatedActor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hello\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").value("hello"))
        .andExpect(jsonPath("$.data.senderId").value(ACTOR_ID));

    verify(chatService).sendMessage(11L, ACTOR_ID, "hello");
  }

  @Test
  void updatesOnlyAuthenticatedActorsReadPosition() throws Exception {
    when(chatService.updateMyReadPosition(11L, ACTOR_ID, 21L))
        .thenReturn(new ReadPositionResponse(11L, 21L));

    mockMvc
        .perform(
            put("/api/v1/chat-rooms/11/read-position")
                .principal(authenticatedActor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lastReadMessageId\":21}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.roomId").value(11))
        .andExpect(jsonPath("$.data.lastReadMessageId").value(21));

    verify(chatService).updateMyReadPosition(11L, ACTOR_ID, 21L);
  }

  @Test
  void rejectsBlankMessageContentBeforeServiceCall() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/chat-rooms/11/messages")
                .principal(authenticatedActor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_001"));
  }

  private UsernamePasswordAuthenticationToken authenticatedActor() {
    return new UsernamePasswordAuthenticationToken(Long.toString(ACTOR_ID), "n/a", List.of());
  }
}

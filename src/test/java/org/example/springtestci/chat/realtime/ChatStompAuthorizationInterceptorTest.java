package org.example.springtestci.chat.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class ChatStompAuthorizationInterceptorTest {

  private static final long USER_ID = 101L;
  private static final long ROOM_ID = 55L;

  private JwtDecoder jwtDecoder;
  private ChatRoomSubscriptionAuthorizer subscriptionAuthorizer;
  private ChatStompAuthorizationInterceptor interceptor;
  private MessageChannel channel;

  @BeforeEach
  void setUp() {
    jwtDecoder = mock(JwtDecoder.class);
    subscriptionAuthorizer = mock(ChatRoomSubscriptionAuthorizer.class);
    interceptor = new ChatStompAuthorizationInterceptor(jwtDecoder, subscriptionAuthorizer);
    channel = mock(MessageChannel.class);
  }

  @Test
  void authenticatesConnectWithVerifiedBearerToken() {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.parse("2026-10-19T00:00:00Z"),
            Instant.parse("2026-10-19T01:00:00Z"),
            Map.of("alg", "HS256"),
            Map.of("sub", Long.toString(USER_ID)));
    when(jwtDecoder.decode("token")).thenReturn(jwt);
    StompHeaderAccessor accessor = accessor(StompCommand.CONNECT, null);
    accessor.addNativeHeader("Authorization", "Bearer token");

    interceptor.preSend(message(accessor), channel);

    assertEquals(Long.toString(USER_ID), accessor.getUser().getName());
  }

  @Test
  void rejectsConnectWithoutBearerToken() {
    StompHeaderAccessor accessor = accessor(StompCommand.CONNECT, null);

    assertThrows(
        BadCredentialsException.class, () -> interceptor.preSend(message(accessor), channel));
  }

  @Test
  void rejectsStompConnectFrameWithoutBearerToken() {
    StompHeaderAccessor accessor = accessor(StompCommand.STOMP, null);

    assertThrows(
        BadCredentialsException.class, () -> interceptor.preSend(message(accessor), channel));
  }

  @Test
  void permitsOnlyParticipantSubscriptionToExactRoomDestination() {
    when(subscriptionAuthorizer.canSubscribe(ROOM_ID, USER_ID)).thenReturn(true);
    StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE, "/sub/chat/room/" + ROOM_ID);
    accessor.setUser(() -> Long.toString(USER_ID));

    Message<?> result = interceptor.preSend(message(accessor), channel);

    assertTrue(result != null);
    verify(subscriptionAuthorizer).canSubscribe(ROOM_ID, USER_ID);
  }

  @Test
  void rejectsOutsiderAndWildcardSubscriptions() {
    when(subscriptionAuthorizer.canSubscribe(ROOM_ID, USER_ID)).thenReturn(false);
    StompHeaderAccessor outsider = accessor(StompCommand.SUBSCRIBE, "/sub/chat/room/" + ROOM_ID);
    outsider.setUser(() -> Long.toString(USER_ID));
    StompHeaderAccessor wildcard = accessor(StompCommand.SUBSCRIBE, "/sub/chat/room/*");
    wildcard.setUser(() -> Long.toString(USER_ID));

    assertThrows(
        AccessDeniedException.class, () -> interceptor.preSend(message(outsider), channel));
    assertThrows(
        AccessDeniedException.class, () -> interceptor.preSend(message(wildcard), channel));
  }

  @Test
  void rejectsAllClientStompSendFrames() {
    StompHeaderAccessor accessor = accessor(StompCommand.SEND, "/sub/chat/room/" + ROOM_ID);
    accessor.setUser(() -> Long.toString(USER_ID));

    assertThrows(
        AccessDeniedException.class, () -> interceptor.preSend(message(accessor), channel));
  }

  private StompHeaderAccessor accessor(StompCommand command, String destination) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    accessor.setDestination(destination);
    accessor.setLeaveMutable(true);
    return accessor;
  }

  private Message<byte[]> message(StompHeaderAccessor accessor) {
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }
}

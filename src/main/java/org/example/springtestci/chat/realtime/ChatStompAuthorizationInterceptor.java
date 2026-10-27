package org.example.springtestci.chat.realtime;

import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
class ChatStompAuthorizationInterceptor implements ChannelInterceptor {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";
  private static final Pattern ROOM_SUBSCRIPTION =
      Pattern.compile("^/sub/chat/room/([1-9][0-9]*)$");

  private final JwtDecoder jwtDecoder;
  private final ChatRoomSubscriptionAuthorizer subscriptionAuthorizer;

  ChatStompAuthorizationInterceptor(
      JwtDecoder jwtDecoder, ChatRoomSubscriptionAuthorizer subscriptionAuthorizer) {
    this.jwtDecoder = jwtDecoder;
    this.subscriptionAuthorizer = subscriptionAuthorizer;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null) {
      return message;
    }
    StompCommand command = accessor.getCommand();
    if (command == null) {
      return message;
    }

    if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
      accessor.setUser(authenticate(accessor));
      return message;
    }
    if (command == StompCommand.SEND) {
      throw new AccessDeniedException("STOMP message sending is not supported");
    }
    if (command == StompCommand.SUBSCRIBE) {
      authorizeSubscription(accessor);
    }
    return message;
  }

  private Principal authenticate(StompHeaderAccessor accessor) {
    String authorization = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      throw new BadCredentialsException("Bearer token is required");
    }

    try {
      Jwt jwt = jwtDecoder.decode(authorization.substring(BEARER_PREFIX.length()));
      if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
        throw new BadCredentialsException("JWT subject is required");
      }
      Long.parseLong(jwt.getSubject());
      return UsernamePasswordAuthenticationToken.authenticated(jwt.getSubject(), "n/a", List.of());
    } catch (JwtException | NumberFormatException exception) {
      throw new BadCredentialsException("Invalid bearer token", exception);
    }
  }

  private void authorizeSubscription(StompHeaderAccessor accessor) {
    Principal principal = accessor.getUser();
    if (principal == null) {
      throw new AccessDeniedException("Authenticated STOMP session is required");
    }

    Matcher matcher = ROOM_SUBSCRIPTION.matcher(String.valueOf(accessor.getDestination()));
    if (!matcher.matches()) {
      throw new AccessDeniedException("Unsupported subscription destination");
    }

    long roomId = Long.parseLong(matcher.group(1));
    long userId = Long.parseLong(principal.getName());
    if (!subscriptionAuthorizer.canSubscribe(roomId, userId)) {
      throw new AccessDeniedException("Chat room subscription denied");
    }
  }
}

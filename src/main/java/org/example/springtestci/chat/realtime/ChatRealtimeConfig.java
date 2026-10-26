package org.example.springtestci.chat.realtime;

import java.util.List;
import org.example.springtestci.common.config.CorsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnProperty(prefix = "app.features.chat-api", name = "enabled", havingValue = "true")
/** P1 STOMP 엔드포인트, 구독 브로커와 인바운드 인증 인터셉터를 구성한다. */
public class ChatRealtimeConfig implements WebSocketMessageBrokerConfigurer {

  private final CorsProperties corsProperties;
  private final ChatStompAuthorizationInterceptor authorizationInterceptor;

  public ChatRealtimeConfig(
      CorsProperties corsProperties, ChatStompAuthorizationInterceptor authorizationInterceptor) {
    this.corsProperties = corsProperties;
    this.authorizationInterceptor = authorizationInterceptor;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/sub");
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/ws-stomp")
        .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authorizationInterceptor);
  }

  @Override
  public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
    messageConverters.add(new JacksonJsonMessageConverter());
    return false;
  }
}

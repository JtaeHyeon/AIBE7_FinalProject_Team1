package org.example.springtestci.chat.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.example.springtestci.SpringTestCiApplication;
import org.example.springtestci.chat.domain.ChatListingSnapshot;
import org.example.springtestci.chat.domain.ChatMessage;
import org.example.springtestci.chat.domain.ChatRoom;
import org.example.springtestci.chat.dto.ChatMessageResponse;
import org.example.springtestci.chat.repository.ChatMessageRepository;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.chat.service.ChatListingReader;
import org.example.springtestci.chat.service.ChatUserReader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ChatRealtimeIntegrationTest {

  private static final long SELLER_ID = 101L;
  private static final long INQUIRER_ID = 202L;
  private static final long OTHER_USER_ID = 303L;
  private static final String SECRET = "chat-realtime-test-secret-at-least-32-bytes";
  private static final String ORIGIN = "http://localhost:3000";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  private static ConfigurableApplicationContext context;
  private static JdbcTemplate jdbcTemplate;
  private static ChatRoomRepository roomRepository;
  private static ChatMessageRepository messageRepository;
  private static WebSocketStompClient stompClient;
  private static String httpBaseUrl;
  private static String webSocketUrl;
  private static String schema;

  @BeforeAll
  static void startServer() {
    String databaseUrl = System.getenv("CHAT_REALTIME_TEST_DB_URL");
    boolean postgres = databaseUrl != null && !databaseUrl.isBlank();
    if (postgres && !databaseUrl.toLowerCase().contains("test")) {
      throw new IllegalStateException("Use a dedicated test database for chat realtime tests");
    }

    schema = postgres ? "chat_realtime_" + Long.toUnsignedString(System.nanoTime()) : "public";
    if (postgres) {
      databaseUrl += (databaseUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    } else {
      databaseUrl =
          "jdbc:h2:mem:chat_realtime;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
              + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
    }

    SpringApplication application =
        new SpringApplication(SpringTestCiApplication.class, FixtureConfig.class);
    application.setDefaultProperties(
        Map.of("spring.main.banner-mode", "off", "logging.level.root", "WARN"));
    context =
        application.run(
            "--spring.profiles.active=test",
            "--server.address=127.0.0.1",
            "--server.port=0",
            "--spring.datasource.url=" + databaseUrl,
            "--spring.datasource.driver-class-name="
                + (postgres ? "org.postgresql.Driver" : "org.h2.Driver"),
            "--spring.datasource.username="
                + (postgres ? System.getenv("CHAT_REALTIME_TEST_DB_USER") : "sa"),
            "--spring.datasource.password="
                + (postgres ? System.getenv("CHAT_REALTIME_TEST_DB_PASSWORD") : ""),
            "--spring.flyway.enabled=true",
            "--spring.flyway.schemas=" + schema,
            "--spring.flyway.default-schema=" + schema,
            "--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.jpa.properties.hibernate.default_schema=" + schema,
            "--app.features.chat-api.enabled=true",
            "--app.cors.allowed-origins=" + ORIGIN,
            "--app.jwt.secret=" + SECRET);

    int port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
    httpBaseUrl = "http://127.0.0.1:" + port;
    webSocketUrl = "ws://127.0.0.1:" + port + "/ws-stomp";
    jdbcTemplate = context.getBean(JdbcTemplate.class);
    roomRepository = context.getBean(ChatRoomRepository.class);
    messageRepository = context.getBean(ChatMessageRepository.class);
    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new JacksonJsonMessageConverter());
  }

  @AfterAll
  static void stopServer() {
    if (stompClient != null) {
      stompClient.stop();
    }
    if (context != null) {
      if (!"public".equals(schema)) {
        jdbcTemplate.execute("DROP SCHEMA " + schema + " CASCADE");
      }
      context.close();
    }
  }

  @BeforeEach
  void resetDatabase() {
    jdbcTemplate.update("DELETE FROM chat_read_positions");
    jdbcTemplate.update("DELETE FROM chat_messages");
    jdbcTemplate.update("DELETE FROM chat_rooms");
  }

  @Test
  void relaysCommittedHttpMessagesToBothAuthenticatedParticipants() throws Exception {
    ChatRoom room = roomRepository.create(1001L, SELLER_ID, INQUIRER_ID);
    BlockingQueue<ChatMessageResponse> sellerMessages = new LinkedBlockingQueue<>();
    BlockingQueue<ChatMessageResponse> inquirerMessages = new LinkedBlockingQueue<>();
    StompSession sellerSession = connect(SELLER_ID);
    StompSession inquirerSession = connect(INQUIRER_ID);

    subscribe(sellerSession, room.roomId(), sellerMessages);
    subscribe(inquirerSession, room.roomId(), inquirerMessages);
    assertTrue(sellerSession.isConnected());
    assertTrue(inquirerSession.isConnected());

    JsonNode inquirerResponse = sendMessage(room.roomId(), INQUIRER_ID, "문의\nIs it available?");
    assertReceivedMessage(sellerMessages, inquirerResponse);
    assertReceivedMessage(inquirerMessages, inquirerResponse);

    JsonNode sellerResponse = sendMessage(room.roomId(), SELLER_ID, "네, 가능합니다.");
    assertReceivedMessage(sellerMessages, sellerResponse);
    assertReceivedMessage(inquirerMessages, sellerResponse);

    ChatMessage stored =
        messageRepository.findById(sellerResponse.get("messageId").asLong()).orElseThrow();
    assertEquals(sellerResponse.get("content").asString(), stored.content());
    assertEquals(sellerResponse.get("senderId").asLong(), stored.senderId());

    sellerSession.disconnect();
    inquirerSession.disconnect();
  }

  @Test
  void rejectsInvalidConnectToken() {
    assertThrows(
        ExecutionException.class, () -> connect("not-a-jwt", ORIGIN).get(10, TimeUnit.SECONDS));
  }

  @Test
  void isolatesMessagesBetweenRooms() throws Exception {
    ChatRoom firstRoom = roomRepository.create(1001L, SELLER_ID, INQUIRER_ID);
    ChatRoom secondRoom = roomRepository.create(1002L, SELLER_ID, OTHER_USER_ID);
    BlockingQueue<ChatMessageResponse> firstRoomMessages = new LinkedBlockingQueue<>();
    BlockingQueue<ChatMessageResponse> secondRoomMessages = new LinkedBlockingQueue<>();
    StompSession inquirerSession = connect(INQUIRER_ID);
    StompSession otherSession = connect(OTHER_USER_ID);
    try {
      subscribe(inquirerSession, firstRoom.roomId(), firstRoomMessages);
      subscribe(otherSession, secondRoom.roomId(), secondRoomMessages);

      JsonNode response = sendMessage(firstRoom.roomId(), SELLER_ID, "first room only");

      assertReceivedMessage(firstRoomMessages, response);
      assertEquals(null, secondRoomMessages.poll(500, TimeUnit.MILLISECONDS));
    } finally {
      inquirerSession.disconnect();
      otherSession.disconnect();
    }
  }

  @Test
  void reconnectsAndRestoresMissedMessagesFromHistoryWithoutDuplicateIds() throws Exception {
    ChatRoom room = roomRepository.create(1001L, SELLER_ID, INQUIRER_ID);
    BlockingQueue<ChatMessageResponse> receivedMessages = new LinkedBlockingQueue<>();
    StompSession initialSession = connect(INQUIRER_ID);
    subscribe(initialSession, room.roomId(), receivedMessages);

    JsonNode first = sendMessage(room.roomId(), SELLER_ID, "before disconnect");
    assertReceivedMessage(receivedMessages, first);
    initialSession.disconnect();
    JsonNode missed = sendMessage(room.roomId(), SELLER_ID, "while disconnected");

    StompSession reconnectedSession = connect(INQUIRER_ID);
    try {
      subscribe(reconnectedSession, room.roomId(), receivedMessages);
      JsonNode history = listMessages(room.roomId(), INQUIRER_ID);
      Set<Long> visibleMessageIds = new HashSet<>();
      history.get("items").forEach(item -> visibleMessageIds.add(item.get("messageId").asLong()));
      assertEquals(
          Set.of(first.get("messageId").asLong(), missed.get("messageId").asLong()),
          visibleMessageIds);

      JsonNode afterReconnect = sendMessage(room.roomId(), SELLER_ID, "after reconnect");
      ChatMessageResponse live = receivedMessages.poll(5, TimeUnit.SECONDS);
      assertNotNull(live);
      visibleMessageIds.add(live.messageId());
      visibleMessageIds.add(afterReconnect.get("messageId").asLong());
      assertEquals(3, visibleMessageIds.size());
    } finally {
      reconnectedSession.disconnect();
    }
  }

  @Test
  void rejectsUnapprovedHandshakeOrigin() throws Exception {
    assertThrows(
        ExecutionException.class,
        () ->
            connect(token(Long.toString(SELLER_ID)), "http://unapproved.example")
                .get(10, TimeUnit.SECONDS));
  }

  private static StompSession connect(long userId) throws Exception {
    return connect(token(Long.toString(userId)), ORIGIN).get(10, TimeUnit.SECONDS);
  }

  private static java.util.concurrent.CompletableFuture<StompSession> connect(
      String bearerToken, String origin) {
    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.setOrigin(origin);
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("Authorization", "Bearer " + bearerToken);
    return stompClient.connectAsync(
        URI.create(webSocketUrl),
        handshakeHeaders,
        connectHeaders,
        new StompSessionHandlerAdapter() {});
  }

  private static void subscribe(
      StompSession session, long roomId, BlockingQueue<ChatMessageResponse> receivedMessages)
      throws InterruptedException {
    session.subscribe(
        "/sub/chat/room/" + roomId,
        new StompFrameHandler() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return ChatMessageResponse.class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {
            receivedMessages.add((ChatMessageResponse) payload);
          }
        });
    Thread.sleep(200);
  }

  private static JsonNode sendMessage(long roomId, long senderId, String content) throws Exception {
    String requestBody = JSON.createObjectNode().put("content", content).toString();
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(httpBaseUrl + "/api/v1/chat-rooms/" + roomId + "/messages"))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer " + token(Long.toString(senderId)))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    return JSON.readTree(response.body()).get("data");
  }

  private static JsonNode listMessages(long roomId, long userId) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(httpBaseUrl + "/api/v1/chat-rooms/" + roomId + "/messages"))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer " + token(Long.toString(userId)))
            .GET()
            .build();
    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    return JSON.readTree(response.body()).get("data");
  }

  private static void assertReceivedMessage(
      BlockingQueue<ChatMessageResponse> receivedMessages, JsonNode expected) throws Exception {
    ChatMessageResponse actual = receivedMessages.poll(5, TimeUnit.SECONDS);
    assertNotNull(actual);
    assertEquals(expected.get("messageId").asLong(), actual.messageId());
    assertEquals(expected.get("roomId").asLong(), actual.roomId());
    assertEquals(expected.get("senderId").asLong(), actual.senderId());
    assertEquals(expected.get("content").asString(), actual.content());
    assertEquals(expected.get("createdAt").asString(), actual.createdAt().toString());
  }

  private static String token(String subject) throws Exception {
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    String header =
        encoder.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    String payload =
        encoder.encodeToString(
            ("{\"sub\":\""
                    + subject
                    + "\",\"exp\":"
                    + (Instant.now().getEpochSecond() + 3600)
                    + "}")
                .getBytes(StandardCharsets.UTF_8));
    String content = header + "." + payload;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return content
        + "."
        + encoder.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixtureConfig {

    @Bean
    ChatListingReader chatListingReader() {
      return new ChatListingReader() {
        @Override
        public Optional<ChatListingSnapshot> findById(long listingId) {
          return Optional.empty();
        }
      };
    }

    @Bean
    ChatUserReader chatUserReader() {
      return userIds -> Map.of();
    }
  }
}

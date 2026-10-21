package org.example.springtestci.trade.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.example.springtestci.chat.domain.ChatListingSnapshot;
import org.example.springtestci.chat.domain.ChatListingStatus;
import org.example.springtestci.chat.dto.ChatMessageResponse;
import org.example.springtestci.chat.service.ChatListingReader;
import org.example.springtestci.trade.service.InternalTransferPendingWriter;
import org.example.springtestci.trade.service.PublishedListing;
import org.example.springtestci.trade.service.PublishedListingAccess;
import org.example.springtestci.trade.service.PublishedListingStatus;
import org.example.springtestci.trade.support.PendingTransferConflictTranslator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@EnabledIfEnvironmentVariable(named = "P2_TRADE_TEST_ENABLED", matches = "true")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.features.trade-purchase-requests.enabled=true",
      "app.features.chat-api.enabled=true",
      "spring.flyway.enabled=false",
      "spring.datasource.driver-class-name=org.postgresql.Driver",
      "spring.jpa.hibernate.ddl-auto=validate"
    })
class PurchaseRequestHttpPostgresTest {

  private static final String SCHEMA =
      "p2_trade_http_" + UUID.randomUUID().toString().replace("-", "");
  private static final String JWT_SECRET = "p2-http-test-secret-that-is-at-least-32-bytes";
  private static DataSource adminDataSource;

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private SubscriptionProbe subscriptionProbe;

  @DynamicPropertySource
  static void configureDatabase(DynamicPropertyRegistry registry) {
    String url = requiredEnvironment("P2_TRADE_TEST_DB_URL");
    String user = requiredEnvironment("P2_TRADE_TEST_DB_USER");
    String password = requiredEnvironment("P2_TRADE_TEST_DB_PASSWORD");
    adminDataSource = new DriverManagerDataSource(url, user, password);
    new JdbcTemplate(adminDataSource).execute("create schema " + SCHEMA);
    String schemaUrl = withCurrentSchema(url, SCHEMA);
    DataSource schemaDataSource = new DriverManagerDataSource(schemaUrl, user, password);
    Flyway.configure()
        .dataSource(schemaDataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    createExternalFixtureTables(new JdbcTemplate(schemaDataSource));
    Flyway.configure()
        .dataSource(schemaDataSource)
        .locations("classpath:db/migration", "classpath:db/pending/trade-p2")
        .outOfOrder(true)
        .load()
        .migrate();
    createTransferFixtureTable(new JdbcTemplate(schemaDataSource));

    registry.add("spring.datasource.url", () -> schemaUrl);
    registry.add("spring.datasource.username", () -> user);
    registry.add("spring.datasource.password", () -> password);
    registry.add("app.jwt.secret", () -> JWT_SECRET);
  }

  @AfterAll
  static void tearDownDatabase() {
    if (adminDataSource != null) {
      new JdbcTemplate(adminDataSource).execute("drop schema " + SCHEMA + " cascade");
    }
  }

  @BeforeEach
  void setUpFixture() {
    jdbcTemplate.execute(
        "truncate table ownership_transfers, purchase_requests, chat_read_positions, chat_messages, chat_rooms, listings, products, users restart identity cascade");
    jdbcTemplate.update(
        "insert into users (id, email, password_hash, nickname, role, created_at, updated_at) values (1, 'seller@http.test', 'hash', 'seller', 'USER', current_timestamp, current_timestamp), (2, 'buyer@http.test', 'hash', 'buyer', 'USER', current_timestamp, current_timestamp), (3, 'other@http.test', 'hash', 'other', 'USER', current_timestamp, current_timestamp)");
    jdbcTemplate.update(
        "insert into products (id, category, manufacturer, manufacturer_normalized, model_name, serial_number_fingerprint, title, owner_id, serial_number_ciphertext) values (1, 'LAPTOP', 'maker', 'MAKER', 'model', 'http-fingerprint', 'title', 1, 'ciphertext')");
    jdbcTemplate.update(
        "insert into listings (id, product_id, seller_id, current_owner_id, title, price, condition_grade, description, status, published_at, source_draft_id, source_draft_version, active_slot) values (1, 1, 1, 1, 'title', 1, 1, 'description', 'ACTIVE', current_timestamp, 1, 0, 1)");
    jdbcTemplate.update(
        "insert into chat_rooms (id, listing_id, seller_id, inquirer_id) values (1, 1, 1, 2)");
  }

  @Test
  void createsPurchaseRequestThroughRunningHttpServerAndBearerJwt() throws Exception {
    HttpResponse<String> response =
        post("/api/v1/market/listings/1/purchase-requests", 2L, "{\"roomId\":1}");
    HttpResponse<String> sent = get("/api/v1/purchase-requests?direction=SENT&status=PENDING", 2L);
    HttpResponse<String> received =
        get("/api/v1/purchase-requests?direction=RECEIVED&roomId=1", 1L);

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.body()).contains("\"requestStatus\":\"PENDING\"");
    assertThat(sent.statusCode()).isEqualTo(200);
    assertThat(sent.body()).contains("\"listingStatus\":\"ACTIVE\"");
    assertThat(received.statusCode()).isEqualTo(200);
    assertThat(received.body()).contains("\"roomId\":1", "\"listingStatus\":\"ACTIVE\"");
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from purchase_requests where room_id = 1 and request_status = 'PENDING'",
                Long.class))
        .isEqualTo(1L);
  }

  @Test
  void reservationBlocksNewChatRoomButKeepsExistingRoomMessaging() throws Exception {
    HttpResponse<String> create =
        post("/api/v1/market/listings/1/purchase-requests", 2L, "{\"roomId\":1}");
    assertThat(create.statusCode()).isEqualTo(201);
    long requestId =
        jdbcTemplate.queryForObject(
            "select id from purchase_requests where room_id = 1", Long.class);

    HttpResponse<String> accept =
        post("/api/v1/purchase-requests/" + requestId + "/accept", 1L, "");
    HttpResponse<String> newRoom = post("/api/v1/market/listings/1/chat-rooms", 3L, "");
    WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    BlockingQueue<ChatMessageResponse> receivedMessages = new LinkedBlockingQueue<>();
    StompSession stompSession = connect(stompClient, 2L);
    subscribe(stompSession, 1L, receivedMessages);
    HttpResponse<String> existingMessage;
    ChatMessageResponse received;
    try {
      existingMessage =
          post("/api/v1/chat-rooms/1/messages", 2L, "{\"content\":\"still connected\"}");
      received = receivedMessages.poll(5, TimeUnit.SECONDS);
    } finally {
      stompSession.disconnect();
      stompClient.stop();
    }
    HttpResponse<String> messageHistory = get("/api/v1/chat-rooms/1/messages", 2L);
    long messageId =
        jdbcTemplate.queryForObject(
            "select id from chat_messages where room_id = 1 and sender_id = 2", Long.class);
    HttpResponse<String> readPosition =
        put("/api/v1/chat-rooms/1/read-position", 2L, "{\"lastReadMessageId\":" + messageId + "}");

    assertThat(accept.statusCode()).isEqualTo(201);
    assertThat(newRoom.statusCode()).isEqualTo(403);
    assertThat(existingMessage.statusCode()).isEqualTo(200);
    assertThat(received).isNotNull();
    assertThat(received.content()).isEqualTo("still connected");
    assertThat(messageHistory.statusCode()).isEqualTo(200);
    assertThat(messageHistory.body()).contains("still connected");
    assertThat(readPosition.statusCode()).isEqualTo(200);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from chat_messages where room_id = 1 and sender_id = 2",
                Long.class))
        .isEqualTo(1L);
    assertThat(
            jdbcTemplate.queryForObject(
                "select last_read_message_id from chat_read_positions where room_id = 1 and user_id = 2",
                Long.class))
        .isEqualTo(messageId);
  }

  @Test
  void rollsBackAcceptanceThroughSpringProxyWhenTransferInsertFails() throws Exception {
    post("/api/v1/market/listings/1/purchase-requests", 2L, "{\"roomId\":1}");
    long requestId =
        jdbcTemplate.queryForObject(
            "select id from purchase_requests where room_id = 1", Long.class);
    jdbcTemplate.execute(
        "create function fail_ownership_transfer() returns trigger language plpgsql as $$ begin raise exception 'injected transfer failure'; end $$");
    jdbcTemplate.execute(
        "create trigger fail_ownership_transfer before insert on ownership_transfers for each row execute function fail_ownership_transfer()");

    try {
      HttpResponse<String> response =
          post("/api/v1/purchase-requests/" + requestId + "/accept", 1L, "");

      assertThat(response.statusCode()).isEqualTo(500);
      assertThat(
              jdbcTemplate.queryForObject(
                  "select request_status from purchase_requests where id = ?",
                  String.class,
                  requestId))
          .isEqualTo("PENDING");
      assertThat(
              jdbcTemplate.queryForObject(
                  "select transaction_status from purchase_requests where id = ?",
                  String.class,
                  requestId))
          .isNull();
      assertThat(
              jdbcTemplate.queryForObject("select status from listings where id = 1", String.class))
          .isEqualTo("ACTIVE");
      assertThat(
              jdbcTemplate.queryForObject("select count(*) from ownership_transfers", Long.class))
          .isZero();
    } finally {
      jdbcTemplate.execute("drop trigger if exists fail_ownership_transfer on ownership_transfers");
      jdbcTemplate.execute("drop function if exists fail_ownership_transfer()");
    }
  }

  @Test
  void returnsTradeConflictAndRollsBackWhenProductAlreadyHasPendingTransfer() throws Exception {
    post("/api/v1/market/listings/1/purchase-requests", 2L, "{\"roomId\":1}");
    long targetRequestId =
        jdbcTemplate.queryForObject(
            "select id from purchase_requests where room_id = 1", Long.class);
    long otherRoomId = 2L;
    jdbcTemplate.update(
        "insert into chat_rooms (id, listing_id, seller_id, inquirer_id) values (?, 1, 1, 3)",
        otherRoomId);
    long existingRequestId =
        jdbcTemplate.queryForObject(
            "insert into purchase_requests (room_id, listing_id, request_status) values (?, 1, 'PENDING') returning id",
            Long.class,
            otherRoomId);
    long existingTransferId =
        jdbcTemplate.queryForObject(
            "insert into ownership_transfers (product_id, purchase_request_id, from_user_id, to_user_id, transfer_type, status) values (1, ?, 1, 3, 'INTERNAL', 'PENDING') returning id",
            Long.class,
            existingRequestId);

    HttpResponse<String> response =
        post("/api/v1/purchase-requests/" + targetRequestId + "/accept", 1L, "");

    assertThat(response.statusCode()).isEqualTo(409);
    assertThat(response.body()).contains("\"code\":\"TRADE_001\"");
    assertThat(
            jdbcTemplate.queryForObject(
                "select request_status from purchase_requests where id = ?",
                String.class,
                targetRequestId))
        .isEqualTo("PENDING");
    assertThat(
            jdbcTemplate.queryForObject(
                "select transaction_status from purchase_requests where id = ?",
                String.class,
                targetRequestId))
        .isNull();
    assertThat(
            jdbcTemplate.queryForObject("select status from listings where id = 1", String.class))
        .isEqualTo("ACTIVE");
    assertThat(
            jdbcTemplate.queryForObject(
                "select id from ownership_transfers where product_id = 1 and status = 'PENDING'",
                Long.class))
        .isEqualTo(existingTransferId);
    assertThat(jdbcTemplate.queryForObject("select count(*) from ownership_transfers", Long.class))
        .isEqualTo(1L);
  }

  @Test
  void rejectsBuyerAcceptanceWithoutChangingPersistentState() throws Exception {
    post("/api/v1/market/listings/1/purchase-requests", 2L, "{\"roomId\":1}");
    long requestId =
        jdbcTemplate.queryForObject(
            "select id from purchase_requests where room_id = 1", Long.class);

    HttpResponse<String> response =
        post("/api/v1/purchase-requests/" + requestId + "/accept", 2L, "");

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).contains("\"code\":\"AUTH_002\"");
    assertThat(
            jdbcTemplate.queryForObject(
                "select request_status from purchase_requests where id = ?",
                String.class,
                requestId))
        .isEqualTo("PENDING");
    assertThat(
            jdbcTemplate.queryForObject(
                "select transaction_status from purchase_requests where id = ?",
                String.class,
                requestId))
        .isNull();
    assertThat(
            jdbcTemplate.queryForObject("select status from listings where id = 1", String.class))
        .isEqualTo("ACTIVE");
    assertThat(jdbcTemplate.queryForObject("select count(*) from ownership_transfers", Long.class))
        .isZero();
  }

  @Test
  void rejectsInvalidAuthenticationInputAndPagingThroughRunningServer() throws Exception {
    HttpResponse<String> missingRoom =
        post("/api/v1/market/listings/1/purchase-requests", 2L, "{}");
    HttpResponse<String> invalidPage = get("/api/v1/purchase-requests?direction=SENT&page=-1", 2L);
    HttpResponse<String> invalidSize = get("/api/v1/purchase-requests?direction=SENT&size=101", 2L);
    HttpRequest invalidTokenRequest =
        HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/v1/purchase-requests?direction=SENT"))
            .header("Authorization", "Bearer invalid-token")
            .GET()
            .build();
    HttpResponse<String> invalidToken =
        HttpClient.newHttpClient().send(invalidTokenRequest, HttpResponse.BodyHandlers.ofString());

    assertThat(missingRoom.statusCode()).isEqualTo(400);
    assertThat(invalidPage.statusCode()).isEqualTo(400);
    assertThat(invalidSize.statusCode()).isEqualTo(400);
    assertThat(invalidToken.statusCode()).isEqualTo(401);
    assertThat(jdbcTemplate.queryForObject("select count(*) from purchase_requests", Long.class))
        .isZero();
  }

  private HttpResponse<String> post(String path, long userId, String body) throws Exception {
    return send(path, userId, "POST", body);
  }

  private HttpResponse<String> put(String path, long userId, String body) throws Exception {
    return send(path, userId, "PUT", body);
  }

  private HttpResponse<String> get(String path, long userId) throws Exception {
    return send(path, userId, "GET", null);
  }

  private HttpResponse<String> send(String path, long userId, String method, String body)
      throws Exception {
    HttpRequest.BodyPublisher publisher =
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Authorization", "Bearer " + tokenFor(userId))
            .header("Content-Type", "application/json")
            .method(method, publisher)
            .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  private String tokenFor(long userId) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(Long.toString(userId))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
    return jwtEncoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  private StompSession connect(WebSocketStompClient client, long userId) throws Exception {
    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.setOrigin("http://localhost:3000");
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("Authorization", "Bearer " + tokenFor(userId));
    return client
        .connectAsync(
            URI.create("ws://127.0.0.1:" + port + "/ws-stomp"),
            handshakeHeaders,
            connectHeaders,
            new StompSessionHandlerAdapter() {})
        .get(10, TimeUnit.SECONDS);
  }

  private void subscribe(
      StompSession session, long roomId, BlockingQueue<ChatMessageResponse> receivedMessages)
      throws Exception {
    String destination = "/sub/chat/room/" + roomId;
    int expectedCount = subscriptionProbe.processedCount(destination) + 1;
    session.subscribe(
        destination,
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
    subscriptionProbe.awaitProcessed(destination, expectedCount);
  }

  private static void createExternalFixtureTables(JdbcTemplate jdbcTemplate) {
    jdbcTemplate.execute(
        "alter table listings add column current_owner_id bigint references users(id)");
  }

  private static void createTransferFixtureTable(JdbcTemplate jdbcTemplate) {
    jdbcTemplate.execute(
        """
        create table ownership_transfers (
          id bigint generated by default as identity primary key,
          product_id bigint not null references products(id),
          purchase_request_id bigint not null unique references purchase_requests(id),
          from_user_id bigint not null references users(id),
          to_user_id bigint not null references users(id),
          transfer_type varchar(20) not null,
          status varchar(20) not null,
          token_hash varchar(255),
          recipient_approved_at timestamp with time zone,
          owner_approved_at timestamp with time zone,
          created_at timestamp with time zone not null default current_timestamp
        )
        """);
    jdbcTemplate.execute(
        "create unique index uq_ownership_transfers_pending_product on ownership_transfers (product_id) where status = 'PENDING'");
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must be configured");
    }
    return value;
  }

  private static String withCurrentSchema(String url, String schema) {
    return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
  }

  @TestConfiguration
  static class FixtureConfiguration {

    @Bean
    SubscriptionProbe subscriptionProbe() {
      return new SubscriptionProbe();
    }

    @Bean
    @Primary
    ChatListingReader chatListingReader(JdbcTemplate jdbcTemplate) {
      return listingId ->
          jdbcTemplate
              .query(
                  "select listing.id, listing.product_id, listing.seller_id, product.owner_id, listing.status from listings listing join products product on product.id = listing.product_id where listing.id = ?",
                  (resultSet, rowNumber) ->
                      new ChatListingSnapshot(
                          resultSet.getLong("id"),
                          resultSet.getLong("seller_id"),
                          resultSet.getLong("product_id"),
                          resultSet.getLong("owner_id"),
                          ChatListingStatus.valueOf(resultSet.getString("status")),
                          "fixture listing"),
                  listingId)
              .stream()
              .findFirst();
    }

    @Bean
    @Primary
    PublishedListingAccess publishedListingAccess(JdbcTemplate jdbcTemplate) {
      return new PublishedListingAccess() {
        @Override
        public Optional<PublishedListing> findById(long listingId) {
          return find(listingId, false);
        }

        @Override
        public Optional<PublishedListing> findByIdForUpdate(long listingId) {
          Long productId =
              jdbcTemplate.queryForObject(
                  "select product_id from listings where id = ?", Long.class, listingId);
          if (productId == null) {
            return Optional.empty();
          }
          jdbcTemplate.queryForObject(
              "select owner_id from products where id = ? for update", Long.class, productId);
          return find(listingId, true);
        }

        @Override
        public Map<Long, PublishedListingStatus> findStatuses(Collection<Long> listingIds) {
          if (listingIds.isEmpty()) {
            return Map.of();
          }
          String placeholders =
              listingIds.stream()
                  .map(ignored -> "?")
                  .collect(java.util.stream.Collectors.joining(","));
          return jdbcTemplate.query(
              "select id, status from listings where id in (" + placeholders + ")",
              resultSet -> {
                java.util.HashMap<Long, PublishedListingStatus> statuses =
                    new java.util.HashMap<>();
                while (resultSet.next()) {
                  statuses.put(
                      resultSet.getLong("id"),
                      PublishedListingStatus.valueOf(resultSet.getString("status")));
                }
                return statuses;
              },
              listingIds.toArray());
        }

        @Override
        public void reserve(long listingId) {
          int updated =
              jdbcTemplate.update(
                  "update listings set status = 'RESERVED' where id = ? and status = 'ACTIVE'",
                  listingId);
          if (updated != 1) {
            throw new org.example.springtestci.common.error.CommonException(
                org.example.springtestci.common.error.ErrorCode.TRADE_CONFLICT);
          }
        }

        private Optional<PublishedListing> find(long listingId, boolean lock) {
          String suffix = lock ? " for update of listing" : "";
          return jdbcTemplate
              .query(
                  "select listing.id, listing.product_id, listing.seller_id, product.owner_id, listing.status from listings listing join products product on product.id = listing.product_id where listing.id = ?"
                      + suffix,
                  (resultSet, rowNumber) ->
                      new PublishedListing(
                          resultSet.getLong("id"),
                          resultSet.getLong("product_id"),
                          resultSet.getLong("seller_id"),
                          resultSet.getLong("owner_id"),
                          PublishedListingStatus.valueOf(resultSet.getString("status"))),
                  listingId)
              .stream()
              .findFirst();
        }
      };
    }

    @Bean
    @Primary
    InternalTransferPendingWriter internalTransferPendingWriter(JdbcTemplate jdbcTemplate) {
      return command -> {
        int inserted;
        try {
          inserted =
              jdbcTemplate.update(
                  "insert into ownership_transfers (product_id, purchase_request_id, from_user_id, to_user_id, transfer_type, status) values (?, ?, ?, ?, ?, ?)",
                  command.productId(),
                  command.purchaseRequestId(),
                  command.fromUserId(),
                  command.toUserId(),
                  command.transferType(),
                  command.status());
        } catch (DataAccessException exception) {
          throw PendingTransferConflictTranslator.translate(exception);
        }
        if (inserted != 1) {
          throw new IllegalStateException(
              "Ownership transfer insert affected an unexpected row count");
        }
      };
    }
  }

  static class SubscriptionProbe {

    private final Map<String, AtomicInteger> processedSubscriptions = new ConcurrentHashMap<>();

    @EventListener
    void onSubscribe(SessionSubscribeEvent event) {
      SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
      processedSubscriptions
          .computeIfAbsent(accessor.getDestination(), ignored -> new AtomicInteger())
          .incrementAndGet();
    }

    int processedCount(String destination) {
      return processedSubscriptions.getOrDefault(destination, new AtomicInteger()).get();
    }

    void awaitProcessed(String destination, int expectedCount) throws InterruptedException {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (processedCount(destination) < expectedCount && System.nanoTime() < deadline) {
        Thread.sleep(10);
      }
      assertThat(processedCount(destination)).isGreaterThanOrEqualTo(expectedCount);
    }
  }
}

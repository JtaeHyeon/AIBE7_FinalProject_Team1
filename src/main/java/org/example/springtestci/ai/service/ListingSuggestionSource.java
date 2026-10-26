package org.example.springtestci.ai.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.dto.ListingPublicationResponse;
import org.example.springtestci.listing.dto.ListingPublicationResponse.DetailsStatus;
import org.example.springtestci.listing.service.ListingHistoryReader.EventStatus;
import org.example.springtestci.listing.service.ListingHistoryReader.State;
import org.example.springtestci.listing.service.ListingPublicationService;
import org.example.springtestci.product.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 짧은 제품 잠금 안에서 입력을 완성한다. API 지연 동안 DB 연결이나 잠금을 유지하지 않는다. */
@Service
public class ListingSuggestionSource {
  static final int MAX_INPUT_BYTES = 48_000;
  private final ProductService products;
  private final ListingPublicationService publications;
  private final ObjectMapper json;

  public ListingSuggestionSource(
      ProductService products, ListingPublicationService publications, ObjectMapper json) {
    this.products = products;
    this.publications = publications;
    this.json = json;
  }

  @Transactional
  public Snapshot capture(long productId, long userId) {
    var publication = publications.getForAi(productId, userId);
    var owned = products.getForListing(userId, productId);
    if (publication.history().status() != State.READY)
      throw new CommonException(ErrorCode.AI_SOURCE_UNAVAILABLE);
    // 무효 이력·미검토 자유 입력은 실제 사실로 사용하지 않는다. 존재·날짜·상태는 전부 남긴다.
    var events = publication.history().items().stream().map(AiEvent::from).toList();
    var data = new Input(publication.product(), events);
    String source = json.writeValueAsString(data);
    if (source.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES)
      throw new CommonException(ErrorCode.AI_INPUT_TOO_LARGE);
    String fingerprint = digest(owned.factsVersion() + ":" + source);
    return new Snapshot(owned.factsVersion(), fingerprint, source, publication.history().sample());
  }

  private static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  public record Snapshot(long factsVersion, String sourceVersion, String json, boolean sample) {}

  private record Input(ListingPublicationResponse.Product product, List<AiEvent> history) {}

  private record AiEvent(
      long eventId,
      String type,
      String occurredOn,
      String status,
      boolean previousOwner,
      boolean corrected,
      boolean detailsReviewed,
      String title,
      String content,
      String replacedPart) {
    static AiEvent from(ListingPublicationResponse.Event event) {
      boolean usable =
          event.status() == EventStatus.VALID && event.detailsStatus() == DetailsStatus.REVIEWED;
      return new AiEvent(
          event.eventId(),
          event.eventType().name(),
          event.occurredOn().toString(),
          event.status().name(),
          event.previousOwner(),
          !event.corrections().isEmpty(),
          event.detailsStatus() == DetailsStatus.REVIEWED,
          usable ? event.title() : null,
          usable ? event.content() : null,
          usable ? event.replacedPart() : null);
    }
  }
}

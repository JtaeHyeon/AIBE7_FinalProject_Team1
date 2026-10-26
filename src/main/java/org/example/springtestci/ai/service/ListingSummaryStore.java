package org.example.springtestci.ai.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.example.springtestci.ai.dto.ListingSummaryResponse;
import org.example.springtestci.ai.entity.ListingSummaryState;
import org.example.springtestci.ai.entity.ListingSummaryState.Status;
import org.example.springtestci.ai.repository.ListingSummaryRepository;
import org.example.springtestci.ai.repository.ListingSummaryRepository.Pending;
import org.example.springtestci.common.error.*;
import org.example.springtestci.listing.entity.Listing;
import org.example.springtestci.listing.repository.ListingRepository;
import org.example.springtestci.product.service.ProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 요약 상태의 짧은 트랜잭션 경계. 제품 → 판매글 잠금을 지키고 외부 AI를 호출하지 않는다. */
@Service
public class ListingSummaryStore {
  private final ListingSummaryRepository summaries;
  private final ListingRepository listings;
  private final ProductService products;
  private final ListingSuggestionSource sources;
  private final AiListingTextPolicy policy;
  private final Clock clock;
  private final AiListingProperties properties;
  private final boolean enabled;

  public ListingSummaryStore(
      ListingSummaryRepository summaries,
      ListingRepository listings,
      ProductService products,
      ListingSuggestionSource sources,
      AiListingTextPolicy policy,
      Clock clock,
      AiListingProperties properties,
      @Value("${app.ai.summary.enabled:true}") boolean enabled) {
    this.summaries = summaries;
    this.listings = listings;
    this.products = products;
    this.sources = sources;
    this.policy = policy;
    this.clock = clock;
    this.properties = properties;
    this.enabled = enabled;
  }

  @Transactional
  public ListingSummaryResponse get(long listingId, long actorId) {
    var current = lock(listingId, null);
    return response(current, summaries.read(listingId), actorId);
  }

  /** 게시 완료 뒤 별도 트랜잭션으로 접수한다. 접수 실패가 이미 성공한 게시를 되돌리지 않는다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ListingSummaryResponse enqueue(long listingId, Long actorId) {
    var current = lock(listingId, actorId);
    var state = summaries.read(listingId);
    if (!enabled) {
      if (actorId != null) throw new CommonException(ErrorCode.SUMMARY_UNAVAILABLE);
      return response(current, state, null);
    }
    boolean same = matches(current, state);
    if (same
        && (state.status() == Status.QUEUED
            || state.status() == Status.PROCESSING
            || state.status() == Status.READY)) return response(current, state, actorId);
    long wait = same ? retryWait(state) : 0;
    if (wait > 0)
      throw new RateLimitedException(ErrorCode.SUMMARY_RETRY_LIMITED, Duration.ofSeconds(wait));
    summaries.queue(
        listingId,
        current.productVersion(),
        current.listing().getContentVersion(),
        UUID.randomUUID(),
        clock.instant());
    return response(current, summaries.read(listingId), actorId);
  }

  @Transactional
  public Job claim(Pending pending) {
    var current = lock(pending.listingId(), null);
    var state = summaries.read(pending.listingId());
    if (state.status() != Status.QUEUED || !Objects.equals(state.attemptId(), pending.attemptId()))
      return null;
    if (!matches(current, state)) {
      summaries.fail(pending, "AI_005", clock.instant());
      return null;
    }
    if (!summaries.claim(pending, clock.instant())) return null;
    var source = sources.capture(current.listing().getProductId(), current.listing().getSellerId());
    policy.validateInput(source.json());
    String input =
        "{\"productAndHistory\":"
            + source.json()
            + ",\"sellerReportedGrade\":"
            + current.listing().getConditionGrade()
            + "}";
    if (input.getBytes(StandardCharsets.UTF_8).length > ListingSuggestionSource.MAX_INPUT_BYTES)
      throw new CommonException(ErrorCode.AI_INPUT_TOO_LARGE);
    return new Job(
        pending,
        current.listing().getSellerId(),
        current.productVersion(),
        current.listing().getContentVersion(),
        source.sourceVersion(),
        input);
  }

  @Transactional
  public void complete(Job job, String text) {
    var current = lock(job.pending().listingId(), null);
    var state = summaries.read(job.pending().listingId());
    if (state.status() != Status.PROCESSING
        || !Objects.equals(state.attemptId(), job.pending().attemptId())) return;
    if (current.productVersion() != job.productVersion()
        || current.listing().getContentVersion() != job.listingVersion()) {
      summaries.fail(job.pending(), "AI_005", clock.instant());
      return;
    }
    var source = sources.capture(current.listing().getProductId(), current.listing().getSellerId());
    if (!source.sourceVersion().equals(job.sourceFingerprint())) {
      summaries.fail(job.pending(), "AI_005", clock.instant());
      return;
    }
    summaries.ready(job.pending(), text, clock.instant());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void fail(Pending pending, String code) {
    summaries.fail(
        pending, code != null && code.matches("AI_\\d{3}") ? code : "AI_001", clock.instant());
  }

  @Transactional
  public void recoverExpired() {
    Instant now = clock.instant();
    summaries.expire(
        now.minusSeconds(properties.timeoutSeconds() + 60L), now.minus(Duration.ofMinutes(5)), now);
  }

  private Current lock(long listingId, Long actorId) {
    var access =
        listings
            .findAccessById(listingId)
            .filter(row -> actorId == null || row.getSellerId() == actorId)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    products.lockOwnedForListing(access.getSellerId(), access.getProductId());
    var listing =
        listings
            .findLockedById(listingId)
            .filter(row -> row.getStatus().isPubliclyReadable())
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    long version =
        products.getForListing(access.getSellerId(), access.getProductId()).factsVersion();
    return new Current(listing, version);
  }

  private boolean matches(Current current, ListingSummaryState state) {
    return Objects.equals(state.productVersion(), current.productVersion())
        && Objects.equals(state.listingVersion(), current.listing().getContentVersion());
  }

  private long retryWait(ListingSummaryState state) {
    return state.status() == Status.FAILED && state.updatedAt() != null
        ? Math.max(0, 60 - Duration.between(state.updatedAt(), clock.instant()).toSeconds())
        : 0;
  }

  private ListingSummaryResponse response(
      Current current, ListingSummaryState state, Long actorId) {
    boolean stale = state.status() != Status.NOT_REQUESTED && !matches(current, state);
    Status status = stale ? Status.FAILED : state.status();
    long wait = stale ? 0 : retryWait(state);
    boolean owner = actorId != null && current.listing().getSellerId() == actorId;
    return new ListingSummaryResponse(
        status,
        status == Status.READY ? state.text() : null,
        stale ? "AI_005" : state.errorCode(),
        state.updatedAt(),
        enabled
            && owner
            && (status == Status.NOT_REQUESTED || status == Status.FAILED)
            && wait == 0,
        wait);
  }

  private record Current(Listing listing, long productVersion) {}

  public record Job(
      Pending pending,
      long sellerId,
      long productVersion,
      long listingVersion,
      String sourceFingerprint,
      String input) {}
}

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

  /** 공개 조건을 확인한 뒤 DB에 저장된 상태만 읽는다. 이 조회가 요약 생성을 시작하지는 않는다. */
  @Transactional
  public ListingSummaryResponse get(long listingId, long actorId) {
    var current = lock(listingId, null);
    return response(current, summaries.read(listingId), actorId);
  }

  /** 게시 완료 뒤 별도 트랜잭션으로 접수한다. 접수 실패가 이미 성공한 게시를 되돌리지 않는다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ListingSummaryResponse enqueue(long listingId, Long actorId) {
    // actorId가 없으면 게시 이벤트의 자동 접수다. 수동 요청이면 해당 판매자인지도 검사한다.
    var current = lock(listingId, actorId);
    var state = summaries.read(listingId);
    if (!enabled) {
      if (actorId != null) throw new CommonException(ErrorCode.SUMMARY_UNAVAILABLE);
      return response(current, state, null);
    }
    // 같은 자료로 생성 중이거나 이미 완료된 결과가 있으면 중복 생성하지 않는다.
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

  /** 대기 작업을 처리 중으로 바꾸고 AI 입력을 준비한다. 여러 작업자 중 한 명만 확보할 수 있다. */
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

  /** 생성 후에도 판매 상태·소유권·버전·이력이 그대로인 경우에만 이번 시도의 결과를 저장한다. */
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

  /** 서버 종료 등으로 멈춘 작업을 실패 처리해 화면에 계속 생성 중으로 남지 않게 한다. */
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

  // 동일 자료의 실패 재시도는 60초 간격으로 제한해 짧은 시간의 반복 AI 호출을 막는다.
  private long retryWait(ListingSummaryState state) {
    return state.status() == Status.FAILED && state.updatedAt() != null
        ? Math.max(0, 60 - Duration.between(state.updatedAt(), clock.instant()).toSeconds())
        : 0;
  }

  private ListingSummaryResponse response(
      Current current, ListingSummaryState state, Long actorId) {
    // 요약의 기준 버전이 현재 자료와 다르면 옛 문장은 숨기고 판매자에게 다시 생성할 기회를 준다.
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

  /** DB 잠금을 해제한 뒤 작업자에게 넘길 입력과 기준 버전이다. 완료 시점에 다시 비교한다. */
  public record Job(
      Pending pending,
      long sellerId,
      long productVersion,
      long listingVersion,
      String sourceFingerprint,
      String input) {}
}

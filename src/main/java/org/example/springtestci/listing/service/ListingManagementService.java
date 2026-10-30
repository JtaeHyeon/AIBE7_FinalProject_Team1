package org.example.springtestci.listing.service;

import java.util.HashSet;
import java.util.List;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.dto.CancelListingRequest;
import org.example.springtestci.listing.dto.ListingManagementResponse;
import org.example.springtestci.listing.dto.ListingPreviewResponse;
import org.example.springtestci.listing.dto.ListingStateResponse;
import org.example.springtestci.listing.dto.UpdateListingRequest;
import org.example.springtestci.listing.entity.Listing;
import org.example.springtestci.listing.entity.ListingStatus;
import org.example.springtestci.listing.event.ListingContentChanged;
import org.example.springtestci.listing.repository.ListingAuditRepository;
import org.example.springtestci.listing.repository.ListingRepository;
import org.example.springtestci.product.service.ProductService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 게시자이자 현재 제품 소유자인 판매자만 글을 수정하거나 판매 중지할 수 있게 한다. */
@Service
public class ListingManagementService {
  private final ListingRepository listings;
  private final ProductService products;
  private final ListingAuditRepository audit;
  private final ObjectProvider<ListingTradeCanceller> tradeCancellers;
  private final ObjectProvider<ListingHistoryPrivatizer> historyPrivatizers;
  private final ObjectMapper json;
  private final ApplicationEventPublisher events;

  public ListingManagementService(
      ListingRepository listings,
      ProductService products,
      ListingAuditRepository audit,
      ObjectProvider<ListingTradeCanceller> tradeCancellers,
      ObjectProvider<ListingHistoryPrivatizer> historyPrivatizers,
      ObjectMapper json,
      ApplicationEventPublisher events) {
    this.listings = listings;
    this.products = products;
    this.audit = audit;
    this.tradeCancellers = tradeCancellers;
    this.historyPrivatizers = historyPrivatizers;
    this.json = json;
    this.events = events;
  }

  @Transactional
  public ListingManagementResponse get(long listingId, long userId) {
    return response(ownedLocked(listingId, userId));
  }

  /** 이력·제품 사양을 수정하지 않고, 필수 판매 조건과 사진의 현재 유효성을 다시 확인한다. */
  @Transactional
  public ListingManagementResponse update(
      long listingId, long userId, UpdateListingRequest request) {
    var listing = ownedLocked(listingId, userId);
    requireVersion(listing, request.version());
    if (listing.getStatus() != ListingStatus.ACTIVE)
      throw new CommonException(ErrorCode.LISTING_STATE_CONFLICT);
    var requested = request.selectedImageIds();
    var allowed = new HashSet<Long>();
    products
        .getForListing(userId, listing.getProductId())
        .images()
        .forEach(image -> allowed.add(image.imageId()));
    if (new HashSet<>(requested).size() != requested.size() || !allowed.containsAll(requested))
      throw new CommonException(ErrorCode.INVALID_INPUT);
    for (long imageId : requested) products.image(userId, listing.getProductId(), imageId);
    String before = facts(listing);
    listing.revise(request);
    listings.flush();
    audit.changed(
        listing.getProductId(), userId, listingId, "LISTING_UPDATED", before, facts(listing), null);
    events.publishEvent(new ListingContentChanged(listingId));
    return response(listing);
  }

  /** 글·거래/이전 취소·이력 비공개·감사 기록을 모두 반영하거나 모두 롤백한다. */
  @Transactional
  public ListingStateResponse cancel(long listingId, long userId, CancelListingRequest request) {
    var listing = ownedLocked(listingId, userId);
    requireVersion(listing, request.version());
    // 응답 유실 재시도는 부작용을 반복하지 않는다. 재판매 글의 이력을 다시 숨기지 않는다.
    if (listing.getStatus() == ListingStatus.CANCELLED) return state(listing);
    if (!listing.getStatus().isPubliclyReadable())
      throw new CommonException(ErrorCode.LISTING_STATE_CONFLICT);
    var trade = tradeCancellers.getIfAvailable();
    var history = historyPrivatizers.getIfAvailable();
    if (trade == null || history == null)
      throw new CommonException(ErrorCode.LISTING_CANCEL_UNAVAILABLE);
    String before = facts(listing);
    var command =
        new ListingTradeCanceller.Command(
            listing.getProductId(),
            listingId,
            userId,
            listing.getStatus(),
            request.reason(),
            request.tradeReason());
    listing.cancel();
    listings.flush();
    trade.cancel(command);
    if (!listings.existsByProductIdAndStatusIn(
        listing.getProductId(), List.of(ListingStatus.ACTIVE, ListingStatus.RESERVED))) {
      history.makePrivate(listing.getProductId(), listingId, userId);
      // 마지막 열린 판매글이 닫혀 이력이 비공개로 바뀐 경우만 SEC-08 처리 기록을 남긴다.
      audit.changed(
          listing.getProductId(),
          userId,
          listingId,
          "HISTORY_VISIBILITY_CHANGED",
          "{\"visibility\":\"PUBLIC\"}",
          "{\"visibility\":\"PRIVATE\"}",
          request.reason());
    }
    audit.changed(
        listing.getProductId(),
        userId,
        listingId,
        "LISTING_CANCELLED",
        before,
        facts(listing),
        request.reason());
    return state(listing);
  }

  // 제품 → 판매글 순으로 잠가 소유권 이전·예약 처리와 동시에 실행되어도 검사 결과를 유지한다.
  private Listing ownedLocked(long listingId, long userId) {
    var access =
        listings
            .findAccessById(listingId)
            .filter(value -> value.getSellerId() == userId)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    products.lockOwnedForListing(userId, access.getProductId());
    return listings
        .findLockedById(listingId)
        .filter(value -> value.getSellerId() == userId)
        .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  private void requireVersion(Listing listing, Long version) {
    if (version == null || version != listing.getContentVersion())
      throw new CommonException(ErrorCode.LISTING_EDIT_CONFLICT);
  }

  private ListingManagementResponse response(Listing listing) {
    var images =
        products.getForListing(listing.getSellerId(), listing.getProductId()).images().stream()
            .map(
                image ->
                    new ListingPreviewResponse.Image(
                        image.imageId(),
                        "/api/v1/products/"
                            + listing.getProductId()
                            + "/listing-draft/images/"
                            + image.imageId()))
            .toList();
    return new ListingManagementResponse(
        listing.getId(),
        listing.getProductId(),
        listing.getContentVersion(),
        listing.getStatus(),
        new ListingPreviewResponse.Content(
            listing.getTitle(),
            listing.getDescription(),
            listing.getPrice(),
            listing.getConditionGrade()),
        listing.getImageIds(),
        images,
        listing.getStatus() == ListingStatus.ACTIVE,
        listing.getStatus().isPubliclyReadable());
  }

  private ListingStateResponse state(Listing listing) {
    return new ListingStateResponse(
        listing.getId(), listing.getContentVersion(), listing.getStatus());
  }

  // 수정·중지 전후의 판매 조건을 감사 기록으로 남겨 누가 무엇을 바꿨는지 추적한다.
  private String facts(Listing listing) {
    return json.writeValueAsString(
        new AuditFacts(
            listing.getTitle(),
            listing.getDescription(),
            listing.getPrice(),
            listing.getConditionGrade(),
            listing.getImageIds(),
            listing.getStatus(),
            listing.getContentVersion()));
  }

  private record AuditFacts(
      String title,
      String description,
      java.math.BigDecimal price,
      Integer conditionGrade,
      List<Long> selectedImageIds,
      ListingStatus status,
      long contentVersion) {}
}

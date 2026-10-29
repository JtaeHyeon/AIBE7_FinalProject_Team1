package org.example.springtestci.listing.service;

import java.util.List;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.dto.ListingDraftResponse;
import org.example.springtestci.listing.dto.PublishListingRequest;
import org.example.springtestci.listing.dto.PublishedListingResponse;
import org.example.springtestci.listing.entity.Listing;
import org.example.springtestci.listing.entity.ListingStatus;
import org.example.springtestci.listing.event.ListingPublished;
import org.example.springtestci.listing.repository.ListingAuditRepository;
import org.example.springtestci.listing.repository.ListingRepository;
import org.example.springtestci.product.service.ProductService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 게시 조건 검사부터 판매글 생성·이력 공개·초안 정리까지 하나의 작업으로 처리한다. */
@Service
public class ListingPublishService {
  private final ProductService products;
  private final ListingDraftService drafts;
  private final ListingPreviewService previews;
  private final ListingRepository listings;
  private final ListingAuditRepository audit;
  private final ObjectProvider<ListingPublishConstraints> constraints;
  private final ObjectProvider<ListingHistoryPublisher> historyPublishers;
  private final ApplicationEventPublisher events;

  public ListingPublishService(
      ProductService products,
      ListingDraftService drafts,
      ListingPreviewService previews,
      ListingRepository listings,
      ListingAuditRepository audit,
      ObjectProvider<ListingPublishConstraints> constraints,
      ObjectProvider<ListingHistoryPublisher> historyPublishers,
      ApplicationEventPublisher events) {
    this.products = products;
    this.drafts = drafts;
    this.previews = previews;
    this.listings = listings;
    this.audit = audit;
    this.constraints = constraints;
    this.historyPublishers = historyPublishers;
    this.events = events;
  }

  /** 이력 공개·초안 삭제·감사 기록까지 같은 트랜잭션에서 처리한다. */
  @Transactional
  public Result publish(long productId, long userId, PublishListingRequest request) {
    products.lockOwnedForListing(userId, productId);
    // 게시 응답이 유실되어 같은 요청을 다시 보내도 새 글을 만들지 않고 기존 결과를 돌려준다.
    var previous = listings.findBySourceDraftId(request.draftId());
    if (previous.isPresent()) {
      var listing = previous.get();
      if (listing.getProductId() != productId
          || listing.getSellerId() != userId
          || listing.getSourceDraftVersion() != request.version()
          || !listing.getStatus().isPubliclyReadable())
        throw new CommonException(ErrorCode.DRAFT_CONFLICT);
      return new Result(PublishedListingResponse.from(listing), false);
    }
    var draft = drafts.get(productId, userId);
    if (draft.draftId() != request.draftId() || draft.version() != request.version())
      throw new CommonException(ErrorCode.DRAFT_CONFLICT);
    validateRequired(draft);
    if (listings.existsByProductIdAndStatusIn(
        productId, List.of(ListingStatus.ACTIVE, ListingStatus.RESERVED)))
      throw new CommonException(ErrorCode.LISTING_ALREADY_OPEN);

    // 거래·이전과 이력 공개 연동이 준비되지 않았다면 안전하게 게시를 막는다.
    var checker = constraints.getIfAvailable();
    var publisher = historyPublishers.getIfAvailable();
    if (checker == null || publisher == null)
      throw new CommonException(ErrorCode.LISTING_PUBLISH_UNAVAILABLE);
    var state = checker.inspect(productId);
    if (state == null || !state.ready())
      throw new CommonException(ErrorCode.LISTING_PUBLISH_UNAVAILABLE);
    if (state.ongoingTrade() || state.pendingTransfer())
      throw new CommonException(ErrorCode.LISTING_PUBLISH_BLOCKED);
    // 같은 잠금 아래 사진 소속·존재와 전체 이력 원천의 유효성을 다시 검증한다.
    var preview = previews.get(productId, draft.draftId(), draft.version(), userId, 0, 1);
    if (preview.history().status() != ListingHistoryReader.State.READY)
      throw new CommonException(ErrorCode.LISTING_HISTORY_UNAVAILABLE);
    var listing = listings.saveAndFlush(new Listing(draft, userId));
    publisher.publish(productId, listing.getId(), userId);
    drafts.delete(productId, draft.draftId(), userId, draft.version());
    audit.published(productId, userId, listing.getId());
    // 이력 공개 상태 변경도 게시와 같은 트랜잭션에서 별도 처리 기록으로 남긴다(SEC-08).
    audit.changed(
        productId,
        userId,
        listing.getId(),
        "HISTORY_VISIBILITY_CHANGED",
        "{\"visibility\":\"PRIVATE\"}",
        "{\"visibility\":\"PUBLIC\"}",
        null);
    // 요약 처리는 이 이벤트를 받은 별도 수신자가 게시 커밋 이후 접수한다.
    events.publishEvent(new ListingPublished(listing.getId()));
    return new Result(PublishedListingResponse.from(listing), true);
  }

  // 빈 항목을 허용하는 임시저장과 달리, 게시할 때는 판매 조건과 사진이 모두 필요하다.
  private void validateRequired(ListingDraftResponse draft) {
    if (draft.title() == null
        || draft.title().isBlank()
        || draft.description() == null
        || draft.description().isBlank()
        || draft.price() == null
        || draft.price().signum() <= 0
        || draft.price().stripTrailingZeros().scale() > 0
        || draft.conditionGrade() == null
        || draft.conditionGrade() < 1
        || draft.conditionGrade() > 5
        || draft.selectedImageIds().isEmpty()) {
      throw new CommonException(ErrorCode.LISTING_REQUIRED_FIELDS);
    }
  }

  public record Result(PublishedListingResponse response, boolean created) {}
}

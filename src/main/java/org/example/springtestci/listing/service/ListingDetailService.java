package org.example.springtestci.listing.service;

import java.io.IOException;
import org.example.springtestci.ai.service.ListingSummaryStore;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.dto.ListingDetailResponse;
import org.example.springtestci.listing.dto.ListingPreviewResponse;
import org.example.springtestci.listing.entity.Listing;
import org.example.springtestci.listing.repository.ListingRepository;
import org.example.springtestci.product.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 게시글·제품 사양·공개 이력·저장된 AI 요약 상태를 합쳐 상세 화면의 응답을 만든다. */
@Service
public class ListingDetailService {
  private final ListingRepository listings;
  private final ProductService products;
  private final ListingPublicationService publications;
  private final ListingSummaryStore summaries;

  public ListingDetailService(
      ListingRepository listings,
      ProductService products,
      ListingPublicationService publications,
      ListingSummaryStore summaries) {
    this.listings = listings;
    this.products = products;
    this.publications = publications;
    this.summaries = summaries;
  }

  @Transactional
  public ListingDetailResponse get(long listingId, long userId, int page, int size) {
    var listing = readable(listingId);
    var publication = publications.get(listing.getProductId(), listing.getSellerId(), page, size);
    if (publication.history().status() != ListingHistoryReader.State.READY)
      throw new CommonException(ErrorCode.LISTING_HISTORY_UNAVAILABLE);
    return new ListingDetailResponse(
        listing.getId(),
        listing.getContentVersion(),
        listing.getStatus(),
        listing.getPublishedAt(),
        new ListingPreviewResponse.Content(
            listing.getTitle(),
            listing.getDescription(),
            listing.getPrice(),
            listing.getConditionGrade()),
        listing.getImageIds().stream()
            .map(
                id ->
                    new ListingPreviewResponse.Image(
                        id, "/api/v1/listings/" + listingId + "/images/" + id))
            .toList(),
        publication.product(),
        publication.history(),
        listing.getSellerId() == userId,
        summaries.get(listingId, userId));
  }

  @Transactional
  public ListingDraftService.DraftImage image(long listingId, long imageId) throws IOException {
    var listing = readable(listingId);
    if (!listing.getImageIds().contains(imageId))
      throw new CommonException(ErrorCode.RESOURCE_NOT_FOUND);
    var image = products.image(listing.getSellerId(), listing.getProductId(), imageId);
    return new ListingDraftService.DraftImage(image.resource(), image.contentType());
  }

  // 게시자가 현재 소유자인지, 글이 판매 중·예약 중인지 확인해 종료된 글의 옛 URL도 차단한다.
  private Listing readable(long listingId) {
    var access =
        listings
            .findAccessById(listingId)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    products.lockOwnedForListing(access.getSellerId(), access.getProductId());
    var listing =
        listings
            .findById(listingId)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!listing.getStatus().isPubliclyReadable())
      throw new CommonException(ErrorCode.RESOURCE_NOT_FOUND);
    return listing;
  }
}

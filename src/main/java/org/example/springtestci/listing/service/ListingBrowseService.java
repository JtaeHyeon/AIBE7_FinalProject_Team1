package org.example.springtestci.listing.service;

import java.io.IOException;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.dto.ListingPageResponse;
import org.example.springtestci.listing.dto.ListingSearchRequest;
import org.example.springtestci.listing.entity.ListingStatus;
import org.example.springtestci.listing.repository.ListingQueryRepository;
import org.example.springtestci.listing.repository.ListingRepository;
import org.example.springtestci.product.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListingBrowseService {
  private final ListingQueryRepository query;
  private final ListingRepository listings;
  private final ProductService products;

  public ListingBrowseService(
      ListingQueryRepository query, ListingRepository listings, ProductService products) {
    this.query = query;
    this.listings = listings;
    this.products = products;
  }

  @Transactional(readOnly = true)
  public ListingPageResponse list(ListingSearchRequest request) {
    return ListingPageResponse.from(query.findActive(request));
  }

  /** 목록의 대표 사진만 공개한다. 예약·종료·소유권 변경 후에는 기존 URL도 다시 차단한다. */
  @Transactional
  public ListingDraftService.DraftImage thumbnail(long listingId) throws IOException {
    var access =
        listings
            .findAccessById(listingId)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    products.lockOwnedForListing(access.getSellerId(), access.getProductId());
    var listing =
        listings
            .findById(listingId)
            .filter(value -> value.getStatus() == ListingStatus.ACTIVE)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    long imageId =
        query
            .findPrimaryImageId(listingId, listing.getProductId())
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    var image = products.image(listing.getSellerId(), listing.getProductId(), imageId);
    return new ListingDraftService.DraftImage(image.resource(), image.contentType());
  }
}

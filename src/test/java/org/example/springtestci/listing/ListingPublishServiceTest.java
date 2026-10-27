package org.example.springtestci.listing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.dto.ListingDraftResponse;
import org.example.springtestci.listing.dto.PublishListingRequest;
import org.example.springtestci.listing.repository.ListingAuditRepository;
import org.example.springtestci.listing.repository.ListingRepository;
import org.example.springtestci.listing.service.ListingDraftService;
import org.example.springtestci.listing.service.ListingHistoryPublisher;
import org.example.springtestci.listing.service.ListingPreviewService;
import org.example.springtestci.listing.service.ListingPublishConstraints;
import org.example.springtestci.listing.service.ListingPublishService;
import org.example.springtestci.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ListingPublishServiceTest {
  @Test
  void missingIntegrationAdaptersNeverAssumeThatPublishingIsSafe() {
    missingAdapter(false);
  }

  @Test
  void aConstraintReaderAloneCannotReplaceTheHistoryPublicationAdapter() {
    missingAdapter(true);
  }

  @SuppressWarnings("unchecked")
  private void missingAdapter(boolean hasConstraints) {
    var products = mock(ProductService.class);
    var drafts = mock(ListingDraftService.class);
    var previews = mock(ListingPreviewService.class);
    var listings = mock(ListingRepository.class);
    var audit = mock(ListingAuditRepository.class);
    ObjectProvider<ListingPublishConstraints> constraints = mock(ObjectProvider.class);
    ObjectProvider<ListingHistoryPublisher> publishers = mock(ObjectProvider.class);
    if (hasConstraints)
      when(constraints.getIfAvailable())
          .thenReturn(productId -> new ListingPublishConstraints.Snapshot(true, false, false));
    when(drafts.get(101, 1))
        .thenReturn(
            new ListingDraftResponse(
                10, 101, "제목", "설명", BigDecimal.ONE, 4, List.of(301L), 0, Instant.now()));
    var service =
        new ListingPublishService(
            products,
            drafts,
            previews,
            listings,
            audit,
            constraints,
            publishers,
            mock(org.springframework.context.ApplicationEventPublisher.class));
    var error =
        assertThrows(
            CommonException.class,
            () -> service.publish(101, 1, new PublishListingRequest(10L, 0L)));
    assertEquals(ErrorCode.LISTING_PUBLISH_UNAVAILABLE, error.errorCode());
    verify(products).lockOwnedForListing(1, 101);
    verify(listings, never()).saveAndFlush(any());
    verifyNoInteractions(previews, audit);
  }
}

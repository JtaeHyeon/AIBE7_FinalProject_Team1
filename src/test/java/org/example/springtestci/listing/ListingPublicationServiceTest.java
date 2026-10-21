package org.example.springtestci.listing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.service.ListingHistoryReader;
import org.example.springtestci.listing.service.ListingPublicationService;
import org.example.springtestci.product.dto.ProductResponse;
import org.example.springtestci.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ListingPublicationServiceTest {
  @Test
  void aMissingHistoryModuleReportsUnavailableInsteadOfClaimingNoHistory() {
    ProductService products = mock(ProductService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ListingHistoryReader> readers = mock(ObjectProvider.class);
    when(products.getForListing(1, 101))
        .thenReturn(
            new ProductResponse(
                101, "개인 별칭", "Apple", "MacBook", null, null, null, 1, null, "UNKNOWN", null,
                List.of(), null));
    var response = new ListingPublicationService(products, readers).get(101, 1, 0, 20);
    assertEquals(ListingHistoryReader.State.UNAVAILABLE, response.history().status());
    assertEquals("MacBook", response.product().modelName());
    assertTrue(response.history().items().isEmpty());
    verify(products).lockOwnedForListing(1, 101);
  }

  @Test
  void deniedOwnershipDoesNotReadProductOrHistoryData() {
    ProductService products = mock(ProductService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ListingHistoryReader> readers = mock(ObjectProvider.class);
    doThrow(new CommonException(ErrorCode.RESOURCE_NOT_FOUND))
        .when(products)
        .lockOwnedForListing(2, 101);
    assertThrows(
        CommonException.class,
        () -> new ListingPublicationService(products, readers).get(101, 2, 0, 20));
    verifyNoInteractions(readers);
    verify(products, never()).getForListing(anyLong(), anyLong());
  }
}

package org.example.springtestci.ai.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import java.util.List;
import org.example.springtestci.listing.event.ListingContentChanged;
import org.example.springtestci.product.event.ProductFactsChanged;
import org.junit.jupiter.api.Test;

class ListingSummaryRefreshListenerTest {
  @Test
  void refreshFailureDoesNotTurnCommittedEditIntoAnError() {
    var store = mock(ListingSummaryStore.class);
    doThrow(new IllegalStateException("fixture")).when(store).refresh(11);
    assertDoesNotThrow(
        () ->
            new ListingSummaryRefreshListener(store).listingChanged(new ListingContentChanged(11)));
    verify(store).refresh(11);
  }

  @Test
  void oneUnavailableListingDoesNotPreventOtherRefreshRequests() {
    var store = mock(ListingSummaryStore.class);
    when(store.publicListingsForProduct(101)).thenReturn(List.of(11L, 12L));
    doThrow(new IllegalStateException("fixture")).when(store).refresh(11);
    assertDoesNotThrow(
        () ->
            new ListingSummaryRefreshListener(store).productChanged(new ProductFactsChanged(101)));
    verify(store).refresh(12);
  }

  @Test
  void lookupFailureDoesNotTurnCommittedProductChangeIntoAnError() {
    var store = mock(ListingSummaryStore.class);
    when(store.publicListingsForProduct(101)).thenThrow(new IllegalStateException("fixture"));
    assertDoesNotThrow(
        () ->
            new ListingSummaryRefreshListener(store).productChanged(new ProductFactsChanged(101)));
    verify(store, never()).refresh(anyLong());
  }
}

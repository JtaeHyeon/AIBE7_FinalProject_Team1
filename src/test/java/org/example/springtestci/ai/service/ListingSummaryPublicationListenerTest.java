package org.example.springtestci.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.example.springtestci.listing.event.ListingPublished;
import org.junit.jupiter.api.Test;

class ListingSummaryPublicationListenerTest {
  @Test
  void queueFailureDoesNotTurnCommittedPublishIntoAnError() {
    var store = mock(ListingSummaryStore.class);
    when(store.enqueue(7, null)).thenThrow(new IllegalStateException("fixture failure"));
    assertDoesNotThrow(
        () -> new ListingSummaryPublicationListener(store).published(new ListingPublished(7)));
    verify(store).enqueue(7, null);
  }
}

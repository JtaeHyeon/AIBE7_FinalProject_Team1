package org.example.springtestci.product.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import java.time.LocalDate;
import org.example.springtestci.listing.dto.ListingSearchRequest;
import org.junit.jupiter.api.Test;

class ProductCategoryTest {

  private static CreateProductRequest request(String category) {
    return new CreateProductRequest(
        "제목", "삼성", "모델", LocalDate.now(), null, null, "SN-1234", null, category);
  }

  @Test
  void missingCategoryDefaultsToLaptop() {
    assertEquals("LAPTOP", request(null).category());
    assertEquals("LAPTOP", request(" ").category());
    assertEquals("KITCHEN", request("KITCHEN").category());
  }

  @Test
  void unknownCategoryIsRejectedByValidation() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      assertTrue(validator.validate(request("KITCHEN")).isEmpty());
      assertFalse(validator.validate(request("DRONE")).isEmpty());
    }
  }

  @Test
  void listingSearchTrimsBlankCategory() {
    var search =
        new ListingSearchRequest(
            null, null, null, " ", " ", null, null, null, null, null, null, null, null, null, null);
    assertEquals(null, search.category());
    assertEquals(null, search.keyword());
  }
}

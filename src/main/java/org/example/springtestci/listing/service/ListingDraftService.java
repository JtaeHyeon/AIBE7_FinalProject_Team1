package org.example.springtestci.listing.service;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.dto.DraftProductResponse;
import org.example.springtestci.listing.dto.ListingDraftResponse;
import org.example.springtestci.listing.dto.SaveListingDraftRequest;
import org.example.springtestci.listing.entity.ListingDraft;
import org.example.springtestci.listing.repository.ListingDraftRepository;
import org.example.springtestci.product.service.ProductService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ListingDraftService {

  private final ListingDraftRepository drafts;
  private final ProductService products;

  public ListingDraftService(ListingDraftRepository drafts, ProductService products) {
    this.drafts = drafts;
    this.products = products;
  }

  public List<DraftProductResponse> products(long userId) {
    return products.listMine(userId).stream().map(DraftProductResponse::from).toList();
  }

  public DraftProductResponse composition(long productId, long userId) {
    return DraftProductResponse.from(products.getForListing(userId, productId));
  }

  @Transactional
  public SavedDraft save(long productId, long userId, SaveListingDraftRequest request) {
    products.lockOwnedForListing(userId, productId);
    List<Long> imageIds = request.imageIds();
    if (new HashSet<>(imageIds).size() != imageIds.size())
      throw new CommonException(ErrorCode.INVALID_INPUT);
    var allowed = new HashSet<Long>();
    products
        .getForListing(userId, productId)
        .images()
        .forEach(image -> allowed.add(image.imageId()));
    if (!allowed.containsAll(imageIds)) throw new CommonException(ErrorCode.INVALID_INPUT);

    ListingDraft draft = drafts.findByProductId(productId).orElse(null);
    boolean created = draft == null;
    if (created) {
      if (request.version() != null) throw new CommonException(ErrorCode.DRAFT_CONFLICT);
      draft = new ListingDraft(productId, userId);
    } else {
      if (draft.getAuthorId() != userId) throw new CommonException(ErrorCode.DRAFT_CONFLICT);
      if (request.version() == null || !request.version().equals(draft.getVersion()))
        throw new CommonException(ErrorCode.DRAFT_CONFLICT);
    }
    draft.replaceContent(request);
    drafts.saveAndFlush(draft);
    return new SavedDraft(ListingDraftResponse.from(draft), created);
  }

  public DraftImage image(long productId, long imageId, long userId) throws IOException {
    var image = products.image(userId, productId, imageId);
    return new DraftImage(image.resource(), image.contentType());
  }

  public record SavedDraft(ListingDraftResponse draft, boolean created) {}

  public record DraftImage(Resource resource, String contentType) {}
}

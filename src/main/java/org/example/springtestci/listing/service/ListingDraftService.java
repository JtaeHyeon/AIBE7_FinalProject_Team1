package org.example.springtestci.listing.service;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.common.storage.FileStorage;
import org.example.springtestci.listing.dto.DraftProductResponse;
import org.example.springtestci.listing.dto.ListingDraftResponse;
import org.example.springtestci.listing.dto.SaveListingDraftRequest;
import org.example.springtestci.listing.entity.ListingDraft;
import org.example.springtestci.listing.repository.DraftProductRepository;
import org.example.springtestci.listing.repository.ListingDraftRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ListingDraftService {

  private final ListingDraftRepository drafts;
  private final DraftProductRepository products;
  private final ObjectProvider<FileStorage> storage;

  public ListingDraftService(
      ListingDraftRepository drafts,
      DraftProductRepository products,
      ObjectProvider<FileStorage> storage) {
    this.drafts = drafts;
    this.products = products;
    this.storage = storage;
  }

  public List<DraftProductResponse> products(long userId) {
    return products.findOwnedProducts(userId);
  }

  public DraftProductResponse composition(long productId, long userId) {
    return products.composition(productId, userId);
  }

  @Transactional
  public SavedDraft save(long productId, long userId, SaveListingDraftRequest request) {
    products.requireOwnership(productId, userId, true);
    List<Long> imageIds = request.imageIds();
    if (new HashSet<>(imageIds).size() != imageIds.size())
      throw new CommonException(ErrorCode.INVALID_INPUT);
    Set<Long> allowed = new HashSet<>();
    products.images(productId, true).forEach(image -> allowed.add(image.imageId()));
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
    products.requireOwnership(productId, userId, false);
    DraftProductRepository.ProductImage image =
        products.images(productId, false).stream()
            .filter(item -> item.imageId() == imageId)
            .findFirst()
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    FileStorage files = storage.getIfAvailable();
    if (files == null) throw new CommonException(ErrorCode.DRAFT_PRODUCT_UNAVAILABLE);
    Resource resource = files.load(image.storageKey());
    if (!resource.exists() || !resource.isReadable())
      throw new CommonException(ErrorCode.RESOURCE_NOT_FOUND);
    return new DraftImage(resource, image.contentType());
  }

  public record SavedDraft(ListingDraftResponse draft, boolean created) {}

  public record DraftImage(Resource resource, String contentType) {}
}

package org.example.springtestci.listing.service;

import java.io.IOException;
import java.util.HashSet;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.dto.ListingDraftResponse;
import org.example.springtestci.listing.dto.ListingPreviewResponse;
import org.example.springtestci.product.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListingPreviewService {
  private final ListingDraftService drafts;
  private final ListingPublicationService publications;
  private final ProductService products;

  public ListingPreviewService(
      ListingDraftService drafts, ListingPublicationService publications, ProductService products) {
    this.drafts = drafts;
    this.publications = publications;
    this.products = products;
  }

  /** 저장 직후의 버전을 고정해, 다른 탭의 수정·삭제나 소유권 변경이 섞인 화면을 방지한다. */
  @Transactional
  public ListingPreviewResponse get(
      long productId, long draftId, long version, long userId, int page, int size) {
    var draft = currentDraft(productId, draftId, version, userId);
    var publication = publications.get(productId, userId, page, size);
    var available = new HashSet<Long>();
    products
        .getForListing(userId, productId)
        .images()
        .forEach(image -> available.add(image.imageId()));
    if (!available.containsAll(draft.selectedImageIds())) {
      throw new CommonException(ErrorCode.DRAFT_CONFLICT);
    }
    var images =
        draft.selectedImageIds().stream()
            .map(
                imageId ->
                    new ListingPreviewResponse.Image(
                        imageId,
                        "/api/v1/products/"
                            + productId
                            + "/listing-draft/preview/images/"
                            + imageId
                            + "?draftId="
                            + draftId
                            + "&version="
                            + version))
            .toList();
    return new ListingPreviewResponse(
        draft.draftId(),
        draft.version(),
        draft.updatedAt(),
        new ListingPreviewResponse.Content(
            draft.title(), draft.description(), draft.price(), draft.conditionGrade()),
        images,
        publication.product(),
        publication.history());
  }

  /** 사진 URL로 직접 접근해도 작성자·현재 소유자·저장 버전과 사진 선택을 다시 확인한다. */
  @Transactional
  public ListingDraftService.DraftImage image(
      long productId, long draftId, long version, long imageId, long userId) throws IOException {
    var draft = currentDraft(productId, draftId, version, userId);
    if (!draft.selectedImageIds().contains(imageId)) {
      throw new CommonException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    return drafts.image(productId, imageId, userId);
  }

  private ListingDraftResponse currentDraft(
      long productId, long draftId, long version, long userId) {
    // get은 현재 소유권과 원래 작성자를 확인하고 호출 트랜잭션 안에서 제품 행을 잠근다.
    var draft = drafts.get(productId, userId);
    if (draft.draftId() != draftId || draft.version() != version) {
      throw new CommonException(ErrorCode.DRAFT_CONFLICT);
    }
    return draft;
  }
}

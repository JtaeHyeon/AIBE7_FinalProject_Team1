package org.example.springtestci.listing.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.listing.dto.ListingDraftResponse;
import org.example.springtestci.listing.dto.UpdateListingRequest;

/** 게시된 판매글의 판매 조건·상태·사진 선택을 저장한다. 원본 제품과는 productId로 연결된다. */
@Entity
@Table(name = "listings")
public class Listing {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private long productId;

  @Column(nullable = false, updatable = false)
  private long sellerId;

  @Column(nullable = false, length = 60)
  private String title;

  @Column(nullable = false, precision = 12, scale = 0)
  private BigDecimal price;

  @Column(nullable = false)
  private Integer conditionGrade;

  @Column(nullable = false, length = 2000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ListingStatus status;

  @Column(nullable = false)
  private long contentVersion;

  @Column(nullable = false, updatable = false)
  private Instant publishedAt;

  @Column(nullable = false, updatable = false)
  private long sourceDraftId;

  @Column(nullable = false, updatable = false)
  private long sourceDraftVersion;

  // 열린 판매글은 1, 종료된 글은 null로 두어 DB 고유 제약으로 제품당 중복 판매를 막는다.
  private Short activeSlot;

  @ElementCollection
  @CollectionTable(name = "listing_images", joinColumns = @JoinColumn(name = "listing_id"))
  @OrderBy("sortOrder ASC")
  private List<ListingImage> images = new ArrayList<>();

  protected Listing() {}

  /** 초안의 내용을 복사해 판매글로 확정한다. 게시 후에는 초안과 별도로 관리된다. */
  public Listing(ListingDraftResponse draft, long sellerId) {
    productId = draft.productId();
    this.sellerId = sellerId;
    title = draft.title();
    price = draft.price();
    conditionGrade = draft.conditionGrade();
    description = draft.description();
    status = ListingStatus.ACTIVE;
    activeSlot = 1;
    publishedAt = Instant.now();
    sourceDraftId = draft.draftId();
    sourceDraftVersion = draft.version();
    for (int i = 0; i < draft.selectedImageIds().size(); i++)
      images.add(new ListingImage(draft.selectedImageIds().get(i), i));
  }

  public Long getId() {
    return id;
  }

  public long getProductId() {
    return productId;
  }

  public long getSellerId() {
    return sellerId;
  }

  public String getTitle() {
    return title;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public Integer getConditionGrade() {
    return conditionGrade;
  }

  public String getDescription() {
    return description;
  }

  public ListingStatus getStatus() {
    return status;
  }

  public long getContentVersion() {
    return contentVersion;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public long getSourceDraftVersion() {
    return sourceDraftVersion;
  }

  public List<Long> getImageIds() {
    return images.stream().map(ListingImage::getProductImageId).toList();
  }

  /** 제품·게시자·게시 시각은 유지하고 판매 조건 버전만 증가시킨다. 호출자는 행 잠금과 버전을 검사한다. */
  public void revise(UpdateListingRequest request) {
    if (status != ListingStatus.ACTIVE) throw new CommonException(ErrorCode.LISTING_STATE_CONFLICT);
    title = request.title();
    price = request.price();
    conditionGrade = request.conditionGrade();
    description = request.description();
    images.clear();
    for (int i = 0; i < request.selectedImageIds().size(); i++)
      images.add(new ListingImage(request.selectedImageIds().get(i), i));
    contentVersion++;
  }

  /** 거래 기록과 사진 선택을 보존하고 열린 판매글의 고유 슬롯만 해제한다. */
  public void cancel() {
    if (!status.isPubliclyReadable()) throw new CommonException(ErrorCode.LISTING_STATE_CONFLICT);
    status = ListingStatus.CANCELLED;
    activeSlot = null;
  }
}

package org.example.springtestci.listing.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.example.springtestci.common.audit.BaseTimeEntity;
import org.example.springtestci.listing.dto.SaveListingDraftRequest;

/** 아직 게시하지 않은 판매 조건과 사진 선택을 저장한다. 제품 원본 정보와 분리된 비공개 초안이다. */
@Entity
@Table(name = "listing_drafts", uniqueConstraints = @UniqueConstraint(columnNames = "product_id"))
public class ListingDraft extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "product_id", nullable = false, updatable = false)
  private long productId;

  @Column(name = "author_id", nullable = false, updatable = false)
  private long authorId;

  @Column(length = 60)
  private String title;

  @Column(length = 2000)
  private String description;

  @Column(precision = 12, scale = 0)
  private BigDecimal price;

  @Column(name = "condition_grade")
  private Integer conditionGrade;

  @Column(nullable = false, length = 20, updatable = false)
  private String visibility = "PRIVATE";

  // JPA가 저장할 때 증가시킨다. 화면에서 보낸 버전과 비교해 오래된 내용의 덮어쓰기를 막는다.
  @Version private Long version;

  @ElementCollection
  @CollectionTable(name = "draft_images", joinColumns = @JoinColumn(name = "draft_id"))
  @OrderBy("sortOrder ASC")
  private List<DraftImage> selectedImages = new ArrayList<>();

  protected ListingDraft() {}

  public ListingDraft(long productId, long authorId) {
    this.productId = productId;
    this.authorId = authorId;
  }

  /** 폼 내용과 사진 순서를 통째로 교체한다. 첫 번째 사진이 대표 사진이 된다. */
  public void replaceContent(SaveListingDraftRequest request) {
    title = nullableText(request.title());
    description = nullableText(request.description());
    price = request.price();
    conditionGrade = request.conditionGrade();
    selectedImages.clear();
    List<Long> imageIds = request.imageIds();
    for (int index = 0; index < imageIds.size(); index++) {
      selectedImages.add(new DraftImage(imageIds.get(index), index));
    }
  }

  private static String nullableText(String text) {
    return text == null || text.isBlank() ? null : text.strip();
  }

  public Long getId() {
    return id;
  }

  public long getProductId() {
    return productId;
  }

  public long getAuthorId() {
    return authorId;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public Integer getConditionGrade() {
    return conditionGrade;
  }

  public Long getVersion() {
    return version;
  }

  public List<Long> getSelectedImageIds() {
    return selectedImages.stream().map(DraftImage::getProductImageId).toList();
  }
}

package org.example.springtestci.listing.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.example.springtestci.common.audit.BaseTimeEntity;
import org.example.springtestci.listing.dto.SaveListingDraftRequest;

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

  @Version private Long version;

  @ElementCollection
  @CollectionTable(name = "listing_draft_images", joinColumns = @JoinColumn(name = "draft_id"))
  @OrderColumn(name = "sort_order")
  @Column(name = "image_id", nullable = false)
  private List<Long> selectedImageIds = new ArrayList<>();

  protected ListingDraft() {}

  public ListingDraft(long productId, long authorId) {
    this.productId = productId;
    this.authorId = authorId;
  }

  public void replaceContent(SaveListingDraftRequest request) {
    title = nullableText(request.title());
    description = nullableText(request.description());
    price = request.price();
    conditionGrade = request.conditionGrade();
    selectedImageIds.clear();
    selectedImageIds.addAll(request.imageIds());
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
    return List.copyOf(selectedImageIds);
  }
}

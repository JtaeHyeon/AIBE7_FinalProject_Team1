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
import org.example.springtestci.listing.dto.ListingDraftResponse;

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

  private Short activeSlot;

  @ElementCollection
  @CollectionTable(name = "listing_images", joinColumns = @JoinColumn(name = "listing_id"))
  @OrderBy("sortOrder ASC")
  private List<ListingImage> images = new ArrayList<>();

  protected Listing() {}

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
}

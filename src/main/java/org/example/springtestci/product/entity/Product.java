package org.example.springtestci.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.example.springtestci.common.audit.BaseTimeEntity;

@Entity
@Table(name = "products")
public class Product extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long ownerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ProductCategory category;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 100)
  private String manufacturer;

  @Column(nullable = false, length = 100)
  private String modelName;

  private LocalDate purchaseDate;

  private LocalDate warrantyExpiresAt;

  @Column(nullable = false, length = 64, unique = true, updatable = false)
  private String serialNumberFingerprint;

  @Column(nullable = false, length = 4, updatable = false)
  private String serialLast4;

  private Instant archivedAt;

  protected Product() {}

  private Product(
      Long ownerId,
      ProductCategory category,
      String title,
      String manufacturer,
      String modelName,
      LocalDate purchaseDate,
      LocalDate warrantyExpiresAt,
      String serialNumberFingerprint,
      String serialLast4) {
    this.ownerId = requirePositive(ownerId, "ownerId");
    this.category = Objects.requireNonNull(category, "category는 필수입니다.");
    this.title = requireText(title, "title");
    this.manufacturer = requireText(manufacturer, "manufacturer");
    this.modelName = requireText(modelName, "modelName");
    this.purchaseDate = purchaseDate;
    this.warrantyExpiresAt = warrantyExpiresAt;
    this.serialNumberFingerprint = requireText(serialNumberFingerprint, "serialNumberFingerprint");
    this.serialLast4 = requireLast4(serialLast4);
  }

  public static Product laptop(
      Long ownerId,
      String title,
      String manufacturer,
      String modelName,
      LocalDate purchaseDate,
      LocalDate warrantyExpiresAt,
      String serialNumberFingerprint,
      String serialLast4) {
    return new Product(
        ownerId,
        ProductCategory.LAPTOP,
        title,
        manufacturer,
        modelName,
        purchaseDate,
        warrantyExpiresAt,
        serialNumberFingerprint,
        serialLast4);
  }

  public void transferOwnershipTo(Long newOwnerId) {
    this.ownerId = requirePositive(newOwnerId, "newOwnerId");
  }

  public void archive(Instant archivedAt) {
    if (archivedAt == null) {
      throw new IllegalArgumentException("archivedAt은 필수입니다.");
    }
    this.archivedAt = archivedAt;
  }

  public void updateEditableInformation(String modelName, LocalDate purchaseDate) {
    this.modelName = requireText(modelName, "modelName");
    this.purchaseDate = purchaseDate;
  }

  public Long getId() {
    return id;
  }

  public Long getOwnerId() {
    return ownerId;
  }

  public ProductCategory getCategory() {
    return category;
  }

  public String getTitle() {
    return title;
  }

  public String getManufacturer() {
    return manufacturer;
  }

  public String getModelName() {
    return modelName;
  }

  public LocalDate getPurchaseDate() {
    return purchaseDate;
  }

  public LocalDate getWarrantyExpiresAt() {
    return warrantyExpiresAt;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public String getSerialLast4() {
    return serialLast4;
  }

  public boolean isArchived() {
    return archivedAt != null;
  }

  private static Long requirePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException(fieldName + "은 1 이상이어야 합니다.");
    }
    return value;
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + "은 필수입니다.");
    }
    return value.trim();
  }

  private static String requireLast4(String value) {
    String last4 = requireText(value, "serialLast4");
    if (last4.length() > 4) {
      throw new IllegalArgumentException("serialLast4는 최대 4자여야 합니다.");
    }
    return last4;
  }
}

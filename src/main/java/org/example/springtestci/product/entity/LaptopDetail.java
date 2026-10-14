package org.example.springtestci.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "laptop_details")
@EntityListeners(AuditingEntityListener.class)
public class LaptopDetail {

  @Id private Long productId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id")
  private Product product;

  @Column(length = 100)
  private String cpuSpec;

  private Integer ramGb;

  private Integer storageGb;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private StorageType storageType;

  @Column(length = 100)
  private String gpuSpec;

  @Column(length = 100)
  private String displaySpec;

  @Column(length = 50)
  private String osName;

  private Short batteryHealthPercent;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  protected LaptopDetail() {}

  public LaptopDetail(
      Product product,
      String cpuSpec,
      Integer ramGb,
      Integer storageGb,
      StorageType storageType,
      String gpuSpec,
      String displaySpec,
      Short batteryHealthPercent,
      String osName) {
    this.product = product;
    this.cpuSpec = trimToNull(cpuSpec);
    this.ramGb = requirePositiveOrNull(ramGb, "ramGb");
    this.storageGb = requirePositiveOrNull(storageGb, "storageGb");
    this.storageType = storageType;
    this.gpuSpec = trimToNull(gpuSpec);
    this.displaySpec = trimToNull(displaySpec);
    this.batteryHealthPercent = requirePercentageOrNull(batteryHealthPercent);
    this.osName = trimToNull(osName);
  }

  public void updateSpecification(
      String cpuSpec,
      Integer ramGb,
      Integer storageGb,
      StorageType storageType,
      String gpuSpec,
      String displaySpec,
      Short batteryHealthPercent,
      String osName) {
    this.cpuSpec = trimToNull(cpuSpec);
    this.ramGb = requirePositiveOrNull(ramGb, "ramGb");
    this.storageGb = requirePositiveOrNull(storageGb, "storageGb");
    this.storageType = storageType;
    this.gpuSpec = trimToNull(gpuSpec);
    this.displaySpec = trimToNull(displaySpec);
    this.batteryHealthPercent = requirePercentageOrNull(batteryHealthPercent);
    this.osName = trimToNull(osName);
  }

  public Long getProductId() {
    return productId;
  }

  public Product getProduct() {
    return product;
  }

  public String getCpuSpec() {
    return cpuSpec;
  }

  public Integer getRamGb() {
    return ramGb;
  }

  public Integer getStorageGb() {
    return storageGb;
  }

  public StorageType getStorageType() {
    return storageType;
  }

  public String getGpuSpec() {
    return gpuSpec;
  }

  public String getDisplaySpec() {
    return displaySpec;
  }

  public String getOsName() {
    return osName;
  }

  public Short getBatteryHealthPercent() {
    return batteryHealthPercent;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  private static Integer requirePositiveOrNull(Integer value, String fieldName) {
    if (value != null && value <= 0) {
      throw new IllegalArgumentException(fieldName + "은 1 이상이어야 합니다.");
    }
    return value;
  }

  private static Short requirePercentageOrNull(Short value) {
    if (value != null && (value < 0 || value > 100)) {
      throw new IllegalArgumentException("batteryHealthPercent는 0 이상 100 이하여야 합니다.");
    }
    return value;
  }

  private static String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}

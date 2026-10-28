package org.example.springtestci.history.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_event_corrections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductEventCorrection {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "event_id", nullable = false)
  private ProductEvent event;

  @Column(name = "actor_id", nullable = false)
  private Long actorId;

  @Column(name = "reason", nullable = false, length = 500)
  private String reason;

  @Column(name = "before_content", columnDefinition = "TEXT")
  private String beforeContent;

  @Column(name = "after_content", columnDefinition = "TEXT")
  private String afterContent;

  @Column(name = "reviewed", nullable = false)
  private boolean reviewed;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public static ProductEventCorrection create(
      ProductEvent event, Long actorId, String reason, String beforeContent, String afterContent) {
    ProductEventCorrection correction = new ProductEventCorrection();
    correction.event = event;
    correction.actorId = actorId;
    correction.reason = reason;
    correction.beforeContent = beforeContent;
    correction.afterContent = afterContent;
    correction.reviewed = true;
    correction.createdAt = Instant.now();
    return correction;
  }
}

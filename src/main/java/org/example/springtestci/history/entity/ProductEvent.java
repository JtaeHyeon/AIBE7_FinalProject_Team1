package org.example.springtestci.history.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.springtestci.listing.service.ListingHistoryReader.EventStatus;
import org.example.springtestci.listing.service.ListingHistoryReader.EventType;

@Entity
@Table(name = "product_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "author_id", nullable = false)
  private Long authorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 30)
  private EventType eventType;

  @Column(name = "occurred_on", nullable = false)
  private LocalDate occurredOn;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "content", columnDefinition = "TEXT")
  private String content;

  @Column(name = "service_provider", length = 100)
  private String serviceProvider;

  @Column(name = "replaced_part", length = 100)
  private String replacedPart;

  @Column(name = "reviewed", nullable = false)
  private boolean reviewed;

  @Enumerated(EnumType.STRING)
  @Column(name = "visibility", nullable = false, length = 20)
  private EventVisibility visibility;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private EventStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("createdAt ASC")
  private List<ProductEventCorrection> corrections = new ArrayList<>();

  public static ProductEvent create(
      Long productId,
      Long authorId,
      EventType eventType,
      LocalDate occurredOn,
      String title,
      String content,
      String serviceProvider,
      String replacedPart,
      EventVisibility visibility) {
    ProductEvent event = new ProductEvent();
    event.productId = productId;
    event.authorId = authorId;
    event.eventType = eventType;
    event.occurredOn = occurredOn;
    event.title = title;
    event.content = content;
    event.serviceProvider = serviceProvider;
    event.replacedPart = replacedPart;
    event.reviewed = true;
    event.visibility = visibility != null ? visibility : EventVisibility.PRIVATE;
    event.status = EventStatus.VALID;
    event.createdAt = Instant.now();
    event.updatedAt = Instant.now();
    return event;
  }

  public void correct(
      Long actorId,
      String reason,
      String newTitle,
      String newContent,
      String newServiceProvider,
      String newReplacedPart) {
    String beforeContent = "title=" + this.title + ", content=" + this.content;
    String afterContent = "title=" + newTitle + ", content=" + newContent;

    ProductEventCorrection correction =
        ProductEventCorrection.create(this, actorId, reason, beforeContent, afterContent);
    this.corrections.add(correction);

    this.title = newTitle;
    this.content = newContent;
    this.serviceProvider = newServiceProvider;
    this.replacedPart = newReplacedPart;
    this.updatedAt = Instant.now();
  }

  public void markVoid(Long actorId, String reason) {
    String beforeContent = "status=" + this.status;
    String afterContent = "status=VOID";

    ProductEventCorrection correction =
        ProductEventCorrection.create(this, actorId, reason, beforeContent, afterContent);
    this.corrections.add(correction);

    this.status = EventStatus.VOID;
    this.updatedAt = Instant.now();
  }

  public void setVisibility(EventVisibility visibility) {
    this.visibility = visibility;
    this.updatedAt = Instant.now();
  }
}

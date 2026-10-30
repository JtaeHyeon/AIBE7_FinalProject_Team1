package org.example.springtestci.history.service;

import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.history.dto.CorrectHistoryEventRequest;
import org.example.springtestci.history.dto.CreateHistoryEventRequest;
import org.example.springtestci.history.dto.HistoryEventResponse;
import org.example.springtestci.history.dto.HistoryEventSource;
import org.example.springtestci.history.dto.VoidHistoryEventRequest;
import org.example.springtestci.history.entity.HistoryEventStatus;
import org.example.springtestci.history.entity.HistoryEventType;
import org.example.springtestci.history.repository.HistoryRepository;
import org.example.springtestci.history.repository.HistoryRepository.HistoryChangeRecord;
import org.example.springtestci.history.repository.HistoryRepository.HistoryEventRecord;
import org.example.springtestci.history.repository.HistoryRepository.ProductSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class HistoryService {

  private static final Set<String> SPEC_KEYS =
      Set.of(
          "cpu",
          "gpu",
          "ramGb",
          "storageGb",
          "storageType",
          "display",
          "batteryHealthPercent",
          "os");

  private final HistoryRepository historyRepository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public HistoryService(
      HistoryRepository historyRepository, ObjectMapper objectMapper, Clock clock) {
    this.historyRepository = historyRepository;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional
  public HistoryEventResponse create(
      long ownerId, long productId, CreateHistoryEventRequest request) {
    requireOwnedLock(productId, ownerId);
    validateCommon(request.eventType(), request.occurredOn(), request.specsAfter());
    if (request.eventType() == HistoryEventType.TRANSFER) {
      throw new CommonException(ErrorCode.INVALID_HISTORY_EVENT);
    }

    List<Long> documentIds = normalizedDocumentIds(request.documentIds());
    if (!historyRepository.documentsCanBeLinked(productId, documentIds)) {
      throw new CommonException(ErrorCode.INVALID_HISTORY_DOCUMENT);
    }

    String specsBeforeJson = null;
    String specsAfterJson = null;
    ProductSpecifications after = null;
    if (hasSpecificationChanges(request.specsAfter())) {
      ProductSpecifications before = historyRepository.findSpecifications(productId);
      after = mergeSpecifications(before, request.specsAfter());
      specsBeforeJson = json(toMap(before));
      specsAfterJson = json(toMap(after));
    }

    long eventId =
        historyRepository.insertEvent(productId, ownerId, request, specsBeforeJson, specsAfterJson);
    historyRepository.linkDocuments(eventId, documentIds);
    if (after != null) historyRepository.replaceSpecifications(productId, after);
    return getOwnedEvent(ownerId, productId, eventId, documentIds);
  }

  @Transactional(readOnly = true)
  public List<HistoryEventResponse> timeline(long ownerId, long productId) {
    if (!historyRepository.existsOwnedProduct(productId, ownerId)) {
      throw new CommonException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    Map<Long, List<Long>> documents = historyRepository.findDocumentIds(productId);
    Map<Long, HistoryChangeRecord> changes = historyRepository.findLatestChanges(productId);
    return historyRepository.findTimeline(productId).stream()
        .map(
            event ->
                toResponse(
                    ownerId,
                    event,
                    documents.getOrDefault(event.eventId(), List.of()),
                    changes.get(event.eventId())))
        .toList();
  }

  @Transactional
  public HistoryEventResponse correct(
      long ownerId, long productId, long eventId, CorrectHistoryEventRequest request) {
    requireOwnedLock(productId, ownerId);
    HistoryEventRecord before = requireChangeableEvent(productId, eventId, ownerId);
    validateCommon(before.eventType(), request.occurredOn(), request.specsAfter());

    String specsBeforeJson = before.specsBefore();
    String specsAfterJson = before.specsAfter();
    ProductSpecifications rebuildBase = null;
    boolean hadSpecificationChanges = before.specsAfter() != null;
    boolean hasSpecificationChanges = hasSpecificationChanges(request.specsAfter());
    if (hadSpecificationChanges != hasSpecificationChanges) {
      throw new CommonException(ErrorCode.INVALID_HISTORY_EVENT);
    }
    if (hadSpecificationChanges) {
      rebuildBase = specifications(parseMap(before.specsBefore()));
      specsAfterJson = json(toMap(mergeSpecifications(rebuildBase, request.specsAfter())));
    }

    historyRepository.correctEvent(eventId, request, specsBeforeJson, specsAfterJson);
    if (rebuildBase != null) rebuildSpecifications(productId, eventId, rebuildBase);
    else historyRepository.incrementFactsVersion(productId);
    HistoryEventRecord after = historyRepository.findEvent(productId, eventId).orElseThrow();
    historyRepository.insertAudit(
        productId,
        ownerId,
        eventId,
        json(before),
        json(after),
        request.reason(),
        "HISTORY_CORRECTED");
    return toResponse(
        ownerId,
        after,
        historyRepository.findDocumentIds(productId).getOrDefault(eventId, List.of()),
        historyRepository.findLatestChanges(productId).get(eventId));
  }

  @Transactional
  public HistoryEventResponse voidEvent(
      long ownerId, long productId, long eventId, VoidHistoryEventRequest request) {
    requireOwnedLock(productId, ownerId);
    HistoryEventRecord before = requireChangeableEvent(productId, eventId, ownerId);
    ProductSpecifications rebuildBase =
        before.specsAfter() != null ? specifications(parseMap(before.specsBefore())) : null;

    historyRepository.voidEvent(eventId);
    if (rebuildBase != null) rebuildSpecifications(productId, eventId, rebuildBase);
    else historyRepository.incrementFactsVersion(productId);
    HistoryEventRecord after = historyRepository.findEvent(productId, eventId).orElseThrow();
    historyRepository.insertAudit(
        productId, ownerId, eventId, json(before), json(after), request.reason(), "HISTORY_VOIDED");
    return toResponse(
        ownerId,
        after,
        historyRepository.findDocumentIds(productId).getOrDefault(eventId, List.of()),
        historyRepository.findLatestChanges(productId).get(eventId));
  }

  private void rebuildSpecifications(
      long productId, long changedEventId, ProductSpecifications base) {
    Map<String, Object> current = new LinkedHashMap<>(toMap(base));
    for (HistoryEventRecord event :
        historyRepository.findValidSpecificationEventsFrom(productId, changedEventId)) {
      Map<String, Object> before = parseMap(event.specsBefore());
      Map<String, Object> after = parseMap(event.specsAfter());
      for (String key : SPEC_KEYS) {
        if (!java.util.Objects.equals(before.get(key), after.get(key))) {
          current.put(key, after.get(key));
        }
      }
    }
    historyRepository.replaceSpecifications(productId, specifications(current));
  }

  private HistoryEventRecord requireChangeableEvent(long productId, long eventId, long ownerId) {
    HistoryEventRecord event =
        historyRepository
            .findEvent(productId, eventId)
            .orElseThrow(() -> new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    if (event.status() == HistoryEventStatus.VOID
        || event.eventType() == HistoryEventType.TRANSFER
        || event.authorId() == null
        || event.authorId() != ownerId) {
      throw new CommonException(ErrorCode.HISTORY_EVENT_CHANGE_NOT_ALLOWED);
    }
    return event;
  }

  private void validateCommon(
      HistoryEventType eventType, java.time.LocalDate occurredOn, Map<String, Object> specsAfter) {
    if (occurredOn.isAfter(java.time.LocalDate.now(clock))) {
      throw new CommonException(ErrorCode.INVALID_HISTORY_EVENT);
    }
    boolean hasSpecs = hasSpecificationChanges(specsAfter);
    if ((eventType == HistoryEventType.SPEC_CHANGE && !hasSpecs)
        || (!eventType.changesSpecifications() && hasSpecs)) {
      throw new CommonException(ErrorCode.INVALID_HISTORY_EVENT);
    }
    if (hasSpecs
        && (!SPEC_KEYS.containsAll(specsAfter.keySet())
            || specsAfter.values().stream().anyMatch(java.util.Objects::isNull))) {
      throw new CommonException(ErrorCode.INVALID_HISTORY_EVENT);
    }
  }

  private static boolean hasSpecificationChanges(Map<String, Object> specsAfter) {
    return specsAfter != null && !specsAfter.isEmpty();
  }

  private ProductSpecifications mergeSpecifications(
      ProductSpecifications before, Map<String, Object> changes) {
    Map<String, Object> merged = new LinkedHashMap<>(toMap(before));
    merged.putAll(changes);
    ProductSpecifications result = specifications(merged);
    if (result.equals(before)) throw new CommonException(ErrorCode.INVALID_HISTORY_EVENT);
    return result;
  }

  private ProductSpecifications specifications(Map<String, Object> values) {
    Integer ramGb = integer(values.get("ramGb"));
    Integer storageGb = integer(values.get("storageGb"));
    Integer battery = integer(values.get("batteryHealthPercent"));
    String storageType = string(values.get("storageType"));
    if ((ramGb != null && ramGb < 1)
        || (storageGb != null && storageGb < 1)
        || (battery != null && (battery < 0 || battery > 100))
        || (storageType != null && !Set.of("SSD", "HDD", "MIXED").contains(storageType))) {
      throw new CommonException(ErrorCode.INVALID_HISTORY_EVENT);
    }
    return new ProductSpecifications(
        string(values.get("cpu")),
        string(values.get("gpu")),
        ramGb,
        storageGb,
        storageType,
        string(values.get("display")),
        battery,
        string(values.get("os")));
  }

  private static Integer integer(Object value) {
    if (value == null) return null;
    if (value instanceof Number number && number.doubleValue() == number.intValue()) {
      return number.intValue();
    }
    throw new CommonException(ErrorCode.INVALID_HISTORY_EVENT);
  }

  private static String string(Object value) {
    if (value == null) return null;
    if (!(value instanceof String text)) throw new CommonException(ErrorCode.INVALID_HISTORY_EVENT);
    String trimmed = text.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static Map<String, Object> toMap(ProductSpecifications specs) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("cpu", specs.cpu());
    values.put("gpu", specs.gpu());
    values.put("ramGb", specs.ramGb());
    values.put("storageGb", specs.storageGb());
    values.put("storageType", specs.storageType());
    values.put("display", specs.display());
    values.put("batteryHealthPercent", specs.batteryHealthPercent());
    values.put("os", specs.os());
    return values;
  }

  private List<Long> normalizedDocumentIds(List<Long> documentIds) {
    if (documentIds == null) return List.of();
    if (new HashSet<>(documentIds).size() != documentIds.size()) {
      throw new CommonException(ErrorCode.INVALID_HISTORY_DOCUMENT);
    }
    return List.copyOf(documentIds);
  }

  private HistoryEventResponse getOwnedEvent(
      long ownerId, long productId, long eventId, List<Long> documentIds) {
    return toResponse(
        ownerId, historyRepository.findEvent(productId, eventId).orElseThrow(), documentIds, null);
  }

  private HistoryEventResponse toResponse(
      long ownerId, HistoryEventRecord event, List<Long> documentIds, HistoryChangeRecord change) {
    HistoryEventSource source =
        event.eventType() == HistoryEventType.TRANSFER
            ? HistoryEventSource.SYSTEM
            : java.util.Objects.equals(event.authorId(), ownerId)
                ? HistoryEventSource.CURRENT_OWNER
                : HistoryEventSource.PREVIOUS_OWNER;
    boolean editable =
        event.status() == HistoryEventStatus.VALID
            && event.eventType() != HistoryEventType.TRANSFER
            && java.util.Objects.equals(event.authorId(), ownerId);
    return new HistoryEventResponse(
        event.eventId(),
        event.productId(),
        event.eventType(),
        event.occurredOn(),
        event.title(),
        event.content(),
        event.serviceProvider(),
        event.cost(),
        event.replacedPart(),
        parseMap(event.specsBefore()),
        parseMap(event.specsAfter()),
        event.status(),
        source,
        editable,
        documentIds,
        change == null ? null : change.action(),
        change == null ? null : change.reason(),
        change == null ? null : change.changedAt(),
        event.createdAt(),
        event.updatedAt());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseMap(String value) {
    if (value == null) return null;
    try {
      Object parsed = objectMapper.readValue(value, Object.class);
      if (parsed instanceof String encoded) {
        return objectMapper.readValue(encoded, Map.class);
      }
      return (Map<String, Object>) parsed;
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to deserialize history specifications", exception);
    }
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to serialize history facts", exception);
    }
  }

  private void requireOwnedLock(long productId, long ownerId) {
    if (!historyRepository.lockOwnedProduct(productId, ownerId)) {
      throw new CommonException(ErrorCode.RESOURCE_NOT_FOUND);
    }
  }
}

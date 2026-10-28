package org.example.springtestci.history.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.common.error.ErrorCode;
import org.example.springtestci.history.dto.ChangeVisibilityRequest;
import org.example.springtestci.history.dto.CorrectEventRequest;
import org.example.springtestci.history.dto.CreateEventRequest;
import org.example.springtestci.history.dto.ProductEventResponse;
import org.example.springtestci.history.dto.VoidEventRequest;
import org.example.springtestci.history.entity.EventVisibility;
import org.example.springtestci.history.entity.ProductEvent;
import org.example.springtestci.history.repository.ProductEventRepository;
import org.example.springtestci.listing.service.ListingHistoryReader.EventStatus;
import org.example.springtestci.listing.service.ListingHistoryReader.EventType;
import org.example.springtestci.product.repository.ProductRepository;
import org.example.springtestci.trade.repository.TradeAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductHistoryServiceTest {

  @Mock private ProductEventRepository eventRepository;
  @Mock private ProductRepository productRepository;
  @Mock private TradeAuditRepository tradeAuditRepository;

  private ProductHistoryService service;

  @BeforeEach
  void setUp() {
    service = new ProductHistoryService(eventRepository, productRepository, tradeAuditRepository);
  }

  @Test
  @DisplayName("소유자가 이력을 등록하면 DB에 저장되고 감사 로그가 기록된다")
  void createEvent_success() {
    long productId = 10L;
    long ownerId = 1L;
    given(productRepository.findOwnerId(productId)).willReturn(Optional.of(ownerId));

    CreateEventRequest req =
        new CreateEventRequest(
            productId,
            EventType.REPAIR,
            LocalDate.of(2025, 1, 15),
            "배터리 교체",
            "공식 서비스센터에서 배터리 정품 교체",
            "Apple 강남",
            "Battery",
            EventVisibility.PRIVATE);

    given(eventRepository.save(any(ProductEvent.class)))
        .willAnswer(
            inv -> {
              ProductEvent e = inv.getArgument(0);
              ReflectionTestUtils.setField(e, "id", 100L);
              return e;
            });

    ProductEventResponse response = service.createEvent(ownerId, req);

    assertThat(response.eventId()).isEqualTo(100L);
    assertThat(response.title()).isEqualTo("배터리 교체");
    assertThat(response.eventType()).isEqualTo(EventType.REPAIR);

    ArgumentCaptor<ProductEvent> captor = ArgumentCaptor.forClass(ProductEvent.class);
    verify(eventRepository).save(captor.capture());
    ProductEvent saved = captor.getValue();
    assertThat(saved.getProductId()).isEqualTo(productId);
    assertThat(saved.getAuthorId()).isEqualTo(ownerId);
    assertThat(saved.getVisibility()).isEqualTo(EventVisibility.PRIVATE);

    verify(tradeAuditRepository)
        .log(eq(productId), eq(ownerId), eq(100L), eq("EVENT_CREATED"), any(), any(), any());
  }

  @Test
  @DisplayName("소유자가 아닌 사용자가 이력 등록을 시도하면 거부된다")
  void createEvent_accessDenied() {
    long productId = 10L;
    given(productRepository.findOwnerId(productId)).willReturn(Optional.of(1L));

    CreateEventRequest req =
        new CreateEventRequest(
            productId,
            EventType.REPAIR,
            LocalDate.of(2025, 1, 15),
            "배터리 교체",
            null,
            null,
            null,
            EventVisibility.PRIVATE);

    assertThatThrownBy(() -> service.createEvent(999L, req))
        .isInstanceOf(CommonException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
  }

  @Test
  @DisplayName("소유자는 비공개 이력까지 모두 조회할 수 있다")
  void getEvents_ownerSeesAll() {
    long productId = 10L;
    long ownerId = 1L;
    given(productRepository.findOwnerId(productId)).willReturn(Optional.of(ownerId));

    ProductEvent e1 =
        ProductEvent.create(
            productId,
            ownerId,
            EventType.PURCHASE,
            LocalDate.of(2024, 1, 1),
            "구매",
            "신품 구매",
            null,
            null,
            EventVisibility.PUBLIC);
    ReflectionTestUtils.setField(e1, "id", 1L);

    ProductEvent e2 =
        ProductEvent.create(
            productId,
            ownerId,
            EventType.REPAIR,
            LocalDate.of(2024, 6, 1),
            "자가 수리",
            "내부 청소",
            null,
            null,
            EventVisibility.PRIVATE);
    ReflectionTestUtils.setField(e2, "id", 2L);

    given(eventRepository.findByProductIdOrderByOccurredOnDescIdDesc(productId))
        .willReturn(List.of(e2, e1));

    List<ProductEventResponse> result = service.getEvents(ownerId, productId);
    assertThat(result).hasSize(2);
  }

  @Test
  @DisplayName("비소유자 또는 비로그인 사용자는 공개 이력만 조회할 수 있다")
  void getEvents_nonOwnerSeesOnlyPublic() {
    long productId = 10L;
    long ownerId = 1L;
    given(productRepository.findOwnerId(productId)).willReturn(Optional.of(ownerId));

    ProductEvent e1 =
        ProductEvent.create(
            productId,
            ownerId,
            EventType.PURCHASE,
            LocalDate.of(2024, 1, 1),
            "구매",
            "신품 구매",
            null,
            null,
            EventVisibility.PUBLIC);
    ReflectionTestUtils.setField(e1, "id", 1L);

    given(
            eventRepository.findByProductIdAndVisibilityOrderByOccurredOnDescIdDesc(
                productId, EventVisibility.PUBLIC))
        .willReturn(List.of(e1));

    List<ProductEventResponse> result = service.getEvents(999L, productId);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).title()).isEqualTo("구매");
    assertThat(result.get(0).authorId()).isZero();
  }

  @Test
  @DisplayName("이전 소유자가 남긴 이력은 새 소유자가 정정하거나 무효 처리할 수 없다")
  void editEvent_previousOwnerRecordDenied() {
    long eventId = 51L;
    long productId = 10L;
    long previousOwnerId = 1L;
    long newOwnerId = 2L;
    ProductEvent event =
        ProductEvent.create(
            productId,
            previousOwnerId,
            EventType.REPAIR,
            LocalDate.of(2025, 1, 10),
            "수리",
            null,
            null,
            null,
            EventVisibility.PUBLIC);
    ReflectionTestUtils.setField(event, "id", eventId);
    given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
    given(productRepository.findOwnerId(productId)).willReturn(Optional.of(newOwnerId));

    assertThatThrownBy(
            () ->
                service.correctEvent(
                    newOwnerId, eventId, new CorrectEventRequest("사유", "제목", null, null, null)))
        .isInstanceOf(CommonException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    assertThatThrownBy(() -> service.voidEvent(newOwnerId, eventId, new VoidEventRequest("사유")))
        .isInstanceOf(CommonException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
  }

  private ProductEvent visibilityEvent(long eventId, long productId, long authorId) {
    ProductEvent event =
        ProductEvent.create(
            productId,
            authorId,
            EventType.REPAIR,
            LocalDate.of(2025, 1, 10),
            "수리",
            null,
            null,
            null,
            EventVisibility.PRIVATE);
    ReflectionTestUtils.setField(event, "id", eventId);
    return event;
  }

  @Test
  @DisplayName("소유자이자 작성자는 이력 공개 상태를 바꾸고 변경 기록이 남는다")
  void changeVisibility_ownerChangesAndAudits() {
    ProductEvent event = visibilityEvent(60L, 10L, 1L);
    given(eventRepository.findById(60L)).willReturn(Optional.of(event));
    given(productRepository.findOwnerId(10L)).willReturn(Optional.of(1L));

    ProductEventResponse response =
        service.changeVisibility(1L, 60L, new ChangeVisibilityRequest(EventVisibility.PUBLIC));

    assertThat(response.visibility()).isEqualTo(EventVisibility.PUBLIC);
    verify(tradeAuditRepository)
        .log(
            eq(10L),
            eq(1L),
            eq(60L),
            eq("EVENT_VISIBILITY_CHANGED"),
            eq("visibility=PRIVATE"),
            eq("visibility=PUBLIC"),
            anyString());
  }

  @Test
  @DisplayName("공개 상태가 이미 같으면 변경 기록을 남기지 않는다")
  void changeVisibility_sameValueDoesNotAudit() {
    ProductEvent event = visibilityEvent(61L, 10L, 1L);
    given(eventRepository.findById(61L)).willReturn(Optional.of(event));
    given(productRepository.findOwnerId(10L)).willReturn(Optional.of(1L));

    service.changeVisibility(1L, 61L, new ChangeVisibilityRequest(EventVisibility.PRIVATE));

    org.mockito.Mockito.verifyNoInteractions(tradeAuditRepository);
  }

  @Test
  @DisplayName("소유자가 아니거나 이전 소유자가 남긴 이력은 공개 상태를 바꿀 수 없다")
  void changeVisibility_nonOwnerAndPreviousOwnerRecordDenied() {
    ProductEvent event = visibilityEvent(62L, 10L, 1L);
    given(eventRepository.findById(62L)).willReturn(Optional.of(event));
    given(productRepository.findOwnerId(10L)).willReturn(Optional.of(2L));

    assertThatThrownBy(
            () ->
                service.changeVisibility(
                    3L, 62L, new ChangeVisibilityRequest(EventVisibility.PUBLIC)))
        .isInstanceOf(CommonException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    assertThatThrownBy(
            () ->
                service.changeVisibility(
                    2L, 62L, new ChangeVisibilityRequest(EventVisibility.PUBLIC)))
        .isInstanceOf(CommonException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    assertThat(event.getVisibility()).isEqualTo(EventVisibility.PRIVATE);
  }

  @Test
  @DisplayName("이미 무효 처리된 이력은 다시 무효 처리할 수 없다")
  void voidEvent_alreadyVoidRejected() {
    long eventId = 52L;
    long productId = 10L;
    long ownerId = 1L;
    ProductEvent event =
        ProductEvent.create(
            productId,
            ownerId,
            EventType.REPAIR,
            LocalDate.of(2025, 1, 10),
            "수리",
            null,
            null,
            null,
            EventVisibility.PUBLIC);
    ReflectionTestUtils.setField(event, "id", eventId);
    event.markVoid(ownerId, "첫 무효");
    given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
    given(productRepository.findOwnerId(productId)).willReturn(Optional.of(ownerId));

    assertThatThrownBy(() -> service.voidEvent(ownerId, eventId, new VoidEventRequest("또 무효")))
        .isInstanceOf(CommonException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
  }

  @Test
  @DisplayName("이력을 정정하면 정정 사유와 전후 내용이 기록된다 (HIST-06, SEC-08)")
  void correctEvent_success() {
    long eventId = 50L;
    long productId = 10L;
    long ownerId = 1L;

    ProductEvent event =
        ProductEvent.create(
            productId,
            ownerId,
            EventType.REPAIR,
            LocalDate.of(2025, 1, 10),
            "화면 수리",
            "사설 수리",
            null,
            null,
            EventVisibility.PUBLIC);
    ReflectionTestUtils.setField(event, "id", eventId);

    given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
    given(productRepository.findOwnerId(productId)).willReturn(Optional.of(ownerId));

    CorrectEventRequest req =
        new CorrectEventRequest("오기입 정정", "디스플레이 교체", "공식 센터 액정 교체", "Apple 가로수길", "Display");

    ProductEventResponse response = service.correctEvent(ownerId, eventId, req);

    assertThat(response.title()).isEqualTo("디스플레이 교체");
    assertThat(response.content()).isEqualTo("공식 센터 액정 교체");
    assertThat(response.corrections()).hasSize(1);
    assertThat(response.corrections().get(0).reason()).isEqualTo("오기입 정정");

    verify(tradeAuditRepository)
        .log(
            eq(productId),
            eq(ownerId),
            eq(eventId),
            eq("EVENT_CORRECTED"),
            anyString(),
            anyString(),
            eq("오기입 정정"));
  }

  @Test
  @DisplayName("이력을 무효 처리하면 상태가 VOID로 변경되고 감사 로그가 기록된다 (HIST-06, SEC-08)")
  void voidEvent_success() {
    long eventId = 50L;
    long productId = 10L;
    long ownerId = 1L;

    ProductEvent event =
        ProductEvent.create(
            productId,
            ownerId,
            EventType.INSPECTION,
            LocalDate.of(2025, 1, 10),
            "점검 기록",
            "정기 점검",
            null,
            null,
            EventVisibility.PUBLIC);
    ReflectionTestUtils.setField(event, "id", eventId);

    given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
    given(productRepository.findOwnerId(productId)).willReturn(Optional.of(ownerId));

    VoidEventRequest req = new VoidEventRequest("중복 등록된 항목");
    ProductEventResponse response = service.voidEvent(ownerId, eventId, req);

    assertThat(response.status()).isEqualTo(EventStatus.VOID);
    assertThat(response.corrections()).hasSize(1);
    assertThat(response.corrections().get(0).reason()).isEqualTo("중복 등록된 항목");

    verify(tradeAuditRepository)
        .log(
            eq(productId),
            eq(ownerId),
            eq(eventId),
            eq("EVENT_VOIDED"),
            anyString(),
            anyString(),
            eq("중복 등록된 항목"));
  }
}

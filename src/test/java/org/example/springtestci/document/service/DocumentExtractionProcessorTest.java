package org.example.springtestci.document.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.example.springtestci.common.storage.FileStorage;
import org.example.springtestci.document.ai.DocumentFactExtractionException;
import org.example.springtestci.document.ai.DocumentFactExtractor;
import org.example.springtestci.document.entity.Document;
import org.example.springtestci.document.entity.DocumentExtraction;
import org.example.springtestci.document.entity.DocumentType;
import org.example.springtestci.document.entity.ExtractionStatus;
import org.example.springtestci.document.repository.DocumentExtractionRepository;
import org.example.springtestci.document.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class DocumentExtractionProcessorTest {

  private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
  private static final Long EXTRACTION = 5L;
  private static final Long DOCUMENT = 10L;

  @Mock private DocumentExtractionRepository extractionRepository;
  @Mock private DocumentRepository documentRepository;
  @Mock private ObjectProvider<FileStorage> storageProvider;
  @Mock private ObjectProvider<DocumentFactExtractor> extractorProvider;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private FileStorage storage;
  @Mock private DocumentFactExtractor extractor;

  private DocumentExtractionProcessor processor;
  private DocumentExtraction extraction;
  private Document document;

  @BeforeEach
  void setUp() throws IOException {
    lenient().when(storageProvider.getIfAvailable()).thenReturn(storage);
    lenient().when(extractorProvider.getIfAvailable()).thenReturn(extractor);
    lenient()
        .when(storage.load(anyString()))
        .thenReturn(new ByteArrayResource(new byte[] {1, 2, 3}));
    document = Document.register(100L, 1L, "documents/100/k.png", "image/png", 3, NOW);
    ReflectionTestUtils.setField(document, "id", DOCUMENT);
    extraction =
        DocumentExtraction.request(
            DOCUMENT, document.getVersion(), 1L, NOW, "v1", "test-model", "product-facts-v1");
    lenient()
        .when(extractionRepository.findById(EXTRACTION))
        .thenAnswer(invocation -> Optional.of(extraction));
    lenient()
        .when(extractionRepository.save(any(DocumentExtraction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(documentRepository.findById(DOCUMENT)).thenReturn(Optional.of(document));
    processor =
        new DocumentExtractionProcessor(
            extractionRepository,
            documentRepository,
            storageProvider,
            extractorProvider,
            transactionManager);
  }

  @Test
  void successStoresSanitizedFacts() throws Exception {
    Map<String, Object> raw = new HashMap<>();
    raw.put("productName", "LG gram");
    raw.put("vendor", "전화 010-1111-2222");
    raw.put("unknownKey", "x");
    when(extractor.extract(any(), anyString(), any(DocumentType.class))).thenReturn(raw);

    processor.process(EXTRACTION, DocumentType.RECEIPT);

    assertEquals(ExtractionStatus.EXTRACTED, extraction.getStatus());
    assertEquals("LG gram", extraction.getExtractedFacts().get("productName"));
    assertNull(extraction.getExtractedFacts().get("vendor"));
    assertEquals(false, extraction.getExtractedFacts().containsKey("unknownKey"));
  }

  @Test
  void rejectedQueuedExtractionFailsWithBusyCode() {
    processor.failRejected(EXTRACTION);

    assertEquals(ExtractionStatus.FAILED, extraction.getStatus());
    assertEquals("AI_BUSY", extraction.getErrorCode());
  }

  @Test
  void aiFailureRecordsErrorCodeOnly() throws Exception {
    when(extractor.extract(any(), anyString(), any(DocumentType.class)))
        .thenThrow(new DocumentFactExtractionException("AI_TIMEOUT"));

    processor.process(EXTRACTION, DocumentType.RECEIPT);

    assertEquals(ExtractionStatus.FAILED, extraction.getStatus());
    assertEquals("AI_TIMEOUT", extraction.getErrorCode());
  }

  @Test
  void unexpectedAiErrorIsClassified() throws Exception {
    when(extractor.extract(any(), anyString(), any(DocumentType.class)))
        .thenThrow(new IllegalStateException("boom"));

    processor.process(EXTRACTION, DocumentType.RECEIPT);

    assertEquals("AI_ERROR", extraction.getErrorCode());
  }

  @Test
  void staleDocumentVersionIsNotSentToAi() throws Exception {
    document.replaceFile("documents/100/n.png", "image/png", 3, NOW);

    processor.process(EXTRACTION, DocumentType.RECEIPT);

    assertEquals("DOCUMENT_CHANGED", extraction.getErrorCode());
    verify(extractor, never()).extract(any(), anyString(), any());
  }

  @Test
  void missingDocumentFails() throws Exception {
    when(documentRepository.findById(DOCUMENT)).thenReturn(Optional.empty());

    processor.process(EXTRACTION, DocumentType.RECEIPT);

    assertEquals("DOCUMENT_GONE", extraction.getErrorCode());
    verify(extractor, never()).extract(any(), anyString(), any());
  }

  @Test
  void storageReadFailureFails() throws Exception {
    when(storage.load(anyString())).thenThrow(new IOException("missing"));

    processor.process(EXTRACTION, DocumentType.RECEIPT);

    assertEquals("STORAGE_READ_FAILED", extraction.getErrorCode());
  }

  @Test
  void alreadyProcessedExtractionIsSkipped() throws Exception {
    extraction.startProcessing();
    extraction.fail("AI_TIMEOUT");

    processor.process(EXTRACTION, DocumentType.RECEIPT);

    verify(extractor, never()).extract(any(), anyString(), any());
    assertEquals("AI_TIMEOUT", extraction.getErrorCode());
  }
}

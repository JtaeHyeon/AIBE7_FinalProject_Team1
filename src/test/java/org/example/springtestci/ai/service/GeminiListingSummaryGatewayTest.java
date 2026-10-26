package org.example.springtestci.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import org.example.springtestci.common.error.CommonException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class GeminiListingSummaryGatewayTest {
  private final GeminiJsonClient client = mock(GeminiJsonClient.class);
  private final GeminiListingSummaryGateway gateway = new GeminiListingSummaryGateway(client);

  @Test
  void usesSharedTransportWithThreeToFiveSentenceSchemaAndNoTools() {
    when(client.configured()).thenReturn(true);
    assertTrue(gateway.configured());
    when(client.generate(anyString(), anyMap(), eq("registered-source")))
        .thenReturn(
            JsonMapper.builder()
                .build()
                .readTree("{\"sentences\":[\"첫 문장입니다.\",\"다음 문장입니다.\",\"마지막 문장입니다.\"]}"));
    assertEquals(3, gateway.summarize("registered-source").size());
    var schema = ArgumentCaptor.forClass(Map.class);
    verify(client).generate(anyString(), schema.capture(), eq("registered-source"));
    Map<?, ?> properties = (Map<?, ?>) schema.getValue().get("properties");
    Map<?, ?> sentences = (Map<?, ?>) properties.get("sentences");
    assertEquals(3, sentences.get("minItems"));
    assertEquals(5, sentences.get("maxItems"));
    assertEquals(false, schema.getValue().get("additionalProperties"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"sentences\":\"text\"}",
        "{\"sentences\":[1,2,3]}",
        "{\"sentences\":[],\"extra\":true}"
      })
  void rejectsWrongShapeBeforeBusinessValidation(String output) {
    when(client.generate(anyString(), anyMap(), anyString()))
        .thenReturn(JsonMapper.builder().build().readTree(output));
    assertThrows(CommonException.class, () -> gateway.summarize("{}"));
  }
}

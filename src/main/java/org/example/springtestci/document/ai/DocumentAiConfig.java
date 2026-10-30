package org.example.springtestci.document.ai;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(GeminiProperties.class)
public class DocumentAiConfig {

  /** API 키가 설정된 환경에서만 실제 AI 호출 구현을 등록한다. 키가 없으면 추출 요청은 503으로 안내한다. */
  @Bean
  @ConditionalOnExpression("!'${app.ai.gemini.api-key:}'.isBlank()")
  DocumentFactExtractor geminiDocumentFactExtractor(GeminiProperties properties) {
    return new GeminiDocumentFactExtractor(properties);
  }

  @Bean(name = "documentExtractionExecutor")
  Executor documentExtractionExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("doc-extract-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.initialize();
    return executor;
  }
}

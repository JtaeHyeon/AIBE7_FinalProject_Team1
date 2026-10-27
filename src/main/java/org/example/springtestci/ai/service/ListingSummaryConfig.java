package org.example.springtestci.ai.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** AI 요약을 HTTP 요청 처리와 분리해 실행하는 작업 스레드 풀을 설정한다. */
@Configuration(proxyBeanMethods = false)
public class ListingSummaryConfig {
  @Bean("listingSummaryExecutor")
  public ThreadPoolTaskExecutor listingSummaryExecutor() {
    // 동시 실행 2개·추가 대기 10개로 제한한다. 초과 작업은 디스패처가 DB 대기열에 남겨 둔다.
    var pool = new ThreadPoolTaskExecutor();
    pool.setCorePoolSize(2);
    pool.setMaxPoolSize(2);
    pool.setQueueCapacity(10);
    pool.setThreadNamePrefix("listing-summary-");
    pool.setWaitForTasksToCompleteOnShutdown(true);
    pool.setAwaitTerminationSeconds(5);
    return pool;
  }
}

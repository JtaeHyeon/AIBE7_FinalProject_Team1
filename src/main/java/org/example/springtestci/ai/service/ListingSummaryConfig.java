package org.example.springtestci.ai.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class ListingSummaryConfig {
  @Bean("listingSummaryExecutor")
  public ThreadPoolTaskExecutor listingSummaryExecutor() {
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

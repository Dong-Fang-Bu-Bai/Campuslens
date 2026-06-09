package com.campuslens.config;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {
  @Bean
  public Executor adaptationTaskExecutor(
      @Value("${campuslens.async.adaptation.core-size:2}") int coreSize,
      @Value("${campuslens.async.adaptation.max-size:4}") int maxSize,
      @Value("${campuslens.async.adaptation.queue-capacity:100}") int queueCapacity) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(coreSize);
    executor.setMaxPoolSize(maxSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("adaptation-");
    executor.initialize();
    return executor;
  }
}

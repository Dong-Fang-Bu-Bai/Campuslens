package com.campuslens.service;

import com.campuslens.model.SearchResult;
import com.campuslens.service.AlgorithmSearchClient.AlgorithmBatchItem;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "campuslens.search.worker-enabled", havingValue = "true", matchIfMissing = true)
public class SearchJobWorker {
  private final SearchQueue queue;
  private final SearchJobRepository repository;
  private final AlgorithmSearchClient algorithm;
  private final LandmarkService landmarks;
  private final int batchSize;
  private final int batchWaitMs;
  private final int leaseSeconds;
  private final int maxAttempts;
  private final AtomicBoolean running = new AtomicBoolean();
  private final String workerId = "search-" + UUID.randomUUID();

  public SearchJobWorker(
      SearchQueue queue,
      SearchJobRepository repository,
      AlgorithmSearchClient algorithm,
      LandmarkService landmarks,
      @Value("${campuslens.search.batch-size:2}") int batchSize,
      @Value("${campuslens.search.batch-wait-ms:100}") int batchWaitMs,
      @Value("${campuslens.search.lease-seconds:90}") int leaseSeconds,
      @Value("${campuslens.search.max-attempts:3}") int maxAttempts) {
    this.queue = queue;
    this.repository = repository;
    this.algorithm = algorithm;
    this.landmarks = landmarks;
    this.batchSize = Math.max(1, batchSize);
    this.batchWaitMs = Math.max(0, batchWaitMs);
    this.leaseSeconds = leaseSeconds;
    this.maxAttempts = maxAttempts;
  }

  @Scheduled(fixedDelayString = "${campuslens.search.worker-delay-ms:100}")
  public void consume() {
    if (!running.compareAndSet(false, true)) return;
    try {
      queue.promoteDue(batchSize);
      List<SearchJobRepository.WorkerJob> jobs = new ArrayList<>();
      List<SearchQueue.ReservedJob> reservations = new ArrayList<>();
      long deadline = System.nanoTime() + Duration.ofMillis(batchWaitMs).toNanos();
      while (jobs.size() < batchSize) {
        SearchQueue.ReservedJob reservation = queue.reserve(Duration.ofSeconds(leaseSeconds));
        if (reservation == null) {
          if (jobs.isEmpty() || batchWaitMs == 0 || System.nanoTime() >= deadline) break;
          sleepBriefly();
          continue;
        }
        repository.claim(reservation.jobId(), workerId, leaseSeconds, maxAttempts).ifPresentOrElse(job -> {
          jobs.add(job);
          reservations.add(reservation);
        }, () -> queue.acknowledge(reservation, false));
      }
      if (jobs.isEmpty()) return;
      process(jobs, reservations);
    } finally {
      running.set(false);
    }
  }

  private void process(
      List<SearchJobRepository.WorkerJob> jobs,
      List<SearchQueue.ReservedJob> reservations) {
    try {
      List<Path> paths = jobs.stream().map(job -> pathFromUrl(job.uploadImageUrl())).toList();
      List<AlgorithmBatchItem> responses = algorithm.searchBatch(paths);
      for (int i = 0; i < jobs.size(); i++) {
        SearchJobRepository.WorkerJob job = jobs.get(i);
        SearchQueue.ReservedJob reservation = reservations.get(i);
        AlgorithmBatchItem item = responses.get(i);
        if (!item.success()) {
          handleFailure(job, reservation, item.errorCode(), item.message(), item.retryable());
          continue;
        }
        List<SearchResult> results = item.response().results().stream()
            .map(this::mapResult)
            .flatMap(java.util.Optional::stream)
            .toList();
        boolean low = item.response().lowConfidence();
        boolean completed = repository.complete(job, results, low, normalizeMessage(item.response().message()));
        queue.acknowledge(reservation, completed);
      }
    } catch (RuntimeException ex) {
      for (int i = 0; i < jobs.size(); i++) {
        handleFailure(jobs.get(i), reservations.get(i), "algorithm_unavailable", ex.getMessage(), true);
      }
    }
  }

  private void handleFailure(
      SearchJobRepository.WorkerJob job,
      SearchQueue.ReservedJob reservation,
      String code,
      String message,
      boolean retryable) {
    if (retryable && job.attemptCount() < maxAttempts) {
      Duration delay = retryDelay(job.attemptCount());
      boolean updated = repository.retry(
          job, code, "算法处理失败，任务将自动重试：" + safeMessage(message),
          LocalDateTime.now().plus(delay));
      if (updated) {
        queue.scheduleRetry(reservation, delay);
      } else {
        queue.acknowledge(reservation, false);
      }
    } else {
      boolean failed = repository.fail(job, code, "检索失败：" + safeMessage(message));
      queue.acknowledge(reservation, failed);
    }
  }

  private Duration retryDelay(int attemptCount) {
    return Duration.ofSeconds(switch (attemptCount) { case 1 -> 2; case 2 -> 5; default -> 15; });
  }

  private String safeMessage(String value) {
    return value == null || value.isBlank() ? "算法服务未返回错误详情" : value;
  }

  private void sleepBriefly() {
    try {
      Thread.sleep(Math.min(10, Math.max(1, batchWaitMs)));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  private java.util.Optional<SearchResult> mapResult(AlgorithmSearchClient.AlgorithmSearchResult result) {
    return landmarks.findByCode(result.landmarkCode()).map(item -> new SearchResult(
        result.rank(), item.id(), item.code(), item.name(), item.englishName(),
        Math.max(0, Math.min(1, result.score())),
        result.confidenceLevel(), result.mahalanobisDistance(), item.coverImageUrl(),
        item.summary(), item.locationText(), item.mapX(), item.mapY()));
  }

  private Path pathFromUrl(String value) {
    return Path.of(value.replaceFirst("^/", ""));
  }

  private String normalizeMessage(String value) {
    if ("Search successful".equalsIgnoreCase(value)) return "算法服务检索成功";
    if (value != null && value.toLowerCase().contains("low")) return "算法匹配等级较低，建议人工核验";
    return value == null || value.isBlank() ? "算法服务检索成功" : value;
  }

  @Scheduled(fixedDelayString = "${campuslens.search.recovery-delay-ms:15000}")
  public void recoverExpired() {
    for (SearchJobRepository.JobRow job : repository.findUnadmittedJobs(100)) {
      try {
        queue.enqueueNew(job.jobId());
        repository.markAdmitted(job.id());
      } catch (SearchQueueFullException ex) {
        break;
      }
    }
    SearchJobRepository.RecoveryResult recovered = repository.recoverExpired(maxAttempts);
    for (String jobId : recovered.requeuedJobIds()) {
      queue.enqueueExisting(jobId);
    }
    for (String jobId : recovered.failedJobIds()) {
      queue.removeActive(jobId);
    }
    for (String jobId : repository.findDueQueuedJobs(500)) {
      queue.enqueueExisting(jobId);
    }
    for (String jobId : repository.findActiveJobIds()) {
      queue.ensureActive(jobId);
    }
    for (String jobId : queue.activeJobIds()) {
      if (!repository.isActive(jobId)) queue.removeActive(jobId);
    }
  }
}

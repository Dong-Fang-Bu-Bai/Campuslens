package com.campuslens.service;

import java.time.Duration;
import java.util.Set;

public interface SearchQueue {
  void enqueueNew(String jobId);

  void enqueueExisting(String jobId);

  ReservedJob reserve(Duration reservationTimeout);

  void acknowledge(ReservedJob reservation, boolean terminal);

  void release(ReservedJob reservation);

  void scheduleRetry(ReservedJob reservation, Duration delay);

  void requeueRecovered(String jobId);

  int promoteDue(int limit);

  Set<String> activeJobIds();

  void ensureActive(String jobId);

  void removeActive(String jobId);

  record ReservedJob(String jobId, String receiptId) {}
}

package com.campuslens.service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class InMemorySearchQueue implements SearchQueue {
  private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
  private final Set<String> active = ConcurrentHashMap.newKeySet();

  @Override
  public void enqueueNew(String jobId) {
    active.add(jobId);
    queue.add(jobId);
  }

  @Override
  public void enqueueExisting(String jobId) {
    active.add(jobId);
    queue.add(jobId);
  }

  @Override
  public ReservedJob reserve(Duration reservationTimeout) {
    String jobId = queue.poll();
    return jobId == null ? null : new ReservedJob(jobId, UUID.randomUUID().toString());
  }

  @Override
  public void acknowledge(ReservedJob reservation, boolean terminal) {
    if (terminal) active.remove(reservation.jobId());
  }

  @Override
  public void scheduleRetry(ReservedJob reservation, Duration delay) {
    queue.add(reservation.jobId());
  }

  @Override
  public void requeueRecovered(String jobId) {
    queue.add(jobId);
  }

  @Override
  public int promoteDue(int limit) {
    return 0;
  }

  @Override
  public Set<String> activeJobIds() {
    return Set.copyOf(active);
  }

  @Override
  public void ensureActive(String jobId) {
    active.add(jobId);
  }

  @Override
  public void removeActive(String jobId) {
    active.remove(jobId);
  }
}

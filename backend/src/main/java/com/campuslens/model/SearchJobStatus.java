package com.campuslens.model;

import java.time.LocalDateTime;
import java.util.List;

public record SearchJobStatus(
    String jobId,
    Long searchRecordId,
    String status,
    int attemptCount,
    String uploadImageUrl,
    String guestId,
    boolean lowConfidence,
    String message,
    String errorCode,
    List<SearchResult> results,
    Boolean sarApplied,
    String trustLevel,
    String modelVersion,
    String algorithmInstanceId,
    String algorithmInstanceRole,
    LocalDateTime queuedAt,
    LocalDateTime startedAt,
    LocalDateTime finishedAt) {
}

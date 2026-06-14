package com.campuslens.model;

import java.time.LocalDateTime;
import java.util.List;

public record UserSearchRecord(
    Long id,
    String uploadImageUrl,
    String bestLandmarkName,
    Double bestScore,
    String status,
    boolean lowConfidence,
    String message,
    String feedbackStatus,
    List<SearchResult> topResults,
    LocalDateTime createdAt) {
}

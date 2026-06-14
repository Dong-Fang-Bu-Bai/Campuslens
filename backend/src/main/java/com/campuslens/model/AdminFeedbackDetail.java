package com.campuslens.model;

import java.time.LocalDateTime;
import java.util.List;

public record AdminFeedbackDetail(
    Long id,
    Long searchRecordId,
    String uploadImageUrl,
    Long predictedLandmarkId,
    String predictedLandmarkName,
    Long confirmedLandmarkId,
    String confirmedLandmarkName,
    Long userId,
    String username,
    String guestId,
    String feedbackType,
    String comment,
    String status,
    List<SearchResult> topResults,
    CorrectionSampleInfo correctionSample,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {
}

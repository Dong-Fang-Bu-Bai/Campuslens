package com.campuslens.model;

import java.time.LocalDateTime;

public record AdminFeedbackRecord(
    Long id,
    Long searchRecordId,
    Long predictedLandmarkId,
    String predictedLandmarkName,
    Long confirmedLandmarkId,
    String confirmedLandmarkName,
    String feedbackType,
    String comment,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {
}

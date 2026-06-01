package com.campuslens.model;

import java.time.LocalDateTime;

public record AdminSearchRecord(
    Long id,
    String uploadImageUrl,
    String bestLandmarkName,
    Double bestScore,
    String status,
    boolean lowConfidence,
    String message,
    String guestId,
    LocalDateTime createdAt) {
}

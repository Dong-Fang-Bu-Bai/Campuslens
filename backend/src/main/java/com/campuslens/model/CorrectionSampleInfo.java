package com.campuslens.model;

import java.time.LocalDateTime;

public record CorrectionSampleInfo(
    Long id,
    String syncStatus,
    Boolean suggestAccept,
    Double reviewScore,
    String reason,
    Boolean sarEligible,
    String nextAction,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {
}

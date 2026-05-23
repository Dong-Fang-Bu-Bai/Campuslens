package com.campuslens.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FeedbackRequest(
    @NotNull Long searchRecordId,
    Long predictedLandmarkId,
    Long confirmedLandmarkId,
    @NotBlank String feedbackType,
    String comment) {
}

package com.campuslens.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LandmarkUpsertRequest(
    @NotBlank String code,
    @NotBlank String name,
    String englishName,
    String type,
    String summary,
    String description,
    String locationText,
    @NotNull Double mapX,
    @NotNull Double mapY,
    String coverImageUrl) {
}

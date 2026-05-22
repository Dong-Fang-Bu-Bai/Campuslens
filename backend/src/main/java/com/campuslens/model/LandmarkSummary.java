package com.campuslens.model;

public record LandmarkSummary(
    Long id,
    String code,
    String name,
    String englishName,
    String type,
    String summary,
    String coverImageUrl,
    String locationText,
    double mapX,
    double mapY) {
}

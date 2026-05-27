package com.campuslens.model;

public record SearchResult(
    int rank,
    Long landmarkId,
    String landmarkCode,
    String name,
    String englishName,
    double score,
    String confidenceLevel,
    Double mahalanobisDistance,
    String coverImageUrl,
    String summary,
    String locationText,
    double mapX,
    double mapY) {
}

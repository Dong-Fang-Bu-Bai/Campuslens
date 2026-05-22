package com.campuslens.model;

public record LandmarkImage(
    Long id,
    String imageUrl,
    String angle,
    String lightCondition,
    boolean isCover) {
}

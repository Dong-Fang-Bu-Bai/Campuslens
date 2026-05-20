package com.campuslens.model;

import java.util.List;

public record LandmarkDetail(
    Long id,
    String code,
    String name,
    String englishName,
    String type,
    String summary,
    String coverImageUrl,
    String description,
    String locationText,
    double mapX,
    double mapY,
    List<LandmarkImage> images) {
}

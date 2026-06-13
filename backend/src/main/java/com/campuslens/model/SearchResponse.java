package com.campuslens.model;

import java.util.List;

public record SearchResponse(
    Long searchRecordId,
    String uploadImageUrl,
    String guestId,
    boolean lowConfidence,
    String message,
    List<SearchResult> results) {
}

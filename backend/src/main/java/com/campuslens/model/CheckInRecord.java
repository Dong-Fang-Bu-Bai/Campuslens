package com.campuslens.model;

import java.time.LocalDateTime;
import java.util.List;

public record CheckInRecord(
    Long id,
    Long landmarkId,
    String landmarkCode,
    String landmarkName,
    String locationText,
    Double mapX,
    Double mapY,
    Long userId,
    String guestId,
    String displayName,
    String message,
    int likeCount,
    int replyCount,
    boolean likedByMe,
    List<CheckInReply> replies,
    LocalDateTime createdAt) {
}

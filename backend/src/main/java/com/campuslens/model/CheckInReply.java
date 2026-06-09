package com.campuslens.model;

import java.time.LocalDateTime;

public record CheckInReply(
    Long id,
    Long checkInId,
    Long userId,
    String guestId,
    String displayName,
    String message,
    LocalDateTime createdAt) {
}

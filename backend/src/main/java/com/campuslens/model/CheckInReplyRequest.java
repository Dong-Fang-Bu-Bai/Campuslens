package com.campuslens.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckInReplyRequest(
    @NotBlank @Size(max = 500) String message,
    @Size(max = 100) String guestId,
    Long parentReplyId) {
}

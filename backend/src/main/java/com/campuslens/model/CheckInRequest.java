package com.campuslens.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CheckInRequest(
    @NotNull Long landmarkId,
    @NotBlank @Size(max = 500) String message,
    @Size(max = 100) String guestId) {
}

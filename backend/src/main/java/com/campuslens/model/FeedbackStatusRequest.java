package com.campuslens.model;

import jakarta.validation.constraints.NotBlank;

public record FeedbackStatusRequest(@NotBlank String status) {
}

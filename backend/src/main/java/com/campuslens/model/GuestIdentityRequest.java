package com.campuslens.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestIdentityRequest(
    @NotBlank @Size(min = 16, max = 200) String clientToken) {
}

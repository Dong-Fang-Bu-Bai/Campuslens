package com.campuslens.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthRegisterRequest(
    @NotBlank String username,
    @NotBlank String password,
    @Email String email) {
}

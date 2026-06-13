package com.campuslens.model;

public record AuthResponse(
    Long userId,
    String username,
    String email,
    String role,
    boolean admin,
    String token,
    String message) {
}

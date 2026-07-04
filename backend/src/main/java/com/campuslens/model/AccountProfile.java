package com.campuslens.model;

public record AccountProfile(
    Long userId,
    String username,
    String email,
    String avatarUrl,
    String role,
    boolean admin) {
}

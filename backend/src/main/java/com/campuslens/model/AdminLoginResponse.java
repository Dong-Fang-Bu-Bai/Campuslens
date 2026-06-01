package com.campuslens.model;

public record AdminLoginResponse(boolean loggedIn, String username, String role, String message) {
}

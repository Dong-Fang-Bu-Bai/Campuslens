package com.campuslens.model;

public record SessionUser(Long userId, String username, String email, String role) {
  public boolean admin() {
    return "admin".equals(role);
  }
}

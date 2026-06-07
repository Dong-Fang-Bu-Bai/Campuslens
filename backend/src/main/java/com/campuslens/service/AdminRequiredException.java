package com.campuslens.service;

public class AdminRequiredException extends RuntimeException {
  public AdminRequiredException(String message) {
    super(message);
  }
}

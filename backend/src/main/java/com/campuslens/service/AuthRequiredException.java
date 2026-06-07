package com.campuslens.service;

public class AuthRequiredException extends RuntimeException {
  public AuthRequiredException(String message) {
    super(message);
  }
}

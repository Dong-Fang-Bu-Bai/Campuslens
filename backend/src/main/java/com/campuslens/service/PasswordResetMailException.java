package com.campuslens.service;

public class PasswordResetMailException extends RuntimeException {
  public PasswordResetMailException(String message, Throwable cause) {
    super(message, cause);
  }
}

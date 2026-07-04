package com.campuslens.service;

public class CheckInConflictException extends RuntimeException {
  public CheckInConflictException(String message) {
    super(message);
  }
}

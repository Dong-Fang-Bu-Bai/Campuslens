package com.campuslens.service;

public class SearchQueueUnavailableException extends RuntimeException {
  public SearchQueueUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}

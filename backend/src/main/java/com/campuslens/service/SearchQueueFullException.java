package com.campuslens.service;

public class SearchQueueFullException extends RuntimeException {
  public SearchQueueFullException(String message) {
    super(message);
  }
}

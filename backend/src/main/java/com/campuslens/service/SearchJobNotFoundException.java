package com.campuslens.service;

public class SearchJobNotFoundException extends RuntimeException {
  public SearchJobNotFoundException(String message) {
    super(message);
  }
}

package com.campuslens.model;

public record SearchJobSubmission(
    String jobId,
    Long searchRecordId,
    String status,
    String jobToken,
    int pollAfterMs) {
}

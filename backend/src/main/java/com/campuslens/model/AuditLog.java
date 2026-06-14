package com.campuslens.model;

import java.time.LocalDateTime;

public record AuditLog(
    Long id,
    Long feedbackId,
    String action,
    String oldStatus,
    String newStatus,
    Long operatorId,
    String comment,
    LocalDateTime createdAt) {
}

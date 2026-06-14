package com.campuslens.repository;

import com.campuslens.model.AuditLog;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {

  private final JdbcTemplate jdbcTemplate;

  private final RowMapper<AuditLog> rowMapper = (rs, rowNum) -> new AuditLog(
      rs.getLong("id"),
      rs.getLong("feedback_id"),
      rs.getString("action"),
      rs.getString("old_status"),
      rs.getString("new_status"),
      (Long) rs.getObject("operator_id"),
      rs.getString("comment"),
      rs.getTimestamp("created_at").toLocalDateTime());

  public AuditLogRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void save(Long feedbackId, String action, String oldStatus,
      String newStatus, Long operatorId, String comment) {
    jdbcTemplate.update("""
        INSERT INTO audit_log (feedback_id, action, old_status, new_status, operator_id, comment)
        VALUES (?, ?, ?, ?, ?, ?)
        """, feedbackId, action, oldStatus, newStatus, operatorId, comment);
  }

  public List<AuditLog> findByFeedbackId(Long feedbackId) {
    return jdbcTemplate.query(
        "SELECT * FROM audit_log WHERE feedback_id = ? ORDER BY created_at DESC",
        rowMapper, feedbackId);
  }
}

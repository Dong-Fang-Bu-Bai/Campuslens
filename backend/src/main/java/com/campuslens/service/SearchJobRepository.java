package com.campuslens.service;

import com.campuslens.model.SearchJobStatus;
import com.campuslens.model.SearchResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class SearchJobRepository {
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public SearchJobRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public JobRow create(
      String jobId,
      String tokenHash,
      String idempotencyKey,
      String fileSha256,
      String uploadUrl,
      String guestId,
      Long userId,
      boolean sarMode) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO search_record (
            upload_image_url, top_results_json, status, low_confidence, message,
            guest_id, user_id, user_type, job_id, job_token_hash, idempotency_key,
            file_sha256, queued_at, sar_mode
          ) VALUES (?, '[]', 'queued', FALSE, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)
          """, new String[] {"id"});
      ps.setString(1, uploadUrl);
      ps.setString(2, "任务已进入检索队列");
      ps.setString(3, guestId);
      ps.setObject(4, userId);
      ps.setString(5, userId == null ? "guest" : "user");
      ps.setString(6, jobId);
      ps.setString(7, tokenHash);
      ps.setString(8, idempotencyKey);
      ps.setString(9, fileSha256);
      ps.setBoolean(10, sarMode);
      return ps;
    }, keyHolder);
    long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
    if ("guest".equals(guestId)) {
      jdbcTemplate.update("UPDATE search_record SET guest_id = ? WHERE id = ?", "guest#" + id, id);
    }
    return new JobRow(id, jobId, fileSha256, uploadUrl, "queued", false, sarMode);
  }

  public Optional<JobRow> findByIdempotencyKey(String key) {
    return jdbcTemplate.query("""
        SELECT id, job_id, file_sha256, upload_image_url, status, queued_at, sar_mode
        FROM search_record WHERE idempotency_key = ?
        """, (rs, rowNum) -> new JobRow(
        rs.getLong("id"), rs.getString("job_id"), rs.getString("file_sha256"),
        rs.getString("upload_image_url"), rs.getString("status"), rs.getTimestamp("queued_at") != null,
        rs.getBoolean("sar_mode")), key)
        .stream().findFirst();
  }

  public void markAdmitted(long id) {
    jdbcTemplate.update("""
        UPDATE search_record SET queued_at = COALESCE(queued_at, CURRENT_TIMESTAMP),
            message = '任务已进入检索队列', updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """, id);
  }

  public Optional<SearchJobStatus> findStatus(String jobId) {
    return jdbcTemplate.query("""
        SELECT id, job_id, status, attempt_count, upload_image_url, guest_id,
               low_confidence, message, error_code, top_results_json,
               sar_mode, sar_applied, trust_level, base_model_version, index_version, sar_state_version,
               algorithm_instance_id, algorithm_instance_role,
               queued_at, started_at, finished_at
        FROM search_record WHERE job_id = ?
        """, (rs, rowNum) -> new SearchJobStatus(
        rs.getString("job_id"),
        rs.getLong("id"),
        rs.getString("status"),
        rs.getInt("attempt_count"),
        rs.getString("upload_image_url"),
        rs.getString("guest_id"),
        rs.getBoolean("low_confidence"),
        rs.getString("message"),
        rs.getString("error_code"),
        parseResults(rs.getString("top_results_json")),
        rs.getObject("sar_applied") == null ? null : rs.getBoolean("sar_applied"),
        rs.getString("trust_level"),
        combinedVersion(rs.getString("base_model_version"), rs.getString("index_version"), rs.getString("sar_state_version")),
        rs.getString("algorithm_instance_id"),
        rs.getString("algorithm_instance_role"),
        toLocalDateTime(rs.getTimestamp("queued_at")),
        toLocalDateTime(rs.getTimestamp("started_at")),
        toLocalDateTime(rs.getTimestamp("finished_at"))), jobId).stream().findFirst();
  }

  public Optional<JobOwnership> findOwnership(String jobId) {
    return jdbcTemplate.query("""
        SELECT user_id, job_token_hash FROM search_record WHERE job_id = ?
        """, (rs, rowNum) -> new JobOwnership(
        rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
        rs.getString("job_token_hash")), jobId).stream().findFirst();
  }

  public Optional<WorkerJob> claim(String jobId, String workerId, int leaseSeconds, int maxAttempts) {
    int updated = jdbcTemplate.update("""
        UPDATE search_record
        SET status = 'processing', started_at = COALESCE(started_at, CURRENT_TIMESTAMP),
            attempt_count = attempt_count + 1,
            lease_until = ?, worker_id = ?, error_code = NULL, updated_at = CURRENT_TIMESTAMP
        WHERE job_id = ? AND status = 'queued'
          AND attempt_count < ?
          AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP)
        """, LocalDateTime.now().plusSeconds(leaseSeconds), workerId, jobId, maxAttempts);
    if (updated == 0) {
      return Optional.empty();
    }
    return findWorkerJob(jobId);
  }

  private Optional<WorkerJob> findWorkerJob(String jobId) {
    return jdbcTemplate.query("""
        SELECT id, job_id, upload_image_url, attempt_count, worker_id, sar_mode
        FROM search_record WHERE job_id = ?
        """, (rs, rowNum) -> new WorkerJob(
        rs.getLong("id"), rs.getString("job_id"), rs.getString("upload_image_url"),
        rs.getInt("attempt_count"), rs.getString("worker_id"), rs.getBoolean("sar_mode")), jobId)
        .stream().findFirst();
  }

  public Optional<Boolean> findSarMode(String jobId) {
    return jdbcTemplate.query("SELECT sar_mode FROM search_record WHERE job_id = ?",
        (rs, rowNum) -> rs.getBoolean("sar_mode"), jobId).stream().findFirst();
  }

  public boolean complete(WorkerJob job, List<SearchResult> results, boolean lowConfidence, String message,
      Boolean sarApplied, String trustLevel, String baseModelVersion, String indexVersion, String sarStateVersion,
      String algorithmInstanceId, String algorithmInstanceRole) {
    SearchResult best = results.isEmpty() ? null : results.get(0);
    return jdbcTemplate.update("""
        UPDATE search_record
        SET top_results_json = ?, best_landmark_id = ?, best_score = ?, status = ?,
            low_confidence = ?, message = ?, error_code = NULL, lease_until = NULL,
            worker_id = NULL, next_attempt_at = NULL, sar_applied = ?, trust_level = ?,
            base_model_version = ?, index_version = ?, sar_state_version = ?,
            algorithm_instance_id = ?, algorithm_instance_role = ?,
            finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
        WHERE id = ? AND status = 'processing' AND worker_id = ? AND attempt_count = ?
        """, toJson(results), best == null ? null : best.landmarkId(), best == null ? null : best.score(),
        lowConfidence ? "low_confidence" : "success", lowConfidence, message,
        sarApplied, trustLevel, baseModelVersion, indexVersion, sarStateVersion,
        algorithmInstanceId, algorithmInstanceRole,
        job.id(), job.workerId(), job.attemptCount()) == 1;
  }

  public boolean retry(WorkerJob job, String errorCode, String message, LocalDateTime nextAttemptAt) {
    return jdbcTemplate.update("""
        UPDATE search_record
        SET status = 'queued', message = ?, error_code = ?, lease_until = NULL, worker_id = NULL,
            next_attempt_at = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ? AND status = 'processing' AND worker_id = ? AND attempt_count = ?
        """, message, errorCode, nextAttemptAt, job.id(), job.workerId(), job.attemptCount()) == 1;
  }

  public boolean fail(WorkerJob job, String errorCode, String message) {
    return jdbcTemplate.update("""
        UPDATE search_record
        SET status = 'failed', low_confidence = TRUE, message = ?, error_code = ?,
            lease_until = NULL, worker_id = NULL, next_attempt_at = NULL,
            finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
        WHERE id = ? AND status = 'processing' AND worker_id = ? AND attempt_count = ?
        """, message, errorCode, job.id(), job.workerId(), job.attemptCount()) == 1;
  }

  public RecoveryResult recoverExpired(int maxAttempts) {
    List<ExpiredJob> jobs = jdbcTemplate.query("""
        SELECT id, job_id, attempt_count, worker_id, lease_until FROM search_record
        WHERE status = 'processing' AND lease_until < CURRENT_TIMESTAMP
        """, (rs, rowNum) -> new ExpiredJob(
        rs.getLong("id"), rs.getString("job_id"), rs.getInt("attempt_count"),
        rs.getString("worker_id"), rs.getTimestamp("lease_until").toLocalDateTime()));
    List<String> requeued = new ArrayList<>();
    List<String> failed = new ArrayList<>();
    for (ExpiredJob job : jobs) {
      if (job.attemptCount() >= maxAttempts) {
        int updated = jdbcTemplate.update("""
            UPDATE search_record SET status = 'failed', low_confidence = TRUE,
                worker_id = NULL, lease_until = NULL, next_attempt_at = NULL,
                error_code = 'lease_expired', message = '工作进程连续超时，任务已终止',
                finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'processing' AND worker_id = ?
              AND attempt_count = ? AND lease_until = ?
            """, job.id(), job.workerId(), job.attemptCount(), job.leaseUntil());
        if (updated == 1) failed.add(job.jobId());
      } else {
        int updated = jdbcTemplate.update("""
            UPDATE search_record SET status = 'queued', worker_id = NULL, lease_until = NULL,
                next_attempt_at = CURRENT_TIMESTAMP, error_code = 'lease_expired',
                message = '工作进程中断，任务已重新排队', updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'processing' AND worker_id = ?
              AND attempt_count = ? AND lease_until = ?
            """, job.id(), job.workerId(), job.attemptCount(), job.leaseUntil());
        if (updated == 1) requeued.add(job.jobId());
      }
    }
    return new RecoveryResult(requeued, failed);
  }

  public List<String> findDueQueuedJobs(int limit) {
    return jdbcTemplate.queryForList("""
        SELECT job_id FROM search_record
        WHERE status = 'queued' AND job_id IS NOT NULL AND queued_at IS NOT NULL
          AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP)
        ORDER BY queued_at, id LIMIT ?
        """, String.class, Math.max(1, limit));
  }

  public List<JobRow> findUnadmittedJobs(int limit) {
    return jdbcTemplate.query("""
        SELECT id, job_id, file_sha256, upload_image_url, status, queued_at, sar_mode
        FROM search_record
        WHERE status = 'queued' AND job_id IS NOT NULL AND queued_at IS NULL
        ORDER BY id LIMIT ?
        """, (rs, rowNum) -> new JobRow(
        rs.getLong("id"), rs.getString("job_id"), rs.getString("file_sha256"),
        rs.getString("upload_image_url"), rs.getString("status"), false, rs.getBoolean("sar_mode")), Math.max(1, limit));
  }

  public boolean isActive(String jobId) {
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM search_record
        WHERE job_id = ? AND status IN ('queued', 'processing')
        """, Integer.class, jobId);
    return count != null && count > 0;
  }

  public List<String> findActiveJobIds() {
    return jdbcTemplate.queryForList("""
        SELECT job_id FROM search_record
        WHERE job_id IS NOT NULL AND status IN ('queued', 'processing')
        """, String.class);
  }

  public boolean deleteUnadmitted(long id) {
    return jdbcTemplate.update(
        "DELETE FROM search_record WHERE id = ? AND queued_at IS NULL", id) == 1;
  }

  private String toJson(List<SearchResult> results) {
    try {
      return objectMapper.writeValueAsString(results.stream()
          .map(SearchResultSnapshot::from)
          .toList());
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Top-5 结果序列化失败");
    }
  }

  private List<SearchResult> parseResults(String json) {
    try {
      return json == null ? List.of() : objectMapper.readValue(json, new TypeReference<>() {});
    } catch (JsonProcessingException ex) {
      return List.of();
    }
  }

  private LocalDateTime toLocalDateTime(Timestamp value) {
    return value == null ? null : value.toLocalDateTime();
  }

  public record JobRow(
      long id,
      String jobId,
      String fileSha256,
      String uploadImageUrl,
      String status,
      boolean admitted,
      boolean sarMode) {}
  public record JobOwnership(Long userId, String tokenHash) {}
  public record WorkerJob(long id, String jobId, String uploadImageUrl, int attemptCount, String workerId, boolean sarMode) {}
  public record RecoveryResult(List<String> requeuedJobIds, List<String> failedJobIds) {}
  private record ExpiredJob(long id, String jobId, int attemptCount, String workerId, LocalDateTime leaseUntil) {}

  private record SearchResultSnapshot(
      int rank,
      Long landmarkId,
      String landmarkCode,
      String name,
      String englishName,
      double score,
      String confidenceLevel,
      Double mahalanobisDistance,
      String summary,
      String locationText,
      double mapX,
      double mapY) {
    private static SearchResultSnapshot from(SearchResult result) {
      return new SearchResultSnapshot(
          result.rank(), result.landmarkId(), result.landmarkCode(), result.name(), result.englishName(),
          result.score(), result.confidenceLevel(), result.mahalanobisDistance(), result.summary(),
          result.locationText(), result.mapX(), result.mapY());
    }
  }

  private String combinedVersion(String base, String index, String sar) {
    if (base == null && index == null && sar == null) return null;
    return String.join("@", base == null ? "unknown" : base, index == null ? "unknown" : index,
        sar == null ? "baseline" : sar);
  }
}

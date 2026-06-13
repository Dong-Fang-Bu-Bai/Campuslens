package com.campuslens.service;

import com.campuslens.model.CorrectionSampleInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class CorrectionSampleService {
  private final JdbcTemplate jdbcTemplate;
  private final Path datasetDir;

  public CorrectionSampleService(
      JdbcTemplate jdbcTemplate,
      @Value("${campuslens.dataset-dir:../datasets/landmarks}") String datasetDir) {
    this.jdbcTemplate = jdbcTemplate;
    this.datasetDir = Path.of(datasetDir).toAbsolutePath().normalize();
  }

  public CorrectionSampleInfo createAndNotify(Long feedbackId) {
    CorrectionSampleInfo existing = findByFeedbackId(feedbackId);
    return existing == null ? createAndStage(feedbackId) : existing;
  }

  public CorrectionSampleInfo findByFeedbackId(Long feedbackId) {
    List<CorrectionSampleInfo> rows = jdbcTemplate.query("""
        SELECT id, sync_status, suggest_accept, review_score, reason,
               sar_eligible, next_action, created_at, updated_at
        FROM correction_sample WHERE feedback_id = ? ORDER BY id DESC LIMIT 1
        """, (rs, rowNum) -> new CorrectionSampleInfo(
        rs.getLong("id"), rs.getString("sync_status"),
        rs.getObject("suggest_accept") == null ? null : rs.getBoolean("suggest_accept"),
        rs.getObject("review_score") == null ? null : rs.getDouble("review_score"),
        rs.getString("reason"),
        rs.getObject("sar_eligible") == null ? null : rs.getBoolean("sar_eligible"),
        rs.getString("next_action"), rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at").toLocalDateTime()), feedbackId);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private CorrectionSampleInfo createAndStage(Long feedbackId) {
    Source source = source(feedbackId);
    Long confirmedId = source.confirmedLandmarkId() != null
        ? source.confirmedLandmarkId() : source.predictedLandmarkId();
    String confirmedCode = source.confirmedCode() != null
        ? source.confirmedCode() : source.predictedCode();
    if (confirmedId == null || confirmedCode == null) {
      throw new IllegalArgumentException("采纳反馈需要可确认的地标");
    }
    Path staged = stageImage(source.uploadImageUrl(), confirmedCode, feedbackId);
    KeyHolder keys = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO correction_sample (
            feedback_id, search_record_id, upload_image_url, predicted_landmark_id,
            confirmed_landmark_id, confirmed_landmark_code, source_feedback_type,
            top_results_json, sync_status, suggest_accept, reason, sar_eligible,
            next_action, dataset_path
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending_index', TRUE, ?, FALSE, 'rebuild_index', ?)
          """, new String[] {"id"});
      ps.setLong(1, feedbackId);
      ps.setLong(2, source.searchRecordId());
      ps.setString(3, source.uploadImageUrl());
      ps.setObject(4, source.predictedLandmarkId());
      ps.setLong(5, confirmedId);
      ps.setString(6, confirmedCode);
      ps.setString(7, source.feedbackType());
      ps.setString(8, source.topResultsJson());
      ps.setString(9, "样本已加入待发布数据集，不修改当前 SAR 状态");
      ps.setString(10, staged.toString());
      return ps;
    }, keys);
    Long id = Objects.requireNonNull(keys.getKey()).longValue();
    return requireById(id);
  }

  private Path stageImage(String uploadUrl, String code, Long feedbackId) {
    Path source = Path.of(uploadUrl.replaceFirst("^/", "")).toAbsolutePath().normalize();
    if (!Files.isRegularFile(source)) throw new IllegalArgumentException("反馈原图不存在，无法加入数据集");
    try {
      Files.createDirectories(datasetDir);
      Path landmarkDir;
      try (var folders = Files.list(datasetDir)) {
        landmarkDir = folders.filter(Files::isDirectory)
            .filter(path -> path.getFileName().toString().equals(code)
                || path.getFileName().toString().startsWith(code + "_"))
            .findFirst().orElse(datasetDir.resolve(code));
      }
      Path pending = landmarkDir.resolve("pending_index");
      Files.createDirectories(pending);
      String name = source.getFileName().toString();
      String extension = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".jpg";
      Path target = pending.resolve("feedback-" + feedbackId + extension).normalize();
      if (!target.startsWith(datasetDir)) throw new IllegalArgumentException("非法样本路径");
      Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
      return target;
    } catch (IOException ex) {
      throw new IllegalArgumentException("校正样本写入待发布目录失败", ex);
    }
  }

  private Source source(Long feedbackId) {
    return jdbcTemplate.query("""
        SELECT f.search_record_id, sr.upload_image_url, sr.top_results_json,
               f.predicted_landmark_id, p.code AS predicted_code,
               f.confirmed_landmark_id, c.code AS confirmed_code, f.feedback_type
        FROM feedback f
        JOIN search_record sr ON f.search_record_id = sr.id
        LEFT JOIN landmark p ON f.predicted_landmark_id = p.id
        LEFT JOIN landmark c ON f.confirmed_landmark_id = c.id
        WHERE f.id = ?
        """, (rs, rowNum) -> new Source(
        rs.getLong("search_record_id"), rs.getString("upload_image_url"),
        rs.getString("top_results_json"),
        rs.getObject("predicted_landmark_id") == null ? null : rs.getLong("predicted_landmark_id"),
        rs.getString("predicted_code"),
        rs.getObject("confirmed_landmark_id") == null ? null : rs.getLong("confirmed_landmark_id"),
        rs.getString("confirmed_code"), rs.getString("feedback_type")), feedbackId).stream()
        .findFirst().orElseThrow(() -> new IllegalArgumentException("反馈记录不存在"));
  }

  private CorrectionSampleInfo requireById(Long id) {
    return jdbcTemplate.query("""
        SELECT id, sync_status, suggest_accept, review_score, reason,
               sar_eligible, next_action, created_at, updated_at
        FROM correction_sample WHERE id = ?
        """, (rs, rowNum) -> new CorrectionSampleInfo(
        rs.getLong("id"), rs.getString("sync_status"), rs.getBoolean("suggest_accept"),
        null, rs.getString("reason"), rs.getBoolean("sar_eligible"),
        rs.getString("next_action"), rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at").toLocalDateTime()), id).stream().findFirst().orElseThrow();
  }

  public void markPublished(String indexVersion) {
    jdbcTemplate.update("""
        UPDATE correction_sample SET sync_status = 'published', published_index_version = ?,
          next_action = 'none' WHERE sync_status = 'pending_index'
        """, indexVersion);
  }

  private record Source(Long searchRecordId, String uploadImageUrl, String topResultsJson,
      Long predictedLandmarkId, String predictedCode, Long confirmedLandmarkId,
      String confirmedCode, String feedbackType) {}
}

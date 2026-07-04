package com.campuslens.service;

import com.campuslens.model.CorrectionSampleInfo;
import com.campuslens.model.SearchResult;
import com.campuslens.service.AlgorithmSearchClient.AdaptationRequest;
import com.campuslens.service.AlgorithmSearchClient.AdaptationResponse;
import com.campuslens.service.AlgorithmSearchClient.AdaptationTopResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class CorrectionSampleService {
  private static final Logger log = LoggerFactory.getLogger(CorrectionSampleService.class);
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final SearchRecordService searchRecordService;
  private final AlgorithmSearchClient algorithmSearchClient;
  private final Executor adaptationTaskExecutor;
  private final Path datasetDir;

  public CorrectionSampleService(
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper,
      SearchRecordService searchRecordService,
      AlgorithmSearchClient algorithmSearchClient,
      @Qualifier("adaptationTaskExecutor") Executor adaptationTaskExecutor,
      @Value("${campuslens.dataset-dir:../datasets/landmarks}") String datasetDir) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.searchRecordService = searchRecordService;
    this.algorithmSearchClient = algorithmSearchClient;
    this.adaptationTaskExecutor = adaptationTaskExecutor;
    this.datasetDir = Path.of(datasetDir).toAbsolutePath().normalize();
  }

  public void startEvaluation(Long feedbackId) {
    CorrectionSampleInfo existing = findByFeedbackId(feedbackId);
    CorrectionSampleInfo sample;
    try {
      sample = existing == null ? createEvaluation(feedbackId, "pending") : existing;
    } catch (IllegalArgumentException ex) {
      log.info("Feedback {} cannot be evaluated automatically: {}", feedbackId, ex.getMessage());
      return;
    }
    if (!"pending".equals(sample.evaluationStatus())) return;
    try {
      adaptationTaskExecutor.execute(() -> evaluate(sample.id()));
    } catch (RuntimeException ex) {
      markEvaluationFailed(sample.id(), "评估任务提交失败：" + ex.getMessage());
    }
  }

  public CorrectionSampleInfo stageAccepted(Long feedbackId) {
    CorrectionSampleInfo sample = findByFeedbackId(feedbackId);
    if (sample == null) {
      sample = createEvaluation(feedbackId, "legacy_unavailable");
    }
    SamplePayload payload = payload(sample.id());
    Path staged = stageImage(payload.uploadImageUrl(), payload.confirmedLandmarkCode(), feedbackId);
    jdbcTemplate.update("""
        UPDATE correction_sample
        SET sync_status = 'pending_index', dataset_path = ?, next_action = 'rebuild_index'
        WHERE id = ?
        """, staged.toString(), sample.id());
    return requireById(sample.id());
  }

  public CorrectionSampleInfo findByFeedbackId(Long feedbackId) {
    List<CorrectionSampleInfo> rows = jdbcTemplate.query("""
        SELECT id, sync_status, evaluation_status, suggest_accept, review_score, reason,
               sar_eligible, next_action, created_at, updated_at
        FROM correction_sample WHERE feedback_id = ? ORDER BY id DESC LIMIT 1
        """, (rs, rowNum) -> mapInfo(rs), feedbackId);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private CorrectionSampleInfo createEvaluation(Long feedbackId, String evaluationStatus) {
    Source source = source(feedbackId);
    Long confirmedId = source.confirmedLandmarkId() != null
        ? source.confirmedLandmarkId() : source.predictedLandmarkId();
    String confirmedCode = source.confirmedCode() != null
        ? source.confirmedCode() : source.predictedCode();
    if (confirmedId == null || confirmedCode == null) {
      throw new IllegalArgumentException("反馈缺少可评估的地标");
    }
    KeyHolder keys = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO correction_sample (
            feedback_id, search_record_id, upload_image_url, predicted_landmark_id,
            confirmed_landmark_id, confirmed_landmark_code, source_feedback_type,
            top_results_json, sync_status, evaluation_status, reason, next_action
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'not_staged', ?, ?, 'await_admin_decision')
          """, new String[] {"id"});
      ps.setLong(1, feedbackId);
      ps.setLong(2, source.searchRecordId());
      ps.setString(3, source.uploadImageUrl());
      ps.setObject(4, source.predictedLandmarkId());
      ps.setLong(5, confirmedId);
      ps.setString(6, confirmedCode);
      ps.setString(7, source.feedbackType());
      ps.setString(8, source.topResultsJson());
      ps.setString(9, evaluationStatus);
      ps.setString(10, "legacy_unavailable".equals(evaluationStatus) ? "旧记录未执行算法评估" : null);
      return ps;
    }, keys);
    return requireById(Objects.requireNonNull(keys.getKey()).longValue());
  }

  private void evaluate(Long sampleId) {
    try {
      SamplePayload payload = payload(sampleId);
      AdaptationResponse response = algorithmSearchClient.submitCorrectionSample(
          new AdaptationRequest(
              sampleId,
              payload.feedbackId(),
              payload.searchRecordId(),
              payload.uploadImageUrl(),
              payload.confirmedLandmarkCode(),
              payload.predictedLandmarkCode(),
              payload.feedbackType(),
              payload.comment(),
              payload.topResults().stream()
                  .map(item -> new AdaptationTopResult(
                      item.rank(), item.landmarkCode(), item.score(), item.mahalanobisDistance()))
                  .toList()),
          resolveUploadPath(payload.uploadImageUrl()));
      if (response == null) throw new AlgorithmSearchException("算法评估接口未返回结果");
      jdbcTemplate.update("""
          UPDATE correction_sample
          SET evaluation_status = 'completed', suggest_accept = ?, review_score = ?,
              reason = ?, sar_eligible = ?, algorithm_response_json = ?
          WHERE id = ?
          """,
          response.suggestAccept(), response.reviewScore(), response.reason(),
          response.sarEligible(), toJson(response), sampleId);
    } catch (Exception ex) {
      log.warn("Failed to evaluate correction sample {}", sampleId, ex);
      markEvaluationFailed(sampleId, ex.getMessage());
    }
  }

  private void markEvaluationFailed(Long sampleId, String reason) {
    jdbcTemplate.update("""
        UPDATE correction_sample SET evaluation_status = 'failed', reason = ? WHERE id = ?
        """, reason == null || reason.isBlank() ? "算法评估失败" : reason, sampleId);
  }

  private Path stageImage(String uploadUrl, String code, Long feedbackId) {
    Path source = resolveUploadPath(uploadUrl);
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

  private Path resolveUploadPath(String uploadUrl) {
    return Path.of(uploadUrl.replaceFirst("^/", "")).toAbsolutePath().normalize();
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

  private SamplePayload payload(Long sampleId) {
    return jdbcTemplate.queryForObject("""
        SELECT cs.feedback_id, cs.search_record_id, cs.upload_image_url,
               cs.confirmed_landmark_code, p.code AS predicted_code,
               cs.source_feedback_type, f.comment, cs.top_results_json
        FROM correction_sample cs
        JOIN feedback f ON cs.feedback_id = f.id
        LEFT JOIN landmark p ON cs.predicted_landmark_id = p.id
        WHERE cs.id = ?
        """, (rs, rowNum) -> new SamplePayload(
        rs.getLong("feedback_id"), rs.getLong("search_record_id"),
        rs.getString("upload_image_url"), rs.getString("confirmed_landmark_code"),
        rs.getString("predicted_code"), rs.getString("source_feedback_type"),
        rs.getString("comment"), searchRecordService.parseTopResults(rs.getString("top_results_json"))), sampleId);
  }

  private CorrectionSampleInfo requireById(Long id) {
    return jdbcTemplate.queryForObject("""
        SELECT id, sync_status, evaluation_status, suggest_accept, review_score, reason,
               sar_eligible, next_action, created_at, updated_at
        FROM correction_sample WHERE id = ?
        """, (rs, rowNum) -> mapInfo(rs), id);
  }

  private CorrectionSampleInfo mapInfo(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new CorrectionSampleInfo(
        rs.getLong("id"), rs.getString("sync_status"), rs.getString("evaluation_status"),
        rs.getObject("suggest_accept") == null ? null : rs.getBoolean("suggest_accept"),
        rs.getObject("review_score") == null ? null : rs.getDouble("review_score"),
        rs.getString("reason"),
        rs.getObject("sar_eligible") == null ? null : rs.getBoolean("sar_eligible"),
        rs.getString("next_action"), rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at").toLocalDateTime());
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      return "{}";
    }
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

  private record SamplePayload(Long feedbackId, Long searchRecordId, String uploadImageUrl,
      String confirmedLandmarkCode, String predictedLandmarkCode, String feedbackType,
      String comment, List<SearchResult> topResults) {}
}

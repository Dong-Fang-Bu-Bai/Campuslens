package com.campuslens.service;

import com.campuslens.model.CorrectionSampleInfo;
import com.campuslens.model.SearchResult;
import com.campuslens.service.AlgorithmSearchClient.AdaptationRequest;
import com.campuslens.service.AlgorithmSearchClient.AdaptationResponse;
import com.campuslens.service.AlgorithmSearchClient.AdaptationTopResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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

  public CorrectionSampleService(
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper,
      SearchRecordService searchRecordService,
      AlgorithmSearchClient algorithmSearchClient,
      @Qualifier("adaptationTaskExecutor") Executor adaptationTaskExecutor) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.searchRecordService = searchRecordService;
    this.algorithmSearchClient = algorithmSearchClient;
    this.adaptationTaskExecutor = adaptationTaskExecutor;
  }

  public CorrectionSampleInfo createAndNotify(Long feedbackId) {
    CorrectionSampleInfo existing = findByFeedbackId(feedbackId);
    CorrectionSampleInfo sample = existing == null ? create(feedbackId) : existing;
    if (!"synced".equals(sample.syncStatus())) {
      Long sampleId = sample.id();
      adaptationTaskExecutor.execute(() -> notifyAlgorithm(sampleId));
    }
    return sample;
  }

  public CorrectionSampleInfo findByFeedbackId(Long feedbackId) {
    List<CorrectionSampleInfo> rows = jdbcTemplate.query("""
        SELECT id, sync_status, suggest_accept, review_score, reason,
               sar_eligible, next_action, created_at, updated_at
        FROM correction_sample
        WHERE feedback_id = ?
        ORDER BY id DESC
        LIMIT 1
        """, (rs, rowNum) -> new CorrectionSampleInfo(
        rs.getLong("id"),
        rs.getString("sync_status"),
        rs.getObject("suggest_accept") == null ? null : rs.getBoolean("suggest_accept"),
        rs.getObject("review_score") == null ? null : rs.getDouble("review_score"),
        rs.getString("reason"),
        rs.getObject("sar_eligible") == null ? null : rs.getBoolean("sar_eligible"),
        rs.getString("next_action"),
        rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at").toLocalDateTime()), feedbackId);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private CorrectionSampleInfo create(Long feedbackId) {
    FeedbackSource source = source(feedbackId);
    Long confirmedId = source.confirmedLandmarkId() != null
        ? source.confirmedLandmarkId()
        : source.predictedLandmarkId();
    String confirmedCode = source.confirmedLandmarkCode() != null
        ? source.confirmedLandmarkCode()
        : source.predictedLandmarkCode();
    if (confirmedId == null || confirmedCode == null) {
      throw new IllegalArgumentException("采纳反馈需要可确认的地标");
    }
    KeyHolder keyHolder = new GeneratedKeyHolder();
    Long finalConfirmedId = confirmedId;
    String finalConfirmedCode = confirmedCode;
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO correction_sample (
            feedback_id, search_record_id, upload_image_url, predicted_landmark_id,
            confirmed_landmark_id, confirmed_landmark_code, source_feedback_type, top_results_json,
            sync_status
          )
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'sync_pending')
          """, new String[] {"id"});
      ps.setLong(1, feedbackId);
      ps.setLong(2, source.searchRecordId());
      ps.setString(3, source.uploadImageUrl());
      ps.setObject(4, source.predictedLandmarkId());
      ps.setLong(5, finalConfirmedId);
      ps.setString(6, finalConfirmedCode);
      ps.setString(7, source.feedbackType());
      ps.setString(8, source.topResultsJson());
      return ps;
    }, keyHolder);
    Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
    return requireById(id);
  }

  private void notifyAlgorithm(Long sampleId) {
    try {
      CorrectionPayload payload = payload(sampleId);
      AdaptationResponse response = algorithmSearchClient.submitCorrectionSample(new AdaptationRequest(
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
                  item.rank(),
                  item.landmarkCode(),
                  item.score(),
                  item.mahalanobisDistance()))
              .toList()));
      if (response == null) {
        throw new AlgorithmSearchException("算法校正接口未返回结果");
      }
      jdbcTemplate.update("""
          UPDATE correction_sample
          SET sync_status = 'synced',
              suggest_accept = ?,
              review_score = ?,
              reason = ?,
              sar_eligible = ?,
              next_action = ?,
              algorithm_response_json = ?
          WHERE id = ?
          """,
          response.suggestAccept(),
          response.reviewScore(),
          response.reason(),
          response.sarEligible(),
          response.nextAction(),
          toJson(response),
          sampleId);
    } catch (Exception ex) {
      log.warn("Failed to notify algorithm adaptation endpoint for sample {}", sampleId, ex);
      jdbcTemplate.update("""
          UPDATE correction_sample
          SET sync_status = 'sync_failed',
              reason = ?
          WHERE id = ?
          """, ex.getMessage(), sampleId);
    }
  }

  private FeedbackSource source(Long feedbackId) {
    List<FeedbackSource> rows = jdbcTemplate.query("""
        SELECT f.search_record_id, sr.upload_image_url, sr.top_results_json,
               f.predicted_landmark_id, p.code AS predicted_code,
               f.confirmed_landmark_id, c.code AS confirmed_code,
               f.feedback_type
        FROM feedback f
        JOIN search_record sr ON f.search_record_id = sr.id
        LEFT JOIN landmark p ON f.predicted_landmark_id = p.id
        LEFT JOIN landmark c ON f.confirmed_landmark_id = c.id
        WHERE f.id = ?
        """, (rs, rowNum) -> new FeedbackSource(
        rs.getLong("search_record_id"),
        rs.getString("upload_image_url"),
        rs.getString("top_results_json"),
        rs.getObject("predicted_landmark_id") == null ? null : rs.getLong("predicted_landmark_id"),
        rs.getString("predicted_code"),
        rs.getObject("confirmed_landmark_id") == null ? null : rs.getLong("confirmed_landmark_id"),
        rs.getString("confirmed_code"),
        rs.getString("feedback_type")), feedbackId);
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("反馈记录不存在");
    }
    return rows.get(0);
  }

  private CorrectionPayload payload(Long sampleId) {
    return jdbcTemplate.queryForObject("""
        SELECT cs.feedback_id, cs.search_record_id, cs.upload_image_url,
               cs.confirmed_landmark_code, pp.code AS predicted_code,
               cs.source_feedback_type, f.comment, cs.top_results_json
        FROM correction_sample cs
        JOIN feedback f ON cs.feedback_id = f.id
        LEFT JOIN landmark pp ON cs.predicted_landmark_id = pp.id
        WHERE cs.id = ?
        """, (rs, rowNum) -> new CorrectionPayload(
        rs.getLong("feedback_id"),
        rs.getLong("search_record_id"),
        rs.getString("upload_image_url"),
        rs.getString("confirmed_landmark_code"),
        rs.getString("predicted_code"),
        rs.getString("source_feedback_type"),
        rs.getString("comment"),
        searchRecordService.parseTopResults(rs.getString("top_results_json"))), sampleId);
  }

  private CorrectionSampleInfo requireById(Long id) {
    return jdbcTemplate.queryForObject("""
        SELECT id, sync_status, suggest_accept, review_score, reason,
               sar_eligible, next_action, created_at, updated_at
        FROM correction_sample
        WHERE id = ?
        """, (rs, rowNum) -> new CorrectionSampleInfo(
        rs.getLong("id"),
        rs.getString("sync_status"),
        rs.getObject("suggest_accept") == null ? null : rs.getBoolean("suggest_accept"),
        rs.getObject("review_score") == null ? null : rs.getDouble("review_score"),
        rs.getString("reason"),
        rs.getObject("sar_eligible") == null ? null : rs.getBoolean("sar_eligible"),
        rs.getString("next_action"),
        rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at").toLocalDateTime()), id);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      return "{}";
    }
  }

  private record FeedbackSource(
      Long searchRecordId,
      String uploadImageUrl,
      String topResultsJson,
      Long predictedLandmarkId,
      String predictedLandmarkCode,
      Long confirmedLandmarkId,
      String confirmedLandmarkCode,
      String feedbackType) {
  }

  private record CorrectionPayload(
      Long feedbackId,
      Long searchRecordId,
      String uploadImageUrl,
      String confirmedLandmarkCode,
      String predictedLandmarkCode,
      String feedbackType,
      String comment,
      List<SearchResult> topResults) {
  }
}

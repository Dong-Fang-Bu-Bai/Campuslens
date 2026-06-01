package com.campuslens.service;

import com.campuslens.model.AdminSearchRecord;
import com.campuslens.model.SearchResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class SearchRecordService {
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public SearchRecordService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public long create(
      String uploadImageUrl,
      List<SearchResult> results,
      boolean lowConfidence,
      String message,
      String status,
      String guestId) {
    SearchResult best = results.isEmpty() ? null : results.get(0);
    String topResultsJson = toJson(results);
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO search_record (
            upload_image_url, top_results_json, best_landmark_id, best_score,
            status, low_confidence, message, guest_id
          )
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          """, new String[] {"id"});
      ps.setString(1, uploadImageUrl);
      ps.setString(2, topResultsJson);
      if (best == null) {
        ps.setObject(3, null);
        ps.setObject(4, null);
      } else {
        ps.setLong(3, best.landmarkId());
        ps.setDouble(4, best.score());
      }
      ps.setString(5, status);
      ps.setBoolean(6, lowConfidence);
      ps.setString(7, message);
      ps.setString(8, guestId == null || guestId.isBlank() ? "guest" : guestId);
      return ps;
    }, keyHolder);
    Number key = Objects.requireNonNull(keyHolder.getKey(), "search_record id not generated");
    return key.longValue();
  }

  public boolean exists(Long searchRecordId) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM search_record WHERE id = ?",
        Integer.class,
        searchRecordId);
    return count != null && count > 0;
  }

  public boolean containsResult(Long searchRecordId, Long landmarkId) {
    if (landmarkId == null) {
      return true;
    }
    String json = jdbcTemplate.queryForObject(
        "SELECT top_results_json FROM search_record WHERE id = ?",
        String.class,
        searchRecordId);
    if (json == null || json.isBlank()) {
      return false;
    }
    try {
      JsonNode root = objectMapper.readTree(json);
      if (!root.isArray()) {
        return false;
      }
      for (JsonNode node : root) {
        if (node.path("landmarkId").asLong(-1) == landmarkId) {
          return true;
        }
      }
      return false;
    } catch (JsonProcessingException ex) {
      return false;
    }
  }

  public List<AdminSearchRecord> listRecent() {
    return jdbcTemplate.query("""
        SELECT sr.id, sr.upload_image_url, l.name AS best_landmark_name, sr.best_score,
               sr.status, sr.low_confidence, sr.message, sr.guest_id, sr.created_at
        FROM search_record sr
        LEFT JOIN landmark l ON sr.best_landmark_id = l.id
        ORDER BY sr.created_at DESC, sr.id DESC
        LIMIT 50
        """, (rs, rowNum) -> new AdminSearchRecord(
        rs.getLong("id"),
        rs.getString("upload_image_url"),
        rs.getString("best_landmark_name"),
        rs.getObject("best_score") == null ? null : rs.getDouble("best_score"),
        rs.getString("status"),
        rs.getBoolean("low_confidence"),
        rs.getString("message"),
        rs.getString("guest_id"),
        rs.getTimestamp("created_at").toLocalDateTime()));
  }

  private String toJson(List<SearchResult> results) {
    try {
      return objectMapper.writeValueAsString(results);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Top-5 结果快照序列化失败");
    }
  }
}

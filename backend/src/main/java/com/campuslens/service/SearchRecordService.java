package com.campuslens.service;

import com.campuslens.model.AdminSearchRecord;
import com.campuslens.model.SearchResult;
import com.campuslens.model.UserSearchRecord;
import com.fasterxml.jackson.core.type.TypeReference;
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
  private final GuestIdentityService guestIdentityService;

  public SearchRecordService(
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper,
      GuestIdentityService guestIdentityService) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.guestIdentityService = guestIdentityService;
  }

  public SearchRecordCreation create(
      String uploadImageUrl,
      List<SearchResult> results,
      boolean lowConfidence,
      String message,
      String status,
      Long userId,
      String guestId) {
    SearchResult best = results.isEmpty() ? null : results.get(0);
    String topResultsJson = toJson(results);
    String storedGuestId = userId == null
        ? guestIdentityService.requireExisting(guestId)
        : "user-" + userId;
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO search_record (
            upload_image_url, top_results_json, best_landmark_id, best_score,
            status, low_confidence, message, guest_id, user_id, user_type
          )
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
      ps.setString(8, storedGuestId);
      ps.setObject(9, userId);
      ps.setString(10, userId == null ? "guest" : "user");
      return ps;
    }, keyHolder);
    Number key = Objects.requireNonNull(keyHolder.getKey(), "search_record id not generated");
    return new SearchRecordCreation(key.longValue(), storedGuestId);
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

  public FeedbackTarget feedbackTarget(Long searchRecordId) {
    return jdbcTemplate.query("""
        SELECT status, user_id, guest_id FROM search_record WHERE id = ?
        """, (rs, rowNum) -> new FeedbackTarget(
        rs.getString("status"),
        rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
        rs.getString("guest_id")), searchRecordId).stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("searchRecordId 对应的检索记录不存在"));
  }

  public CheckInSource checkInSource(Long searchRecordId) {
    return jdbcTemplate.query("""
        SELECT status, user_id, guest_id, upload_image_url
        FROM search_record
        WHERE id = ?
        """, (rs, rowNum) -> new CheckInSource(
        rs.getString("status"),
        rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
        rs.getString("guest_id"),
        rs.getString("upload_image_url")), searchRecordId).stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("searchRecordId 对应的检索记录不存在"));
  }

  public List<AdminSearchRecord> listRecent() {
    return jdbcTemplate.query("""
        SELECT sr.id, sr.upload_image_url, l.name AS best_landmark_name, sr.best_score,
               sr.status, sr.low_confidence, sr.message, sr.guest_id,
               sr.user_id, u.username, sr.user_type, sr.created_at
        FROM search_record sr
        LEFT JOIN landmark l ON sr.best_landmark_id = l.id
        LEFT JOIN app_user u ON sr.user_id = u.id
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
        rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
        rs.getString("username"),
        rs.getString("user_type"),
        rs.getTimestamp("created_at").toLocalDateTime()));
  }

  public List<UserSearchRecord> listForUser(Long userId, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 50));
    return jdbcTemplate.query("""
        SELECT sr.id, sr.upload_image_url, l.name AS best_landmark_name, sr.best_score,
               sr.status, sr.low_confidence, sr.message, sr.top_results_json,
               (
                 SELECT f.status
                 FROM feedback f
                 WHERE f.search_record_id = sr.id
                 ORDER BY f.updated_at DESC, f.id DESC
                 LIMIT 1
               ) AS feedback_status,
               sr.created_at
        FROM search_record sr
        LEFT JOIN landmark l ON sr.best_landmark_id = l.id
        WHERE sr.user_id = ?
        ORDER BY sr.created_at DESC, sr.id DESC
        LIMIT ?
        """, (rs, rowNum) -> new UserSearchRecord(
        rs.getLong("id"),
        rs.getString("upload_image_url"),
        rs.getString("best_landmark_name"),
        rs.getObject("best_score") == null ? null : rs.getDouble("best_score"),
        rs.getString("status"),
        rs.getBoolean("low_confidence"),
        rs.getString("message"),
        rs.getString("feedback_status"),
        parseTopResults(rs.getString("top_results_json")),
        rs.getTimestamp("created_at").toLocalDateTime()), userId, safeLimit);
  }

  public List<SearchResult> topResults(Long searchRecordId) {
    String json = jdbcTemplate.queryForObject(
        "SELECT top_results_json FROM search_record WHERE id = ?",
        String.class,
        searchRecordId);
    return parseTopResults(json);
  }

  public List<SearchResult> parseTopResults(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<List<SearchResult>>() {});
    } catch (JsonProcessingException ex) {
      return List.of();
    }
  }

  private String toJson(List<SearchResult> results) {
    try {
      return objectMapper.writeValueAsString(results.stream()
          .map(SearchResultSnapshot::from)
          .toList());
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Top-5 结果快照序列化失败");
    }
  }

  public record SearchRecordCreation(Long id, String guestId) {
  }

  public record FeedbackTarget(String status, Long userId, String guestId) {}

  public record CheckInSource(String status, Long userId, String guestId, String uploadImageUrl) {}

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
          result.rank(),
          result.landmarkId(),
          result.landmarkCode(),
          result.name(),
          result.englishName(),
          result.score(),
          result.confidenceLevel(),
          result.mahalanobisDistance(),
          result.summary(),
          result.locationText(),
          result.mapX(),
          result.mapY());
    }
  }
}

package com.campuslens.service;

import com.campuslens.model.AdminFeedbackRecord;
import com.campuslens.model.FeedbackRecord;
import com.campuslens.model.FeedbackRequest;
import com.campuslens.model.FeedbackResponse;
import com.campuslens.model.FeedbackStatusRequest;
import com.campuslens.model.SessionUser;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class FeedbackService {
  private static final Set<String> FEEDBACK_TYPES = Set.of("correct", "wrong", "uncertain");
  private static final Set<String> FEEDBACK_STATUSES = Set.of("pending", "accepted", "ignored");
  private final JdbcTemplate jdbcTemplate;
  private final SearchRecordService searchRecordService;
  private final AuthService authService;

  public FeedbackService(
      JdbcTemplate jdbcTemplate,
      SearchRecordService searchRecordService,
      AuthService authService) {
    this.jdbcTemplate = jdbcTemplate;
    this.searchRecordService = searchRecordService;
    this.authService = authService;
  }

  public FeedbackResponse submit(FeedbackRequest request, SessionUser user) {
    validate(request);
    Long userId = user == null ? null : activeUserId(user.userId());
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO feedback (
            search_record_id, predicted_landmark_id, confirmed_landmark_id, user_id,
            feedback_type, comment, status
          )
          VALUES (?, ?, ?, ?, ?, ?, 'pending')
          """, new String[] {"id"});
      ps.setLong(1, request.searchRecordId());
      ps.setObject(2, request.predictedLandmarkId());
      ps.setObject(3, request.confirmedLandmarkId());
      ps.setObject(4, userId);
      ps.setString(5, request.feedbackType());
      ps.setString(6, request.comment());
      return ps;
    }, keyHolder);
    Number key = Objects.requireNonNull(keyHolder.getKey(), "feedback id not generated");
    return new FeedbackResponse(key.longValue(), "pending", "反馈已落库，等待管理员审核");
  }

  public List<AdminFeedbackRecord> listRecent() {
    return jdbcTemplate.query("""
        SELECT f.id, f.search_record_id, f.predicted_landmark_id, p.name AS predicted_name,
               f.confirmed_landmark_id, c.name AS confirmed_name, f.user_id,
               u.username, f.feedback_type,
               f.comment, f.status, f.created_at, f.updated_at
        FROM feedback f
        LEFT JOIN landmark p ON f.predicted_landmark_id = p.id
        LEFT JOIN landmark c ON f.confirmed_landmark_id = c.id
        LEFT JOIN app_user u ON f.user_id = u.id
        ORDER BY f.created_at DESC, f.id DESC
        LIMIT 50
        """, (rs, rowNum) -> new AdminFeedbackRecord(
        rs.getLong("id"),
        rs.getLong("search_record_id"),
        rs.getObject("predicted_landmark_id") == null ? null : rs.getLong("predicted_landmark_id"),
        rs.getString("predicted_name"),
        rs.getObject("confirmed_landmark_id") == null ? null : rs.getLong("confirmed_landmark_id"),
        rs.getString("confirmed_name"),
        rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
        rs.getString("username"),
        rs.getString("feedback_type"),
        rs.getString("comment"),
        rs.getString("status"),
        rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at").toLocalDateTime()));
  }

  public List<FeedbackRecord> listBySearchRecordId(Long searchRecordId) {
    return jdbcTemplate.query("""
        SELECT id, search_record_id, predicted_landmark_id, confirmed_landmark_id,
               feedback_type, comment, status, created_at
        FROM feedback
        WHERE search_record_id = ?
        ORDER BY created_at DESC
        """, (rs, rowNum) -> new FeedbackRecord(
        rs.getLong("id"),
        rs.getLong("search_record_id"),
        (Long) rs.getObject("predicted_landmark_id"),
        (Long) rs.getObject("confirmed_landmark_id"),
        rs.getString("feedback_type"),
        rs.getString("comment"),
        rs.getString("status"),
        rs.getTimestamp("created_at").toLocalDateTime()),
        searchRecordId);
  }

  public FeedbackResponse updateStatus(Long id, FeedbackStatusRequest request) {
    if (!FEEDBACK_STATUSES.contains(request.status())) {
      throw new IllegalArgumentException("反馈状态只能为 pending、accepted 或 ignored");
    }
    int updated = jdbcTemplate.update(
        "UPDATE feedback SET status = ? WHERE id = ?",
        request.status(),
        id);
    if (updated == 0) {
      throw new IllegalArgumentException("反馈记录不存在");
    }
    return new FeedbackResponse(id, request.status(), "反馈状态已更新");
  }

  private void validate(FeedbackRequest request) {
    if (!FEEDBACK_TYPES.contains(request.feedbackType())) {
      throw new IllegalArgumentException("feedbackType 只能为 correct、wrong 或 uncertain");
    }
    if (!searchRecordService.exists(request.searchRecordId())) {
      throw new IllegalArgumentException("searchRecordId 对应的检索记录不存在");
    }
    if (("correct".equals(request.feedbackType()) || "wrong".equals(request.feedbackType()))
        && request.predictedLandmarkId() == null) {
      throw new IllegalArgumentException("correct 和 wrong 反馈需要提供 predictedLandmarkId");
    }
    if (request.predictedLandmarkId() != null
        && !searchRecordService.containsResult(request.searchRecordId(), request.predictedLandmarkId())) {
      throw new IllegalArgumentException("predictedLandmarkId 必须来自本次检索结果");
    }
    if ("wrong".equals(request.feedbackType()) && request.confirmedLandmarkId() == null) {
      throw new IllegalArgumentException("识别错误反馈需要提供 confirmedLandmarkId");
    }
  }

  private Long activeUserId(Long userId) {
    if (userId == null) {
      return null;
    }
    if (!authService.isActiveUser(userId)) {
      throw new IllegalArgumentException("用户不存在或已停用");
    }
    return userId;
  }
}

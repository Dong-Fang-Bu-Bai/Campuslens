package com.campuslens.service;

import com.campuslens.model.AdminFeedbackDetail;
import com.campuslens.model.AdminFeedbackRecord;
import com.campuslens.model.CorrectionSampleInfo;
import com.campuslens.model.AuditLog;
import com.campuslens.model.FeedbackRecord;
import com.campuslens.model.FeedbackRequest;
import com.campuslens.model.FeedbackResponse;
import com.campuslens.model.FeedbackStats;
import com.campuslens.model.FeedbackStats.DailyTrend;
import com.campuslens.model.FeedbackStatusRequest;
import com.campuslens.model.SessionUser;
import com.campuslens.repository.AuditLogRepository;
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
  private final CorrectionSampleService correctionSampleService;
  private final AuditLogRepository auditLogRepository;

  public FeedbackService(
      JdbcTemplate jdbcTemplate,
      SearchRecordService searchRecordService,
      AuthService authService,
      CorrectionSampleService correctionSampleService,
      AuditLogRepository auditLogRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.searchRecordService = searchRecordService;
    this.authService = authService;
    this.correctionSampleService = correctionSampleService;
    this.auditLogRepository = auditLogRepository;
  }

  public FeedbackResponse submit(FeedbackRequest request, SessionUser user) {
    Long userId = user == null ? null : activeUserId(user.userId());
    validate(request, userId);
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
    // Get old status for audit log
    String oldStatus = jdbcTemplate.queryForObject(
        "SELECT status FROM feedback WHERE id = ?", String.class, id);
    if (oldStatus == null) {
      throw new IllegalArgumentException("反馈记录不存在");
    }

    int updated = jdbcTemplate.update(
        "UPDATE feedback SET status = ? WHERE id = ?",
        request.status(), id);
    if (updated == 0) {
      throw new IllegalArgumentException("反馈记录不存在");
    }

    // Record audit log
    auditLogRepository.save(id, "status_change", oldStatus, request.status(),
        null, null);

    if ("accepted".equals(request.status())) {
      correctionSampleService.createAndNotify(id);
      return new FeedbackResponse(id, request.status(), "反馈已采纳，图片已加入待发布样本，等待索引重建");
    }
    return new FeedbackResponse(id, request.status(), "反馈状态已更新");
  }

  public AdminFeedbackDetail detail(Long id) {
    List<AdminFeedbackDetail> rows = jdbcTemplate.query("""
        SELECT f.id, f.search_record_id, sr.upload_image_url, sr.top_results_json, sr.guest_id,
               f.predicted_landmark_id, p.name AS predicted_name,
               f.confirmed_landmark_id, c.name AS confirmed_name, f.user_id,
               u.username, f.feedback_type,
               f.comment, f.status, f.created_at, f.updated_at
        FROM feedback f
        JOIN search_record sr ON f.search_record_id = sr.id
        LEFT JOIN landmark p ON f.predicted_landmark_id = p.id
        LEFT JOIN landmark c ON f.confirmed_landmark_id = c.id
        LEFT JOIN app_user u ON f.user_id = u.id
        WHERE f.id = ?
        """, (rs, rowNum) -> {
      CorrectionSampleInfo sample = correctionSampleService.findByFeedbackId(rs.getLong("id"));
      return new AdminFeedbackDetail(
          rs.getLong("id"),
          rs.getLong("search_record_id"),
          rs.getString("upload_image_url"),
          rs.getObject("predicted_landmark_id") == null ? null : rs.getLong("predicted_landmark_id"),
          rs.getString("predicted_name"),
          rs.getObject("confirmed_landmark_id") == null ? null : rs.getLong("confirmed_landmark_id"),
          rs.getString("confirmed_name"),
          rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
          rs.getString("username"),
          rs.getString("guest_id"),
          rs.getString("feedback_type"),
          rs.getString("comment"),
          rs.getString("status"),
          searchRecordService.parseTopResults(rs.getString("top_results_json")),
          sample,
          rs.getTimestamp("created_at").toLocalDateTime(),
          rs.getTimestamp("updated_at").toLocalDateTime());
    }, id);
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("反馈记录不存在");
    }
    return rows.get(0);
  }

  public List<AuditLog> listAuditLogs(Long feedbackId) {
    return auditLogRepository.findByFeedbackId(feedbackId);
  }

  public FeedbackStats getStats() {
    // Aggregate counts
    var row = jdbcTemplate.queryForMap("""
        SELECT
          COUNT(*) AS total,
          SUM(CASE WHEN feedback_type = 'correct' THEN 1 ELSE 0 END) AS correct,
          SUM(CASE WHEN feedback_type = 'wrong' THEN 1 ELSE 0 END) AS wrong,
          SUM(CASE WHEN feedback_type = 'uncertain' THEN 1 ELSE 0 END) AS uncertain,
          SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) AS pending,
          SUM(CASE WHEN status = 'accepted' THEN 1 ELSE 0 END) AS accepted,
          SUM(CASE WHEN status = 'ignored' THEN 1 ELSE 0 END) AS ignored
        FROM feedback
        """);

    long total = ((Number) row.get("total")).longValue();
    long correct = ((Number) row.get("correct")).longValue();
    long wrong = ((Number) row.get("wrong")).longValue();
    long uncertain = ((Number) row.get("uncertain")).longValue();
    long pending = ((Number) row.get("pending")).longValue();
    long accepted = ((Number) row.get("accepted")).longValue();
    long ignored = ((Number) row.get("ignored")).longValue();
    double accuracy = total > 0 ? Math.round(correct * 10000.0 / total) / 100.0 : 0;

    // Daily trend for last 7 days
    List<DailyTrend> trend = jdbcTemplate.query("""
        SELECT DATE(created_at) AS dt, COUNT(*) AS cnt
        FROM feedback
        WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
        GROUP BY DATE(created_at)
        ORDER BY dt
        """, (rs, rowNum) -> new DailyTrend(
        rs.getString("dt"),
        rs.getLong("cnt")));

    return new FeedbackStats(total, correct, wrong, uncertain,
        pending, accepted, ignored, accuracy, trend);
  }

  private void validate(FeedbackRequest request, Long userId) {
    if (!FEEDBACK_TYPES.contains(request.feedbackType())) {
      throw new IllegalArgumentException("feedbackType 只能为 correct、wrong 或 uncertain");
    }
    SearchRecordService.FeedbackTarget target = searchRecordService.feedbackTarget(request.searchRecordId());
    if (!Set.of("success", "low_confidence").contains(target.status())) {
      throw new IllegalArgumentException("只有成功或低置信度任务允许提交反馈");
    }
    if (target.userId() != null && !target.userId().equals(userId)) {
      throw new AuthRequiredException("无权对该检索任务提交反馈");
    }
    if (target.userId() == null
        && (request.guestId() == null || !target.guestId().equals(request.guestId().trim()))) {
      throw new AuthRequiredException("游客身份与检索任务不匹配");
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

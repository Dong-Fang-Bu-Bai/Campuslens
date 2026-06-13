package com.campuslens.service;

import com.campuslens.model.CheckInRecord;
import com.campuslens.model.CheckInReply;
import com.campuslens.model.CheckInReplyRequest;
import com.campuslens.model.CheckInRequest;
import com.campuslens.model.LikeResponse;
import com.campuslens.model.SessionUser;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class CheckInService {
  private final JdbcTemplate jdbcTemplate;
  private final GuestIdentityService guestIdentityService;

  public CheckInService(JdbcTemplate jdbcTemplate, GuestIdentityService guestIdentityService) {
    this.jdbcTemplate = jdbcTemplate;
    this.guestIdentityService = guestIdentityService;
  }

  public List<CheckInRecord> list(Long landmarkId, int limit, SessionUser user, String guestId) {
    int safeLimit = Math.max(1, Math.min(limit, 100));
    String viewerGuestId = user == null ? guestIdentityService.optionalExisting(guestId) : null;
    String sql = """
        SELECT ci.id, ci.landmark_id, l.code, l.name, l.location_text, l.map_x, l.map_y,
               ci.user_id, ci.guest_id, ci.display_name, ci.message,
               ci.like_count, ci.reply_count, ci.created_at,
               CASE WHEN EXISTS (
                 SELECT 1 FROM check_in_like cil
                 WHERE cil.check_in_id = ci.id
                   AND ((? IS NOT NULL AND cil.user_id = ?) OR (? IS NOT NULL AND cil.guest_id = ?))
               ) THEN TRUE ELSE FALSE END AS liked_by_me
        FROM check_in ci
        JOIN landmark l ON ci.landmark_id = l.id
        WHERE ci.status = 'visible'
        """ + (landmarkId == null ? "" : " AND ci.landmark_id = ? ") + """
        ORDER BY ci.created_at DESC, ci.id DESC
        LIMIT ?
        """;
    Object[] args = landmarkId == null
        ? new Object[] {user == null ? null : user.userId(), user == null ? null : user.userId(), viewerGuestId, viewerGuestId, safeLimit}
        : new Object[] {user == null ? null : user.userId(), user == null ? null : user.userId(), viewerGuestId, viewerGuestId, landmarkId, safeLimit};
    return jdbcTemplate.query(sql, (rs, rowNum) -> new CheckInRecord(
        rs.getLong("id"),
        rs.getLong("landmark_id"),
        rs.getString("code"),
        rs.getString("name"),
        rs.getString("location_text"),
        rs.getObject("map_x") == null ? null : rs.getDouble("map_x"),
        rs.getObject("map_y") == null ? null : rs.getDouble("map_y"),
        rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
        rs.getString("guest_id"),
        rs.getString("display_name"),
        rs.getString("message"),
        rs.getInt("like_count"),
        rs.getInt("reply_count"),
        rs.getBoolean("liked_by_me"),
        listReplies(rs.getLong("id")),
        rs.getTimestamp("created_at").toLocalDateTime()), args);
  }

  public CheckInRecord create(CheckInRequest request, SessionUser user) {
    ensureLandmarkExists(request.landmarkId());
    String guestId = user == null ? guestIdentityService.requireExisting(request.guestId()) : null;
    String displayName = user == null ? guestId : user.username();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO check_in (landmark_id, user_id, guest_id, display_name, message)
          VALUES (?, ?, ?, ?, ?)
          """, new String[] {"id"});
      ps.setLong(1, request.landmarkId());
      ps.setObject(2, user == null ? null : user.userId());
      ps.setString(3, guestId);
      ps.setString(4, displayName);
      ps.setString(5, request.message().trim());
      return ps;
    }, keyHolder);
    Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
    return find(id, user, guestId);
  }

  public LikeResponse toggleLike(Long checkInId, SessionUser user, String guestId) {
    ensureCheckInExists(checkInId);
    Long userId = user == null ? null : user.userId();
    String normalizedGuestId = user == null ? guestIdentityService.requireExisting(guestId) : null;
    Long likeId = findLikeId(checkInId, userId, normalizedGuestId);
    boolean liked;
    if (likeId == null) {
      jdbcTemplate.update(
          "INSERT INTO check_in_like (check_in_id, user_id, guest_id) VALUES (?, ?, ?)",
          checkInId,
          userId,
          normalizedGuestId);
      liked = true;
    } else {
      jdbcTemplate.update("DELETE FROM check_in_like WHERE id = ?", likeId);
      liked = false;
    }
    int count = refreshLikeCount(checkInId);
    return new LikeResponse(checkInId, liked, count);
  }

  public CheckInReply addReply(Long checkInId, CheckInReplyRequest request, SessionUser user) {
    ensureCheckInExists(checkInId);
    String guestId = user == null ? guestIdentityService.requireExisting(request.guestId()) : null;
    String displayName = user == null ? guestId : user.username();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO check_in_reply (check_in_id, user_id, guest_id, display_name, message)
          VALUES (?, ?, ?, ?, ?)
          """, new String[] {"id"});
      ps.setLong(1, checkInId);
      ps.setObject(2, user == null ? null : user.userId());
      ps.setString(3, guestId);
      ps.setString(4, displayName);
      ps.setString(5, request.message().trim());
      return ps;
    }, keyHolder);
    refreshReplyCount(checkInId);
    Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
    return jdbcTemplate.queryForObject("""
        SELECT id, check_in_id, user_id, guest_id, display_name, message, created_at
        FROM check_in_reply
        WHERE id = ?
        """, (rs, rowNum) -> replyFromRow(rs), id);
  }

  private CheckInRecord find(Long id, SessionUser user, String guestId) {
    return list(null, 100, user, guestId).stream()
        .filter(item -> item.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("打卡记录不存在"));
  }

  private List<CheckInReply> listReplies(Long checkInId) {
    return jdbcTemplate.query("""
        SELECT id, check_in_id, user_id, guest_id, display_name, message, created_at
        FROM check_in_reply
        WHERE check_in_id = ? AND status = 'visible'
        ORDER BY created_at ASC, id ASC
        LIMIT 20
        """, (rs, rowNum) -> replyFromRow(rs), checkInId);
  }

  private CheckInReply replyFromRow(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new CheckInReply(
        rs.getLong("id"),
        rs.getLong("check_in_id"),
        rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
        rs.getString("guest_id"),
        rs.getString("display_name"),
        rs.getString("message"),
        rs.getTimestamp("created_at").toLocalDateTime());
  }

  private void ensureLandmarkExists(Long landmarkId) {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM landmark WHERE id = ?", Integer.class, landmarkId);
    if (count == null || count == 0) {
      throw new IllegalArgumentException("地标不存在");
    }
  }

  private void ensureCheckInExists(Long checkInId) {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM check_in WHERE id = ?", Integer.class, checkInId);
    if (count == null || count == 0) {
      throw new IllegalArgumentException("打卡记录不存在");
    }
  }

  private Long findLikeId(Long checkInId, Long userId, String guestId) {
    List<Long> ids = jdbcTemplate.queryForList("""
        SELECT id FROM check_in_like
        WHERE check_in_id = ?
          AND ((? IS NOT NULL AND user_id = ?) OR (? IS NOT NULL AND guest_id = ?))
        LIMIT 1
        """, Long.class, checkInId, userId, userId, guestId, guestId);
    return ids.isEmpty() ? null : ids.get(0);
  }

  private int refreshLikeCount(Long checkInId) {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM check_in_like WHERE check_in_id = ?", Integer.class, checkInId);
    int value = count == null ? 0 : count;
    jdbcTemplate.update("UPDATE check_in SET like_count = ? WHERE id = ?", value, checkInId);
    return value;
  }

  private void refreshReplyCount(Long checkInId) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM check_in_reply WHERE check_in_id = ? AND status = 'visible'",
        Integer.class,
        checkInId);
    jdbcTemplate.update("UPDATE check_in SET reply_count = ? WHERE id = ?", count == null ? 0 : count, checkInId);
  }

}

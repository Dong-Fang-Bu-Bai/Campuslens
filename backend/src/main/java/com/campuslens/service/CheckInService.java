package com.campuslens.service;

import com.campuslens.model.CheckInRecord;
import com.campuslens.model.CheckInReply;
import com.campuslens.model.CheckInReplyRequest;
import com.campuslens.model.CheckInRequest;
import com.campuslens.model.LikeResponse;
import com.campuslens.model.ReplyLikeResponse;
import com.campuslens.model.SessionUser;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class CheckInService {
  private final JdbcTemplate jdbcTemplate;
  private final GuestIdentityService guestIdentityService;
  private final SearchRecordService searchRecordService;

  public CheckInService(
      JdbcTemplate jdbcTemplate,
      GuestIdentityService guestIdentityService,
      SearchRecordService searchRecordService) {
    this.jdbcTemplate = jdbcTemplate;
    this.guestIdentityService = guestIdentityService;
    this.searchRecordService = searchRecordService;
  }

  public List<CheckInRecord> list(Long landmarkId, int limit, SessionUser user, String guestId) {
    int safeLimit = Math.max(1, Math.min(limit, 100));
    String viewerGuestId = user == null ? guestIdentityService.optionalExisting(guestId) : null;
    String sql = """
        SELECT ci.id, ci.search_record_id,
               CASE WHEN ci.publish_image THEN sr.upload_image_url ELSE NULL END AS source_image_url,
               ci.landmark_id, l.code, l.name, l.location_text, l.map_x, l.map_y,
               ci.user_id, ci.guest_id, ci.display_name, ci.message,
               ci.like_count, ci.reply_count, ci.created_at,
               CASE WHEN EXISTS (
                 SELECT 1 FROM check_in_like cil
                 WHERE cil.check_in_id = ci.id
                   AND ((? IS NOT NULL AND cil.user_id = ?) OR (? IS NOT NULL AND cil.guest_id = ?))
               ) THEN TRUE ELSE FALSE END AS liked_by_me
        FROM check_in ci
        JOIN landmark l ON ci.landmark_id = l.id
        LEFT JOIN search_record sr ON ci.search_record_id = sr.id
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
        rs.getObject("search_record_id") == null ? null : rs.getLong("search_record_id"),
        rs.getString("source_image_url"),
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
        List.of(),
        rs.getTimestamp("created_at").toLocalDateTime()), args);
  }

  public CheckInRecord detail(Long id, SessionUser user, String guestId) {
    ensureCheckInExists(id);
    String viewerGuestId = user == null ? guestIdentityService.optionalExisting(guestId) : null;
    return jdbcTemplate.queryForObject("""
        SELECT ci.id, ci.search_record_id,
               CASE WHEN ci.publish_image THEN sr.upload_image_url ELSE NULL END AS source_image_url,
               ci.landmark_id, l.code, l.name, l.location_text, l.map_x, l.map_y,
               ci.user_id, ci.guest_id, ci.display_name, ci.message,
               ci.like_count, ci.reply_count, ci.created_at,
               CASE WHEN EXISTS (
                 SELECT 1 FROM check_in_like cil
                 WHERE cil.check_in_id = ci.id
                   AND ((? IS NOT NULL AND cil.user_id = ?) OR (? IS NOT NULL AND cil.guest_id = ?))
               ) THEN TRUE ELSE FALSE END AS liked_by_me
        FROM check_in ci
        JOIN landmark l ON ci.landmark_id = l.id
        LEFT JOIN search_record sr ON ci.search_record_id = sr.id
        WHERE ci.id = ? AND ci.status = 'visible'
        """, (rs, rowNum) -> new CheckInRecord(
            rs.getLong("id"),
            rs.getObject("search_record_id") == null ? null : rs.getLong("search_record_id"),
            rs.getString("source_image_url"),
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
            listReplyTree(rs.getLong("id"), user, viewerGuestId),
            rs.getTimestamp("created_at").toLocalDateTime()),
        user == null ? null : user.userId(),
        user == null ? null : user.userId(),
        viewerGuestId,
        viewerGuestId,
        id);
  }

  public CheckInRecord create(CheckInRequest request, SessionUser user) {
    ensureLandmarkExists(request.landmarkId());
    String guestId = user == null ? guestIdentityService.requireExisting(request.guestId()) : null;
    validateSource(request, user, guestId);
    String displayName = user == null ? guestId : user.username();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    try {
      jdbcTemplate.update(connection -> {
        PreparedStatement ps = connection.prepareStatement("""
            INSERT INTO check_in (
              search_record_id, landmark_id, user_id, guest_id, display_name, message, publish_image
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """, new String[] {"id"});
        ps.setLong(1, request.searchRecordId());
        ps.setLong(2, request.landmarkId());
        ps.setObject(3, user == null ? null : user.userId());
        ps.setString(4, guestId);
        ps.setString(5, displayName);
        ps.setString(6, request.message().trim());
        ps.setBoolean(7, request.publishImage());
        return ps;
      }, keyHolder);
    } catch (DuplicateKeyException ex) {
      throw new CheckInConflictException("该检索记录已经发布过打卡");
    }
    Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
    return find(id, user, guestId);
  }

  private void validateSource(CheckInRequest request, SessionUser user, String guestId) {
    SearchRecordService.CheckInSource source = searchRecordService.checkInSource(request.searchRecordId());
    if (!Set.of("success", "low_confidence").contains(source.status())) {
      throw new IllegalArgumentException("只有成功或低置信度检索记录可以发布打卡");
    }
    boolean owned = user == null
        ? source.userId() == null && Objects.equals(source.guestId(), guestId)
        : Objects.equals(source.userId(), user.userId());
    if (!owned) {
      throw new AuthRequiredException("无权使用该检索记录发布打卡");
    }
    if (!searchRecordService.containsResult(request.searchRecordId(), request.landmarkId())) {
      throw new IllegalArgumentException("打卡地标必须来自该检索记录的 Top-5 结果");
    }
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

  public ReplyLikeResponse toggleReplyLike(Long replyId, SessionUser user, String guestId) {
    ensureReplyExists(replyId);
    Long userId = user == null ? null : user.userId();
    String normalizedGuestId = user == null ? guestIdentityService.requireExisting(guestId) : null;
    List<Long> ids = jdbcTemplate.queryForList("""
        SELECT id FROM check_in_reply_like
        WHERE reply_id = ?
          AND ((? IS NOT NULL AND user_id = ?) OR (? IS NOT NULL AND guest_id = ?))
        LIMIT 1
        """, Long.class, replyId, userId, userId, normalizedGuestId, normalizedGuestId);
    boolean liked;
    if (ids.isEmpty()) {
      jdbcTemplate.update(
          "INSERT INTO check_in_reply_like (reply_id, user_id, guest_id) VALUES (?, ?, ?)",
          replyId,
          userId,
          normalizedGuestId);
      liked = true;
    } else {
      jdbcTemplate.update("DELETE FROM check_in_reply_like WHERE id = ?", ids.get(0));
      liked = false;
    }
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM check_in_reply_like WHERE reply_id = ?", Integer.class, replyId);
    int value = count == null ? 0 : count;
    jdbcTemplate.update("UPDATE check_in_reply SET like_count = ? WHERE id = ?", value, replyId);
    return new ReplyLikeResponse(replyId, liked, value);
  }

  public CheckInReply addReply(Long checkInId, CheckInReplyRequest request, SessionUser user) {
    ensureCheckInExists(checkInId);
    if (request.parentReplyId() != null) {
      ensureParentReplyBelongsToCheckIn(checkInId, request.parentReplyId());
    }
    String guestId = user == null ? guestIdentityService.requireExisting(request.guestId()) : null;
    String displayName = user == null ? guestId : user.username();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO check_in_reply (check_in_id, parent_reply_id, user_id, guest_id, display_name, message)
          VALUES (?, ?, ?, ?, ?, ?)
          """, new String[] {"id"});
      ps.setLong(1, checkInId);
      ps.setObject(2, request.parentReplyId());
      ps.setObject(3, user == null ? null : user.userId());
      ps.setString(4, guestId);
      ps.setString(5, displayName);
      ps.setString(6, request.message().trim());
      return ps;
    }, keyHolder);
    refreshReplyCount(checkInId);
    if (request.parentReplyId() != null) {
      refreshChildReplyCount(request.parentReplyId());
    }
    Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
    return jdbcTemplate.queryForObject("""
        SELECT id, check_in_id, parent_reply_id, user_id, guest_id, display_name, message,
               like_count, reply_count, created_at
        FROM check_in_reply
        WHERE id = ?
        """, (rs, rowNum) -> replyFromRow(rs, false), id);
  }

  private CheckInRecord find(Long id, SessionUser user, String guestId) {
    return list(null, 100, user, guestId).stream()
        .filter(item -> item.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("打卡记录不存在"));
  }

  private List<CheckInReply> listReplyTree(Long checkInId, SessionUser user, String guestId) {
    Long userId = user == null ? null : user.userId();
    List<CheckInReply> flat = jdbcTemplate.query("""
        SELECT cir.id, cir.check_in_id, cir.parent_reply_id, cir.user_id, cir.guest_id,
               cir.display_name, cir.message, cir.like_count, cir.reply_count, cir.created_at,
               CASE WHEN EXISTS (
                 SELECT 1 FROM check_in_reply_like cirl
                 WHERE cirl.reply_id = cir.id
                   AND ((? IS NOT NULL AND cirl.user_id = ?) OR (? IS NOT NULL AND cirl.guest_id = ?))
               ) THEN TRUE ELSE FALSE END AS liked_by_me
        FROM check_in_reply cir
        WHERE cir.check_in_id = ? AND cir.status = 'visible'
        ORDER BY cir.created_at ASC, cir.id ASC
        LIMIT 500
        """, (rs, rowNum) -> replyFromRow(rs, rs.getBoolean("liked_by_me")),
        userId, userId, guestId, guestId, checkInId);
    Map<Long, List<CheckInReply>> childrenByParent = new LinkedHashMap<>();
    for (CheckInReply reply : flat) {
      childrenByParent.computeIfAbsent(reply.parentReplyId(), ignored -> new ArrayList<>()).add(reply);
    }
    return buildReplyBranch(null, childrenByParent);
  }

  private List<CheckInReply> buildReplyBranch(Long parentId, Map<Long, List<CheckInReply>> childrenByParent) {
    return childrenByParent.getOrDefault(parentId, List.of()).stream()
        .map(reply -> new CheckInReply(
            reply.id(), reply.checkInId(), reply.parentReplyId(), reply.userId(), reply.guestId(),
            reply.displayName(), reply.message(), reply.likeCount(), reply.replyCount(), reply.likedByMe(),
            buildReplyBranch(reply.id(), childrenByParent), reply.createdAt()))
        .toList();
  }

  private CheckInReply replyFromRow(java.sql.ResultSet rs, boolean likedByMe) throws java.sql.SQLException {
    return new CheckInReply(
        rs.getLong("id"),
        rs.getLong("check_in_id"),
        rs.getObject("parent_reply_id") == null ? null : rs.getLong("parent_reply_id"),
        rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
        rs.getString("guest_id"),
        rs.getString("display_name"),
        rs.getString("message"),
        rs.getInt("like_count"),
        rs.getInt("reply_count"),
        likedByMe,
        List.of(),
        rs.getTimestamp("created_at").toLocalDateTime());
  }

  private void ensureLandmarkExists(Long landmarkId) {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM landmark WHERE id = ?", Integer.class, landmarkId);
    if (count == null || count == 0) {
      throw new IllegalArgumentException("地标不存在");
    }
  }

  private void ensureCheckInExists(Long checkInId) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM check_in WHERE id = ? AND status = 'visible'", Integer.class, checkInId);
    if (count == null || count == 0) {
      throw new IllegalArgumentException("打卡记录不存在");
    }
  }

  private void ensureReplyExists(Long replyId) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM check_in_reply WHERE id = ? AND status = 'visible'", Integer.class, replyId);
    if (count == null || count == 0) {
      throw new IllegalArgumentException("回复不存在");
    }
  }

  private void ensureParentReplyBelongsToCheckIn(Long checkInId, Long replyId) {
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM check_in_reply
        WHERE id = ? AND check_in_id = ? AND status = 'visible'
        """, Integer.class, replyId, checkInId);
    if (count == null || count == 0) {
      throw new IllegalArgumentException("父回复不属于当前帖子");
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

  private void refreshChildReplyCount(Long replyId) {
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM check_in_reply
        WHERE parent_reply_id = ? AND status = 'visible'
        """, Integer.class, replyId);
    jdbcTemplate.update(
        "UPDATE check_in_reply SET reply_count = ? WHERE id = ?", count == null ? 0 : count, replyId);
  }

}

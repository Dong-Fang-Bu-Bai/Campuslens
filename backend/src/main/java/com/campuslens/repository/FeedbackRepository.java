package com.campuslens.repository;

import com.campuslens.model.FeedbackRecord;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 反馈数据访问层，使用 JdbcTemplate 操作 feedback 表。
 * 参照 LandmarkService 的 JdbcTemplate 风格。
 */
@Repository
public class FeedbackRepository {

  private static final String BASE_SELECT = """
      SELECT id, search_record_id, predicted_landmark_id, confirmed_landmark_id,
             feedback_type, comment, status, created_at
      FROM feedback
      """;

  private final JdbcTemplate jdbcTemplate;

  private final RowMapper<FeedbackRecord> rowMapper = (rs, rowNum) -> new FeedbackRecord(
      rs.getLong("id"),
      rs.getLong("search_record_id"),
      (Long) rs.getObject("predicted_landmark_id"),
      (Long) rs.getObject("confirmed_landmark_id"),
      rs.getString("feedback_type"),
      rs.getString("comment"),
      rs.getString("status"),
      rs.getTimestamp("created_at").toLocalDateTime());

  public FeedbackRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * 保存反馈记录，返回带自增 ID 的 FeedbackRecord。
   */
  public FeedbackRecord save(Long searchRecordId, Long predictedLandmarkId,
      Long confirmedLandmarkId, String feedbackType, String comment) {
    var keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(con -> {
      var ps = con.prepareStatement(
          """
          INSERT INTO feedback (search_record_id, predicted_landmark_id, confirmed_landmark_id,
                                feedback_type, comment)
          VALUES (?, ?, ?, ?, ?)
          """,
          Statement.RETURN_GENERATED_KEYS);
      ps.setLong(1, searchRecordId);
      if (predictedLandmarkId != null) {
        ps.setLong(2, predictedLandmarkId);
      } else {
        ps.setNull(2, java.sql.Types.BIGINT);
      }
      if (confirmedLandmarkId != null) {
        ps.setLong(3, confirmedLandmarkId);
      } else {
        ps.setNull(3, java.sql.Types.BIGINT);
      }
      ps.setString(4, feedbackType);
      ps.setString(5, comment);
      return ps;
    }, keyHolder);

    long id = keyHolder.getKey().longValue();
    return findById(id).orElseThrow();
  }

  public Optional<FeedbackRecord> findById(Long id) {
    return jdbcTemplate.query(BASE_SELECT + " WHERE id = ?", rowMapper, id)
        .stream()
        .findFirst();
  }

  public List<FeedbackRecord> findBySearchRecordId(Long searchRecordId) {
    return jdbcTemplate.query(
        BASE_SELECT + " WHERE search_record_id = ? ORDER BY created_at DESC",
        rowMapper, searchRecordId);
  }

  public List<FeedbackRecord> findAll() {
    return jdbcTemplate.query(BASE_SELECT + " ORDER BY created_at DESC", rowMapper);
  }
}

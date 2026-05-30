package com.campuslens.service;

import com.campuslens.model.LandmarkDetail;
import com.campuslens.model.LandmarkImage;
import com.campuslens.model.LandmarkSummary;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class LandmarkService {
  private static final String BASE_SELECT = """
      SELECT id, code, name, english_name, type, summary, description,
             location_text, map_x, map_y, cover_image_url
      FROM landmark
      """;

  private final JdbcTemplate jdbcTemplate;
  private final RowMapper<LandmarkRow> landmarkRowMapper = (rs, rowNum) -> new LandmarkRow(
      rs.getLong("id"),
      rs.getString("code"),
      rs.getString("name"),
      rs.getString("english_name"),
      rs.getString("type"),
      rs.getString("summary"),
      rs.getString("description"),
      rs.getString("location_text"),
      toDouble(rs.getBigDecimal("map_x")),
      toDouble(rs.getBigDecimal("map_y")),
      rs.getString("cover_image_url"));

  public LandmarkService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<LandmarkSummary> list() {
    return jdbcTemplate.query(BASE_SELECT + " ORDER BY code", landmarkRowMapper).stream()
        .map(this::summary)
        .toList();
  }

  public Optional<LandmarkDetail> findById(Long id) {
    return jdbcTemplate.query(BASE_SELECT + " WHERE id = ?", landmarkRowMapper, id).stream()
        .findFirst()
        .map(this::detail);
  }

  public Optional<LandmarkDetail> findByCode(String code) {
    return jdbcTemplate.query(BASE_SELECT + " WHERE UPPER(code) = UPPER(?)", landmarkRowMapper, code).stream()
        .findFirst()
        .map(this::detail);
  }

  public List<LandmarkDetail> topCandidates() {
    return jdbcTemplate.query(BASE_SELECT + " ORDER BY code LIMIT 5", landmarkRowMapper).stream()
        .map(this::detail)
        .toList();
  }

  private LandmarkSummary summary(LandmarkRow row) {
    return new LandmarkSummary(
        row.id(),
        row.code(),
        row.name(),
        row.englishName(),
        row.type(),
        row.summary(),
        row.coverImageUrl(),
        row.locationText(),
        row.mapX(),
        row.mapY());
  }

  private LandmarkDetail detail(LandmarkRow row) {
    return new LandmarkDetail(
        row.id(),
        row.code(),
        row.name(),
        row.englishName(),
        row.type(),
        row.summary(),
        row.coverImageUrl(),
        row.description(),
        row.locationText(),
        row.mapX(),
        row.mapY(),
        images(row));
  }

  private List<LandmarkImage> images(LandmarkRow row) {
    List<LandmarkImage> images = jdbcTemplate.query("""
        SELECT id, image_url, angle, light_condition, is_cover
        FROM landmark_image
        WHERE landmark_id = ?
        ORDER BY is_cover DESC, id
        """, (rs, rowNum) -> new LandmarkImage(
        rs.getLong("id"),
        rs.getString("image_url"),
        rs.getString("angle"),
        rs.getString("light_condition"),
        rs.getBoolean("is_cover")), row.id());

    if (!images.isEmpty()) {
      return images;
    }
    return List.of(new LandmarkImage(row.id() * 100 + 1, row.coverImageUrl(), "front", "day", true));
  }

  private static double toDouble(BigDecimal value) {
    return value == null ? 0 : value.doubleValue();
  }

  private record LandmarkRow(
      Long id,
      String code,
      String name,
      String englishName,
      String type,
      String summary,
      String description,
      String locationText,
      double mapX,
      double mapY,
      String coverImageUrl) {
  }
}

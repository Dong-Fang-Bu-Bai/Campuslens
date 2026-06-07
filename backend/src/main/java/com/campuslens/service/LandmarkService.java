package com.campuslens.service;

import com.campuslens.model.LandmarkDetail;
import com.campuslens.model.LandmarkImage;
import com.campuslens.model.LandmarkSummary;
import com.campuslens.model.LandmarkUpsertRequest;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LandmarkService {
  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
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

  public LandmarkDetail create(LandmarkUpsertRequest request) {
    validate(request);
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO landmark (
            code, name, english_name, type, summary, description,
            location_text, map_x, map_y, cover_image_url
          )
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """, new String[] {"id"});
      fillLandmarkStatement(ps, request);
      return ps;
    }, keyHolder);
    Long id = Objects.requireNonNull(keyHolder.getKey(), "landmark id not generated").longValue();
    return findById(id).orElseThrow(() -> new IllegalArgumentException("地标新增失败"));
  }

  public LandmarkDetail update(Long id, LandmarkUpsertRequest request) {
    validate(request);
    int updated = jdbcTemplate.update("""
        UPDATE landmark
        SET code = ?, name = ?, english_name = ?, type = ?, summary = ?, description = ?,
            location_text = ?, map_x = ?, map_y = ?, cover_image_url = ?
        WHERE id = ?
        """,
        normalize(request.code()),
        normalize(request.name()),
        normalizeNullable(request.englishName()),
        normalizeNullable(request.type()),
        normalizeNullable(request.summary()),
        normalizeNullable(request.description()),
        normalizeNullable(request.locationText()),
        request.mapX(),
        request.mapY(),
        normalizeNullable(request.coverImageUrl()),
        id);
    if (updated == 0) {
      throw new IllegalArgumentException("地标不存在");
    }
    return findById(id).orElseThrow(() -> new IllegalArgumentException("地标不存在"));
  }

  public LandmarkImage addImage(Long landmarkId, MultipartFile file, String angle, String lightCondition) {
    if (findById(landmarkId).isEmpty()) {
      throw new IllegalArgumentException("地标不存在");
    }
    validateImage(file);
    String imageUrl = saveImage(landmarkId, file);
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO landmark_image (landmark_id, image_url, angle, light_condition, is_cover)
          VALUES (?, ?, ?, ?, FALSE)
          """, new String[] {"id"});
      ps.setLong(1, landmarkId);
      ps.setString(2, imageUrl);
      ps.setString(3, normalizeNullable(angle));
      ps.setString(4, normalizeNullable(lightCondition));
      return ps;
    }, keyHolder);
    Long id = Objects.requireNonNull(keyHolder.getKey(), "landmark image id not generated").longValue();
    return new LandmarkImage(id, imageUrl, normalizeNullable(angle), normalizeNullable(lightCondition), false);
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

  private void fillLandmarkStatement(PreparedStatement ps, LandmarkUpsertRequest request) throws java.sql.SQLException {
    ps.setString(1, normalize(request.code()));
    ps.setString(2, normalize(request.name()));
    ps.setString(3, normalizeNullable(request.englishName()));
    ps.setString(4, normalizeNullable(request.type()));
    ps.setString(5, normalizeNullable(request.summary()));
    ps.setString(6, normalizeNullable(request.description()));
    ps.setString(7, normalizeNullable(request.locationText()));
    ps.setDouble(8, request.mapX());
    ps.setDouble(9, request.mapY());
    ps.setString(10, normalizeNullable(request.coverImageUrl()));
  }

  private void validate(LandmarkUpsertRequest request) {
    if (request.mapX() < 0 || request.mapX() > 100 || request.mapY() < 0 || request.mapY() > 100) {
      throw new IllegalArgumentException("地图坐标必须在 0 到 100 之间");
    }
  }

  private void validateImage(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("请上传地标样本图片");
    }
    if (file.getSize() > 8L * 1024 * 1024) {
      throw new IllegalArgumentException("图片大小不能超过 8MB");
    }
    if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
      throw new IllegalArgumentException("仅支持 JPG、PNG、WebP 图片");
    }
  }

  private String saveImage(Long landmarkId, MultipartFile file) {
    String extension = extension(file.getOriginalFilename(), file.getContentType());
    String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String filename = UUID.randomUUID() + extension;
    Path uploadDir = Path.of("uploads", "landmarks", String.valueOf(landmarkId), date);
    Path target = uploadDir.resolve(filename);
    try {
      Files.createDirectories(uploadDir);
      try (InputStream input = file.getInputStream()) {
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
      }
      return "/" + target.toString().replace('\\', '/');
    } catch (IOException ex) {
      throw new IllegalArgumentException("地标样本图片保存失败");
    }
  }

  private String extension(String filename, String contentType) {
    if (filename != null) {
      String lower = filename.toLowerCase();
      if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
        return ".jpg";
      }
      if (lower.endsWith(".png")) {
        return ".png";
      }
      if (lower.endsWith(".webp")) {
        return ".webp";
      }
    }
    return "image/png".equals(contentType) ? ".png" : "image/webp".equals(contentType) ? ".webp" : ".jpg";
  }

  private String normalize(String value) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("地标编号和名称不能为空");
    }
    return normalized;
  }

  private String normalizeNullable(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
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

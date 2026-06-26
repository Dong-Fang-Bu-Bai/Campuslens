package com.campuslens.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AccountAvatarService {
  private static final long MAX_SIZE = 5L * 1024 * 1024;
  private static final int MIN_DIMENSION = 32;
  private static final int MAX_DIMENSION = 4096;
  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

  private final JdbcTemplate jdbcTemplate;
  private final Path uploadRoot;

  public AccountAvatarService(
      JdbcTemplate jdbcTemplate,
      @Value("${campuslens.upload-dir:uploads}") String uploadDir) {
    this.jdbcTemplate = jdbcTemplate;
    this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
  }

  public String update(Long userId, MultipartFile file) {
    byte[] bytes = validate(file);
    String previousUrl = jdbcTemplate.queryForObject(
        "SELECT avatar_url FROM app_user WHERE id = ? AND enabled = TRUE",
        String.class,
        userId);
    String extension = "image/png".equals(file.getContentType()) ? ".png" : ".jpg";
    Path userDirectory = uploadRoot.resolve("avatars").resolve(String.valueOf(userId)).normalize();
    if (!userDirectory.startsWith(uploadRoot)) {
      throw new IllegalArgumentException("头像存储路径无效");
    }
    Path target = userDirectory.resolve(UUID.randomUUID() + extension).normalize();
    try {
      Files.createDirectories(userDirectory);
      Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
      String avatarUrl = "/uploads/avatars/" + userId + "/" + target.getFileName();
      int updated = jdbcTemplate.update(
          "UPDATE app_user SET avatar_url = ? WHERE id = ? AND enabled = TRUE",
          avatarUrl,
          userId);
      if (updated == 0) {
        Files.deleteIfExists(target);
        throw new AuthRequiredException("账号不存在或已停用");
      }
      deletePrevious(previousUrl, userDirectory, target);
      return avatarUrl;
    } catch (IOException ex) {
      throw new IllegalArgumentException("头像保存失败");
    }
  }

  private byte[] validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("请选择头像图片");
    }
    if (file.getSize() > MAX_SIZE) {
      throw new IllegalArgumentException("头像大小不能超过 5MB");
    }
    if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
      throw new IllegalArgumentException("头像仅支持 JPG 或 PNG 图片");
    }
    try {
      byte[] bytes = file.getBytes();
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null) {
        throw new IllegalArgumentException("头像文件不是有效图片");
      }
      if (image.getWidth() < MIN_DIMENSION || image.getHeight() < MIN_DIMENSION
          || image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION) {
        throw new IllegalArgumentException("头像尺寸需在 32×32 到 4096×4096 之间");
      }
      return bytes;
    } catch (IOException ex) {
      throw new IllegalArgumentException("头像文件读取失败");
    }
  }

  private void deletePrevious(String previousUrl, Path userDirectory, Path currentTarget) {
    String prefix = "/uploads/avatars/" + userDirectory.getFileName() + "/";
    if (previousUrl == null || !previousUrl.startsWith(prefix)) {
      return;
    }
    Path previous = userDirectory.resolve(previousUrl.substring(prefix.length())).normalize();
    if (!previous.startsWith(userDirectory) || previous.equals(currentTarget)) {
      return;
    }
    try {
      Files.deleteIfExists(previous);
    } catch (IOException ignored) {
      // 新头像已经生效，旧文件清理失败不应回滚用户操作。
    }
  }
}

package com.campuslens.service;

import com.campuslens.model.GuestIdentityResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class GuestIdentityService {
  private static final Pattern GUEST_ID_PATTERN = Pattern.compile("^guest#([1-9]\\d*)$");
  private final JdbcTemplate jdbcTemplate;

  public GuestIdentityService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public GuestIdentityResponse allocate(String clientToken) {
    String tokenHash = sha256(normalizeClientToken(clientToken));
    Long id = findByTokenHash(tokenHash).orElseGet(() -> insert(tokenHash));
    return new GuestIdentityResponse(format(id));
  }

  public String requireExisting(String guestId) {
    Long id = parse(guestId)
        .orElseThrow(() -> new IllegalArgumentException("游客身份无效，请刷新页面后重试"));
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM guest_identity WHERE id = ?", Integer.class, id);
    if (count == null || count == 0) {
      throw new IllegalArgumentException("游客身份不存在，请刷新页面后重试");
    }
    return format(id);
  }

  public String optionalExisting(String guestId) {
    return guestId == null || guestId.isBlank() ? null : requireExisting(guestId);
  }

  private Long insert(String tokenHash) {
    try {
      jdbcTemplate.update("INSERT INTO guest_identity (client_token_hash) VALUES (?)", tokenHash);
    } catch (DuplicateKeyException ignored) {
      // A concurrent request with the same browser token won the insert race.
    }
    return findByTokenHash(tokenHash)
        .orElseThrow(() -> new IllegalStateException("游客身份创建失败"));
  }

  private Optional<Long> findByTokenHash(String tokenHash) {
    List<Long> ids = jdbcTemplate.queryForList(
        "SELECT id FROM guest_identity WHERE client_token_hash = ?", Long.class, tokenHash);
    return ids.stream().findFirst();
  }

  private String normalizeClientToken(String clientToken) {
    if (clientToken == null) {
      throw new IllegalArgumentException("clientToken 不能为空");
    }
    String value = clientToken.trim();
    if (value.length() < 16 || value.length() > 200) {
      throw new IllegalArgumentException("clientToken 长度必须为 16 到 200 个字符");
    }
    return value;
  }

  private Optional<Long> parse(String guestId) {
    Matcher matcher = GUEST_ID_PATTERN.matcher(guestId == null ? "" : guestId.trim());
    if (!matcher.matches()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Long.parseLong(matcher.group(1)));
    } catch (NumberFormatException ex) {
      return Optional.empty();
    }
  }

  private String format(Long id) {
    return "guest#" + id;
  }

  private String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}

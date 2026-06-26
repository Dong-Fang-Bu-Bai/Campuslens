package com.campuslens.service;

import com.campuslens.model.PasswordResetCodeRequest;
import com.campuslens.model.PasswordResetConfirmRequest;
import com.campuslens.model.PasswordResetResponse;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
  private static final String SENT_MESSAGE = "如果该邮箱已绑定账号，验证码邮件将发送到该邮箱";
  private static final int MAX_ATTEMPTS = 5;
  private final JdbcTemplate jdbcTemplate;
  private final PasswordService passwordService;
  private final PasswordResetMailer mailer;
  private final SessionService sessionService;
  private final SecureRandom random = new SecureRandom();
  private final int validMinutes;
  private final int cooldownSeconds;

  public PasswordResetService(
      JdbcTemplate jdbcTemplate,
      PasswordService passwordService,
      PasswordResetMailer mailer,
      SessionService sessionService,
      @Value("${campuslens.password-reset.valid-minutes:10}") int validMinutes,
      @Value("${campuslens.password-reset.cooldown-seconds:60}") int cooldownSeconds) {
    this.jdbcTemplate = jdbcTemplate;
    this.passwordService = passwordService;
    this.mailer = mailer;
    this.sessionService = sessionService;
    this.validMinutes = validMinutes;
    this.cooldownSeconds = cooldownSeconds;
  }

  @Transactional
  public PasswordResetResponse requestCode(PasswordResetCodeRequest request) {
    String email = normalizeEmail(request.email());
    List<UserAccount> users = jdbcTemplate.query("""
        SELECT id, username, email FROM app_user
        WHERE LOWER(email) = ? AND enabled = TRUE
        """, (rs, rowNum) -> new UserAccount(
        rs.getLong("id"), rs.getString("username"), rs.getString("email")), email);
    if (users.isEmpty()) {
      return new PasswordResetResponse(SENT_MESSAGE);
    }

    UserAccount user = users.get(0);
    Integer recent = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM password_reset_code
        WHERE user_id = ? AND created_at > ?
        """, Integer.class, user.id(), Timestamp.valueOf(LocalDateTime.now().minusSeconds(cooldownSeconds)));
    if (recent != null && recent > 0) {
      return new PasswordResetResponse(SENT_MESSAGE);
    }

    jdbcTemplate.update(
        "UPDATE password_reset_code SET used_at = CURRENT_TIMESTAMP WHERE user_id = ? AND used_at IS NULL",
        user.id());
    String code = "%06d".formatted(random.nextInt(1_000_000));
    jdbcTemplate.update("""
        INSERT INTO password_reset_code (user_id, code_hash, expires_at, attempt_count)
        VALUES (?, ?, ?, 0)
        """, user.id(), passwordService.hash(code),
        Timestamp.valueOf(LocalDateTime.now().plusMinutes(validMinutes)));
    try {
      mailer.sendCode(user.email(), user.username(), code, validMinutes);
    } catch (PasswordResetMailException ex) {
      jdbcTemplate.update(
          "DELETE FROM password_reset_code WHERE user_id = ? AND used_at IS NULL", user.id());
    }
    return new PasswordResetResponse(SENT_MESSAGE);
  }

  @Transactional(noRollbackFor = IllegalArgumentException.class)
  public PasswordResetResponse confirm(PasswordResetConfirmRequest request) {
    String email = normalizeEmail(request.email());
    List<ResetCode> codes = jdbcTemplate.query("""
        SELECT prc.id, prc.user_id, prc.code_hash, prc.expires_at, prc.attempt_count, au.password_hash
        FROM password_reset_code prc
        JOIN app_user au ON au.id = prc.user_id
        WHERE LOWER(au.email) = ? AND au.enabled = TRUE AND prc.used_at IS NULL
        ORDER BY prc.id DESC LIMIT 1 FOR UPDATE
        """, (rs, rowNum) -> new ResetCode(
        rs.getLong("id"), rs.getLong("user_id"), rs.getString("code_hash"),
        rs.getTimestamp("expires_at").toLocalDateTime(), rs.getInt("attempt_count"),
        rs.getString("password_hash")), email);
    if (codes.isEmpty()) {
      throw new IllegalArgumentException("验证码无效或已过期");
    }

    ResetCode reset = codes.get(0);
    if (reset.expiresAt().isBefore(LocalDateTime.now()) || reset.attemptCount() >= MAX_ATTEMPTS) {
      jdbcTemplate.update("UPDATE password_reset_code SET used_at = CURRENT_TIMESTAMP WHERE id = ?", reset.id());
      throw new IllegalArgumentException("验证码无效或已过期");
    }
    if (!passwordService.matches(request.code(), reset.codeHash())) {
      int attempts = reset.attemptCount() + 1;
      jdbcTemplate.update("""
          UPDATE password_reset_code
          SET attempt_count = ?, used_at = CASE WHEN ? >= ? THEN CURRENT_TIMESTAMP ELSE used_at END
          WHERE id = ?
          """, attempts, attempts, MAX_ATTEMPTS, reset.id());
      throw new IllegalArgumentException("验证码无效或已过期");
    }
    if (passwordService.matches(request.newPassword(), reset.passwordHash())) {
      throw new IllegalArgumentException("新密码不能与当前密码相同");
    }

    jdbcTemplate.update(
        "UPDATE app_user SET password_hash = ? WHERE id = ?",
        passwordService.hash(request.newPassword()), reset.userId());
    jdbcTemplate.update(
        "UPDATE password_reset_code SET used_at = CURRENT_TIMESTAMP WHERE user_id = ? AND used_at IS NULL",
        reset.userId());
    sessionService.revokeUser(reset.userId());
    return new PasswordResetResponse("密码重置成功，请使用新密码登录");
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }

  private record UserAccount(Long id, String username, String email) {
  }

  private record ResetCode(
      Long id, Long userId, String codeHash, LocalDateTime expiresAt,
      int attemptCount, String passwordHash) {
  }
}

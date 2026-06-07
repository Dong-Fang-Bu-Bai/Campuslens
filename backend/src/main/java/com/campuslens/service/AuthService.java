package com.campuslens.service;

import com.campuslens.model.AuthLoginRequest;
import com.campuslens.model.AuthRegisterRequest;
import com.campuslens.model.AuthResponse;
import com.campuslens.model.SessionUser;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final JdbcTemplate jdbcTemplate;
  private final PasswordService passwordService;
  private final SessionService sessionService;

  public AuthService(JdbcTemplate jdbcTemplate, PasswordService passwordService, SessionService sessionService) {
    this.jdbcTemplate = jdbcTemplate;
    this.passwordService = passwordService;
    this.sessionService = sessionService;
  }

  public AuthResponse register(AuthRegisterRequest request) {
    String username = normalizeUsername(request.username());
    validatePassword(request.password());
    String email = normalizeEmail(request.email());
    if (exists(username)) {
      throw new IllegalArgumentException("用户名已存在");
    }

    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          INSERT INTO app_user (username, password_hash, email, role, enabled)
          VALUES (?, ?, ?, 'user', TRUE)
          """, new String[] {"id"});
      ps.setString(1, username);
      ps.setString(2, passwordService.hash(request.password()));
      ps.setString(3, email);
      return ps;
    }, keyHolder);
    Long userId = Objects.requireNonNull(keyHolder.getKey(), "app_user id not generated").longValue();
    String token = sessionService.create(new SessionUser(userId, username, email, "user"));
    return new AuthResponse(userId, username, email, "user", false, token, "注册成功");
  }

  public AuthResponse login(AuthLoginRequest request) {
    String username = normalizeUsername(request.username());
    List<UserRow> users = jdbcTemplate.query("""
        SELECT id, username, email, role, password_hash
        FROM app_user
        WHERE username = ? AND enabled = TRUE
        """, (rs, rowNum) -> new UserRow(
        rs.getLong("id"),
        rs.getString("username"),
        rs.getString("email"),
        rs.getString("role"),
        rs.getString("password_hash")), username);
    if (users.isEmpty() || !passwordService.matches(request.password(), users.get(0).passwordHash())) {
      throw new IllegalArgumentException("用户名或密码错误");
    }
    UserRow user = users.get(0);
    boolean admin = "admin".equals(user.role());
    String token = sessionService.create(new SessionUser(user.id(), user.username(), user.email(), user.role()));
    return new AuthResponse(
        user.id(),
        user.username(),
        user.email(),
        user.role(),
        admin,
        token,
        admin ? "管理员登录成功" : "登录成功");
  }

  public boolean isActiveUser(Long userId) {
    if (userId == null) {
      return false;
    }
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM app_user WHERE id = ? AND enabled = TRUE",
        Integer.class,
        userId);
    return count != null && count > 0;
  }

  private boolean exists(String username) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM app_user WHERE username = ?",
        Integer.class,
        username);
    return count != null && count > 0;
  }

  private String normalizeUsername(String username) {
    String value = username == null ? "" : username.trim();
    if (value.isBlank()) {
      throw new IllegalArgumentException("用户名不能为空");
    }
    return value;
  }

  private String normalizeEmail(String email) {
    if (email == null || email.isBlank()) {
      return null;
    }
    return email.trim();
  }

  private void validatePassword(String password) {
    if (password == null || password.length() < 8) {
      throw new IllegalArgumentException("密码至少 8 位");
    }
  }

  private record UserRow(Long id, String username, String email, String role, String passwordHash) {
  }
}

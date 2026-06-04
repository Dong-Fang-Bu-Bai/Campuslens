package com.campuslens.service;

import com.campuslens.model.AdminLoginRequest;
import com.campuslens.model.AdminLoginResponse;
import com.campuslens.model.SessionUser;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminService {
  private final JdbcTemplate jdbcTemplate;
  private final PasswordService passwordService;
  private final SessionService sessionService;

  public AdminService(JdbcTemplate jdbcTemplate, PasswordService passwordService, SessionService sessionService) {
    this.jdbcTemplate = jdbcTemplate;
    this.passwordService = passwordService;
    this.sessionService = sessionService;
  }

  public AdminLoginResponse login(AdminLoginRequest request) {
    List<AdminRow> admins = jdbcTemplate.query("""
        SELECT id, username, password_hash, role
        FROM admin_user
        WHERE username = ? AND enabled = TRUE
        """, (rs, rowNum) -> new AdminRow(
        rs.getLong("id"),
        rs.getString("username"),
        rs.getString("password_hash"),
        rs.getString("role")), request.username());
    if (admins.isEmpty() || !passwordService.matches(request.password(), admins.get(0).passwordHash())) {
      throw new IllegalArgumentException("管理员账号或密码错误");
    }
    AdminRow admin = admins.get(0);
    String token = sessionService.create(new SessionUser(admin.id(), admin.username(), null, admin.role()));
    return new AdminLoginResponse(true, admin.username(), admin.role(), token, "管理员登录成功");
  }

  private record AdminRow(Long id, String username, String passwordHash, String role) {
  }
}

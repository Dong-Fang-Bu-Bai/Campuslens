package com.campuslens.service;

import com.campuslens.model.AdminLoginRequest;
import com.campuslens.model.AdminLoginResponse;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminService {
  private final JdbcTemplate jdbcTemplate;

  public AdminService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public AdminLoginResponse login(AdminLoginRequest request) {
    List<String> roles = jdbcTemplate.query("""
        SELECT role
        FROM admin_user
        WHERE username = ? AND password_hash = ? AND enabled = TRUE
        """, (rs, rowNum) -> rs.getString("role"), request.username(), request.password());
    if (roles.isEmpty()) {
      throw new IllegalArgumentException("管理员账号或密码错误");
    }
    return new AdminLoginResponse(true, request.username(), roles.get(0), "管理员登录成功");
  }
}

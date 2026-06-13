package com.campuslens.service;

import com.campuslens.model.SessionUser;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
  private final SecureRandom random = new SecureRandom();
  private final ConcurrentMap<String, SessionUser> sessions = new ConcurrentHashMap<>();

  public String create(SessionUser user) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    sessions.put(token, user);
    return token;
  }

  public Optional<SessionUser> find(String authorization) {
    String token = bearerToken(authorization);
    if (token == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(sessions.get(token));
  }

  public SessionUser requireUser(String authorization) {
    return find(authorization).orElseThrow(() -> new AuthRequiredException("请先登录"));
  }

  public SessionUser requireAdmin(String authorization) {
    SessionUser user = requireUser(authorization);
    if (!user.admin()) {
      throw new AdminRequiredException("需要管理员权限");
    }
    return user;
  }

  private String bearerToken(String authorization) {
    if (authorization == null || authorization.isBlank()) {
      return null;
    }
    String prefix = "Bearer ";
    if (!authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
      return null;
    }
    String token = authorization.substring(prefix.length()).trim();
    return token.isBlank() ? null : token;
  }
}

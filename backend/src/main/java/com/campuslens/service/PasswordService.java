package com.campuslens.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
  private static final int ITERATIONS = 120_000;
  private static final int KEY_LENGTH = 256;
  private static final SecureRandom RANDOM = new SecureRandom();

  public String hash(String password) {
    byte[] salt = new byte[16];
    RANDOM.nextBytes(salt);
    byte[] digest = pbkdf2(password, salt, ITERATIONS);
    return "pbkdf2$" + ITERATIONS + "$" + encode(salt) + "$" + encode(digest);
  }

  public boolean matches(String password, String encoded) {
    if (password == null || encoded == null || !encoded.startsWith("pbkdf2$")) {
      return false;
    }
    String[] parts = encoded.split("\\$");
    if (parts.length != 4) {
      return false;
    }
    try {
      int iterations = Integer.parseInt(parts[1]);
      byte[] salt = Base64.getDecoder().decode(parts[2]);
      byte[] expected = Base64.getDecoder().decode(parts[3]);
      byte[] actual = pbkdf2(password, salt, iterations);
      return MessageDigest.isEqual(expected, actual);
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  private byte[] pbkdf2(String password, byte[] salt, int iterations) {
    try {
      KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH);
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (Exception ex) {
      throw new IllegalArgumentException("密码哈希处理失败");
    }
  }

  private String encode(byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }
}

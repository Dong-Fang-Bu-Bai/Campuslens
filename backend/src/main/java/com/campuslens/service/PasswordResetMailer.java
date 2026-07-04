package com.campuslens.service;

public interface PasswordResetMailer {
  void sendCode(String recipient, String username, String code, int validMinutes);
}

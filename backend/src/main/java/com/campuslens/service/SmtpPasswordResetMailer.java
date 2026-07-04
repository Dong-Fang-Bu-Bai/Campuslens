package com.campuslens.service;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SmtpPasswordResetMailer implements PasswordResetMailer {
  private static final Logger LOGGER = LoggerFactory.getLogger(SmtpPasswordResetMailer.class);
  private final JavaMailSender mailSender;
  private final String from;

  public SmtpPasswordResetMailer(
      JavaMailSender mailSender,
      @Value("${campuslens.mail.from:${spring.mail.username:}}") String from) {
    this.mailSender = mailSender;
    this.from = from;
  }

  @Override
  public void sendCode(String recipient, String username, String code, int validMinutes) {
    if (from == null || from.isBlank()) {
      throw new PasswordResetMailException("邮件服务尚未配置", null);
    }
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
      helper.setFrom(from, "CampusLens");
      helper.setTo(recipient);
      helper.setSubject("CampusLens 密码重置验证码");
      String safeUsername = HtmlUtils.htmlEscape(username);
      String text = "你好，" + username + "：\n\n你的 CampusLens 密码重置验证码是 " + code
          + "，有效期 " + validMinutes + " 分钟。\n如果不是你本人操作，请忽略此邮件。";
      String html = """
          <!doctype html><html lang="zh-CN"><body style="font-family:Arial,sans-serif;color:#18202a">
          <div style="max-width:560px;margin:24px auto;padding:28px;border:1px solid #e5e7eb;border-radius:14px">
            <h2 style="margin-top:0">CampusLens 密码找回</h2>
            <p>你好，%s：</p><p>你的密码重置验证码是：</p>
            <p style="font-size:32px;font-weight:700;letter-spacing:8px;color:#2563eb">%s</p>
            <p>验证码在 %d 分钟内有效。若不是你本人操作，请忽略此邮件。</p>
          </div></body></html>
          """.formatted(safeUsername, code, validMinutes);
      helper.setText(text, html);
      mailSender.send(message);
    } catch (Exception ex) {
      LOGGER.warn("Password reset email delivery failed via SMTP: {}", ex.getMessage());
      throw new PasswordResetMailException("验证码邮件发送失败，请稍后重试", ex);
    }
  }
}

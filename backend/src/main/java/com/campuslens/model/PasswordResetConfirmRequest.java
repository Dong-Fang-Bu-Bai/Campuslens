package com.campuslens.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 160, message = "邮箱不能超过 160 个字符")
    String email,
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "\\d{6}", message = "验证码必须为 6 位数字")
    String code,
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, message = "新密码至少 8 位")
    String newPassword) {
}

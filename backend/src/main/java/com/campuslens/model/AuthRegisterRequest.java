package com.campuslens.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRegisterRequest(
    @NotBlank String username,
    @NotBlank String password,
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 160, message = "邮箱不能超过 160 个字符")
    String email) {
}

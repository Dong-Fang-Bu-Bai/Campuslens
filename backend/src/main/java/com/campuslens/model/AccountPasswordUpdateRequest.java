package com.campuslens.model;

import jakarta.validation.constraints.NotBlank;

public record AccountPasswordUpdateRequest(
    @NotBlank(message = "请输入当前密码") String currentPassword,
    @NotBlank(message = "请输入新密码") String newPassword) {
}

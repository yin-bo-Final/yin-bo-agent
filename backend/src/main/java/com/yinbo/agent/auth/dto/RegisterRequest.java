package com.yinbo.agent.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 注册请求。
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不能超过64个字符")
        String username,
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度需要在6到64个字符之间")
        String password
) {
}

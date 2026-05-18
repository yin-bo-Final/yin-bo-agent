package com.yinbo.agent.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
        @NotBlank(message = "请先输入当前密码再确认注销")
        String password
) {
}

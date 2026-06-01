package com.yinbo.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
// 认证和种子管理员配置。
public record AuthProperties(
        String seedAdminUsername,
        String seedAdminPassword
) {

    // 规范化种子管理员配置。
    public AuthProperties {
        seedAdminUsername = seedAdminUsername == null ? "" : seedAdminUsername.trim();
        seedAdminPassword = seedAdminPassword == null ? "" : seedAdminPassword;
    }
}

package com.yinbo.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String seedAdminUsername,
        String seedAdminPassword
) {

    public AuthProperties {
        seedAdminUsername = seedAdminUsername == null ? "" : seedAdminUsername.trim();
        seedAdminPassword = seedAdminPassword == null ? "" : seedAdminPassword;
    }
}

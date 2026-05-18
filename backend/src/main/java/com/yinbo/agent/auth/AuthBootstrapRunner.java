package com.yinbo.agent.auth;

import com.yinbo.agent.config.AuthProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AuthBootstrapRunner implements ApplicationRunner {

    private final SessionAuthService sessionAuthService;
    private final AuthProperties authProperties;

    public AuthBootstrapRunner(SessionAuthService sessionAuthService, AuthProperties authProperties) {
        this.sessionAuthService = sessionAuthService;
        this.authProperties = authProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (authProperties.seedAdminUsername().isBlank() || authProperties.seedAdminPassword().isBlank()) {
            return;
        }

        sessionAuthService.createSeedUser(
                authProperties.seedAdminUsername(),
                authProperties.seedAdminPassword(),
                "系统管理员"
        );
    }
}

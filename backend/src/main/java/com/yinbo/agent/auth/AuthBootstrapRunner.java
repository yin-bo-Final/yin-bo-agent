package com.yinbo.agent.auth;

import com.yinbo.agent.auth.service.SessionAuthService;
import com.yinbo.agent.config.AuthProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
// 启动时初始化种子管理员。
public class AuthBootstrapRunner implements ApplicationRunner {

    private final SessionAuthService sessionAuthService;
    private final AuthProperties authProperties;

    // 注入认证服务和认证配置。
    public AuthBootstrapRunner(SessionAuthService sessionAuthService, AuthProperties authProperties) {
        this.sessionAuthService = sessionAuthService;
        this.authProperties = authProperties;
    }

    @Override
    // 根据本地配置创建默认管理员账号。
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

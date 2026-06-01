package com.yinbo.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
// 密码编码器配置。
public class PasswordConfig {

    @Bean
    // 创建 BCrypt 密码编码器。
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

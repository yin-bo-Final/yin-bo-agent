package com.yinbo.agent.config;

import com.yinbo.agent.auth.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
// Spring MVC Web 配置。
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    // 注入登录拦截器。
    public WebConfig(LoginInterceptor loginInterceptor) {
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    // 注册需要登录态保护的接口路径。
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns(
                        "/api/auth/me",
                        "/api/auth/logout",
                        "/api/chat",
                        "/api/chat/stream",
                        "/api/conversations",
                        "/api/conversations/**",
                        "/api/ingestion/**",
                        "/api/admin/**"
                );
    }
}

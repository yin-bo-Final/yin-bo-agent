package com.yinbo.gateway;

import com.yinbo.gateway.config.ConcurrencyLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ConcurrencyLimitProperties.class)
// 网关服务启动入口。
public class YinboAgentGatewayApplication {

    // 启动网关服务并加载路由、过滤器、Redis 和限流配置。
    public static void main(String[] args) {
        SpringApplication.run(YinboAgentGatewayApplication.class, args);
    }
}

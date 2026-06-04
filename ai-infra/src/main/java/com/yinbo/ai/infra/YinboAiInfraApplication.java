package com.yinbo.ai.infra;

import com.yinbo.ai.infra.config.AiModelProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AiModelProperties.class)
// AI 基础设施服务启动入口。
public class YinboAiInfraApplication {

    // 启动 AI 基础设施服务。
    public static void main(String[] args) {
        SpringApplication.run(YinboAiInfraApplication.class, args);
    }
}

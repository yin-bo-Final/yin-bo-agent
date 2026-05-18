package com.yinbo.agent;

import com.yinbo.agent.config.AiModelProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AiModelProperties.class)
public class YinboAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(YinboAgentApplication.class, args);
    }
}

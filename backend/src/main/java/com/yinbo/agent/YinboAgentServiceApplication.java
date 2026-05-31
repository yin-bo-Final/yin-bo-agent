package com.yinbo.agent;

import com.yinbo.agent.config.AiModelProperties;
import com.yinbo.agent.config.AuthProperties;
import com.yinbo.agent.config.ObjectStorageProperties;
import com.yinbo.agent.config.RagProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        AiModelProperties.class,
        AuthProperties.class,
        ObjectStorageProperties.class,
        RagProperties.class
})
@MapperScan({
        "com.yinbo.agent.auth.mapper",
        "com.yinbo.agent.chat.mapper",
        "com.yinbo.agent.ingestion.mapper",
        "com.yinbo.agent.knowledge.mapper"
})
public class YinboAgentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(YinboAgentServiceApplication.class, args);
    }
}

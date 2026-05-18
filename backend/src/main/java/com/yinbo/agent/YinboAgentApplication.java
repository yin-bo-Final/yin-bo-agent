package com.yinbo.agent;

import com.yinbo.agent.config.AiModelProperties;
import com.yinbo.agent.config.AuthProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AiModelProperties.class, AuthProperties.class})
@MapperScan({
        "com.yinbo.agent.auth.mapper",
        "com.yinbo.agent.chat.mapper"
})
public class YinboAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(YinboAgentApplication.class, args);
    }
}

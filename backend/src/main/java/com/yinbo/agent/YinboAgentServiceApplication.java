package com.yinbo.agent;

import com.yinbo.agent.config.AiInfraProperties;
import com.yinbo.agent.config.AuthProperties;
import com.yinbo.agent.config.ChatMemoryProperties;
import com.yinbo.agent.config.ConcurrencyLimitProperties;
import com.yinbo.agent.config.ObjectStorageProperties;
import com.yinbo.agent.config.RagProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        AiInfraProperties.class,
        AuthProperties.class,
        ChatMemoryProperties.class,
        ConcurrencyLimitProperties.class,
        ObjectStorageProperties.class,
        RagProperties.class
})
@MapperScan({
        "com.yinbo.agent.auth.mapper",
        "com.yinbo.agent.chat.mapper",
        "com.yinbo.agent.ingestion.mapper",
        "com.yinbo.agent.knowledge.mapper"
})
// 后端业务服务启动入口。
public class YinboAgentServiceApplication {

    // 启动后端业务服务并加载配置属性和 MyBatis Mapper。
    public static void main(String[] args) {
        SpringApplication.run(YinboAgentServiceApplication.class, args);
    }
}

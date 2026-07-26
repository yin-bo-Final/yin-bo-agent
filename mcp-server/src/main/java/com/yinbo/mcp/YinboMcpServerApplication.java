package com.yinbo.mcp;

import com.yinbo.mcp.config.LogisticsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LogisticsProperties.class)
// MCP 工具服务启动入口。
public class YinboMcpServerApplication {

    // 启动独立 MCP 工具服务。
    public static void main(String[] args) {
        SpringApplication.run(YinboMcpServerApplication.class, args);
    }
}

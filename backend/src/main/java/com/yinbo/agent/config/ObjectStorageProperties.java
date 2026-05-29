package com.yinbo.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record ObjectStorageProperties(
        String provider,
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket
) {

    public ObjectStorageProperties {
        provider = blankToDefault(provider, "rustfs");
        endpoint = blankToDefault(endpoint, "http://localhost:9000");
        accessKey = blankToDefault(accessKey, "rustfsadmin");
        secretKey = blankToDefault(secretKey, "rustfsadmin");
        bucket = blankToDefault(bucket, "yinbo-agent-documents");
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}

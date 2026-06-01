package com.yinbo.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app.gateway.request-size")
// Gateway 请求体大小限制配置属性。
public record GatewayRequestSizeProperties(
        DataSize uploadMaxSize
) {

    private static final DataSize DEFAULT_UPLOAD_MAX_SIZE = DataSize.ofMegabytes(200);

    // 给请求体大小限制配置补默认值。
    public GatewayRequestSizeProperties {
        uploadMaxSize = uploadMaxSize == null || uploadMaxSize.toBytes() <= 0
                ? DEFAULT_UPLOAD_MAX_SIZE
                : uploadMaxSize;
    }
}

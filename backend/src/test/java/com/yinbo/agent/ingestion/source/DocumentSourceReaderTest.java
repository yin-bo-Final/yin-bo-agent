package com.yinbo.agent.ingestion.source;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.config.RagProperties;
import org.junit.jupiter.api.Test;

class DocumentSourceReaderTest {

    private final DocumentSourceReader reader = new DocumentSourceReader(new RagProperties(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            1024L,
            null,
            null
    ), null);

    @Test
    void rejectsLoopbackUrlBeforeDownload() {
        assertThatThrownBy(() -> reader.fromUrl("http://127.0.0.1/document.pdf", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本机或内网地址");
    }

    @Test
    void rejectsPrivateNetworkUrlBeforeDownload() {
        assertThatThrownBy(() -> reader.fromUrl("http://192.168.1.10/document.pdf", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本机或内网地址");
    }

    @Test
    void rejectsUrlWithUserInfo() {
        assertThatThrownBy(() -> reader.fromUrl("https://user:pass@example.com/document.pdf", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持携带用户名或密码");
    }
}

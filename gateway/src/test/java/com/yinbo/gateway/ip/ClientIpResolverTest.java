package com.yinbo.gateway.ip;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class ClientIpResolverTest {

    // 非可信直连来源不能通过请求头伪造客户端 IP。
    @Test
    void ignoresForwardedHeadersWhenRemoteAddressIsNotTrusted() throws Exception {
        ClientIpResolver resolver = new ClientIpResolver(new TrustedProxyProperties(List.of("10.0.0.0/8")));
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/chat")
                .remoteAddress(remoteAddress("203.0.113.10"))
                .header("X-Forwarded-For", "1.1.1.1")
                .header("X-Real-IP", "2.2.2.2")
                .build();

        String clientIp = resolver.resolve(request);

        assertThat(clientIp).isEqualTo("203.0.113.10");
    }

    // 可信代理来源会从 X-Forwarded-For 右往左找到第一个非可信代理 IP。
    @Test
    void resolvesFirstUntrustedAddressFromForwardedForWhenRemoteAddressIsTrusted() throws Exception {
        ClientIpResolver resolver = new ClientIpResolver(new TrustedProxyProperties(List.of("10.0.0.0/8")));
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/chat")
                .remoteAddress(remoteAddress("10.0.0.2"))
                .header("X-Forwarded-For", "198.51.100.7, 10.0.0.3")
                .build();

        String clientIp = resolver.resolve(request);

        assertThat(clientIp).isEqualTo("198.51.100.7");
    }

    // 所有转发链路地址都属于可信代理时回退到最左侧地址。
    @Test
    void fallsBackToLeftMostForwardedAddressWhenAllForwardedAddressesAreTrusted() throws Exception {
        ClientIpResolver resolver = new ClientIpResolver(new TrustedProxyProperties(List.of("10.0.0.0/8")));
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/chat")
                .remoteAddress(remoteAddress("10.0.0.2"))
                .header("X-Forwarded-For", "10.0.0.4, 10.0.0.3")
                .build();

        String clientIp = resolver.resolve(request);

        assertThat(clientIp).isEqualTo("10.0.0.4");
    }

    // 转发头只接受 IP 字面量，避免请求头里的主机名触发 DNS 查询。
    @Test
    void ignoresHostNamesInForwardedHeaders() throws Exception {
        ClientIpResolver resolver = new ClientIpResolver(new TrustedProxyProperties(List.of("10.0.0.0/8")));
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/chat")
                .remoteAddress(remoteAddress("10.0.0.2"))
                .header("X-Forwarded-For", "example.com")
                .header("X-Real-IP", "198.51.100.9")
                .build();

        String clientIp = resolver.resolve(request);

        assertThat(clientIp).isEqualTo("198.51.100.9");
    }

    // 构造测试用远端地址。
    private static InetSocketAddress remoteAddress(String ip) throws Exception {
        return new InetSocketAddress(InetAddress.getByName(ip), 12345);
    }
}

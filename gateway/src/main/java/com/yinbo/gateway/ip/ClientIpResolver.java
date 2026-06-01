package com.yinbo.gateway.ip;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
// 客户端真实 IP 解析器。
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String UNKNOWN_IP = "unknown";

    private final List<CidrBlock> trustedProxyBlocks;

    // 加载可信代理网段。
    public ClientIpResolver(TrustedProxyProperties trustedProxyProperties) {
        this.trustedProxyBlocks = trustedProxyProperties.trustedProxies()
                .stream()
                .map(CidrBlock::parse)
                .flatMap(Optional::stream)
                .toList();
    }

    // 解析客户端真实 IP。
    public String resolve(ServerHttpRequest request) {
        InetAddress remoteAddress = remoteAddress(request);
        if (remoteAddress == null) {
            return UNKNOWN_IP;
        }
        if (!isTrustedProxy(remoteAddress)) {
            return sanitizeIdentity(remoteAddress.getHostAddress());
        }

        String forwardedFor = request.getHeaders().getFirst(X_FORWARDED_FOR);
        String forwardedIp = resolveForwardedFor(forwardedFor);
        if (StringUtils.hasText(forwardedIp)) {
            return forwardedIp;
        }

        String realIp = request.getHeaders().getFirst(X_REAL_IP);
        InetAddress realAddress = parseIp(realIp);
        if (realAddress != null) {
            return sanitizeIdentity(realAddress.getHostAddress());
        }
        return sanitizeIdentity(remoteAddress.getHostAddress());
    }

    // 从请求连接中读取直接来源 IP。
    private InetAddress remoteAddress(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return null;
        }
        return remoteAddress.getAddress();
    }

    // 从 X-Forwarded-For 中解析第一个非可信代理 IP。
    private String resolveForwardedFor(String forwardedFor) {
        if (!StringUtils.hasText(forwardedFor)) {
            return null;
        }

        String[] parts = forwardedFor.split(",");
        List<InetAddress> validAddresses = new ArrayList<>();
        for (String part : parts) {
            InetAddress address = parseIp(part);
            if (address != null) {
                validAddresses.add(address);
            }
        }
        if (validAddresses.isEmpty()) {
            return null;
        }

        for (int index = validAddresses.size() - 1; index >= 0; index--) {
            InetAddress address = validAddresses.get(index);
            if (!isTrustedProxy(address)) {
                return sanitizeIdentity(address.getHostAddress());
            }
        }
        return sanitizeIdentity(validAddresses.get(0).getHostAddress());
    }

    // 判断 IP 是否属于可信代理网段。
    private boolean isTrustedProxy(InetAddress address) {
        return trustedProxyBlocks.stream().anyMatch(block -> block.matches(address));
    }

    // 解析单个 IP 字符串。
    private static InetAddress parseIp(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String ip = value.trim();
        if (ip.startsWith("[") && ip.endsWith("]") && ip.length() > 2) {
            ip = ip.substring(1, ip.length() - 1);
        }
        int zoneIndex = ip.indexOf('%');
        if (zoneIndex > 0) {
            ip = ip.substring(0, zoneIndex);
        }
        if (!isIpLiteral(ip)) {
            return null;
        }
        try {
            return InetAddress.getByName(ip);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    // 判断文本是否是 IP 字面量，避免请求头触发 DNS 查询。
    private static boolean isIpLiteral(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if (value.contains(":")) {
            return value.matches("[0-9A-Fa-f:.]+");
        }
        return value.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    // 清洗限流和日志中的 IP 文本。
    private static String sanitizeIdentity(String value) {
        if (!StringUtils.hasText(value)) {
            return UNKNOWN_IP;
        }
        return value.replaceAll("[^A-Za-z0-9:.\\-]", "_");
    }

    // CIDR 网段匹配器。
    private record CidrBlock(
            byte[] networkAddress,
            int prefixLength
    ) {

        // 解析 CIDR 配置。
        private static Optional<CidrBlock> parse(String value) {
            if (!StringUtils.hasText(value)) {
                return Optional.empty();
            }
            try {
                String[] parts = value.trim().split("/", 2);
                InetAddress network = parseIp(parts[0]);
                if (network == null) {
                    log.warn("event=trusted_proxy_config_invalid value={}", sanitizeLogValue(value));
                    return Optional.empty();
                }
                int maxPrefixLength = network.getAddress().length * 8;
                int prefixLength = parts.length == 2 ? Integer.parseInt(parts[1]) : maxPrefixLength;
                if (prefixLength < 0 || prefixLength > maxPrefixLength) {
                    log.warn("event=trusted_proxy_config_invalid value={}", sanitizeLogValue(value));
                    return Optional.empty();
                }
                return Optional.of(new CidrBlock(network.getAddress(), prefixLength));
            } catch (IllegalArgumentException exception) {
                log.warn("event=trusted_proxy_config_invalid value={}", sanitizeLogValue(value));
                return Optional.empty();
            }
        }

        // 判断 IP 是否命中当前 CIDR 网段。
        private boolean matches(InetAddress address) {
            byte[] targetAddress = address.getAddress();
            if (targetAddress.length != networkAddress.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int index = 0; index < fullBytes; index++) {
                if (targetAddress[index] != networkAddress[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (targetAddress[fullBytes] & mask) == (networkAddress[fullBytes] & mask);
        }
    }

    // 清洗配置错误日志文本。
    private static String sanitizeLogValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 128 ? sanitized : sanitized.substring(0, 128);
    }
}

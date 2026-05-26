package com.yinbo.agent.ingestion.source;

import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.config.RagProperties;
import com.yinbo.agent.ingestion.DocumentSourceType;
import com.yinbo.agent.ingestion.RawDocument;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class DocumentSourceReader {

    private static final int CONNECT_TIMEOUT_MILLIS = 8_000;
    private static final int READ_TIMEOUT_MILLIS = 20_000;
    private static final int MAX_REDIRECTS = 5;

    private final RagProperties ragProperties;

    public DocumentSourceReader(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public RawDocument fromUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请上传一个非空文件");
        }
        String fileName = sanitizeFileName(file.getOriginalFilename(), "uploaded-document");
        byte[] bytes;
        try {
            bytes = readWithLimit(file.getInputStream(), ragProperties.maxSourceBytes());
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "上传文件读取失败");
        }
        return new RawDocument(
                DocumentSourceType.UPLOAD,
                null,
                fileName,
                file.getContentType(),
                bytes.length,
                bytes
        );
    }

    public RawDocument fromUrl(String rawUrl, String requestedFileName) {
        URI uri = parseHttpUri(rawUrl);
        try {
            DownloadConnection downloadConnection = openSafeConnection(uri);
            HttpURLConnection connection = downloadConnection.connection();
            try {
                URI finalUri = downloadConnection.uri();
                long contentLength = connection.getContentLengthLong();
                if (contentLength > ragProperties.maxSourceBytes()) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "URL 文件超过当前允许大小");
                }

                byte[] bytes;
                try (InputStream inputStream = connection.getInputStream()) {
                    bytes = readWithLimit(inputStream, ragProperties.maxSourceBytes());
                }
                String fileName = sanitizeFileName(
                        requestedFileName == null || requestedFileName.isBlank()
                                ? fileNameFromUri(finalUri)
                                : requestedFileName,
                        "remote-document"
                );
                return new RawDocument(
                        DocumentSourceType.URL,
                        finalUri.toString(),
                        fileName,
                        connection.getContentType(),
                        bytes.length,
                        bytes
                );
            } finally {
                connection.disconnect();
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "URL 下载失败，请确认地址可访问");
        }
    }

    private URI parseHttpUri(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "URL 不能为空");
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            validateHttpUriShape(uri);
            return uri;
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "URL 格式不正确");
        }
    }

    private DownloadConnection openSafeConnection(URI initialUri) throws IOException {
        URI currentUri = initialUri;
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            validateSafeRemoteUri(currentUri);
            URL url = currentUri.toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("User-Agent", "yinbo-agent-ingestion/0.1");
            connection.connect();

            int status = connection.getResponseCode();
            if (isRedirect(status)) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.isBlank()) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "URL 重定向缺少 Location");
                }
                currentUri = currentUri.resolve(location);
                validateHttpUriShape(currentUri);
                continue;
            }
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new BusinessException(HttpStatus.BAD_REQUEST, "URL 下载失败，HTTP 状态码：" + status);
            }
            return new DownloadConnection(connection, currentUri);
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "URL 重定向次数过多");
    }

    private void validateHttpUriShape(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "URL 仅支持 http 或 https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "URL 必须包含有效主机名");
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "URL 不支持携带用户名或密码");
        }
    }

    private void validateSafeRemoteUri(URI uri) {
        validateHttpUriShape(uri);
        try {
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            if (addresses.length == 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "URL 主机无法解析");
            }
            for (InetAddress address : addresses) {
                if (isBlockedAddress(address)) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "URL 不允许访问本机或内网地址");
                }
            }
        } catch (UnknownHostException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "URL 主机无法解析");
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isCarrierGradeNatAddress(address)
                || isUniqueLocalAddress(address);
    }

    private boolean isCarrierGradeNatAddress(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 100 && second >= 64 && second <= 127;
    }

    private boolean isUniqueLocalAddress(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        return (bytes[0] & 0xfe) == 0xfc;
    }

    private boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307
                || status == 308;
    }

    private byte[] readWithLimit(InputStream inputStream, long maxBytes) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long totalBytes = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            totalBytes += read;
            if (totalBytes > maxBytes) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "文档超过当前允许大小");
            }
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private String fileNameFromUri(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || path.endsWith("/")) {
            return "remote-document";
        }
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return URLDecoder.decode(fileName, StandardCharsets.UTF_8);
    }

    private String sanitizeFileName(String value, String defaultValue) {
        String fileName = value == null || value.isBlank() ? defaultValue : value.trim();
        fileName = fileName.replace("\\", "/");
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        fileName = fileName.replaceAll("[\\p{Cntrl}]", "");
        return fileName.isBlank() ? defaultValue : fileName;
    }

    private record DownloadConnection(HttpURLConnection connection, URI uri) {
    }
}

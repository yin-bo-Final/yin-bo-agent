package com.yinbo.agent.storage.service;

import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.config.ObjectStorageProperties;
import com.yinbo.agent.storage.StoredObject;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.ObjectWriteResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
// 对象存储服务。
public class ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

    private final ObjectStorageProperties properties;
    private final MinioClient minioClient;

    // 初始化 MinIO 兼容客户端。
    public ObjectStorageService(ObjectStorageProperties properties) {
        this.properties = properties;
        this.minioClient = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    // 上传原始文档到对象存储。
    public StoredObject uploadOriginalDocument(
            String fileName,
            String contentType,
            long sizeBytes,
            InputStream inputStream
    ) {
        if (sizeBytes <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请上传一个非空文件");
        }
        String objectKey = originalDocumentObjectKey(fileName);
        try {
            ensureBucket();
            PutObjectArgs.Builder putObjectBuilder = PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(inputStream, sizeBytes, -1);
            if (contentType != null && !contentType.isBlank()) {
                putObjectBuilder.contentType(contentType);
            }
            ObjectWriteResponse response = minioClient.putObject(putObjectBuilder.build());
            return new StoredObject(
                    properties.provider(),
                    response.bucket(),
                    response.object(),
                    response.etag(),
                    sizeBytes
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "原始文件上传 RustFS 失败，请检查对象存储配置");
        }
    }

    // 打开对象存储中的文件流。
    public InputStream open(String bucket, String objectKey) throws IOException {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(blankToDefault(bucket, properties.bucket()))
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new IOException("打开对象存储文件失败", exception);
        }
    }

    // 静默删除对象存储文件。
    public void deleteQuietly(String bucket, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(blankToDefault(bucket, properties.bucket()))
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            log.warn("Failed to delete object storage file. bucket={}, objectKey={}", bucket, objectKey, exception);
        }
    }

    // 确保目标 bucket 已存在。
    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(properties.bucket())
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(properties.bucket())
                    .build());
        }
    }

    // 生成原始文档对象 key。
    private String originalDocumentObjectKey(String fileName) {
        LocalDate today = LocalDate.now();
        return "ingestion/original/"
                + today.getYear()
                + "/"
                + twoDigits(today.getMonthValue())
                + "/"
                + twoDigits(today.getDayOfMonth())
                + "/"
                + UUID.randomUUID()
                + "/"
                + sanitizeObjectFileName(fileName);
    }

    // 清洗对象 key 中的文件名。
    private String sanitizeObjectFileName(String value) {
        String fileName = value == null || value.isBlank() ? "uploaded-document" : value.trim();
        fileName = fileName.replace("\\", "/");
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        fileName = fileName.replaceAll("[\\p{Cntrl}]", "");
        return fileName.isBlank() ? "uploaded-document" : fileName;
    }

    // 将日期数字格式化为两位。
    private String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}

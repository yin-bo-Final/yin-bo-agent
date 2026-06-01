package com.yinbo.agent.storage;

// 已存储对象元数据。
public record StoredObject(
        String provider,
        String bucket,
        String objectKey,
        String etag,
        long sizeBytes
) {
}

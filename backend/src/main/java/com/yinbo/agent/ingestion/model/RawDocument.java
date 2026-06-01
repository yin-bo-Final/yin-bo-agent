package com.yinbo.agent.ingestion.model;

// 待入库的原始文档。
public record RawDocument(
        DocumentSourceType sourceType,
        String sourceUrl,
        String fileName,
        String contentType,
        long sizeBytes,
        byte[] bytes,
        String storageProvider,
        String storageBucket,
        String storageObjectKey,
        String storageEtag
) {

    // 判断原始文档是否已经存入对象存储。
    public boolean hasStoredObject() {
        return storageObjectKey != null && !storageObjectKey.isBlank();
    }
}

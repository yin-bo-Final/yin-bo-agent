package com.yinbo.agent.ingestion;

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

    public boolean hasStoredObject() {
        return storageObjectKey != null && !storageObjectKey.isBlank();
    }
}

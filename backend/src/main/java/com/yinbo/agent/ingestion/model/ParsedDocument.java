package com.yinbo.agent.ingestion.model;

import java.util.Map;

// 文档解析后的文本和元数据。
public record ParsedDocument(
        String text,
        String title,
        Map<String, String> parserMetadata
) {
}

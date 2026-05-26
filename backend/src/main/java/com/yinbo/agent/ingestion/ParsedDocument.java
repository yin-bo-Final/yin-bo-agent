package com.yinbo.agent.ingestion;

import java.util.Map;

public record ParsedDocument(
        String text,
        String title,
        Map<String, String> parserMetadata
) {
}

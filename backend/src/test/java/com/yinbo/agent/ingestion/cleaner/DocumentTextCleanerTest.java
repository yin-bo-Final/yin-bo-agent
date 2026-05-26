package com.yinbo.agent.ingestion.cleaner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentTextCleanerTest {

    private final DocumentTextCleaner cleaner = new DocumentTextCleaner();

    @Test
    void cleansPdfLineBreakNoise() {
        String cleaned = cleaner.clean("""
                Spring AI 实战指南

                第 1 页

                RAG 是 Retrieval-
                Augmented Generation 的缩写。



                它的核心流程是检索。
                """);

        assertThat(cleaned).doesNotContain("第 1 页");
        assertThat(cleaned).contains("RetrievalAugmented Generation");
        assertThat(cleaned).doesNotContain("\n\n\n");
    }
}

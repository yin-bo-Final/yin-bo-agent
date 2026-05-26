package com.yinbo.agent.ingestion.splitter;

import static org.assertj.core.api.Assertions.assertThat;

import com.yinbo.agent.ingestion.ChunkingOptions;
import com.yinbo.agent.ingestion.ChunkingStrategy;
import com.yinbo.agent.ingestion.DocumentChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecursiveDocumentChunkSplitterTest {

    private final RecursiveDocumentChunkSplitter splitter = new RecursiveDocumentChunkSplitter();

    @Test
    void attachesMarkdownHeadingPathToChunks() {
        String text = """
                # Spring AI

                RAG 第一段内容。RAG 第二段内容。RAG 第三段内容。

                ## PGVector

                向量检索第一段内容。向量检索第二段内容。向量检索第三段内容。
                """;

        List<DocumentChunk> chunks = splitter.split(
                text,
                "课程笔记",
                new ChunkingOptions(ChunkingStrategy.RECURSIVE, 120, 20, 10)
        );

        assertThat(chunks).isNotEmpty();
        assertThat(chunks)
                .extracting(DocumentChunk::title)
                .anyMatch(title -> title.contains("课程笔记 > Spring AI"))
                .anyMatch(title -> title.contains("课程笔记 > Spring AI > PGVector"));
    }
}

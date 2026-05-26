package com.yinbo.agent.ingestion.splitter;

import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.ingestion.ChunkingOptions;
import com.yinbo.agent.ingestion.DocumentChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RecursiveDocumentChunkSplitter {

    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final List<String> SEPARATORS = List.of(
            "\n\n",
            "\n",
            "。", "！", "？",
            ".", "!", "?",
            "；", ";",
            "，", ",",
            " "
    );

    public List<DocumentChunk> split(String text, String documentTitle, ChunkingOptions options) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (options.strategy() == com.yinbo.agent.ingestion.ChunkingStrategy.NONE) {
            String title = documentTitle == null || documentTitle.isBlank() ? "未命名文档" : documentTitle.trim();
            return List.of(new DocumentChunk(0, title, text.trim()));
        }

        List<Section> sections = splitSections(text, documentTitle);
        List<TitledText> rawChunks = new ArrayList<>();
        for (Section section : sections) {
            splitRecursive(section.content(), options.chunkSize(), 0).stream()
                    .map(String::trim)
                    .filter(chunk -> !chunk.isBlank())
                    .map(chunk -> new TitledText(section.title(), chunk))
                    .forEach(rawChunks::add);
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        for (int index = 0; index < rawChunks.size(); index++) {
            TitledText current = rawChunks.get(index);
            String content = current.content();
            if (options.chunkOverlap() > 0 && index > 0) {
                String previous = rawChunks.get(index - 1).content();
                String overlap = previous.substring(Math.max(0, previous.length() - options.chunkOverlap()));
                content = overlap + "\n" + content;
            }
            chunks.add(new DocumentChunk(chunks.size(), current.title(), content.trim()));
        }

        if (chunks.size() > options.maxChunks()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "文档切块数量超过上限，请调大 chunkSize 或 maxChunks"
            );
        }
        return chunks;
    }

    private List<Section> splitSections(String text, String documentTitle) {
        String fallbackTitle = documentTitle == null || documentTitle.isBlank() ? "未命名文档" : documentTitle.trim();
        String[] lines = text.split("\\n", -1);
        List<Section> sections = new ArrayList<>();
        List<String> titleStack = new ArrayList<>();
        StringBuilder currentContent = new StringBuilder();
        String currentTitle = fallbackTitle;

        for (String line : lines) {
            Matcher matcher = MARKDOWN_HEADING_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                appendSection(sections, currentTitle, currentContent);
                int level = matcher.group(1).length();
                String heading = matcher.group(2).trim();
                while (titleStack.size() >= level) {
                    titleStack.remove(titleStack.size() - 1);
                }
                titleStack.add(heading);
                currentTitle = fallbackTitle + " > " + String.join(" > ", titleStack);
                currentContent.append(line).append('\n');
                continue;
            }
            currentContent.append(line).append('\n');
        }
        appendSection(sections, currentTitle, currentContent);
        return sections.isEmpty() ? List.of(new Section(fallbackTitle, text)) : sections;
    }

    private void appendSection(List<Section> sections, String title, StringBuilder content) {
        String sectionContent = content.toString().trim();
        if (!sectionContent.isBlank()) {
            sections.add(new Section(title, sectionContent));
        }
        content.setLength(0);
    }

    private List<String> splitRecursive(String text, int chunkSize, int separatorIndex) {
        if (text.length() <= chunkSize) {
            return List.of(text);
        }
        if (separatorIndex >= SEPARATORS.size()) {
            return hardCut(text, chunkSize);
        }

        String separator = SEPARATORS.get(separatorIndex);
        String[] parts = text.split(Pattern.quote(separator));
        if (parts.length <= 1) {
            return splitRecursive(text, chunkSize, separatorIndex + 1);
        }

        List<String> grouped = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            String segment = i < parts.length - 1 ? part + separator : part;
            if (segment.isBlank()) {
                continue;
            }
            if (segment.length() > chunkSize) {
                if (!current.isEmpty()) {
                    grouped.add(current.toString());
                    current.setLength(0);
                }
                grouped.addAll(splitRecursive(segment, chunkSize, separatorIndex + 1));
                continue;
            }
            if (current.length() + segment.length() > chunkSize && !current.isEmpty()) {
                grouped.add(current.toString());
                current.setLength(0);
            }
            current.append(segment);
        }
        if (!current.isEmpty()) {
            grouped.add(current.toString());
        }

        if (grouped.size() == 1 && grouped.get(0).length() > chunkSize) {
            return splitRecursive(grouped.get(0), chunkSize, separatorIndex + 1);
        }
        return grouped;
    }

    private List<String> hardCut(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < text.length(); start += chunkSize) {
            chunks.add(text.substring(start, Math.min(start + chunkSize, text.length())));
        }
        return chunks;
    }

    private record Section(String title, String content) {
    }

    private record TitledText(String title, String content) {
    }
}

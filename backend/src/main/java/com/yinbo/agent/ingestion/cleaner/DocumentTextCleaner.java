package com.yinbo.agent.ingestion.cleaner;

import org.springframework.stereotype.Component;

@Component
public class DocumentTextCleaner {

    public String clean(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String text = rawText
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replace('\u3000', ' ');

        text = text.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");
        text = text.replaceAll("(?i)([a-z])-[\\n]+([a-z])", "$1$2");
        text = text.replaceAll("(?<=[a-zA-Z,;:])\\n(?=[a-zA-Z])", " ");
        text = text.replaceAll("(?m)^\\s*第?\\s*\\d+\\s*页?\\s*$", "");
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("(?m)^\\s+$", "");
        text = text.replaceAll("\\n{3,}", "\n\n");

        return text.trim();
    }
}

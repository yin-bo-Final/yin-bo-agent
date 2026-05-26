package com.yinbo.agent.ingestion;

import com.yinbo.agent.common.BusinessException;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public enum ChunkingStrategy {
    AUTO,
    RECURSIVE,
    NONE;

    public static ChunkingStrategy from(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        try {
            return ChunkingStrategy.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的切块策略：" + value);
        }
    }
}

CREATE TABLE IF NOT EXISTS conversation_memory_summary (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    summary_content TEXT NOT NULL,
    covered_start_message_id BIGINT NOT NULL,
    covered_end_message_id BIGINT NOT NULL,
    source_message_count INTEGER NOT NULL DEFAULT 0,
    summary_tokens INTEGER NOT NULL DEFAULT 0,
    compression_model_id VARCHAR(128) NULL,
    compression_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    trigger_type VARCHAR(20) NOT NULL DEFAULT 'AUTO',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE conversation_memory_summary
ADD COLUMN IF NOT EXISTS compression_version VARCHAR(32) NOT NULL DEFAULT 'v1';

ALTER TABLE conversation_memory_summary
ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(20) NOT NULL DEFAULT 'AUTO';

ALTER TABLE conversation_memory_summary
ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_conversation_memory_summary_active
ON conversation_memory_summary (conversation_id, user_id, status, id DESC);

CREATE INDEX IF NOT EXISTS idx_conversation_memory_summary_watermark
ON conversation_memory_summary (conversation_id, user_id, covered_end_message_id DESC);

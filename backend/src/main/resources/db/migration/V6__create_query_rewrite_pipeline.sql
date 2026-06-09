CREATE TABLE IF NOT EXISTS chat_terminology_term (
    id BIGINT PRIMARY KEY,
    canonical_name VARCHAR(128) NOT NULL,
    term_type VARCHAR(64) NOT NULL DEFAULT 'TECH',
    description VARCHAR(512) NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_terminology_alias (
    id BIGINT PRIMARY KEY,
    term_id BIGINT NOT NULL,
    alias_name VARCHAR(128) NOT NULL,
    alias_normalized VARCHAR(128) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_query_rewrite_record (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_message_id BIGINT NULL,
    original_query TEXT NOT NULL,
    normalized_query TEXT NOT NULL,
    rewritten_query TEXT NOT NULL,
    sub_questions_json TEXT NOT NULL,
    matched_terms_json TEXT NOT NULL,
    model_id VARCHAR(128) NULL,
    prompt_version VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    fallback_reason VARCHAR(64) NULL,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message VARCHAR(512) NULL,
    raw_model_response TEXT NULL,
    duration_ms BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_pipeline_config (
    id BIGINT PRIMARY KEY,
    terminology_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    llm_rewrite_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    rule_split_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    fallback_policy VARCHAR(32) NOT NULL DEFAULT 'TERM_ONLY',
    rewrite_timeout_ms INTEGER NOT NULL DEFAULT 3000,
    rewrite_context_turns INTEGER NOT NULL DEFAULT 3,
    updated_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE chat_terminology_term
ADD COLUMN IF NOT EXISTS description VARCHAR(512) NULL;

ALTER TABLE chat_terminology_term
ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 0;

ALTER TABLE chat_terminology_term
ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE chat_terminology_alias
ADD COLUMN IF NOT EXISTS alias_normalized VARCHAR(128);

ALTER TABLE chat_terminology_alias
ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 0;

ALTER TABLE chat_terminology_alias
ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE chat_terminology_alias
SET alias_normalized = lower(trim(alias_name))
WHERE alias_normalized IS NULL OR alias_normalized = '';

CREATE INDEX IF NOT EXISTS idx_chat_terminology_term_enabled
ON chat_terminology_term (enabled, priority DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_chat_terminology_alias_term
ON chat_terminology_alias (term_id);

CREATE INDEX IF NOT EXISTS idx_chat_terminology_alias_enabled
ON chat_terminology_alias (enabled, priority DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_chat_terminology_alias_normalized
ON chat_terminology_alias (alias_normalized);

CREATE INDEX IF NOT EXISTS idx_chat_query_rewrite_record_message
ON chat_query_rewrite_record (conversation_id, user_id, user_message_id);

CREATE INDEX IF NOT EXISTS idx_chat_query_rewrite_record_created
ON chat_query_rewrite_record (created_at DESC);

INSERT INTO chat_pipeline_config (
    id,
    terminology_enabled,
    llm_rewrite_enabled,
    rule_split_enabled,
    fallback_policy,
    rewrite_timeout_ms,
    rewrite_context_turns
)
VALUES (1, TRUE, TRUE, TRUE, 'TERM_ONLY', 3000, 3)
ON CONFLICT (id) DO NOTHING;

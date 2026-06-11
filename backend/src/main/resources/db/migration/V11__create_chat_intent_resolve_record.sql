CREATE TABLE IF NOT EXISTS chat_intent_resolve_record (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_message_id BIGINT NULL,
    original_query TEXT NOT NULL,
    normalized_query TEXT NULL,
    rewritten_query TEXT NULL,
    sub_questions_json TEXT NOT NULL,
    intents_json TEXT NOT NULL,
    selected_nodes_json TEXT NOT NULL,
    sub_question_intents_json TEXT NOT NULL,
    model_id VARCHAR(128) NULL,
    ambiguous BOOLEAN NOT NULL DEFAULT FALSE,
    guidance_question TEXT NULL,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message VARCHAR(512) NULL,
    duration_ms BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_intent_resolve_record_message
ON chat_intent_resolve_record (conversation_id, user_id, user_message_id);

CREATE INDEX IF NOT EXISTS idx_chat_intent_resolve_record_created
ON chat_intent_resolve_record (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_intent_resolve_record_ambiguous
ON chat_intent_resolve_record (ambiguous, created_at DESC);

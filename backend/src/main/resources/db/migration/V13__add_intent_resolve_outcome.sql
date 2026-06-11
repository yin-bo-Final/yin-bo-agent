ALTER TABLE chat_intent_resolve_record
ADD COLUMN IF NOT EXISTS outcome VARCHAR(32) NULL;

ALTER TABLE chat_intent_resolve_record
ADD COLUMN IF NOT EXISTS fallback_reason VARCHAR(64) NULL;

CREATE INDEX IF NOT EXISTS idx_chat_intent_resolve_record_outcome
ON chat_intent_resolve_record (outcome, created_at DESC);

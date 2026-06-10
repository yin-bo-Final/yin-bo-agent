DROP INDEX IF EXISTS idx_chat_intent_rule_enabled;

ALTER TABLE chat_intent_rule
DROP COLUMN IF EXISTS priority;

CREATE INDEX IF NOT EXISTS idx_chat_intent_rule_enabled
ON chat_intent_rule (enabled, score DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_chat_message_created
ON chat_message (created_at DESC);

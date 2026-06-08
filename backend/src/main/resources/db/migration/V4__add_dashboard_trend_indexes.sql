CREATE INDEX IF NOT EXISTS idx_chat_conversation_created
ON chat_conversation (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_auth_user_last_login
ON auth_user (last_login_at DESC);

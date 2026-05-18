CREATE TABLE IF NOT EXISTS auth_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE auth_user DROP CONSTRAINT IF EXISTS auth_user_username_key;
DROP INDEX IF EXISTS uk_auth_user_username;

CREATE UNIQUE INDEX IF NOT EXISTS uk_auth_user_username_active
ON auth_user (username)
WHERE status = 1;

CREATE TABLE IF NOT EXISTS chat_conversation (
    id BIGINT PRIMARY KEY,
    conversation_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    model_id VARCHAR(128) NOT NULL,
    last_message_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_conversation_no
ON chat_conversation (conversation_no);

CREATE INDEX IF NOT EXISTS idx_chat_conversation_user_created
ON chat_conversation (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_conversation_user_last_message
ON chat_conversation (user_id, last_message_at DESC);

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    model_id VARCHAR(128) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_message_conversation_created
ON chat_message (conversation_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_chat_message_user_created
ON chat_message (user_id, created_at DESC);

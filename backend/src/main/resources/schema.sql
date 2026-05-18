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

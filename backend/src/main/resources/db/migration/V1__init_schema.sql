CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;

CREATE TABLE IF NOT EXISTS auth_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    status INTEGER NOT NULL DEFAULT 1,
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE auth_user
ADD COLUMN IF NOT EXISTS role VARCHAR(32) NOT NULL DEFAULT 'USER';

ALTER TABLE auth_user DROP CONSTRAINT IF EXISTS ck_auth_user_role;

ALTER TABLE auth_user
ADD CONSTRAINT ck_auth_user_role CHECK (role IN ('ADMIN', 'USER'));

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
    pinned_at TIMESTAMP NULL,
    last_message_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE chat_conversation
ADD COLUMN IF NOT EXISTS pinned_at TIMESTAMP NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_conversation_no
ON chat_conversation (conversation_no);

CREATE INDEX IF NOT EXISTS idx_chat_conversation_user_pinned
ON chat_conversation (user_id, pinned_at DESC)
WHERE pinned_at IS NOT NULL;

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
    response_duration_ms BIGINT NULL,
    prompt_tokens INTEGER NULL,
    completion_tokens INTEGER NULL,
    total_tokens INTEGER NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE chat_message
ADD COLUMN IF NOT EXISTS response_duration_ms BIGINT NULL;

ALTER TABLE chat_message
ADD COLUMN IF NOT EXISTS prompt_tokens INTEGER NULL;

ALTER TABLE chat_message
ADD COLUMN IF NOT EXISTS completion_tokens INTEGER NULL;

ALTER TABLE chat_message
ADD COLUMN IF NOT EXISTS total_tokens INTEGER NULL;

CREATE INDEX IF NOT EXISTS idx_chat_message_conversation_created
ON chat_message (conversation_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_chat_message_user_created
ON chat_message (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT PRIMARY KEY,
    knowledge_base_no VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    embedding_model VARCHAR(128) NOT NULL,
    collection_name VARCHAR(128) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_base_no
ON knowledge_base (knowledge_base_no);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_base_collection
ON knowledge_base (collection_name);

CREATE INDEX IF NOT EXISTS idx_knowledge_base_created
ON knowledge_base (created_at DESC);

CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT PRIMARY KEY,
    document_no VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_url TEXT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NULL,
    parser VARCHAR(64) NOT NULL DEFAULT 'TIKA',
    original_size_bytes BIGINT NOT NULL DEFAULT 0,
    storage_provider VARCHAR(32) NULL,
    storage_bucket VARCHAR(128) NULL,
    storage_object_key TEXT NULL,
    storage_etag VARCHAR(128) NULL,
    text_content TEXT NULL,
    text_char_count INTEGER NOT NULL DEFAULT 0,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    error_message TEXT NULL,
    chunk_strategy VARCHAR(32) NOT NULL DEFAULT 'RECURSIVE',
    chunk_size INTEGER NOT NULL DEFAULT 1000,
    chunk_overlap INTEGER NOT NULL DEFAULT 150,
    max_chunks INTEGER NOT NULL DEFAULT 200,
    text_extracted_at TIMESTAMP NULL,
    parse_duration_ms BIGINT NOT NULL DEFAULT 0,
    chunk_duration_ms BIGINT NOT NULL DEFAULT 0,
    embedding_duration_ms BIGINT NOT NULL DEFAULT 0,
    other_duration_ms BIGINT NOT NULL DEFAULT 0,
    total_duration_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE knowledge_document
ADD COLUMN IF NOT EXISTS knowledge_base_id BIGINT NULL;

ALTER TABLE knowledge_document
ADD COLUMN IF NOT EXISTS text_content TEXT NULL;

ALTER TABLE knowledge_document
ADD COLUMN IF NOT EXISTS storage_provider VARCHAR(32) NULL;

ALTER TABLE knowledge_document
ADD COLUMN IF NOT EXISTS storage_bucket VARCHAR(128) NULL;

ALTER TABLE knowledge_document
ADD COLUMN IF NOT EXISTS storage_object_key TEXT NULL;

ALTER TABLE knowledge_document
ADD COLUMN IF NOT EXISTS storage_etag VARCHAR(128) NULL;

ALTER TABLE knowledge_document
ADD COLUMN IF NOT EXISTS text_extracted_at TIMESTAMP NULL;

ALTER TABLE knowledge_document
ADD COLUMN IF NOT EXISTS parse_duration_ms BIGINT NOT NULL DEFAULT 0;

ALTER TABLE knowledge_document
ADD COLUMN IF NOT EXISTS chunk_duration_ms BIGINT NOT NULL DEFAULT 0;

ALTER TABLE knowledge_document
ADD COLUMN IF NOT EXISTS embedding_duration_ms BIGINT NOT NULL DEFAULT 0;

ALTER TABLE knowledge_document
ADD COLUMN IF NOT EXISTS other_duration_ms BIGINT NOT NULL DEFAULT 0;

ALTER TABLE knowledge_document
ADD COLUMN IF NOT EXISTS total_duration_ms BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_document_no
ON knowledge_document (document_no);

CREATE INDEX IF NOT EXISTS idx_knowledge_document_base_created
ON knowledge_document (knowledge_base_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_knowledge_document_user_created
ON knowledge_document (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT PRIMARY KEY,
    chunk_no VARCHAR(64) NOT NULL,
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    vector_document_id VARCHAR(128) NOT NULL,
    chunk_index INTEGER NOT NULL,
    title VARCHAR(255) NULL,
    content TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    token_count INTEGER NOT NULL DEFAULT 0,
    char_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE knowledge_chunk
ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE knowledge_chunk
ADD COLUMN IF NOT EXISTS token_count INTEGER NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_chunk_no
ON knowledge_chunk (chunk_no);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_chunk_vector_document
ON knowledge_chunk (vector_document_id);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_document_index
ON knowledge_chunk (document_id, chunk_index ASC);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_user_created
ON knowledge_chunk (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.knowledge_chunk_vector (
    id TEXT PRIMARY KEY,
    content TEXT,
    metadata JSON,
    embedding vector(1024)
);

CREATE INDEX IF NOT EXISTS knowledge_chunk_vector_index
ON public.knowledge_chunk_vector
USING HNSW (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS ingestion_task (
    id BIGINT PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    document_id BIGINT NOT NULL,
    document_no VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NULL,
    action VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    strategy VARCHAR(32) NULL,
    chunk_size INTEGER NULL,
    chunk_overlap INTEGER NULL,
    max_chunks INTEGER NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 10,
    last_error TEXT NULL,
    source_request_id VARCHAR(64) NULL,
    mq_message_id VARCHAR(128) NULL,
    last_started_at TIMESTAMP NULL,
    last_failed_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS task_no VARCHAR(64);

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS document_id BIGINT;

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS document_no VARCHAR(64);

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS knowledge_base_id BIGINT NULL;

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS action VARCHAR(32);

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'PENDING';

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS strategy VARCHAR(32) NULL;

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS chunk_size INTEGER NULL;

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS chunk_overlap INTEGER NULL;

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS max_chunks INTEGER NULL;

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS max_retries INTEGER NOT NULL DEFAULT 10;

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS last_error TEXT NULL;

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS source_request_id VARCHAR(64) NULL;

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS mq_message_id VARCHAR(128) NULL;

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS last_started_at TIMESTAMP NULL;

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS last_failed_at TIMESTAMP NULL;

ALTER TABLE ingestion_task
ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP NULL;

ALTER TABLE ingestion_task DROP CONSTRAINT IF EXISTS ck_ingestion_task_action;

ALTER TABLE ingestion_task
ADD CONSTRAINT ck_ingestion_task_action CHECK (action IN ('CHUNK', 'REBUILD_VECTORS'));

ALTER TABLE ingestion_task DROP CONSTRAINT IF EXISTS ck_ingestion_task_status;

ALTER TABLE ingestion_task
ADD CONSTRAINT ck_ingestion_task_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'RETRYING', 'DEAD'));

CREATE UNIQUE INDEX IF NOT EXISTS uk_ingestion_task_no
ON ingestion_task (task_no);

CREATE INDEX IF NOT EXISTS idx_ingestion_task_status_updated
ON ingestion_task (status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ingestion_task_document_created
ON ingestion_task (document_id, created_at DESC);

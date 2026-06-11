CREATE INDEX IF NOT EXISTS idx_ingestion_task_failed_created
ON ingestion_task (status, created_at DESC)
WHERE status IN ('FAILED', 'DEAD');

CREATE INDEX IF NOT EXISTS idx_chat_query_rewrite_record_source_created
ON chat_query_rewrite_record (source_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_query_rewrite_record_success_created
ON chat_query_rewrite_record (success, created_at DESC);

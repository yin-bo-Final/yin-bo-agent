ALTER TABLE chat_message
ADD COLUMN IF NOT EXISTS assistant_trace_json TEXT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_terminology_term_canonical_name
ON chat_terminology_term (canonical_name);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_terminology_alias_normalized
ON chat_terminology_alias (alias_normalized);

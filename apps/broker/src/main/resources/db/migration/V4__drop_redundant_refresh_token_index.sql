-- Drop redundant explicit index on token_id, as UNIQUE constraint already provides an index
DROP INDEX IF EXISTS idx_refresh_tokens_token_id;

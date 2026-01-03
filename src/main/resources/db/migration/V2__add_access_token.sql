-- Add access token HMAC column to policy_jobs table
-- Token is stored as HMAC-SHA256 hash, never as raw token
-- No index on access_token_hmac alone - validation fetches by job_uuid and compares in-memory

ALTER TABLE policy_jobs
ADD COLUMN access_token_hmac VARCHAR(255);

-- Note: Column is nullable initially to allow existing rows
-- No backfill required - existing jobs will not have tokens


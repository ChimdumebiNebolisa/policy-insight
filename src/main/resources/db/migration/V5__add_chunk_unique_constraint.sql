-- Add unique constraint on (job_uuid, chunk_index) to ensure idempotent chunk writes
-- This prevents duplicate chunks if a job is retried after partial chunk insertion

-- First, remove any existing duplicate chunks (keep the first one per job_uuid/chunk_index)
DELETE FROM document_chunks
WHERE id NOT IN (
    SELECT MIN(id)
    FROM document_chunks
    GROUP BY job_uuid, chunk_index
);

-- Add unique constraint
ALTER TABLE document_chunks
ADD CONSTRAINT uk_document_chunks_job_uuid_chunk_index UNIQUE (job_uuid, chunk_index);


-- Add share link revocation support
-- V6: Add revoked_at column to share_links table

ALTER TABLE share_links
ADD COLUMN revoked_at TIMESTAMP;

-- Add index for querying revoked links
CREATE INDEX idx_share_links_revoked_at ON share_links(revoked_at);

-- Note: Existing share links will have revoked_at = NULL (not revoked)


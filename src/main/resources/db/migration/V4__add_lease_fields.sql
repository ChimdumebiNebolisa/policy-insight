-- Add lease fields to policy_jobs for stuck PROCESSING recovery
-- lease_expires_at: timestamp when the lease expires (worker should complete by then)
-- attempt_count: number of times this job has been attempted
-- last_error_code: error code from last failure (for visibility)

ALTER TABLE policy_jobs
ADD COLUMN lease_expires_at TIMESTAMP,
ADD COLUMN attempt_count INT DEFAULT 0,
ADD COLUMN last_error_code VARCHAR(50);

-- Index for reaper query: find PROCESSING jobs with expired lease
CREATE INDEX idx_status_lease_expires ON policy_jobs(status, lease_expires_at)
WHERE status = 'PROCESSING';


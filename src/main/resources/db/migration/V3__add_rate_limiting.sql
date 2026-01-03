-- Rate limiting table for per-IP and per-endpoint rate limiting
-- Uses sliding window approach with time-bucketed counters
CREATE TABLE rate_limit_counters (
    ip_address VARCHAR(45) NOT NULL,
    endpoint VARCHAR(100) NOT NULL,
    window_start TIMESTAMP NOT NULL,
    count INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ip_address, endpoint, window_start)
);

-- Index for cleanup queries (find old windows)
CREATE INDEX idx_rate_limit_window_start ON rate_limit_counters(window_start);

-- Index for querying current window (optional, but helps with lookups)
CREATE INDEX idx_rate_limit_lookup ON rate_limit_counters(ip_address, endpoint, window_start DESC);


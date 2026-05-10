create index idx_policy_jobs_status_updated_at on policy_jobs(status, updated_at);
create index idx_policy_jobs_status_created_at on policy_jobs(status, created_at);

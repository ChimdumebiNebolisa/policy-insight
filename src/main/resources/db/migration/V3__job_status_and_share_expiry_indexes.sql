alter table policy_jobs
    add constraint chk_policy_jobs_status
    check (status in (
        'UPLOADED',
        'TEXT_EXTRACTED',
        'BUILDING_AI_REPORT',
        'VALIDATING_CITATIONS',
        'PROCESSING',
        'COMPLETED',
        'FAILED'
    ));

create index idx_share_links_expires_at on share_links(expires_at);

create table policy_jobs (
    id uuid primary key,
    status varchar(32) not null,
    owner_token_hash varchar(128) not null,
    owner_token_expires_at timestamp not null,
    safe_error_message varchar(500),
    created_at timestamp not null,
    updated_at timestamp not null
);

create table document_chunks (
    id uuid primary key,
    job_id uuid not null references policy_jobs(id) on delete cascade,
    chunk_index integer not null,
    text_content text not null,
    unique (job_id, chunk_index)
);

create table reports (
    id uuid primary key,
    job_id uuid not null unique references policy_jobs(id) on delete cascade,
    content text not null,
    created_at timestamp not null
);

create table share_links (
    id uuid primary key,
    report_id uuid not null references reports(id) on delete cascade,
    token_hash varchar(128) not null unique,
    created_at timestamp not null,
    expires_at timestamp not null
);

create table qa_interactions (
    id uuid primary key,
    report_id uuid not null references reports(id) on delete cascade,
    question text not null,
    answer text not null,
    created_at timestamp not null
);

create index idx_document_chunks_job on document_chunks(job_id);
create index idx_share_links_token_hash on share_links(token_hash);
create index idx_qa_interactions_report on qa_interactions(report_id);

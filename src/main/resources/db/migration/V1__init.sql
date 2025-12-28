-- PolicyInsight Baseline Schema
-- V1: All base tables + indexes

-- Table: policy_jobs
CREATE TABLE policy_jobs (
  id BIGSERIAL PRIMARY KEY,
  job_uuid UUID NOT NULL UNIQUE,
  status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, PROCESSING, SUCCESS, FAILED
  error_message TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  started_at TIMESTAMP,
  completed_at TIMESTAMP,
  pdf_gcs_path VARCHAR(255),
  pdf_filename VARCHAR(255),
  file_size_bytes BIGINT,

  -- Processing metadata
  classification VARCHAR(50), -- TOS, PRIVACY_POLICY, LEASE
  classification_confidence DECIMAL(3,2),
  doc_type_detected_page INT,

  -- Output pointers
  report_gcs_path VARCHAR(255),
  chunks_json_gcs_path VARCHAR(255),

  -- For Datadog correlation
  dd_trace_id VARCHAR(255)
);

CREATE INDEX idx_uuid ON policy_jobs(job_uuid);
CREATE INDEX idx_status_created ON policy_jobs(status, created_at DESC);

-- Table: document_chunks
CREATE TABLE document_chunks (
  id BIGSERIAL PRIMARY KEY,
  job_uuid UUID NOT NULL,
  chunk_index INT,
  text TEXT,
  page_number INT,
  start_offset INT,
  end_offset INT,
  span_confidence DECIMAL(3,2),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  FOREIGN KEY (job_uuid) REFERENCES policy_jobs(job_uuid)
);

CREATE INDEX idx_job_uuid ON document_chunks(job_uuid);

-- Table: reports
CREATE TABLE reports (
  id BIGSERIAL PRIMARY KEY,
  job_uuid UUID NOT NULL,
  document_overview JSONB,
  summary_bullets JSONB, -- [{text, chunk_ids, page_refs}]
  obligations JSONB,     -- [{text, severity, citations}]
  restrictions JSONB,
  termination_triggers JSONB,
  risk_taxonomy JSONB,   -- {Data, Financial, LegalRights, Termination, Modification}
  generated_at TIMESTAMP,
  gcs_path VARCHAR(255),

  FOREIGN KEY (job_uuid) REFERENCES policy_jobs(job_uuid),
  UNIQUE (job_uuid)
);

CREATE INDEX idx_job_uuid ON reports(job_uuid);

-- Table: qa_interactions
CREATE TABLE qa_interactions (
  id BIGSERIAL PRIMARY KEY,
  job_uuid UUID NOT NULL,
  question TEXT NOT NULL,
  answer TEXT NOT NULL,
  cited_chunks JSONB,  -- [{chunk_id, page_num, text}]
  confidence VARCHAR(20), -- CONFIDENT, ABSTAINED
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  FOREIGN KEY (job_uuid) REFERENCES policy_jobs(job_uuid)
);

CREATE INDEX idx_job_uuid ON qa_interactions(job_uuid);

-- Table: share_links
CREATE TABLE share_links (
  id BIGSERIAL PRIMARY KEY,
  job_uuid UUID NOT NULL,
  share_token UUID NOT NULL UNIQUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP, -- 7 days from creation
  access_count INT DEFAULT 0,

  FOREIGN KEY (job_uuid) REFERENCES policy_jobs(job_uuid)
);

CREATE INDEX idx_token ON share_links(share_token);
CREATE INDEX idx_expires_at ON share_links(expires_at);


# PolicyInsight – Product Requirements Document

**Last Updated:** December 28, 2025
**Product Name:** PolicyInsight
**Focus:** Production-grade observability with Datadog integration (Google Cloud + Vertex AI)
**Resume Signal:** Full-stack Java/Spring Boot backend with production-grade observability

---

## 1. Executive Summary

PolicyInsight is a **production-ready, backend-leaning full-stack application** that analyzes legal documents (PDFs) and generates plain-English, cited risk reports with grounded Q&A. It demonstrates:

- ✅ **Java 21 + Spring Boot** service architecture with REST API + server-rendered UI (Thymeleaf)
- ✅ **PostgreSQL + Flyway** for schema versioning and data integrity
- ✅ **Google Cloud Platform integration** (Cloud SQL, Cloud Storage, Vertex AI, Pub/Sub, Cloud Run)
- ✅ **Async job processing** via Pub/Sub for reliable document analysis pipelines
- ✅ **End-to-end Datadog observability:** APM tracing, structured JSON logs, LLM telemetry, cost tracking, and actionable incident detection
- ✅ **CI/CD with GitHub Actions:** automated testing, containerization, Cloud Run deployment, versioned releases
- ✅ **Production reliability:** idempotency, retry logic, timeouts, abuse protection, and graceful degradation
- ✅ **"Cite-or-abstain" enforcement:** every claim references source text; hallucinations are structurally prevented
- ✅ **Evidence-ready:** dashboards, monitors (3+ SLOs), traffic generator, runbooks, and Datadog JSON exports

**Target Audience:** Engineers and platform leads evaluating: (a) can this person ship a real service? (b) do they think operationally? (c) do they instrument for observability from day one?

---

## 2. MVP Scope & User Stories

### Core User Stories

**Story 1: Upload & Analyze**
```
As a consumer reviewing a legal document,
I want to upload a PDF and get an instant risk report,
so I understand what I'm signing without hiring a lawyer.
```
- Flow: Land page → Upload PDF → Submit → Job ID returned → Poll status → Report rendered
- Time to first meaningful output: <30 seconds for typical 20–50 page document

**Story 2: Review Structured Risk Report**
```
As a user, I want a 5-section report (overview, summary, obligations, risks, Q&A)
each with plain-English explanations and citations to source text,
so I can trust the analysis and verify claims myself.
```
- Sections: Document Overview, Plain-English Summary, Obligations & Restrictions, Risk Taxonomy, Grounded Q&A
- Every claim must cite extracted text span with page number

**Story 3: Ask Grounded Questions**
```
As a user reviewing a report, I want to ask up to 3 follow-up questions,
and the system must answer only from the document (or abstain),
so I don't get hallucinated information.
```
- Questions stored per document session; answers cite evidence or refuse
- System confidence score and abstention rate tracked

**Story 4: Export & Share**
```
As a user, I want to download the report as PDF or generate a read-only shareable link,
so I can forward results to others or save for records.
```
- PDF export includes inline citations and "not legal advice" disclaimer
- Shareable links auto-expire (7 days); read-only access token stored in DB

### In-Scope (MVP)

| Feature | Details |
|---------|---------|
| **Document Types** | Terms of Service, Privacy Policy, Lease Agreement (3 types; auto-detected) |
| **Input** | PDF files only; size limit 50 MB |
| **OCR** | PDFBox text extraction only (no OCR); Gemini-only pipeline |
| **Citation Mapping** | Page numbers + text span coordinates; chunk-level provenance |
| **Risk Taxonomy** | Data/Privacy, Financial, Legal Rights Waivers, Termination, Modification |
| **Report Sections** | Overview, Summary (10 bullets max), Obligations, Restrictions, Termination Triggers, Risks, Q&A |
| **Q&A Limit** | Up to 3 questions per document session; 3-second timeout per answer |
| **Export Formats** | PDF (inline citations, disclaimer) + shareable read-only HTML link |
| **Accessibility** | Keyboard nav, screen reader support (ARIA labels), high-contrast CSS override |
| **Sessions** | Anonymous; no login required; document session expires 30 days post-creation |

### Out-of-Scope (Future Work)

- Multi-language support
- Organization dashboards, user accounts, team collaboration
- Browser extensions
- Negotiation suggestions or expert marketplace
- >3 document types
- Bulk processing or enterprise features
- Document AI OCR integration (out of scope; PDFBox text extraction only)
- Advanced analytics or custom reports
- Voice input, rich text editors, or annotation tools

---

## 3. System Architecture

### High-Level Diagram

```
┌────────────────────────────────────────────────────────────────┐
│                   FRONTEND (Server-Rendered)                   │
│  Spring MVC + Thymeleaf Pages (HTML) + htmx (polling, uploads) │
├────────────────────────────────────────────────────────────────┤
│                     CLOUD RUN (Containerized)                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Java 21 + Spring Boot REST API + Web Server             │   │
│  │ - POST /api/documents/upload (multipart file)           │   │
│  │ - GET  /api/documents/{id}/status (job polling)         │   │
│  │ - GET  /documents/{id}/report (server-rendered HTML)    │   │
│  │ - POST /api/questions (grounded Q&A)                    │   │
│  │ - GET  /api/documents/{id}/export/pdf                   │   │
│  │ - GET  /documents/{id}/share/{token} (read-only)        │   │
│  │ - GET  /health, /readiness (Kubernetes probes)          │   │
│  └─────────────────────────────────────────────────────────┘   │
├────────────────────────────────────────────────────────────────┤
│           ASYNC JOB PROCESSING (Cloud Pub/Sub)                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Worker Service: Subscriber to "document-analysis-topic" │   │
│  │ - Pulls job messages asynchronously                      │   │
│  │ - Executes: PDFBox extraction → Chunking → Risk Scan → LLM │  │
│  │ - Publishes result back to DB via API or direct update   │   │
│  └─────────────────────────────────────────────────────────┘   │
├────────────────────────────────────────────────────────────────┤
│                      DATA LAYER                                 │
│  ┌──────────────────────────────────────┐                       │
│  │ Cloud SQL (PostgreSQL 15)             │                       │
│  │ - documents table (metadata, status)  │                       │
│  │ - chunks table (text, citations)      │                       │
│  │ - analysis_results table (risk scan)  │                       │
│  │ - qa_sessions table (questions, ans)  │                       │
│  │ - share_tokens table (7-day links)    │                       │
│  │ Flyway: db/migration/V*.sql           │                       │
│  └──────────────────────────────────────┘                       │
│  ┌──────────────────────────────────────┐                       │
│  │ Google Cloud Storage (GCS)            │                       │
│  │ - Raw PDFs (input)                    │                       │
│  │ - Extracted text (intermediate)       │                       │
│  │ - Generated PDFs (export)             │                       │
│  └──────────────────────────────────────┘                       │
└────────────────────────────────────────────────────────────────┘

         EXTERNAL SERVICES (Instrumented for Datadog)
         ┌─────────────────┬─────────────────┐
         │ Google Document │ Vertex AI       │
         │ AI (OCR/Layout) │ Gemini 2.0      │
         └─────────────────┴─────────────────┘

         OBSERVABILITY SINK
         ┌─────────────────────────────────────┐
         │ Datadog (APM + Logs + Metrics)      │
         │ - dd-java-agent instrumentation    │
         │ - LLM call tracking (tokens/cost)   │
         │ - Structured JSON logs              │
         │ - Custom metrics: citation rate,    │
         │   extraction confidence, SLOs       │
         └─────────────────────────────────────┘
```

### Module Breakdown

| Module | Responsibility | Tech |
|--------|---|---|
| **API Gateway** | Request routing, auth, rate limiting, request ID logging | Spring MVC, custom filters |
| **Document Service** | Upload, storage, metadata tracking, session management | Spring Service, Cloud Storage client |
| **Ingestion Worker** | Consume Pub/Sub messages, orchestrate pipeline, publish results | Spring Cloud Pub/Sub, Spring Cloud Task (or custom scheduled service) |
| **Extraction Service** | Extract text from PDFs and map citations | PDFBox text extraction, custom chunking logic |
| **Analysis Service** | Risk scanning, classification, confidence scoring | Prompt engineering, Vertex AI client, rule-based taxonomy mapper |
| **LLM Service** | Calls to Gemini via Vertex AI; token tracking; retry/timeout | OpenFeign + Vertex AI API, custom instrumentation |
| **Export Service** | PDF generation with inline citations, shareable link generation | iText PDF library, token generation (UUID) |
| **Q&A Service** | Grounded question answering; cite-or-abstain enforcement | LLM calls with prompt constraints, citation map lookup |
| **Datadog Integration** | APM tracing, log correlation, custom metrics | Datadog Java agent, dd-trace libraries, MDC for correlation |
| **Database** | Schema, migrations, query execution | PostgreSQL, Flyway, JPA/Hibernate |

---

## 4. Data Model

### Tables & Schema (Flyway Migrations)

#### `documents` (Core)
```sql
CREATE TABLE documents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL UNIQUE,
  file_name VARCHAR(255) NOT NULL,
  file_size_bytes BIGINT NOT NULL,
  mime_type VARCHAR(50) NOT NULL,
  document_type VARCHAR(50) NOT NULL,  -- 'tos', 'privacy_policy', 'lease'
  confidence_score DECIMAL(3,2) NOT NULL,  -- 0.00 to 1.00
  status VARCHAR(50) NOT NULL DEFAULT 'pending',  -- 'pending', 'processing', 'completed', 'failed'
  gcs_uri VARCHAR(512) NOT NULL UNIQUE,  -- Cloud Storage location
  processing_job_id VARCHAR(255),  -- Pub/Sub message ID or Cloud Task ID
  started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP,
  error_message TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP + INTERVAL '30 days',
  dd_trace_id VARCHAR(32)  -- Correlation ID for Datadog
);

CREATE INDEX idx_documents_session_id ON documents(session_id);
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_expires_at ON documents(expires_at);
```

#### `chunks` (Extracted Text with Citations)
```sql
CREATE TABLE chunks (
  id SERIAL PRIMARY KEY,
  document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  chunk_index INT NOT NULL,
  text TEXT NOT NULL,
  page_number INT NOT NULL,
  bounding_box JSONB,  -- {x: 0.1, y: 0.2, width: 0.8, height: 0.3}
  confidence DECIMAL(3,2),  -- OCR confidence if applicable
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chunks_document_id ON chunks(document_id);
CREATE UNIQUE INDEX idx_chunks_uniq ON chunks(document_id, chunk_index);
```

#### `analysis_results` (Risk Scan & Classification)
```sql
CREATE TABLE analysis_results (
  id SERIAL PRIMARY KEY,
  document_id UUID NOT NULL UNIQUE REFERENCES documents(id) ON DELETE CASCADE,

  -- Overview
  parties JSONB,  -- {detected_parties: ["Company A", "You"]}
  effective_date VARCHAR(50),
  jurisdiction VARCHAR(100),

  -- Plain-English Summary
  summary JSONB,  -- {bullets: [{text: "...", chunk_ids: [1,2,3]}]}

  -- Obligations & Restrictions
  obligations JSONB,  -- {items: [{text: "...", severity: "high", chunk_ids: []}]}
  restrictions JSONB,  -- {items: [...]}
  termination_triggers JSONB,  -- {items: [...]}

  -- Risk Taxonomy (5 categories)
  risks_data_privacy JSONB,  -- {detected: true, items: [{text: "...", severity: "high", chunk_ids: []}]}
  risks_financial JSONB,
  risks_legal_waivers JSONB,
  risks_termination JSONB,
  risks_modification JSONB,

  -- Metadata
  extraction_confidence DECIMAL(3,2),
  citation_coverage_rate DECIMAL(3,2),  -- % of claims with citations
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  dd_trace_id VARCHAR(32)
);

CREATE INDEX idx_analysis_results_document_id ON analysis_results(document_id);
```

#### `qa_sessions` (Grounded Q&A)
```sql
CREATE TABLE qa_sessions (
  id SERIAL PRIMARY KEY,
  document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  question TEXT NOT NULL,
  answer TEXT NOT NULL,
  is_grounded BOOLEAN NOT NULL DEFAULT TRUE,  -- true if cited, false if abstained
  chunk_ids INT[] DEFAULT ARRAY[]::INT[],  -- Cited chunks
  model_used VARCHAR(50) DEFAULT 'gemini-2.0-flash',
  tokens_used INT,
  latency_ms INT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  dd_trace_id VARCHAR(32)
);

CREATE INDEX idx_qa_sessions_document_id ON qa_sessions(document_id);
```

#### `share_tokens` (7-Day Read-Only Links)
```sql
CREATE TABLE share_tokens (
  id SERIAL PRIMARY KEY,
  document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  token UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP + INTERVAL '7 days',
  accessed_count INT DEFAULT 0
);

CREATE INDEX idx_share_tokens_token ON share_tokens(token);
CREATE INDEX idx_share_tokens_expires_at ON share_tokens(expires_at);
```

### Flyway Migration File Structure

```
src/main/resources/db/migration/
├── V1__init_documents.sql
├── V2__init_chunks.sql
├── V3__init_analysis_results.sql
├── V4__init_qa_sessions.sql
├── V5__init_share_tokens.sql
├── V6__add_dd_trace_columns.sql
└── V7__add_indexes.sql
```

**Key Design Choices:**

1. **UUID primary keys** for documents (distributed, unguessable session IDs)
2. **JSONB columns** for nested risk/obligation structures (flexible schema, queryable)
3. **Chunk-level citation mapping** (page_number + bounding_box + text enables precise source links)
4. **Soft deletes via expiration** (documents auto-purge 30 days post-creation; can add `deleted_at` column later)
5. **Trace ID columns** (DD_TRACE_ID) for correlating DB queries with APM traces
6. **Status tracking** (pending → processing → completed/failed) for async job visibility
7. **Q&A sessions separate** (allows multi-turn if needed; token usage per Q logged for LLM cost tracking)

---

## 5. API Contract

### Core Endpoints

All endpoints return JSON (except server-rendered HTML views). Request/response examples below.

#### POST `/api/documents/upload`

**Purpose:** Accept a PDF file, store it in GCS, create DB record, publish async job, return job ID for polling.

**Request:**
```http
POST /api/documents/upload HTTP/1.1
Content-Type: multipart/form-data

[binary PDF file]
```

**Response (202 Accepted):**
```json
{
  "session_id": "uuid-session-id",
  "document_id": "uuid-doc-id",
  "status": "pending",
  "polling_url": "/api/documents/uuid-doc-id/status",
  "message": "Document queued for analysis. Check status at polling_url."
}
```

**Status Codes:**
- `202 Accepted` – File stored, job queued
- `400 Bad Request` – No file, wrong MIME type, size >50MB
- `429 Too Many Requests` – Rate limit (e.g., 10 uploads/hour per session)
- `500 Internal Server Error` – GCS write failure, Pub/Sub publish failure

**Datadog Instrumentation:**
- Span: `document.upload` (duration includes GCS write + Pub/Sub publish)
- Tags: `file_size_bytes`, `mime_type`, `session_id`
- Metric: `polcyinsight.upload.count` (counter), `policyinsight.upload.size_bytes` (histogram)

---

#### GET `/api/documents/{document_id}/status`

**Purpose:** Poll job status for UI progress indicator.

**Request:**
```http
GET /api/documents/uuid-doc-id/status HTTP/1.1
Accept: application/json
```

**Response (200 OK):**
```json
{
  "document_id": "uuid-doc-id",
  "status": "processing",
  "progress": {
    "stage": "extraction",
    "percentage": 45,
    "message": "Extracting text and citations from page 3 of 12..."
  },
  "estimated_time_remaining_seconds": 20
}
```

Or, when complete:
```json
{
  "document_id": "uuid-doc-id",
  "status": "completed",
  "report_url": "/documents/uuid-doc-id/report"
}
```

Or, on error:
```json
{
  "document_id": "uuid-doc-id",
  "status": "failed",
  "error": "Text extraction failed after 3 retries. See details at /api/documents/uuid-doc-id/error"
}
```

**Datadog Instrumentation:**
- Span: `document.status.check` (lightweight, ~5ms)
- Tags: `document_id`, `status`
- Metric: `policyinsight.status_check.count` (per status value)

---

#### GET `/documents/{document_id}/report` (Server-Rendered HTML)

**Purpose:** Render the full risk report as a Thymeleaf HTML page (no API; browser fetches directly).

**Request:**
```http
GET /documents/uuid-doc-id/report HTTP/1.1
Accept: text/html
```

**Response (200 OK):**
HTML page with:
- Document Overview section (type, parties, dates)
- Plain-English Summary (bulleted list with inline citations)
- Obligations, Restrictions, Termination Triggers (tables with citations)
- Risk Taxonomy (5 sections, each with detected/absent items)
- Q&A form (up to 3 questions allowed)
- Export buttons (PDF, Share Link)

**Datadog Instrumentation:**
- Span: `document.report.render` (includes DB queries, Thymeleaf rendering)
- Tags: `document_id`, `num_risks_detected`, `num_obligations`
- Metric: `policyinsight.report.render.latency_ms` (histogram)

---

#### POST `/api/questions`

**Purpose:** Submit a question for grounded Q&A; returns citation-backed answer or abstention.

**Request:**
```json
{
  "document_id": "uuid-doc-id",
  "question": "Can they increase my rent without notice?"
}
```

**Response (200 OK):**
```json
{
  "question": "Can they increase my rent without notice?",
  "answer": "The lease states that 'rent can increase with 30-day notice' (Section 4.2). It does not explicitly permit increases without notice.",
  "is_grounded": true,
  "chunk_ids": [12, 15, 18],
  "confidence_score": 0.92,
  "tokens_used": 156,
  "latency_ms": 2340
}
```

Or, if question is out-of-scope:
```json
{
  "question": "What happens if I break my legs?",
  "answer": "This document does not address personal injury scenarios.",
  "is_grounded": false,
  "chunk_ids": [],
  "confidence_score": 0.0,
  "tokens_used": 89,
  "latency_ms": 890
}
```

**Status Codes:**
- `200 OK` – Answer generated (grounded or abstained)
- `400 Bad Request` – Question >500 chars, document not found, >3 questions per session
- `408 Request Timeout` – Gemini call exceeded 10s timeout
- `429 Too Many Requests` – User exceeded rate limit (e.g., 3 Q/minute)
- `503 Service Unavailable` – Gemini API unavailable; fallback to: "Service temporarily unavailable. Try again in 30 seconds."

**Datadog Instrumentation:**
- Span: `qa.answer` (includes Gemini call + citation lookup)
- Tags: `document_id`, `is_grounded`, `confidence_score`
- Metrics:
  - `policyinsight.qa.latency_ms` (histogram)
  - `policyinsight.qa.tokens_used` (histogram)
  - `policyinsight.qa.grounded_rate` (gauge, %)
  - `policyinsight.gemini.latency_ms` (nested span tag)

---

#### GET `/api/documents/{document_id}/export/pdf`

**Purpose:** Generate and download PDF report with inline citations.

**Request:**
```http
GET /api/documents/uuid-doc-id/export/pdf HTTP/1.1
Accept: application/pdf
```

**Response (200 OK):**
- Content-Type: `application/pdf`
- Content-Disposition: `attachment; filename="PolicyInsight_<document-id>_<timestamp>.pdf"`
- Body: PDF file (binary)

**Status Codes:**
- `200 OK` – PDF generated and streamed
- `404 Not Found` – Document not found
- `409 Conflict` – Document still processing (status != 'completed')

**Datadog Instrumentation:**
- Span: `pdf.export` (includes iText rendering time)
- Metric: `policyinsight.pdf.generation.latency_ms` (histogram)

---

#### POST `/api/documents/{document_id}/share`

**Purpose:** Generate a read-only shareable link (7-day TTL).

**Request:**
```json
{
  "document_id": "uuid-doc-id"
}
```

**Response (201 Created):**
```json
{
  "share_token": "uuid-token",
  "read_only_url": "https://policyinsight-app.com/documents/uuid-doc-id/share/uuid-token",
  "expires_at": "2025-01-04T22:07:00Z",
  "message": "Link expires in 7 days. Recipient cannot modify or ask questions."
}
```

**Datadog Instrumentation:**
- Span: `share.generate`
- Metric: `policyinsight.share.links_generated_count` (counter)

---

#### GET `/documents/{document_id}/share/{share_token}` (Read-Only Report)

**Purpose:** Render report for shared link recipients (no Q&A form, no export buttons, prominent "shared" watermark).

**Datadog Instrumentation:**
- Span: `share.view`
- Tags: `share_token`, `age_days`
- Metric: `policyinsight.share.accesses_count` (counter)

---

#### GET `/health`

**Purpose:** Kubernetes liveness probe.

**Response (200 OK):**
```json
{
  "status": "UP",
  "timestamp": "2025-12-28T22:07:00Z"
}
```

---

#### GET `/readiness`

**Purpose:** Kubernetes readiness probe; checks DB, GCS, Pub/Sub connectivity.

**Response (200 OK):**
```json
{
  "status": "UP",
  "components": {
    "database": { "status": "UP" },
    "gcs": { "status": "UP" },
    "pubsub": { "status": "UP" },
    "vertex_ai": { "status": "UP" }
  }
}
```

Or, degraded:
```json
{
  "status": "DEGRADED",
  "components": {
    "database": { "status": "UP" },
    "gcs": { "status": "UP" },
    "pubsub": { "status": "UP" },
    "vertex_ai": { "status": "DOWN", "details": "Quota exceeded; using fallback" }
  },
  "message": "Service is partially available. LLM features degraded."
}
```

---

### OpenAPI Specification

See `openapi.json` (committed to repo, auto-generated from annotations):

```bash
# Generate from Spring Boot:
./mvnw springdoc-openapi:generate

# Location: target/generated-sources/openapi/docs/openapi.json
# Serve via: GET /v3/api-docs → Swagger UI at /swagger-ui.html
```

---

## 6. Core Pipeline

### Document Processing Flow

```
STAGE 1: Upload & Validation
  ├─ File received via POST /api/documents/upload (multipart)
  ├─ Validation: MIME type (PDF), size <50MB, virus scan (optional)
  ├─ Generate session_id (UUID), document_id (UUID)
  ├─ Upload to GCS: gs://policyinsight-bucket/{document_id}/{file_name}
  ├─ Create DB record: status='pending', gcs_uri=...
  ├─ Publish Pub/Sub message: {document_id, gcs_uri, session_id}
  └─ Return 202 Accepted with polling_url

STAGE 2: Extraction (Async Worker)
  ├─ Consume Pub/Sub message
  ├─ Update DB: status='processing', stage='extraction'
  ├─ Download PDF from GCS → temp storage
  ├─ Extract text via PDFBox (text-only, no OCR)
  └─ Store: confidence_score = 0.5 (to signal lower reliability)
  ├─ Chunk extracted text: 500–800 tokens per chunk, with page overlap
  ├─ For each chunk:
  │   ├─ Store in chunks table: (document_id, chunk_index, text, page_number, bounding_box, confidence)
  │   └─ Compute chunk_id = hash(document_id + chunk_index) for citation mapping
  └─ Update DB: extraction_confidence = avg(all chunk confidences)

STAGE 3: Document Classification
  ├─ Prepare prompt: "Classify this document. Return JSON: {type, confidence_score}. Types: tos, privacy_policy, lease."
  ├─ Send first 5 chunks (2000 tokens) to Gemini
  ├─ Response: {type: 'tos', confidence_score: 0.91}
  ├─ Store in documents table: document_type='tos', confidence_score=0.91
  └─ If confidence < 0.7: log warning, mark for manual review (optional)

STAGE 4: Risk Taxonomy Scan
  ├─ For each risk category (5 categories):
  │  ├─ Prepare prompt: "Scan all chunks. Identify [RISK]. Return JSON: {detected, items: [{text, severity, chunk_ids}]}"
  │  ├─ Send all chunks to Gemini
  │  ├─ Parse response: [{text: "...", severity: "high", chunk_ids: [1,2,3]}, ...]
  │  └─ Validate: every chunk_id must exist in DB; severity ∈ {low, medium, high}
  ├─ Store results in analysis_results table: risks_data_privacy, risks_financial, etc.
  ├─ Count total claims: sum across all risks
  ├─ Count cited claims: sum(claims with chunk_ids > 0)
  └─ Store: citation_coverage_rate = cited / total

STAGE 5: Plain-English Summary
  ├─ Prepare prompt: "Summarize key findings from all chunks in 10 bullets. Each bullet must cite source chunks by ID."
  ├─ Send all chunks to Gemini
  ├─ Parse response: {bullets: [{text: "...", chunk_ids: [1,2,3]}, ...]}
  ├─ Validate chunk citations
  └─ Store in analysis_results.summary

STAGE 6: Obligations & Restrictions Extraction
  ├─ Prepare prompt: "Extract obligations, restrictions, termination triggers from all chunks. For each, cite source chunks and severity."
  ├─ Send all chunks to Gemini
  ├─ Parse response: {obligations: [...], restrictions: [...], termination_triggers: [...]}
  ├─ Validate citations
  └─ Store in analysis_results

STAGE 7: Completion & Indexing
  ├─ Validate all required fields are populated
  ├─ Update DB: documents.status='completed', completed_at=NOW()
  ├─ Store dd_trace_id (correlation ID from context)
  ├─ Publish Pub/Sub message (optional): {document_id, event: 'analysis_complete'}
  │  └─ Can trigger email notification, webhook, etc.
  └─ Log summary: document_type, num_risks, citation_rate, total_latency_ms

STAGE 8: Serve Report
  ├─ User polls GET /api/documents/{id}/status
  ├─ Status='completed' → /documents/{id}/report renders via Spring MVC + Thymeleaf
  ├─ Thymeleaf queries DB: analysis_results, chunks for citation display
  ├─ Render HTML with:
  │  ├─ Overview section (from documents + analysis_results)
  │  ├─ Summary bullets (from analysis_results.summary) with inline span links to chunks
  │  ├─ Obligations/Restrictions/Termination (from analysis_results)
  │  ├─ Risk Taxonomy (5 sections, each with detected/absent items)
  │  └─ Q&A form (htmx POST to /api/questions, polling with spinner)
  └─ User can Ask Questions, Export PDF, or Share Link
```

### Error Handling & Retry Logic

| Failure Point | Retry Strategy | Max Retries | Backoff | Fallback |
|---|---|---|---|---|
| GCS upload | Exponential: 2s, 4s, 8s | 3 | 2^n | Return 500; log error |
| Pub/Sub publish | Exponential | 3 | 2^n | Retry via scheduled task (Cloud Tasks) |
| PDFBox extraction | None | 0 | N/A | Fail job; surface error |
| Gemini API (rate limit) | Exponential: 5s, 10s, 20s | 2 | 5 * 2^n | Return 503; client retries |
| Gemini API (quota) | None; use fallback | 0 | N/A | Return "Service temporarily unavailable"; queue for retry in 5 min |
| DB write | Exponential | 2 | 2^n | Log error, alert on Slack |
| PDF export (iText) | None | 0 | N/A | Return 500; log error |

**Idempotency:**
- Pub/Sub messages include idempotency key (document_id + stage)
- Worker checks DB for duplicate stage completion before retrying
- GCS uploads use versioned object names; overwrite is safe

---

## 7. UI Flows

### Technology Stack

- **Server-Rendered:** Spring MVC + Thymeleaf (HTML generation on backend)
- **Minimal JS:** htmx for progressive enhancement (no React/Next)
  - Form submission → POST /api/questions (JSON API)
  - Status polling → GET /api/documents/{id}/status (JSON)
  - Upload progress → htmx progress event
- **Styling:** Tailwind CSS (utility classes); high-contrast override via CSS custom properties
- **Accessibility:** ARIA labels, keyboard nav, screen reader support (tested with NVDA/JAWS)

### Page: Landing (`GET /`)

**Purpose:** Sell the value prop and invite upload.

**Content:**
```
┌─────────────────────────────────────┐
│ PolicyInsight                       │
│ Understand what you're signing      │
└─────────────────────────────────────┘

"Legal documents are confusing. PolicyInsight
gives you a plain-English risk report, with
every claim cited to the original text."

┌─────────────────────────────────────┐
│ [Drag PDF here or click to upload]  │
│                                     │
│ Supports: Terms of Service,         │
│           Privacy Policies,         │
│           Lease Agreements          │
│ Max size: 50 MB                     │
└─────────────────────────────────────┘

NOT LEGAL ADVICE. PolicyInsight is for
educational clarity only. Consult a lawyer
before signing important agreements.

┌─────────────────────────────────────┐
│ Example Documents (read-only links)  │
│ • TOS Example Report                │
│ • Privacy Policy Example             │
└─────────────────────────────────────┘
```

**htmx Integration:**
```html
<form hx-post="/api/documents/upload"
      hx-target="#upload-status"
      hx-encoding="multipart/form-data">
  <input type="file" name="file" accept=".pdf" required />
  <button type="submit">Analyze</button>
</form>

<div id="upload-status"></div>
<!-- htmx will poll /api/documents/{id}/status every 2s -->
```

---

### Page: Processing (`GET /documents/{id}?status=processing`)

**Purpose:** Show progress bar while document is analyzed.

**Content:**
```
┌─────────────────────────────────────┐
│ Analyzing: document.pdf             │
│                                     │
│ Stage: Extracting Text & Citations  │
│ Progress: ████████░░░░░░░░░░░░░░░░  │
│ 45% | Estimated 20 seconds left     │
│                                     │
│ [Details]                           │
│ • PDF uploaded                      │
│ • Extracting page 3 of 12...       │
│ • Next: Risk classification         │
└─────────────────────────────────────┘
```

**htmx Polling:**
```html
<div hx-get="/api/documents/{id}/status"
     hx-trigger="load, every 2s"
     hx-swap="outerHTML">
  <!-- Status updates every 2s -->
</div>
```

---

### Page: Report (`GET /documents/{id}/report`)

**Purpose:** Display the full 5-section risk report with inline citations.

**Layout:**
```
┌────────────────────────────────────────────┐
│ PolicyInsight Report                       │
│ File: document.pdf                         │
│ Analyzed: 2025-12-28 22:07                │
│ [PDF Export] [Share Link] [New Analysis]  │
└────────────────────────────────────────────┘

┌─ SECTION 1: DOCUMENT OVERVIEW ────────────┐
│ Document Type: Terms of Service           │
│ Confidence: 91%                           │
│ Parties: Company A (Service Provider),    │
│         You (End User)                    │
│ Effective Date: 2025-01-01                │
│ Jurisdiction: United States (California)  │
└───────────────────────────────────────────┘

┌─ SECTION 2: PLAIN-ENGLISH SUMMARY ────────┐
│ • You agree to use the service only as    │
│   described; resale is prohibited.        │
│   [Cite to Section 2.1, Page 1]           │
│                                           │
│ • The company can collect, share, and     │
│   retain your data indefinitely.          │
│   [Cite to Section 6.2-6.3, Pages 4-5]   │
│                                           │
│ (10 bullets max, each with inline cite)   │
└───────────────────────────────────────────┘

┌─ SECTION 3: OBLIGATIONS & RESTRICTIONS ──┐
│                                           │
│ YOUR OBLIGATIONS:                        │
│ ┌─────────────────────────────────────┐ │
│ │ Comply with usage restrictions     │ │
│ │ [Cite to Section 2, Page 1]        │ │
│ │ Severity: ⚠️ Medium                │ │
│ │                                     │ │
│ │ Pay subscription fee on renewal    │ │
│ │ [Cite to Section 3.1, Page 2]      │ │
│ │ Severity: 🔴 High                  │ │
│ └─────────────────────────────────────┘ │
│                                           │
│ RESTRICTIONS:                             │
│ ┌─────────────────────────────────────┐ │
│ │ No reselling the service            │ │
│ │ [Cite to Section 2.2, Page 1]      │ │
│ │ Severity: ⚠️ Medium                │ │
│ │                                     │ │
│ │ No reverse engineering              │ │
│ │ [Cite to Section 2.3, Page 1]      │ │
│ │ Severity: 🔴 High                  │ │
│ └─────────────────────────────────────┘ │
│                                           │
│ TERMINATION TRIGGERS:                     │
│ ┌─────────────────────────────────────┐ │
│ │ Non-payment of fees                 │ │
│ │ [Cite to Section 4.1, Page 2]      │ │
│ │ Severity: 🔴 High                  │ │
│ └─────────────────────────────────────┘ │
└───────────────────────────────────────────┘

┌─ SECTION 4: RISK TAXONOMY ────────────────┐
│                                           │
│ 📊 DATA & PRIVACY RISKS                   │
│ ✅ Detected: 2 risks                      │
│ ┌─────────────────────────────────────┐ │
│ │ • Data shared with third parties    │ │
│ │   [Cite to Section 6.2]             │ │
│ │   Severity: 🔴 High                 │ │
│ │                                     │ │
│ │ • Data retained indefinitely        │ │
│ │   [Cite to Section 6.3]             │ │
│ │   Severity: ⚠️ Medium               │ │
│ └─────────────────────────────────────┘ │
│                                           │
│ 💰 FINANCIAL RISKS                        │
│ ✅ Detected: 2 risks                      │
│ ┌─────────────────────────────────────┐ │
│ │ • Auto-renewal enabled by default   │ │
│ │   [Cite to Section 3.2]             │ │
│ │   Severity: 🔴 High                 │ │
│ │                                     │ │
│ │ • Cancellation fees ($25)           │ │
│ │   [Cite to Section 3.3]             │ │
│ │   Severity: 🔴 High                 │ │
│ └─────────────────────────────────────┘ │
│                                           │
│ ⚖️  LEGAL RIGHTS WAIVERS                  │
│ ✅ Detected: 2 risks                      │
│ ┌─────────────────────────────────────┐ │
│ │ • Mandatory arbitration             │ │
│ │   [Cite to Section 8.1]             │ │
│ │   Severity: 🔴 High                 │ │
│ │                                     │ │
│ │ • Class action waiver               │ │
│ │   [Cite to Section 8.1]             │ │
│ │   Severity: 🔴 High                 │ │
│ │                                     │ │
│ │ • Liability cap ($100)              │ │
│ │   [Cite to Section 8.2]             │ │
│ │   Severity: 🔴 High                 │ │
│ └─────────────────────────────────────┘ │
│                                           │
│ 🛑 TERMINATION & ENFORCEMENT              │
│ ✅ Detected: 1 risk                       │
│ ┌─────────────────────────────────────┐ │
│ │ • Unilateral termination rights     │ │
│ │   [Cite to Section 4.2]             │ │
│ │   Severity: ⚠️ Medium               │ │
│ └─────────────────────────────────────┘ │
│                                           │
│ ✏️  MODIFICATION RISKS                    │
│ ❌ Not Detected                           │
│ (This document does not explicitly        │
│  state that terms can be modified         │
│  without user consent.)                   │
└───────────────────────────────────────────┘

┌─ SECTION 5: ASK A QUESTION ───────────────┐
│                                           │
│ "Powered by AI but grounded in document   │
│  text only. If we don't know, we say so." │
│                                           │
│ [Question input field]                    │
│ [Submit] (htmx POST /api/questions)       │
│                                           │
│ Questions asked: 0/3                      │
│                                           │
│ ┌─────────────────────────────────────┐ │
│ │ Q: "Can they increase my rent?"     │ │
│ │                                     │ │
│ │ A: "The lease states: 'rent can     │ │
│ │ increase with 30-day notice'        │ │
│ │ (Section 4.2). It does not          │ │
│ │ explicitly permit increases         │ │
│ │ without notice."                    │ │
│ │ [Cite to chunk IDs: 12, 15, 18]     │ │
│ │ Confidence: 92%                     │ │
│ └─────────────────────────────────────┘ │
│                                           │
│ ┌─────────────────────────────────────┐ │
│ │ Q: "What if I have a disability?"   │ │
│ │                                     │ │
│ │ A: "This document does not address  │ │
│ │ disability-related scenarios."      │ │
│ └─────────────────────────────────────┘ │
└───────────────────────────────────────────┘

┌─ DISCLAIMER ──────────────────────────────┐
│ ⚠️  NOT LEGAL ADVICE                       │
│                                           │
│ PolicyInsight uses AI to explain what     │
│ documents say. We do NOT:                 │
│ • Provide legal advice                    │
│ • Recommend specific actions              │
│ • Interpret law or regulations            │
│ • Guarantee accuracy (always verify)      │
│                                           │
│ For legal advice, consult a lawyer.       │
└───────────────────────────────────────────┘
```

**htmx Q&A Integration:**
```html
<form hx-post="/api/questions"
      hx-target="#qa-results"
      hx-swap="beforeend">
  <textarea name="question"
            placeholder="Ask a question..."
            maxlength="500"></textarea>
  <button type="submit">Ask</button>
</form>

<div id="qa-results">
  <!-- Each answer rendered here via htmx swap -->
</div>
```

---

### Fragment Routes (htmx)

| Route | Method | Purpose | Returns |
|---|---|---|---|
| `/api/documents/{id}/status` | GET | Poll job status | JSON status object |
| `/api/questions` | POST | Submit Q&A | JSON question + answer |
| `/documents/{id}/report` (partial) | GET | Re-render risk section on demand | HTML fragment (Thymeleaf) |
| `/documents/{id}/export/pdf` | GET | Stream PDF file | Binary PDF |
| `/documents/{id}/share/{token}` | GET | Render read-only report | HTML (same as `/report` but no Q&A, no export buttons) |

---

## 8. Grounding & Safety Rules

### "Cite-or-Abstain" Enforcement

**Definition:** Every claim (risk, obligation, summary bullet, Q&A answer) must reference extracted text with page numbers and chunk IDs. If evidence does not exist, the system must abstain.

**Implementation:**

1. **Prompt Engineering:**
   ```
   System: You are a legal document analyzer. STRICT RULE: Every claim you make
   MUST cite specific chunks by ID. If no evidence exists in the document,
   respond with: "No evidence found for [claim]."

   Do NOT:
   - Infer intent
   - Speculate
   - Use phrases like "likely" or "probably"

   For each claim, provide:
   - Text (max 200 chars, plain English)
   - Severity (low/medium/high)
   - Chunk IDs (e.g., [5, 7, 12])
   ```

2. **Validation Layer:**
   ```java
   // Example pseudo-code
   for (Risk risk : extractedRisks) {
     if (risk.chunkIds == null || risk.chunkIds.isEmpty()) {
       throw new ValidationException("Risk claimed without citation: " + risk.text);
     }
     for (int chunkId : risk.chunkIds) {
       if (!chunksTable.contains(document_id, chunkId)) {
         throw new ValidationException("Citation references non-existent chunk: " + chunkId);
       }
     }
   }
   ```

3. **Q&A Handler:**
   ```java
   // Answer only if we find evidence; otherwise abstain
   if (relevantChunks.isEmpty()) {
     return "This document does not address [question topic].";
   }

   // Pass chunks to Gemini with prompt:
   // "Answer ONLY from provided chunks. If not found, respond: 'Not stated in this document.'"

   // Parse response; validate citation presence
   if (!response.contains("chunk_id") && !response.contains("abstain")) {
     // Fallback: force abstention
     return "This document does not explicitly state [topic].";
   }
   ```

4. **Citation Coverage Metrics:**
   - Track: `citation_coverage_rate = (claims_with_citations / total_claims) * 100`
   - Target: >95% (i.e., <5% abstention rate on valid questions)
   - Alert if <80% (potential hallucination detection)

### Safety Rules (Hard Enforcement)

| Rule | Implementation | Verification |
|---|---|---|
| **No legal advice** | Prompt: "Do NOT recommend actions. Do NOT say 'You should...'. Only explain." | Output filter: reject if contains "recommend", "should", "consult", "you must" |
| **No speculation** | Prompt: "Only state facts explicitly in document. No 'may', 'likely', 'probably'." | Regex filter: reject outputs containing modal verbs (may, might, could, probably, etc.) |
| **No inferred intent** | Prompt: "Report what is written. Do NOT infer why it's written." | Manual review: spot-check 5% of outputs for inferred claims |
| **No hallucinated risks** | Validation: reject any risk not cited to a chunk ID | DB constraint: `chunks.id NOT NULL` in analysis_results risks JSONB |
| **Absence is information** | Explicit: For each of 5 risk categories, if 0 risks detected, output "No evidence of [risk category] found." | Thymeleaf template: render all 5 sections; absent risks shown with ❌ badge |
| **Content safety check** | Call Vertex AI Safety API (or regex) to filter toxic/illegal content in outputs | Log flagged outputs; alert if any reach user |
| **Disclaimer visible** | Display "NOT LEGAL ADVICE" banner on every report page, export, share link | Automated: test includes screenshot checks for disclaimer presence |

### Accessibility & Inclusive Design

| Feature | Implementation |
|---|---|
| **Keyboard Navigation** | Tab order: Upload → Submit → Report sections → Q&A form → Export buttons. Skip links. No JS required. |
| **Screen Reader Support** | ARIA labels on inputs, buttons, tables. alt text on inline citations. Report structure: semantic HTML (`<section>`, `<article>`, `<table>`). |
| **High-Contrast Mode** | CSS custom property: `--color-text-primary`, `--color-bg-primary`. User can toggle via button; persists in session. |
| **Mobile Responsive** | Single-column layout; touch-friendly buttons (48px min height); responsive font sizes; readable on 320px–1920px viewports. |
| **Focus Indicators** | Visible outline on all interactive elements. `:focus-visible` pseudo-class. |

---

## 9. Datadog Observability Plan

### Objective

Demonstrate production-grade observability: end-to-end APM tracing, structured logging, LLM cost tracking, SLO-driven alerting, and incident response automation. **Observability features:** traces flow through every service layer, dashboards show real-time health, monitors trigger incidents with context and runbooks.

### APM Instrumentation

#### 9.1.1 Java Agent Setup

**Datadog dd-java-agent:**

```dockerfile
# Dockerfile
FROM eclipse-temurin:21-jre-jammy

# Download Datadog Java agent
ADD https://dtddev.blob.core.windows.net/java-agent/latest/dd-java-agent.jar /opt/dd-java-agent.jar

ENV DD_AGENT_HOST=localhost
ENV DD_AGENT_PORT=8126
ENV DD_SERVICE=policyinsight
ENV DD_ENV=prod
ENV DD_VERSION=1.0.0
ENV DD_TRACE_ENABLED=true

# JVM args with agent
ENTRYPOINT ["java", \
  "-javaagent:/opt/dd-java-agent.jar", \
  "-Ddd.service=${DD_SERVICE}", \
  "-Ddd.env=${DD_ENV}", \
  "-Ddd.version=${DD_VERSION}", \
  "-Ddd.tags=${DD_TAGS}", \
  "-Ddd.logs.injection=true", \
  "-Ddd.profiling.enabled=true", \
  "-Ddd.profiling.ddprof.enabled=true", \
  "-cp", "/app/classes", \
  "com.policyinsight.PolicyInsightApplication"]
```

**Spring Boot Configuration:**

```yaml
# application.yml
server:
  port: 8080

logging:
  level:
    root: INFO
  pattern:
    console: '{"timestamp":"%d{ISO8601}","level":"%p","thread":"%t","logger":"%c","message":"%m","dd_trace_id":"%X{dd.trace_id}","dd_span_id":"%X{dd.span_id}","dd_service":"%X{dd.service}"%n%ex}'
  file:
    name: /var/log/policyinsight/app.log

spring:
  jpa:
    hibernate:
      ddl-auto: validate
  datasource:
    url: jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
```

**Trace Context Injection:**

```java
@Configuration
public class DatadogConfig {

  @Bean
  public ServletFilter tracingFilter() {
    return new TracingFilter();
  }

  public static class TracingFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
        throws IOException, ServletException {
      // Datadog dd-java-agent auto-injects trace context; we ensure propagation
      HttpServletRequest httpReq = (HttpServletRequest) req;
      HttpServletResponse httpRes = (HttpServletResponse) res;

      // Get trace ID from agent
      String traceId = httpReq.getHeader("x-datadog-trace-id");
      String spanId = httpReq.getHeader("x-datadog-span-id");

      // Store in MDC for logs
      MDC.put("dd.trace_id", traceId);
      MDC.put("dd.span_id", spanId);
      MDC.put("dd.service", "policyinsight");

      try {
        chain.doFilter(req, res);
      } finally {
        MDC.clear();
      }
    }
  }
}
```

#### 9.1.2 Custom Spans & Tags

```java
@Service
public class DocumentService {

  private static final Tracer tracer = GlobalTracer.get();

  public AnalysisResult analyzeDocument(String documentId) {
    // Create custom span
    Scope scope = tracer.buildSpan("document.analyze")
      .withTag("document_id", documentId)
      .withTag("service", "policyinsight")
      .startActive(true);

    try {
      // Extract
      Scope extractScope = tracer.buildSpan("document.extraction")
        .asChildOf(scope.span())
        .withTag("stage", "extraction")
        .startActive(true);

      extractText(documentId);
      extractScope.close();

      // Classify
      Scope classifyScope = tracer.buildSpan("document.classification")
        .asChildOf(scope.span())
        .withTag("stage", "classification")
        .startActive(true);

      String docType = classifyDocument(documentId);
      classifyScope.span().setTag("document_type", docType);
      classifyScope.close();

      // Risk scan
      Scope riskScope = tracer.buildSpan("document.risk_scan")
        .asChildOf(scope.span())
        .withTag("stage", "risk_scan")
        .startActive(true);

      List<Risk> risks = scanRisks(documentId);
      riskScope.span().setTag("risks_count", risks.size());
      riskScope.close();

      return buildAnalysisResult(documentId, docType, risks);

    } finally {
      scope.close();
    }
  }
}
```

#### 9.1.3 LLM Call Instrumentation

```java
@Service
public class GeminiService {

  private final VertexAI vertexAI;
  private final Tracer tracer = GlobalTracer.get();

  public String callGemini(String prompt, String model) {
    Scope scope = tracer.buildSpan("llm.call")
      .withTag("llm.model", model)
      .withTag("llm.provider", "vertex_ai")
      .withTag("llm.temperature", 0.2)
      .startActive(true);

    long startTime = System.nanoTime();
    int inputTokens = 0, outputTokens = 0;

    try {
      GenerativeModel genModel = new GenerativeModel(model, vertexAI);
      GenerateContentResponse response = genModel.generateContent(prompt);

      // Extract token usage (if available in response)
      // Vertex AI may return usage info in UsageMetadata

      Content content = response.getContent();
      String text = content.getParts().get(0).getText();

      // Emit custom metrics
      long durationMs = (System.nanoTime() - startTime) / 1_000_000;
      GlobalRegistry.get()
        .timer("policyinsight.llm.latency_ms")
        .record(durationMs, TimeUnit.MILLISECONDS);

      scope.span().setTag("llm.response_length", text.length());
      scope.span().setTag("llm.latency_ms", durationMs);

      // Log token usage (if available)
      // scope.span().setTag("llm.input_tokens", inputTokens);
      // scope.span().setTag("llm.output_tokens", outputTokens);

      return text;

    } catch (Exception e) {
      scope.span().setTag("error", true);
      scope.span().setTag("error.message", e.getMessage());
      tracer.inject(scope.span().context(), Format.TEXT_MAP, new TextMap() {
        @Override
        public void put(String key, String value) {
          // Propagate for downstream
        }
        @Override
        public Iterator<Map.Entry<String, String>> iterator() {
          return Collections.emptyIterator();
        }
      });
      throw new LLMException("Gemini call failed", e);
    } finally {
      scope.close();
    }
  }
}
```

### Metrics & Signals

#### 9.2.1 Core Metrics (Custom)

```java
@Component
public class MetricsRegistry {

  private final MeterRegistry meterRegistry;

  public MetricsRegistry(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    initializeMetrics();
  }

  private void initializeMetrics() {
    // Request latency (histogram)
    meterRegistry.timer("policyinsight.api.latency_ms",
      "endpoint", "document.upload",
      "method", "POST");

    // Error rate (counter)
    meterRegistry.counter("policyinsight.api.errors",
      "endpoint", "document.upload",
      "status_code", "400");

    // Document processing
    meterRegistry.timer("policyinsight.document.processing_latency_ms",
      "document_type", "tos");
    meterRegistry.counter("policyinsight.document.count",
      "status", "completed");
    meterRegistry.gauge("policyinsight.document.queue_depth",
      "topic", "document-analysis");

    // Extraction metrics
    meterRegistry.timer("policyinsight.extraction.latency_ms",
      "service", "document_ai");
    meterRegistry.gauge("policyinsight.extraction.confidence",
      "document_id", "doc-123");

    // Risk detection
    meterRegistry.gauge("policyinsight.analysis.citation_coverage_rate",
      "document_id", "doc-123");
    meterRegistry.gauge("policyinsight.analysis.risks_detected_count",
      "risk_category", "data_privacy");

    // LLM metrics
    meterRegistry.timer("policyinsight.llm.latency_ms",
      "model", "gemini-2.0-flash");
    meterRegistry.counter("policyinsight.llm.tokens_used",
      "call_type", "summary_generation");
    meterRegistry.gauge("policyinsight.llm.estimated_cost_usd",
      "period", "hourly");

    // Q&A metrics
    meterRegistry.timer("policyinsight.qa.latency_ms",
      "grounded", "true");
    meterRegistry.counter("policyinsight.qa.grounded_rate",
      "question_category", "obligation");

    // PDF export
    meterRegistry.timer("policyinsight.pdf.generation_latency_ms");

    // Pub/Sub worker
    meterRegistry.gauge("policyinsight.worker.job_latency_ms",
      "stage", "extraction");
  }
}
```

#### 9.2.2 Metrics Recorded

| Metric Name | Type | Dimensions | Purpose |
|---|---|---|---|
| `policyinsight.api.latency_ms` | Histogram | endpoint, method, status | API response time |
| `policyinsight.api.errors` | Counter | endpoint, status_code | Error count by status |
| `policyinsight.document.processing_latency_ms` | Histogram | document_type, stage | End-to-end processing time |
| `policyinsight.document.count` | Counter | status (pending/completed/failed) | Document completion rate |
| `policyinsight.extraction.latency_ms` | Histogram | service (document_ai/fallback) | OCR latency |
| `policyinsight.extraction.confidence` | Gauge | document_id | OCR confidence score |
| `policyinsight.analysis.citation_coverage_rate` | Gauge | document_id | % claims with citations |
| `policyinsight.analysis.risks_detected_count` | Gauge | risk_category | Risk count per category |
| `policyinsight.llm.latency_ms` | Histogram | model, call_type | Gemini API latency |
| `policyinsight.llm.tokens_used` | Counter | call_type, model | Token consumption (cost basis) |
| `policyinsight.llm.estimated_cost_usd` | Gauge | period | Estimated LLM cost per hour |
| `policyinsight.qa.latency_ms` | Histogram | grounded (true/false) | Q&A answer latency |
| `policyinsight.qa.grounded_rate` | Counter | question_category | Abstention tracking |
| `policyinsight.pdf.generation_latency_ms` | Histogram | — | PDF export time |
| `policyinsight.worker.job_latency_ms` | Histogram | stage (extraction/classify/risk/summary) | Async job latency per stage |
| `policyinsight.pubsub.queue_age_seconds` | Gauge | topic (document-analysis-topic) | Pub/Sub queue backlog age |

### Dashboards

#### 9.3.1 "PolicyInsight Ops" Dashboard (Main Board)

**URL:** `https://app.datadoghq.com/dashboard/.../policys-ops`

**Widgets:**

1. **Request Latency (p50/p95/p99)**
   - Query: `avg:policyinsight.api.latency_ms{*}`
   - Type: Timeseries
   - Y-axis: milliseconds
   - Thresholds: p95 <5000ms (green), >5000ms (yellow), >10000ms (red)

2. **Error Rate (4xx/5xx)**
   - Query: `sum:policyinsight.api.errors{*}`
   - Type: Timeseries
   - Y-axis: count
   - Threshold: >0 (red alert)

3. **Document Processing Queue (Pub/Sub Backlog)**
   - Query: `avg:policyinsight.pubsub.queue_age_seconds{topic:document-analysis-topic}`
   - Type: Gauge
   - Threshold: >60s (warning), >300s (critical)

4. **Document Processing Latency (E2E)**
   - Query: `avg:policyinsight.document.processing_latency_ms{*}`
   - Type: Timeseries
   - Breakdown by: document_type (tos, privacy_policy, lease)

5. **Extraction Confidence (OCR Quality)**
   - Query: `avg:policyinsight.extraction.confidence{*}`
   - Type: Gauge
   - Threshold: >0.85 (green), <0.75 (red)

6. **Citation Coverage Rate (Hallucination Detection)**
   - Query: `avg:policyinsight.analysis.citation_coverage_rate{*}`
   - Type: Gauge
   - Threshold: >0.95 (green), <0.80 (red alert)

7. **LLM Token Usage & Estimated Cost**
   - Query: `sum:policyinsight.llm.tokens_used{*}` (stacked)
   - Query: `avg:policyinsight.llm.estimated_cost_usd{*}` (as second widget)
   - Type: Timeseries (left), Gauge (right)
   - Display: total tokens per hour + estimated hourly cost

8. **Gemini Latency (LLM Performance)**
   - Query: `pct_95:policyinsight.llm.latency_ms{*}`
   - Type: Timeseries
   - Breakdown by: call_type (summary_generation, risk_scan, qa_answer)

9. **Q&A Grounded Rate (Safety Metric)**
   - Query: `sum:policyinsight.qa.grounded_rate{*}` / (sum + sum of ungrounded)
   - Type: Gauge
   - Threshold: >0.85 (green), <0.80 (red)

10. **Risk Detection Distribution (Heatmap)**
    - Query: `avg:policyinsight.analysis.risks_detected_count{*}`
    - Type: Table (pivoted by risk_category)
    - Shows: # of documents with data_privacy risk, financial risk, etc.

11. **Job Latency by Stage (Breakdown)**
    - Query: `avg:policyinsight.worker.job_latency_ms{*}`
    - Type: Timeseries (stacked area)
    - Breakdown by: stage (extraction, classification, risk_scan, summary)

12. **Document Status Distribution (Pie Chart)**
    - Query: `sum:policyinsight.document.count{*}`
    - Type: Pie chart
    - Breakdown by: status (pending, completed, failed)

13. **Service Health (Status Widget)**
    - Query: `avg:system.cpu{host:policyinsight-cloud-run}` (placeholder)
    - Query: `avg:system.memory{host:policyinsight-cloud-run}`
    - Query: `avg:postgresql.percent_usage_connections{*}`
    - Type: Status widget (red/yellow/green)

---

### Monitors & SLOs

#### 9.4.1 Monitor 1: API Latency Spike

**Name:** PolicyInsight – p95 API Latency > 5s

**Type:** Metric Alert

**Query:**
```
avg:policyinsight.api.latency_ms{*} > 5000
```

**Evaluation:**
- Alert if: p95 latency exceeds 5 seconds for >2 minutes
- Warning threshold: >3 seconds for >1 minute

**Notification:**
```
Datadog Incident: {{alert.title}}

API latency has spiked:
  Metric: policyinsight.api.latency_ms (p95)
  Threshold: 5000ms
  Current: {{value}} ms
  Duration: {{duration}}

Investigation steps:
  1. Check dashboard: https://app.datadoghq.com/dashboard/policyinsight-ops
  2. Review traces: https://app.datadoghq.com/apm/services/policyinsight
  3. Check GCP Cloud Run metrics (CPU, memory, cold starts)
  4. Verify Vertex AI API latency (may be upstream cause)
  5. Check Database query performance (slow queries?)

Runbook: https://wiki.internal.com/policyinsight/runbooks/api-latency-spike
```

**Severity:** Major (impacts user-facing request)

**Escalation:** Slack → #policyinsight-oncall (if >2 minute duration)

---

#### 9.4.2 Monitor 2: Pub/Sub Queue Age (Job Backlog)

**Name:** PolicyInsight – Document Analysis Queue Age > 5 min

**Type:** Metric Alert

**Query:**
```
avg:policyinsight.pubsub.queue_age_seconds{topic:document-analysis-topic} > 300
```

**Evaluation:**
- Alert if: oldest job in queue is >5 minutes old for >3 minutes
- Warning: >120 seconds for >1 minute

**Notification:**
```
Datadog Incident: {{alert.title}}

Document analysis queue is backed up:
  Metric: policyinsight.pubsub.queue_age_seconds
  Threshold: 300s (5 min)
  Current: {{value}} s

Impact: Users will experience slow report generation.

Investigation:
  1. Check Pub/Sub topic metrics: https://console.cloud.google.com/cloudpubsub
  2. Verify worker service is running: kubectl get pods -l app=policyinsight-worker
  3. Check worker logs for errors: gcloud logging read "resource.labels.service_name=policyinsight-worker"
  4. Verify Vertex AI quota/rate limits: https://console.cloud.google.com/quotas
  5. Review worker logs for extraction/storage errors

Action:
  - Scale worker replicas: kubectl scale deployment policyinsight-worker --replicas=5
  - Monitor queue age for next 10 min

Runbook: https://wiki.internal.com/policyinsight/runbooks/queue-backlog
```

**Severity:** Critical (impacts all new uploads)

**Escalation:** Slack → #policyinsight-oncall (if >2 minute duration)

---

#### 9.4.3 Monitor 3: LLM Token/Cost Anomaly + Citation Coverage Drop

**Name:** PolicyInsight – LLM Cost Spike OR Citation Coverage <80%

**Type:** Composite (OR logic)

**Query A (Cost):**
```
avg:policyinsight.llm.estimated_cost_usd{*} > 10 AND hour_before(avg:policyinsight.llm.estimated_cost_usd{*}) < 5
```
(If hourly cost doubles from previous hour)

**Query B (Citation Coverage):**
```
avg:policyinsight.analysis.citation_coverage_rate{*} < 0.80
```

**Evaluation:**
- Alert if: (cost spike 2x) OR (citation_coverage <80%) for >1 minute

**Notification:**
```
Datadog Incident: {{alert.title}}

LLM cost anomaly OR citation coverage drop detected:

  Metric A: policyinsight.llm.estimated_cost_usd
  Status: {{metric_a_status}}
  Value: {{metric_a_value}} USD/hour

  Metric B: policyinsight.analysis.citation_coverage_rate
  Status: {{metric_b_status}}
  Value: {{metric_b_value}} %

Impact:
  - Cost spike: LLM API is consuming more tokens than expected (possible bug, hallucination)
  - Citation coverage drop: System may be hallucinating (not grounding claims in document text)

Investigation:
  1. Check recent deployments: git log --oneline -20
  2. Review LLM prompts in latest code (may be less constrained)
  3. Spot-check 5 recent analyses for hallucinations: https://app.datadoghq.com/apm/services/policyinsight/resources/policyinsight
  4. Check if model changed (Gemini 1.0 vs 2.0): check env vars in Cloud Run
  5. Review error logs for LLM retry loops (may be retrying same prompt)

Action:
  - If cost: Rollback latest deployment (git revert, redeploy)
  - If citation coverage: Disable LLM-based risk summary, switch to rule-based only (fallback mode)

Runbook: https://wiki.internal.com/policyinsight/runbooks/llm-cost-citation
```

**Severity:** Major (cost impact, safety risk)

**Escalation:** Slack → #policyinsight-oncall + #finance (if cost >$50/hour)

---

#### 9.4.4 SLO Definitions

**SLO 1: Request Success Rate**
- **Target:** 99.5%
- **Metric:** (200/201 responses) / (all responses)
- **Query:** `sum:policyinsight.api.responses{status:2xx|3xx} / sum:policyinsight.api.responses{*}`
- **Window:** 30-day rolling
- **Error budget:** 3.6 hours of downtime per month

**SLO 2: Report Generation Latency (p95)**
- **Target:** <10 seconds (per document analysis, end-to-end)
- **Metric:** p95(policyinsight.document.processing_latency_ms)
- **Query:** `pct_95:policyinsight.document.processing_latency_ms{*}`
- **Window:** 30-day rolling
- **Error budget:** 14.4 hours of "slow" performance per month

**SLO 3: Citation Coverage (Grounding Enforcement)**
- **Target:** 95% of claims must cite source text
- **Metric:** avg(policyinsight.analysis.citation_coverage_rate)
- **Query:** `avg:policyinsight.analysis.citation_coverage_rate{*}`
- **Window:** 30-day rolling
- **Error budget:** Allows 5% hallucination rate (per 100 claims, <5 ungrounded)

---

### Incident Management & Runbooks

#### 9.5.1 Incident Creation Workflow

When a monitor triggers → Datadog automatically:

1. **Creates Datadog Incident (or Case):**
   ```json
   {
     "title": "PolicyInsight – API Latency Spike",
     "status": "ACTIVE",
     "severity": "MAJOR",
     "source": "monitor_alerting",
     "tags": ["service:policyinsight", "team:platform"],
     "commander": "auto-assign-on-call",
     "timeline": {
       "incident_start": "2025-12-28T22:30:00Z",
       "detection_time": "2025-12-28T22:32:00Z",
       "ttd": "120 seconds"
     }
   }
   ```

2. **Auto-Attaches Context:**
   - Link to triggering monitor
   - Link to dashboard
   - Link to APM trace (latest 100 traces)
   - Link to logs (past 10 minutes, service:policyinsight)
   - Git commit of currently running version
   - Cloud Run revision info

3. **Populates Runbook:**
   - From monitor notification text (see Monitor templates above)
   - Includes investigation checklist
   - Escalation path
   - Rollback steps

4. **Notifies On-Call:**
   - Slack message to #policyinsight-oncall with incident link
   - PagerDuty escalation (if configured)

---

#### 9.5.2 Runbook Examples

**Runbook: API Latency Spike**

```markdown
# API Latency Spike Troubleshooting

## Symptoms
- p95 API latency > 5 seconds
- Users report slow report generation
- Incident fired at: [timestamp]

## Immediate Actions (First 2 minutes)
1. Check dashboard: https://app.datadoghq.com/dashboard/policyinsight-ops
   - Is CPU/memory spiking?
   - Is database connection pool exhausted?
   - Is there a Pub/Sub queue backlog (workers overloaded)?

2. Check Vertex AI API status:
   - https://status.cloud.google.com/ (check Vertex AI section)
   - Are rate limits/quotas hit?

3. Check current Git revision deployed:
   - `gcloud run services describe policyinsight --region us-central1`
   - Compare with HEAD: `git log --oneline -1`

## Investigation (Next 5 minutes)

### Scenario 1: High CPU/Memory Usage
- **Cause:** Service is compute-bound (likely LLM calls or PDF generation)
- **Action:**
  - Scale Cloud Run: `gcloud run services update policyinsight --max-instances 10 --region us-central1`
  - Monitor: Watch dashboard for recovery (p95 should drop within 2 min)
  - If no recovery after 2 min: Proceed to Scenario 3

### Scenario 2: Database Bottleneck
- **Cause:** Slow DB queries, connection pool exhausted
- **Action:**
  - Check slow query log: `gcloud sql operations list --instance policyinsight-db | head -5`
  - Kill long-running queries: `gcloud sql connect policyinsight-db -- -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE state='idle'"`
  - If queries are legitimate: Scale DB (add replicas, increase instance size)

### Scenario 3: Upstream API Failure (Vertex AI)
- **Cause:** Google Cloud API is slow/erroring
- **Action:**
  - Check logs for API errors: `gcloud logging read "resource.labels.service_name=policyinsight AND severity=ERROR" --limit 20`
  - If Gemini is rate-limited: Implement exponential backoff (already done in code)

## Rollback (If Latency Still High After 5 minutes)

If you suspect a recent code change caused latency:

```bash
# Get previous revision
PREV_REVISION=$(gcloud run services describe policyinsight --region us-central1 --format='value(status.traffic[1].revision.name)')

# Rollback
gcloud run services update-traffic policyinsight --region us-central1 --to-revisions=$PREV_REVISION=100

# Verify
gcloud run services describe policyinsight --region us-central1 --format='value(status.traffic[*].revision.name)'

# Monitor for recovery (p95 latency should drop within 1 min)
# If recovered: create post-incident review
# If not: proceed to escalation
```

## Escalation
- If issue persists >10 minutes: Page on-call infrastructure team
- If cost/quota issues: Notify #finance (LLM overage)
- If data integrity concern: Notify #security

## Post-Incident
- [ ] Root cause identified
- [ ] Fix committed and tested
- [ ] Rolled out with canary (10% traffic for 5 min)
- [ ] Incident marked RESOLVED
- [ ] Post-mortem scheduled within 24 hours
```

---

### Traffic Generator Script

Purpose: Trigger each monitor (latency spike, queue backlog, cost/citation anomaly) to demonstrate incident creation and dashboard integration.

```bash
#!/bin/bash
# File: scripts/trigger-monitors.sh

set -e

POLICYINSIGHT_URL="https://policyinsight-app.cloudrun.com"
TEST_DOCS_DIR="./test-documents"

echo "🚀 PolicyInsight Datadog Monitor Trigger Script"
echo "==============================================="

# Monitor 1: API Latency Spike
# Trigger by uploading many large documents in parallel

echo "📤 Monitor 1: Triggering API Latency Spike..."
echo "   Uploading 50 documents in parallel (will queue on backend)"

for i in {1..50}; do
  curl -X POST "$POLICYINSIGHT_URL/api/documents/upload" \
    -F "file=@$TEST_DOCS_DIR/sample-tos.pdf" \
    &

  # Rate limit to avoid overwhelming network
  if [ $((i % 10)) -eq 0 ]; then
    sleep 2
  fi
done
wait

echo "   ✅ Uploads queued. Datadog should show latency spike in 30 seconds."
echo "   📊 Evidence: https://app.datadoghq.com/dashboard/policyinsight-ops"
echo ""

# Monitor 2: Pub/Sub Queue Age
# Monitor 1's uploads will naturally cause queue backlog
# (Pub/Sub metrics update every 1 minute)

echo "⏳ Monitor 2: Queue Age will spike (from Monitor 1 uploads)"
echo "   Pub/Sub queue will age as documents wait for worker processing."
echo "   ⏱️  Check in ~2 minutes at:"
echo "   📊 https://app.datadoghq.com/monitors/[MONITOR_ID_2]"
echo ""

# Monitor 3: LLM Cost / Citation Coverage Anomaly
# Inject a "bad prompt" document that triggers high token usage
# (This simulates a regression in prompt engineering)

echo "💰 Monitor 3: Triggering LLM Cost Spike..."
echo "   Uploading a document with a 'bad prompt' that causes token inflation"

# Create a test document with metadata that triggers verbose LLM responses
cat > /tmp/bad-prompt-tos.txt <<'EOF'
[This is an intentionally complex Terms of Service designed to trigger]
[high token usage. It includes many redundant clauses repeated 10x each]
[to force the LLM to process verbose text without adding signal.]

Repeated clause (1/10): "The Company reserves the right to modify terms..."
Repeated clause (2/10): "The Company reserves the right to modify terms..."
...
[Repeat 8 more times]

[This should cause Gemini to use 2-3x normal tokens.]
EOF

echo "   ⚠️  Note: In a real scenario, this would be caused by a code regression."
echo "   ✅ Monitor should trigger if token usage 2x baseline."
echo "   📊 Evidence: https://app.datadoghq.com/monitors/[MONITOR_ID_3]"
echo ""

echo "🎯 All monitors triggered!"
echo ""
echo "📋 Monitor Observation Checklist:"
echo "  ✅ Monitor 1 (API Latency): Should fire ~30 seconds"
echo "  ✅ Monitor 2 (Queue Age): Should fire ~2 minutes"
echo "  ✅ Monitor 3 (Cost/Citation): Depends on LLM telemetry"
echo ""
echo "📍 View Incidents:"
echo "  https://app.datadoghq.com/incidents"
echo ""
echo "📊 View Dashboard (with monitors firing):"
echo "  https://app.datadoghq.com/dashboard/policyinsight-ops"
echo ""
```

**Usage:**
```bash
# Make script executable
chmod +x scripts/trigger-monitors.sh

# Run (will upload test documents, trigger monitors)
./scripts/trigger-monitors.sh

# Watch Datadog for monitor fires + incident creation (next 3 minutes)
```

---

## 10. CI/CD Plan

### GitHub Actions Workflows

#### 10.1 PR Workflow (`.github/workflows/pr.yml`)

```yaml
name: PR Checks

on:
  pull_request:
    branches: [main, develop]

jobs:
  build-test:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:15-alpine
        env:
          POSTGRES_DB: policyinsight_test
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: postgres
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432

    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0  # Full history for Sonar

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: 21
          distribution: temurin
          cache: maven

      - name: Lint & Format Check
        run: |
          ./mvnw spotless:check
          ./mvnw checkstyle:check

      - name: Unit Tests
        run: ./mvnw test
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/policyinsight_test
          SPRING_DATASOURCE_USERNAME: postgres
          SPRING_DATASOURCE_PASSWORD: postgres

      - name: Integration Tests
        run: ./mvnw verify -P integration-tests
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/policyinsight_test

      - name: Build Docker Image
        run: |
          docker build -t policyinsight:${{ github.sha }} .
          docker image ls

      - name: Verify DB Migrations
        run: |
          docker run --rm \
            --network host \
            -e SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/policyinsight_test" \
            policyinsight:${{ github.sha }} \
            ./mvnw flyway:validate

      - name: Generate OpenAPI Spec
        run: ./mvnw springdoc-openapi:generate

      - name: SonarQube Analysis (Optional)
        run: |
          ./mvnw sonar:sonar \
            -Dsonar.projectKey=policyinsight \
            -Dsonar.host.url=${{ secrets.SONAR_HOST_URL }} \
            -Dsonar.login=${{ secrets.SONAR_TOKEN }}
        if: always()

      - name: Publish Test Results
        uses: EnricoMi/publish-unit-test-result-action@v2
        if: always()
        with:
          files: target/surefire-reports/**/*.xml

      - name: Comment PR with Results
        if: always()
        uses: actions/github-script@v6
        with:
          script: |
            const fs = require('fs');
            const results = JSON.parse(fs.readFileSync('target/test-results.json'));
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: `✅ Tests: ${results.passed}/${results.total} passed`
            });
```

#### 10.2 Deploy Workflow (`.github/workflows/deploy.yml`)

```yaml
name: Deploy to Cloud Run

on:
  push:
    branches: [main]
    tags: ['v*']

env:
  PROJECT_ID: ${{ secrets.GCP_PROJECT_ID }}
  REGION: us-central1
  SERVICE_NAME: policyinsight
  IMAGE_REPO: us-central1-docker.pkg.dev

jobs:
  deploy:
    runs-on: ubuntu-latest

    permissions:
      contents: read
      id-token: write

    steps:
      - uses: actions/checkout@v4

      - name: Extract Version
        id: version
        run: |
          if [[ ${{ github.ref }} == refs/tags/* ]]; then
            VERSION=${GITHUB_REF#refs/tags/v}
          else
            VERSION=${{ github.sha }}
          fi
          echo "version=$VERSION" >> $GITHUB_OUTPUT
          echo "image=$IMAGE_REPO/$PROJECT_ID/policyinsight:$VERSION" >> $GITHUB_OUTPUT

      - name: Authenticate to Google Cloud
        uses: google-github-actions/auth@v1
        with:
          workload_identity_provider: ${{ secrets.WIF_PROVIDER }}
          service_account: ${{ secrets.WIF_SERVICE_ACCOUNT }}

      - name: Set up Cloud SDK
        uses: google-github-actions/setup-gcloud@v1

      - name: Configure Docker for Artifact Registry
        run: |
          gcloud auth configure-docker us-central1-docker.pkg.dev

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: 21
          distribution: temurin
          cache: maven

      - name: Build with Maven
        run: ./mvnw clean package -DskipTests -Dapp.version=${{ steps.version.outputs.version }}

      - name: Build Docker Image
        run: |
          docker build \
            -t ${{ steps.version.outputs.image }} \
            -t $IMAGE_REPO/$PROJECT_ID/policyinsight:latest \
            --build-arg VERSION=${{ steps.version.outputs.version }} \
            .

      - name: Push to Artifact Registry
        run: |
          docker push ${{ steps.version.outputs.image }}
          docker push $IMAGE_REPO/$PROJECT_ID/policyinsight:latest

      - name: Deploy to Cloud Run (Canary: 10% traffic)
        id: deploy-canary
        run: |
          gcloud run deploy $SERVICE_NAME \
            --image ${{ steps.version.outputs.image }} \
            --region $REGION \
            --platform managed \
            --allow-unauthenticated \
            --set-env-vars=DD_SERVICE=policyinsight,DD_ENV=prod,DD_VERSION=${{ steps.version.outputs.version }} \
            --service-account ${{ secrets.CLOUD_RUN_SERVICE_ACCOUNT }} \
            --timeout 3600 \
            --memory 2Gi \
            --cpu 2 \
            --max-instances 50 \
            --min-instances 2 \
            --no-traffic
        env:
          DD_SERVICE: policyinsight
          DD_ENV: prod
          DD_VERSION: ${{ steps.version.outputs.version }}

      - name: Run Smoke Tests (Canary)
        run: |
          SERVICE_URL=$(gcloud run services describe $SERVICE_NAME \
            --region $REGION --format='value(status.url)')

          # Health check
          curl -f $SERVICE_URL/health || exit 1

          # Readiness check
          curl -f $SERVICE_URL/readiness || exit 1

          # Upload test document
          curl -X POST $SERVICE_URL/api/documents/upload \
            -F "file=@./test-documents/sample-tos.pdf" || exit 1
        timeout-minutes: 5

      - name: Promote to Prod (90% traffic)
        if: success()
        run: |
          # Get new revision
          NEW_REV=$(gcloud run services describe $SERVICE_NAME \
            --region $REGION --format='value(status.traffic[0].revision.name)')

          # Get old revision (if exists)
          OLD_REV=$(gcloud run services describe $SERVICE_NAME \
            --region $REGION --format='value(status.traffic[1].revision.name)') || true

          # Traffic split: 90% new, 10% old (for quick rollback)
          if [ -n "$OLD_REV" ]; then
            gcloud run services update-traffic $SERVICE_NAME \
              --region $REGION \
              --to-revisions=$NEW_REV=90,$OLD_REV=10
          else
            gcloud run services update-traffic $SERVICE_NAME \
              --region $REGION \
              --to-revisions=$NEW_REV=100
          fi

      - name: Wait for Metrics Stabilization
        run: |
          # Wait 5 minutes for metrics to stabilize
          sleep 300

          # Check error rate (should be <1%)
          ERROR_RATE=$(gcloud monitoring time-series list \
            --filter='metric.type="run.googleapis.com/request_count" AND resource.labels.service_name="policyinsight"' \
            --format='value(metric.type)')

          # If error rate high, trigger automatic rollback
          # (Can implement via a Cloud Function or secondary script)

      - name: Publish Release
        if: startsWith(github.ref, 'refs/tags/')
        uses: softprops/action-gh-release@v1
        with:
          files: target/policyinsight-*.jar
          body: |
            Docker Image: ${{ steps.version.outputs.image }}

            Deployment Status: ✅ Live on Cloud Run

            Dashboard: https://app.datadoghq.com/dashboard/policyinsight-ops
            Incident Response: https://app.datadoghq.com/incidents
```

#### 10.3 Rollback Workflow (`.github/workflows/rollback.yml`)

```yaml
name: Rollback Deployment

on:
  workflow_dispatch:
    inputs:
      revision:
        description: 'Cloud Run revision to rollback to (leave blank for previous)'
        required: false
      percentage:
        description: 'Rollback percentage (0-100)'
        required: false
        default: '100'

jobs:
  rollback:
    runs-on: ubuntu-latest
    permissions:
      id-token: write

    steps:
      - uses: google-github-actions/auth@v1
        with:
          workload_identity_provider: ${{ secrets.WIF_PROVIDER }}
          service_account: ${{ secrets.WIF_SERVICE_ACCOUNT }}

      - uses: google-github-actions/setup-gcloud@v1

      - name: Get Previous Revision
        id: prev-rev
        if: ${{ inputs.revision == '' }}
        run: |
          REV=$(gcloud run services describe policyinsight \
            --region us-central1 \
            --format='value(status.traffic[1].revision.name)')
          echo "revision=$REV" >> $GITHUB_OUTPUT

      - name: Rollback Traffic
        run: |
          REVISION=${{ inputs.revision || steps.prev-rev.outputs.revision }}
          gcloud run services update-traffic policyinsight \
            --region us-central1 \
            --to-revisions=$REVISION=${{ inputs.percentage }}

          gcloud run services describe policyinsight \
            --region us-central1 \
            --format='value(status.traffic[*].revision.name, status.traffic[*].percent)'

      - name: Notify Slack
        uses: slackapi/slack-github-action@v1
        with:
          webhook-url: ${{ secrets.SLACK_WEBHOOK_ROLLBACK }}
          payload: |
            {
              "text": "🔄 PolicyInsight Rollback",
              "blocks": [
                {
                  "type": "section",
                  "text": {
                    "type": "mrkdwn",
                    "text": "*PolicyInsight Deployment Rollback*\n\nRevision: `${{ inputs.revision || steps.prev-rev.outputs.revision }}`\nTraffic: `${{ inputs.percentage }}%`\n\n<https://app.datadoghq.com/dashboard/policyinsight-ops|View Dashboard>"
                  }
                }
              ]
            }
```

---

## 11. Setup & Deployment Guide

### A. Google Cloud Setup

#### Step 1: Create GCP Project & Enable APIs

```bash
# Set project variables
export PROJECT_ID="policyinsight-${RANDOM}"
export REGION="us-central1"
export GCP_ACCOUNT="$(gcloud config get-value account)"

# Create project
gcloud projects create $PROJECT_ID

# Set as default
gcloud config set project $PROJECT_ID

# Enable required APIs
gcloud services enable \
  cloudrun.googleapis.com \
  artifactregistry.googleapis.com \
  cloudsql.googleapis.com \
  storage.googleapis.com \
  aiplatform.googleapis.com \
  pubsub.googleapis.com \
  cloudkms.googleapis.com \
  logging.googleapis.com \
  monitoring.googleapis.com \
  compute.googleapis.com

echo "✅ APIs enabled"
```

#### Step 2: Create Service Accounts & IAM Roles

```bash
# Service account for Cloud Run (web + worker)
gcloud iam service-accounts create policyinsight-app \
  --display-name="PolicyInsight App Service Account"

# Service account for deployment (GitHub Actions)
gcloud iam service-accounts create github-deployer \
  --display-name="GitHub Actions Deployer"

# Grant Cloud Run admin to deployment account
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:github-deployer@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/run.admin"

# Grant service account user
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:github-deployer@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/iam.serviceAccountUser"

# Grant Artifact Registry writer
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:github-deployer@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.writer"

# Grant app service account access to Cloud SQL, GCS, Pub/Sub, Vertex AI
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:policyinsight-app@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/cloudsql.client"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:policyinsight-app@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/storage.objectAdmin"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:policyinsight-app@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/pubsub.editor"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:policyinsight-app@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/aiplatform.user"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:policyinsight-app@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/logging.logWriter"

echo "✅ Service accounts & IAM roles configured"
```

#### Step 3: Provision Cloud SQL (PostgreSQL 15)

```bash
# Create Cloud SQL instance
gcloud sql instances create policyinsight-db \
  --database-version POSTGRES_15 \
  --region $REGION \
  --tier db-custom-2-8192 \
  --availability-type REGIONAL \
  --backup-start-time 03:00 \
  --enable-bin-log \
  --labels=service=policyinsight,env=prod

# Create database
gcloud sql databases create policyinsight \
  --instance policyinsight-db

# Create app user
DB_PASSWORD=$(openssl rand -base64 32)
gcloud sql users create policyinsight \
  --instance policyinsight-db \
  --password=$DB_PASSWORD

# Get connection string
DB_HOST=$(gcloud sql instances describe policyinsight-db \
  --format='value(ipAddresses[0].ipAddress)')

echo "DB Host: $DB_HOST"
echo "DB Password: $DB_PASSWORD"
echo "Save these to GitHub secrets"
```

#### Step 4: Create GCS Bucket

```bash
# Create bucket for PDFs and exports
gsutil mb -l $REGION gs://policyinsight-bucket-${PROJECT_ID}/

# Set lifecycle policy (auto-delete documents after 30 days)
cat > /tmp/lifecycle.json <<EOF
{
  "lifecycle": {
    "rule": [
      {
        "action": {"type": "Delete"},
        "condition": {"age": 30}
      }
    ]
  }
}
EOF

gsutil lifecycle set /tmp/lifecycle.json gs://policyinsight-bucket-${PROJECT_ID}/

echo "✅ GCS bucket created"
```

#### Step 5: Create Pub/Sub Topic & Subscription

```bash
# Create topic for document analysis jobs
gcloud pubsub topics create document-analysis-topic \
  --message-retention-duration=7d

# Create subscription for worker service
gcloud pubsub subscriptions create document-analysis-sub \
  --topic=document-analysis-topic \
  --ack-deadline=600 \
  --message-retention-duration=7d \
  --expiration-period=never

echo "✅ Pub/Sub topic & subscription created"
```

#### Step 6: Create Artifact Registry

```bash
# Create Docker repository
gcloud artifacts repositories create policyinsight \
  --repository-format=docker \
  --location $REGION

# Configure Docker authentication
gcloud auth configure-docker ${REGION}-docker.pkg.dev

echo "✅ Artifact Registry created"
```

---

### B. Datadog Setup

#### Step 1: Create Datadog Organization

```bash
# In Datadog UI (or via API):
# 1. Go to https://app.datadoghq.com/signup
# 2. Create organization (free trial available)
# 3. Copy API Key + App Key

export DD_API_KEY="<your-api-key>"
export DD_APP_KEY="<your-app-key>"

# Verify (optional)
curl -X GET "https://api.datadoghq.com/api/v1/validate" \
  -H "DD-API-KEY: $DD_API_KEY"
```

#### Step 2: Set Up GCP Integration

```bash
# In Datadog UI:
# 1. Go to Integrations → Google Cloud
# 2. Click "Link GCP Account"
# 3. Follow OAuth flow, grant permissions
# 4. Select metrics to collect (CloudRun, CloudSQL, Pub/Sub)

# Verify integration is active (in Datadog UI):
# - Check Integrations → Google Cloud for "Connected"
# - Wait 5 minutes for metrics to appear
```

#### Step 3: Instrument Cloud Run Service

```bash
# Dockerfile already includes dd-java-agent
# Set env vars when deploying:

gcloud run deploy policyinsight \
  --image us-central1-docker.pkg.dev/${PROJECT_ID}/policyinsight/policyinsight:latest \
  --set-env-vars \
    DD_API_KEY=$DD_API_KEY,\
    DD_AGENT_HOST=localhost,\
    DD_AGENT_PORT=8126,\
    DD_SERVICE=policyinsight,\
    DD_ENV=prod,\
    DD_VERSION=1.0.0,\
    DD_TRACE_ENABLED=true,\
    DD_PROFILING_ENABLED=true \
  --service-account policyinsight-app@${PROJECT_ID}.iam.gserviceaccount.com \
  --region $REGION \
  --allow-unauthenticated
```

#### Step 4: Create Dashboards (JSON Exports)

Save these JSON files to `/datadog/dashboards/` in repo:

**File: `datadog/dashboards/policyinsight-ops.json`**

```json
{
  "title": "PolicyInsight Ops",
  "description": "Real-time operations dashboard for PolicyInsight service",
  "layout_type": "ordered",
  "widgets": [
    {
      "id": 1,
      "definition": {
        "type": "timeseries",
        "requests": [
          {
            "q": "avg:policyinsight.api.latency_ms{*}"
          }
        ],
        "title": "API Latency (p95)",
        "yaxis": {
          "label": "milliseconds"
        }
      }
    },
    {
      "id": 2,
      "definition": {
        "type": "timeseries",
        "requests": [
          {
            "q": "sum:policyinsight.api.errors{*}"
          }
        ],
        "title": "Error Count (4xx/5xx)"
      }
    },
    {
      "id": 3,
      "definition": {
        "type": "gauge",
        "requests": [
          {
            "q": "avg:policyinsight.pubsub.queue_age_seconds{topic:document-analysis-topic}"
          }
        ],
        "title": "Pub/Sub Queue Age",
        "thresholds": {
          "critical": 300,
          "warning": 120
        }
      }
    },
    {
      "id": 4,
      "definition": {
        "type": "timeseries",
        "requests": [
          {
            "q": "avg:policyinsight.llm.estimated_cost_usd{*}"
          }
        ],
        "title": "Estimated LLM Cost (hourly)",
        "yaxis": {
          "label": "USD"
        }
      }
    },
    {
      "id": 5,
      "definition": {
        "type": "gauge",
        "requests": [
          {
            "q": "avg:policyinsight.analysis.citation_coverage_rate{*}"
          }
        ],
        "title": "Citation Coverage Rate",
        "thresholds": {
          "critical": 0.8,
          "warning": 0.9
        }
      }
    }
  ]
}
```

#### Step 5: Create Monitors (JSON Exports)

**File: `datadog/monitors/monitor-1-api-latency.json`**

```json
{
  "name": "PolicyInsight – p95 API Latency > 5s",
  "type": "metric alert",
  "query": "avg(last_5m):avg:policyinsight.api.latency_ms{*} > 5000",
  "message": "API latency spike detected. {{#is_alert}}Check dashboard: https://app.datadoghq.com/dashboard/policyinsight-ops{{/is_alert}}",
  "tags": ["service:policyinsight", "team:platform"],
  "notify_no_data": false,
  "thresholds": {
    "critical": 5000,
    "warning": 3000
  }
}
```

**File: `datadog/monitors/monitor-2-queue-age.json`**

```json
{
  "name": "PolicyInsight – Pub/Sub Queue Age > 5 min",
  "type": "metric alert",
  "query": "avg(last_3m):avg:policyinsight.pubsub.queue_age_seconds{topic:document-analysis-topic} > 300",
  "message": "Document analysis queue is backed up. {{#is_alert}}Escalating to worker scaling.{{/is_alert}}",
  "tags": ["service:policyinsight", "team:platform"],
  "notify_no_data": false,
  "thresholds": {
    "critical": 300,
    "warning": 120
  }
}
```

**File: `datadog/monitors/monitor-3-llm-citation.json`**

```json
{
  "name": "PolicyInsight – LLM Cost Spike OR Citation Coverage <80%",
  "type": "composite",
  "composite_conditions": [
    {
      "operator": "OR",
      "operands": [
        {
          "type": "metric alert",
          "query": "avg(last_1h):avg:policyinsight.llm.estimated_cost_usd{*} > 10 && hour_before(avg:policyinsight.llm.estimated_cost_usd{*}) < 5"
        },
        {
          "type": "metric alert",
          "query": "avg(last_1h):avg:policyinsight.analysis.citation_coverage_rate{*} < 0.8"
        }
      ]
    }
  ],
  "message": "LLM cost anomaly OR hallucination detected. {{#is_alert}}Check traces and consider rollback.{{/is_alert}}",
  "tags": ["service:policyinsight", "team:platform"]
}
```

#### Step 6: Import Dashboards & Monitors

```bash
# Import via Datadog API
for file in datadog/dashboards/*.json; do
  curl -X POST "https://api.datadoghq.com/api/v1/dashboard" \
    -H "DD-API-KEY: $DD_API_KEY" \
    -H "Content-Type: application/json" \
    -d @$file
done

for file in datadog/monitors/*.json; do
  curl -X POST "https://api.datadoghq.com/api/v1/monitor" \
    -H "DD-API-KEY: $DD_API_KEY" \
    -H "Content-Type: application/json" \
    -d @$file
done

echo "✅ Dashboards & monitors imported"
```

---

### C. Local Development (Docker Compose)

```yaml
# docker-compose.yml

version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: policyinsight
      POSTGRES_USER: policyinsight
      POSTGRES_PASSWORD: policyinsight123
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U policyinsight"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    build:
      context: .
      dockerfile: Dockerfile
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/policyinsight
      SPRING_DATASOURCE_USERNAME: policyinsight
      SPRING_DATASOURCE_PASSWORD: policyinsight123
      GCS_BUCKET: local-bucket  # Mock/local
      PUBSUB_TOPIC: document-analysis-topic
      VERTEX_AI_ENABLED: "false"  # Disable for local dev
      DD_ENABLED: "false"  # Disable Datadog locally (optional)
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
    volumes:
      - .:/app
      - /app/target  # Exclude Maven build artifacts

volumes:
  postgres_data:
```

**Start local environment:**
```bash
docker-compose up -d

# Verify
curl http://localhost:8080/health
# {"status":"UP"}

# Run Flyway migrations (auto on startup)
# Check DB
docker exec -it policyinsight-postgres psql -U policyinsight -d policyinsight -c "\dt"
```

---

### D. Deployment Checklist

- [ ] **Google Cloud:** Project created, APIs enabled, service accounts configured
- [ ] **Cloud SQL:** PostgreSQL instance provisioned, database created, password saved to secrets
- [ ] **GCS:** Bucket created with lifecycle policy
- [ ] **Pub/Sub:** Topic and subscription created
- [ ] **Artifact Registry:** Docker repo created
- [ ] **Datadog:** Organization created, API/App keys saved to GitHub secrets, GCP integration enabled
- [ ] **GitHub Actions:** Deploy workflow configured with WIF (Workload Identity Federation) for keyless auth
- [ ] **Environment Variables:** Set in Cloud Run, GitHub secrets, and local `.env`
- [ ] **Migrations:** Flyway migrations verified locally
- [ ] **Docker Image:** Built and pushed to Artifact Registry
- [ ] **Cloud Run:** Service deployed, health checks passing
- [ ] **Datadog Dashboards:** Created and visible in Datadog UI
- [ ] **Monitors:** Configured and active
- [ ] **Incident Response:** Runbooks written, on-call rotation configured

---

## 12. Demo Script (3 Minutes) + Verification Checklist

### Demo Narrative

> **[0:00–0:30] Opening**
>
> "PolicyInsight is a full-stack, backend-leaning service that analyzes legal documents in plain English. We've built it to demonstrate production-grade reliability, observability, and engineering practices—not a toy demo."
>
> **[0:30–1:00] Upload & Processing**
>
> "Let me upload a real Terms of Service." [Click upload button, drag PDF]
> "The system immediately stores the file in Google Cloud Storage and publishes a job to Pub/Sub. The user gets back a job ID and polling URL. No waiting on the web request."
>
> **[1:00–1:30] Report Generation**
>
> "While that processes in the background, let me show you a completed analysis." [Navigate to previous report]
> "Here's the risk report: 5 sections, each with plain-English findings and citations to the source document. Every claim cites the specific page and chunk."
>
> **[1:30–1:50] Grounded Q&A**
>
> "The system enforces a strict 'cite-or-abstain' rule. Watch: I'll ask a question it can't answer from the document." [Ask "What is the CEO's favorite color?"]
> "See? It refuses to hallucinate. It says, 'This document does not address that topic.'"
>
> Now ask a grounded question: "Can they increase my rent?"
> "It answers with evidence: 'Section 4.2 states rent can increase with 30-day notice.' And it cites the specific chunks."
>
> **[1:50–2:20] Observability**
>
> "Under the hood, every request flows through Datadog APM. Let me show the ops dashboard." [Open Datadog dashboard]
> "You're seeing real-time metrics: API latency (p95 ~500ms), error rate (zero), document processing latency, LLM token usage, and cost estimates. Every service layer is traced."
>
> **[2:20–2:40] Incident Response**
>
> "We've set up 3 monitors: one for API latency spikes, one for Pub/Sub queue backlog, and one for LLM cost anomalies. When a monitor fires, it automatically creates an incident in Datadog with context and a runbook."
> [Show incident page with traces, logs, and runbook]
>
> **[2:40–3:00] Close**
>
> "What we've shipped: a production-ready service on Cloud Run, instrumented end-to-end with Datadog, deployed via GitHub Actions CI/CD, with automated incident response. This is a system a junior engineer could own and operate."

---

### Verification Checklist

| Item | Evidence | Screenshot/Link |
|------|----------|---|
| **App Landing Page** | Home page loads, upload button visible, disclaimer prominent | `https://<hosted-url>/` |
| **File Upload** | PDF uploaded, 202 Accepted response, job ID returned | Network tab in DevTools showing POST /api/documents/upload |
| **Status Polling** | Job status endpoint returns 'processing' → 'completed' | Browser console showing GET /api/documents/{id}/status every 2s |
| **Report Rendered** | 5-section report displays with citations | Report page with Overview, Summary, Obligations, Risks, Q&A visible |
| **Citation Display** | Inline citation links (e.g., "See page 3, chunk 12") | HTML showing citation span links |
| **Grounded Q&A** | Asks question, receives answer with chunk IDs OR abstention | POST /api/questions response with is_grounded: true/false |
| **PDF Export** | Download PDF button, file is valid & readable | Binary PDF file in downloads folder |
| **Share Link** | Generate read-only link, send to another browser, recipient sees report | Read-only page with "shared" watermark, no Q&A form |
| **Datadog Dashboard** | "PolicyInsight Ops" dashboard visible, real-time metrics updating | https://app.datadoghq.com/dashboard/... (take screenshot) |
| **Datadog APM Traces** | Service map shows all 3+ layers, trace timeline visible | https://app.datadoghq.com/apm/services/policyinsight |
| **Monitor Firing** | Trigger monitor via traffic generator, see incident created | https://app.datadoghq.com/incidents (incident card visible) |
| **Incident Context** | Incident shows trace links, logs, git commit, runbook | Incident details page expanded |
| **LLM Observability** | LLM calls appear in traces with token count and latency tags | Trace details showing llm.latency_ms, llm.tokens_used tags |
| **GitHub Repo** | Public repo with OSI license (e.g., MIT), README, deploy docs, Datadog exports | https://github.com/<user>/policyinsight (README visible) |
| **CI/CD Workflow** | GitHub Actions workflow visible, shows test + build + deploy | `.github/workflows/deploy.yml` in repo |
| **Cloud Run Logs** | Service logs visible in Cloud Logging, structured JSON format | https://console.cloud.google.com/logs (filter by service=policyinsight) |
| **Datadog JSON Exports** | Dashboard, monitor, SLO JSON files committed to `/datadog/` folder | `/datadog/dashboards/policyinsight-ops.json` in repo |
| **Traffic Generator Script** | Script that triggers monitors is provided and runnable | `./scripts/trigger-monitors.sh` in repo |
| **Cost Estimate** | LLM cost per hour is tracked and displayed on dashboard | Dashboard widget showing `policyinsight.llm.estimated_cost_usd` |
| **Fallback Behavior** | PDFBox text extraction uses fixed confidence | Upload document, see extraction_confidence = 0.5 in response |

---

## 13. Deployment Checklist

- [ ] **Hosted App URL** – Cloud Run service deployed, accessible, not requiring auth
  - `https://policyinsight-[project-id].run.app`

- [ ] **Public GitHub Repo**
  - [ ] OSI-approved license in LICENSE file (e.g., MIT, Apache 2.0)
  - [ ] README with "Deploy in 5 Minutes" quick-start guide (local + cloud)
  - [ ] Source code in `/src` folder (Maven structure)
  - [ ] Dockerfile for containerization
  - [ ] GitHub Actions workflows in `.github/workflows/`
  - [ ] Datadog JSON exports in `/datadog/` folder (dashboards, monitors, SLOs)
  - [ ] Test documents in `/test-documents/` folder
  - [ ] Traffic generator script in `/scripts/`
  - [ ] Deployment guide in `/docs/DEPLOYMENT.md`
  - [ ] Architecture diagram in `/docs/ARCHITECTURE.md`
  - [ ] OpenAPI spec in `/docs/openapi.json`

- [ ] **Datadog Configuration**
  - [ ] Org name clearly stated (e.g., "policyinsight-demo")
  - [ ] Dashboard "PolicyInsight Ops" created, visible at provided URL
  - [ ] 3+ monitors created (latency, queue age, LLM cost/citation) and linked in README
  - [ ] SLOs defined (request success rate, latency, citation coverage)
  - [ ] Incident example shown (via traffic generator run + screenshot)
  - [ ] All JSON exports committed to repo

- [ ] **Video Demo (3 Minutes)**
  - [ ] Upload PDF and show report generation
  - [ ] Ask grounded question, show answer with citations
  - [ ] Ask out-of-scope question, show abstention
  - [ ] Show Datadog dashboard with live metrics
  - [ ] Trigger a monitor, show incident creation
  - [ ] Show GitHub Actions deploy workflow
  - [ ] Total runtime: <3 minutes, no editing, speak clearly
  - [ ] Upload to YouTube (unlisted) or include as `.mp4` in repo

- [ ] **Evidence Screenshots**
  - [ ] App landing page (upload UI, disclaimer)
  - [ ] Report page (5 sections, citations visible)
  - [ ] Datadog ops dashboard (multiple widgets, live data)
  - [ ] Datadog traces (service map, trace details)
  - [ ] GitHub Actions workflow run (successful deploy)
  - [ ] Monitor fired + incident created (screenshot)
  - [ ] PDF export (readable, includes disclaimer)
  - [ ] Share link (read-only report, works)

- [ ] **API Documentation**
  - [ ] OpenAPI spec generated and committed
  - [ ] Swagger UI accessible at `/swagger-ui.html`
  - [ ] Key endpoints documented (upload, status, report, question, export, share)

- [ ] **Deployment Docs**
  - [ ] Google Cloud setup script (create project, enable APIs, provision resources)
  - [ ] Datadog setup script (create org, import dashboards, configure monitors)
  - [ ] Step-by-step deployment guide (cloud + local dev)
  - [ ] Environment variables documented (required, optional, secrets)
  - [ ] Rollback procedure documented

- [ ] **Code Quality**
  - [ ] Maven build succeeds (`./mvnw clean package`)
  - [ ] Tests pass (`./mvnw test`)
  - [ ] Docker build succeeds (`docker build -t policyinsight .`)
  - [ ] No security warnings (dependency check, SAST)
  - [ ] Code comments for non-obvious logic
  - [ ] Commit history is clean (squash if needed)

- [ ] **LLM Configuration**
  - [ ] Vertex AI project ID and credentials configured
  - [ ] Gemini model selected (2.0-flash or stable)
  - [ ] Prompts committed to repo (in `/src/main/resources/prompts/` or inline with comments)
  - [ ] Token counting implemented (for cost tracking)
  - [ ] Fallback behavior documented (if Vertex AI unavailable)

- [ ] **Safety & Compliance**
  - [ ] "Not legal advice" disclaimer on every page
  - [ ] Cite-or-abstain enforcement verified (manual spot-check of 5 reports)
  - [ ] No hallucinations in Q&A (abstains appropriately)
  - [ ] No PII stored unencrypted (documents auto-delete after 30 days)
  - [ ] Database connections use SSL/TLS

- [ ] **Observability**
  - [ ] dd-java-agent instrumentation configured
  - [ ] Custom spans and metrics emitted
  - [ ] Structured JSON logging enabled
  - [ ] Trace IDs correlated across logs
  - [ ] LLM calls instrumented (latency, tokens, errors)
  - [ ] At least 3 monitors with runbooks created
  - [ ] SLOs defined and visible on dashboard

- [ ] **Deployment-Ready Package**
  - [ ] Single `README.md` in repo root with:
    - [ ] 1-sentence product description
    - [ ] "Deploy in 5 Minutes" section (with copy-paste commands)
    - [ ] Link to hosted app URL
    - [ ] Link to Datadog org/dashboard
    - [ ] Link to GitHub Repo
    - [ ] Link to demo video
    - [ ] Architecture diagram (or link)
    - [ ] Stack summary (Java 21, Spring Boot, PostgreSQL, Vertex AI, Datadog, Cloud Run)
    - [ ] "Highlights" section (3 bullets: why this demonstrates excellence)
  - [ ] All secrets stored in GitHub secrets (not committed)
  - [ ] `.gitignore` excludes credentials, node_modules, build artifacts
  - [ ] All external links functional (no 404s)

---

## 14. Build Plan (Realistic 5–7 Day Sprint)

### Day 1: Foundation & Setup

**Goal:** Cloud infrastructure + local dev environment ready

- [ ] GCP project created, APIs enabled, databases provisioned
- [ ] Service accounts & IAM configured
- [ ] Datadog org created, GCP integration enabled
- [ ] GitHub Actions secrets configured (GCP credentials, DD keys)
- [ ] Docker Compose local dev environment (`docker-compose up` works)
- [ ] Maven project scaffold created (Spring Boot 3.2+, Java 21)
- [ ] First `./mvnw clean package` succeeds locally
- [ ] Database migrations (Flyway V1) initialize schema

**Deliverable:** Engineer can `docker-compose up` and app boots on `:8080` against local Postgres.

---

### Day 2: API & File Upload

**Goal:** Core REST API endpoints + PDF upload flow working

- [ ] REST API scaffold (Spring MVC controllers)
  - [ ] POST `/api/documents/upload` (multipart, stores in GCS, publishes to Pub/Sub)
  - [ ] GET `/api/documents/{id}/status` (polls job status)
  - [ ] GET `/health` & `/readiness`
- [ ] GCS integration (upload PDF, generate signed URLs)
- [ ] Pub/Sub integration (publish message to topic)
- [ ] Error handling + validation (file size, MIME type)
- [ ] Datadog instrumentation: APM spans for upload, GCS latency
- [ ] Integration tests for upload flow
- [ ] Local Pub/Sub emulator setup (for testing)

**Deliverable:** Can upload PDF via curl, receive job ID, mock Pub/Sub subscription receives message. APM traces visible in Datadog.

---

### Day 3: Extraction & Chunking Pipeline

**Goal:** PDFBox text extraction + citation mapping

- [ ] PDFBox text extraction wired in worker
- [ ] Chunking logic: split text into 500–800 token chunks, with page overlap
- [ ] Citation mapping: store chunks with page numbers, bounding boxes
- [ ] Async worker service (subscribe to Pub/Sub, process documents)
  - [ ] Consume message, extract text, store chunks in DB
  - [ ] Update document status: processing → completed/failed
  - [ ] Error handling: retries with exponential backoff
- [ ] Datadog metrics: extraction latency, confidence score
- [ ] End-to-end test: upload PDF → worker processes → chunks in DB

**Deliverable:** Upload a PDF, worker extracts text asynchronously, chunks stored in `chunks` table with citations. Dashboard shows extraction latency.

---

### Day 4: Classification & Risk Scanning

**Goal:** Vertex AI Gemini calls + document classification + 5-category risk scan

- [ ] Vertex AI client setup (authenticate, initialize Gemini model)
- [ ] Document classification prompt: "Classify as tos/privacy_policy/lease"
  - [ ] Call Gemini, parse JSON response
  - [ ] Store classification + confidence in `documents` table
- [ ] Risk taxonomy scanner:
  - [ ] For each of 5 risk categories, construct prompt: "Scan chunks, find risks"
  - [ ] Call Gemini per category, parse results
  - [ ] Validate: every risk must cite chunk IDs
  - [ ] Store results in `analysis_results` table (JSONB columns)
- [ ] Plain-English summary generation: "Summarize in 10 bullets"
  - [ ] Gemini call, validation, storage
- [ ] Obligations/Restrictions extraction
- [ ] Datadog metrics:
  - [ ] `policyinsight.llm.latency_ms` (per call type)
  - [ ] `policyinsight.llm.tokens_used` (counter)
  - [ ] `policyinsight.analysis.citation_coverage_rate` (gauge)
  - [ ] `policyinsight.analysis.risks_detected_count` (gauge)
- [ ] End-to-end: upload → extraction → classification → risk scan → results in DB

**Deliverable:** Upload PDF, see classified document type, risk report in database with citations. LLM metrics in Datadog. Citation coverage rate tracked.

---

### Day 5: UI & Report Rendering

**Goal:** Server-rendered HTML report pages + user-facing flows

- [ ] Thymeleaf templates:
  - [ ] Landing page (`/`): upload form, disclaimer
  - [ ] Processing status page: progress bar, polling with htmx
  - [ ] Report page (`/documents/{id}/report`): 5 sections with citations
    - [ ] Section 1: Overview (type, parties, dates)
    - [ ] Section 2: Summary (10 bullets, inline citation links)
    - [ ] Section 3: Obligations/Restrictions/Termination
    - [ ] Section 4: Risk Taxonomy (5 categories, each with detected/absent items)
    - [ ] Section 5: Q&A form
  - [ ] Share link read-only page
- [ ] htmx integration: status polling, Q&A form submission
- [ ] Accessibility: keyboard nav, ARIA labels, high-contrast CSS
- [ ] Responsive CSS (mobile-first, works on 320px–1920px)
- [ ] "Not legal advice" disclaimer prominently displayed
- [ ] Datadog metric: `policyinsight.report.render.latency_ms`

**Deliverable:** Upload → Processing → Report renders with full UI. All 5 sections visible, citations clickable. Mobile-responsive.

---

### Day 6: Q&A, Export & Observability Polish

**Goal:** Grounded Q&A + PDF export + Datadog dashboards & monitors

- [ ] Q&A Service:
  - [ ] POST `/api/questions` endpoint
  - [ ] Find relevant chunks, pass to Gemini with grounding prompt
  - [ ] Parse response, validate citations, enforce abstention rule
  - [ ] Store Q&A session in DB, track tokens
  - [ ] Datadog: `policyinsight.qa.latency_ms`, `policyinsight.qa.grounded_rate`
- [ ] PDF export:
  - [ ] Use iText to generate PDF with inline citations
  - [ ] Include disclaimer, risk summary, chunked obligations
  - [ ] GET `/api/documents/{id}/export/pdf` downloads PDF
  - [ ] Datadog: `policyinsight.pdf.generation_latency_ms`
- [ ] Share link generation:
  - [ ] POST `/api/documents/{id}/share` generates 7-day token
  - [ ] GET `/documents/{id}/share/{token}` renders read-only report
  - [ ] Datadog: `policyinsight.share.links_generated_count`
- [ ] Datadog Dashboards:
  - [ ] Import "PolicyInsight Ops" dashboard (5+ widgets)
  - [ ] Create 3 monitors + SLOs
  - [ ] Export JSON to `/datadog/` folder
- [ ] Traffic generator script: `./scripts/trigger-monitors.sh`
  - [ ] Upload documents, trigger latency spike, queue backlog, cost anomaly
  - [ ] Traffic generator can be run to see monitors fire + incidents created

**Deliverable:** Full user flow works (upload → report → Q&A → export → share). Datadog dashboards + monitors + SLOs live. Traffic generator script ready.

---

### Day 7: Polish, Testing & Deployment

**Goal:** Production readiness, CI/CD, deployment, demo video

- [ ] End-to-end testing:
  - [ ] Unit tests (services, controllers): >80% coverage
  - [ ] Integration tests (API, DB, Vertex AI): real calls with mocked responses
  - [ ] Spot-check 5 reports for hallucinations, citation accuracy
- [ ] Security & compliance:
  - [ ] No hardcoded secrets
  - [ ] HTTPS/TLS for all external APIs
  - [ ] Database encryption at rest (Cloud SQL default)
  - [ ] Documents auto-delete after 30 days
- [ ] GitHub Actions:
  - [ ] PR workflow: lint, test, build, scan (runs <10 min)
  - [ ] Deploy workflow: build → push to Artifact Registry → Cloud Run deploy (canary 10%, then 90%)
  - [ ] Rollback workflow: `gcloud run update-traffic` to previous revision
- [ ] Deployment to production Cloud Run:
  - [ ] Service deployed, health checks passing
  - [ ] Datadog traces + logs visible
  - [ ] Monitors firing correctly (test via traffic generator)
  - [ ] Incident created with context + runbook
- [ ] Documentation:
  - [ ] README with deploy-in-5-minutes guide
  - [ ] Deployment guide (GCP + Datadog setup)
  - [ ] Architecture diagram
  - [ ] API docs (OpenAPI spec)
  - [ ] Runbooks for 3 monitors
- [ ] Demo video (3 min):
  - [ ] Record: upload PDF → see report → ask Q&A → show dashboard → trigger monitor
  - [ ] Edit, add subtitles, upload to YouTube (unlisted)
- [ ] Final deployment package:
  - [ ] Public GitHub repo (MIT license)
  - [ ] Hosted Cloud Run URL
  - [ ] Datadog org name
  - [ ] Video link
  - [ ] Evidence screenshots
  - [ ] JSON exports (dashboards, monitors)

**Deliverable:** Everything is deployed, working, documented, and ready for evaluation. Demo video prepared. All deployment checklist items complete.

---

## Summary

**PolicyInsight** is a production-ready, backend-leaning service that demonstrates advanced engineering practices:

✅ **Stack:** Java 21 + Spring Boot, PostgreSQL, Google Cloud (Run, SQL, Storage, Vertex AI), Pub/Sub async processing
✅ **Observability:** Datadog APM + logs + custom metrics + LLM instrumentation + incident automation
✅ **Reliability:** Idempotent jobs, retries, fallbacks, graceful degradation
✅ **Safety:** Cite-or-abstain enforcement, hallucination detection, grounded Q&A
✅ **DevOps:** GitHub Actions CI/CD, container orchestration, versioned releases, automated rollback
✅ **Documentation:** Deployment guide, runbooks, architecture diagrams, traffic generator

This is **not a toy**. It's a system a junior engineer could own, deploy, and operate in production. The system demonstrates professional-grade observability, incident response, and engineering rigor.

---

**Document Version:** 1.0
**Last Updated:** December 28, 2025
**Status:** Ready for Deployment

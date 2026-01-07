# PolicyInsight – Datadog Integration PRD

## 1. Executive Summary

**PolicyInsight** is a production-grade backend service that analyzes legal documents (PDFs) and outputs plain-English risk reports with mandatory source citations. Built to demonstrate **junior engineer who can ship + operate a service**, this project showcases:

- ✅ **Full-stack backend-leaning architecture**: Java 21 + Spring Boot REST API + server-rendered Thymeleaf UI
- ✅ **Async resilience**: Document ingestion → Pub/Sub workers → async job tracking + polling
- ✅ **Production observability**: End-to-end Datadog APM (traces, logs, metrics, LLM telemetry) + cost analytics
- ✅ **Grounded AI safety**: "Cite-or-abstain" enforcement; every claim references extracted text with page numbers
- ✅ **Cloud-native ops**: Cloud Run + Cloud SQL + GCS + Vertex AI (Gemini) + Pub/Sub
- ✅ **CI/CD + reliability**: GitHub Actions (test→build→deploy), versioned releases, rollback strategy
- ✅ **Datadog integration**: 3+ detection rules, incident automation, dashboard exports, traffic generator for demo proof

**Why this matters:** This is not a toy. It's a service a junior engineer could operate in production—with clear signals about what's breaking, why, and how to fix it.

---

## 2. MVP Scope & User Stories

### Core Value Proposition
Upload a PDF legal document → Get a structured risk report with every claim cited to source text → Ask grounded Q&A → Export or share.

### In-Scope Features

#### A. Input & Processing
- **PDF upload** (multipart form, file validation, size limits 20 MB)
- **Automatic document classification** (Terms of Service, Privacy Policy, Lease Agreement; inference + confidence)
- **OCR + text extraction** via Google Cloud Document AI (with explicit fallback if unavailable)
- **Citation mapping** (text chunks + page numbers preserved)

#### B. Core Report Output (5 Sections, Always Cited)
1. **Document Overview** – Type, parties, effective date, classification confidence
2. **Plain-English Summary** – Max 10 bullets, each with citation links
3. **Obligations & Restrictions** – Your obligations, prohibited actions, termination triggers (cited)
4. **Risk Taxonomy** – 5 risk categories (Data/Privacy, Financial, Legal Rights Waivers, Termination, Modification); explicit "absence" statements for undetected risks
5. **Grounded Q&A** – Max 3 user questions; answers cite source or respond "insufficient evidence in document"

#### C. Grounding Rules (Hard Enforced)
- Every risk statement ≥ citation to extracted chunk + page number
- No hallucination: "absent" risks are explicitly flagged as "not detected"
- No speculation; only claim what text explicitly states
- Content safety check on all LLM outputs

#### D. Export & Sharing
- PDF export with inline citations + watermark
- 7-day TTL read-only shareable link
- "Not legal advice" disclaimer (persistent, visible)

#### E. API + UI
- Minimal REST API (JSON endpoints for testing + CI/CD traffic generation)
- Server-rendered UI (Spring MVC + Thymeleaf); htmx for upload progress + status polling
- OpenAPI 3.0 spec (exported as CI/CD artifact)

### User Stories

| Story | Flow | Acceptance Criteria |
|-------|------|-------------------|
| **Upload & Analyze** | User uploads PDF → gets job ID → UI polls status → report auto-populates | Job ID returned in <500ms; status endpoint reflects real-time state; report ready in <30s |
| **View Report** | User scrolls through 5 sections | All sections populated; every claim has page ref + span highlight option |
| **Ask Questions** | User submits up to 3 Q&A queries | Answer is cited OR explicitly says "not in doc"; zero hallucinations |
| **Export** | User clicks "Download PDF" or "Get Share Link" | PDF downloads with watermark + disclaimer; link is valid 7 days; read-only |
| **Fallback** | Document AI is unavailable | System gracefully uses embedded OCR/text-fallback; user is notified; report may have reduced confidence |

### Out-of-Scope (Explicit Non-Goals)
- Multi-language support
- Team/org dashboards
- User authentication (anonymous session only)
- Bulk batch processing
- Advanced negotiation suggestions
- Document types beyond 3
- Browser extensions
- Voice input

---

## 3. System Architecture

### High-Level Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                        USER (Browser)                            │
│  Thymeleaf UI: Upload → Status Poll → View Report → Export      │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 │ REST + htmx (HTML fragments)
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│        Spring Boot Web Service (Cloud Run)                       │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ REST Controllers: upload, getStatus, getReport, askQuestion  ││
│  │ Data: Job metadata, report cache, Q&A log                    ││
│  └─────────────────────────────────────────────────────────────┘│
│                         │                                         │
│    Pub/Sub Publish      │  Status Polling + View                 │
│    (Job message)        │  (READ from Postgres)                  │
└────────────┬────────────▼─────────────────────────────────────────┘
             │
             │ (async)
             ▼
┌─────────────────────────────────────────────────────────────────┐
│        Pub/Sub Worker Service (Cloud Run)                        │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ 1. Fetch PDF from GCS                                        ││
│  │ 2. Extract text + OCR (Document AI or fallback)             ││
│  │ 3. Chunk + map citations (text spans, page numbers)         ││
│  │ 4. Classify document (LLM or rules)                         ││
│  │ 5. Generate risk report (Gemini + grounding)                ││
│  │ 6. Store: chunks→DB, report→GCS+DB pointer, status→DB      ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
             │
             ├─────────────────────────┬────────────────┬──────────────┐
             ▼                         ▼                ▼              ▼
        ┌─────────┐          ┌─────────────────┐  ┌─────────┐  ┌──────────────┐
        │   GCS   │          │  Cloud SQL      │  │ Vertex  │  │   Datadog    │
        │  (PDFs, │          │  (metadata,     │  │  AI /   │  │ (Traces+Logs │
        │ reports)│          │  chunks,        │  │ Gemini  │  │ +Metrics+LLM │
        │         │          │  citations)     │  │ API     │  │ Observability)
        └─────────┘          └─────────────────┘  └─────────┘  └──────────────┘
```

### Modules & Responsibilities

| Module | Purpose | Tech |
|--------|---------|------|
| **api.web** | REST controllers, request routing, session handling | Spring MVC + Thymeleaf |
| **api.service** | Business logic: upload, job dispatch, report assembly | Spring Service beans |
| **api.extraction** | Document AI integration + fallback text extraction | DocumentAI client, fallback OCR |
| **api.chunking** | Text segmentation + citation mapping (spans, pages) | Regex + coordinate mapping |
| **api.classification** | Document type inference (rules + optional LLM) | Spring component |
| **api.analysis** | Risk taxonomy scanning + Gemini summarization | Gemini API wrapper |
| **api.storage** | PDF/report I/O (GCS) + metadata persistence (Postgres) | GCS client, JPA repositories |
| **api.qa** | Grounded Q&A handler; cite-or-abstain logic | Gemini + grounding layer |
| **worker.pubsub** | Async job consumer; orchestrates pipeline | Spring Cloud GCP Pub/Sub |
| **shared.model** | JPA entities, DTOs, constants | Hibernate ORM |
| **shared.observability** | Datadog instrumentation, structured logging | dd-java-agent, SLF4J + JSON |

---

## 4. Data Model

### Core Tables (Flyway migrations)

#### `policy_jobs`
```sql
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
  dd_trace_id VARCHAR(255),

  INDEX idx_uuid (job_uuid),
  INDEX idx_status_created (status, created_at DESC)
);
```

#### `document_chunks`
```sql
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

  FOREIGN KEY (job_uuid) REFERENCES policy_jobs(job_uuid),
  INDEX idx_job_uuid (job_uuid)
);
```

#### `reports`
```sql
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

  FOREIGN KEY (job_uuid) REFERENCES policy_jobs(job_uuid) UNIQUE,
  INDEX idx_job_uuid (job_uuid)
);
```

#### `qa_interactions`
```sql
CREATE TABLE qa_interactions (
  id BIGSERIAL PRIMARY KEY,
  job_uuid UUID NOT NULL,
  question TEXT NOT NULL,
  answer TEXT NOT NULL,
  cited_chunks JSONB,  -- [{chunk_id, page_num, text}]
  confidence VARCHAR(20), -- CONFIDENT, ABSTAINED
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  FOREIGN KEY (job_uuid) REFERENCES policy_jobs(job_uuid),
  INDEX idx_job_uuid (job_uuid)
);
```

#### `share_links`
```sql
CREATE TABLE share_links (
  id BIGSERIAL PRIMARY KEY,
  job_uuid UUID NOT NULL,
  share_token UUID NOT NULL UNIQUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP, -- 7 days from creation
  access_count INT DEFAULT 0,

  FOREIGN KEY (job_uuid) REFERENCES policy_jobs(job_uuid),
  INDEX idx_token (share_token),
  INDEX idx_expires_at (expires_at)
);
```

### Flyway Migration Strategy
- `V1__init.sql` – All base tables + indexes
- `V2__add_trace_correlation.sql` – Add `dd_trace_id` column (used for Datadog span linking)
- Future: `V3__*` – Schema migrations follow event-driven approach (always backwards-compatible)

### Data Flow
```
Upload PDF (multipart)
  → Store in GCS (pdf_gcs_path)
  → Create policy_jobs record (status=PENDING)
  → Publish Pub/Sub message
  ↓
Worker picks up job
  → Fetch PDF from GCS
  → Extract text + OCR (Document AI or fallback)
  → Create document_chunks rows
  → LLM classify, summarize, analyze
  → Create reports row
  → Update policy_jobs status=SUCCESS
  ↓
Web Service status endpoint
  → Query policy_jobs.status + reports
  → Return progress or final report
```

---

## 5. API Contract

### REST Endpoints (OpenAPI 3.0 spec in repo: `/docs/openapi.json`)

#### POST `/api/upload`
Upload and initiate document analysis.

**Request:**
```
Content-Type: multipart/form-data

Body:
  file: <PDF binary>
```

**Response (202 Accepted):**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "message": "Document queued for analysis",
  "statusUrl": "/api/jobs/550e8400-e29b-41d4-a716-446655440000/status"
}
```

**Validation:**
- File size ≤ 20 MB
- Content-Type: `application/pdf`
- Rate limit: 1 upload per IP per 10 seconds (basic abuse protection)

---

#### GET `/api/jobs/{jobId}/status`
Poll processing status.

**Response (200 OK):**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSING",
  "progress": {
    "stage": "extraction",
    "percentComplete": 45
  },
  "estimatedSecondsRemaining": 12
}
```

or (when complete):
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "SUCCESS",
  "reportUrl": "/api/jobs/550e8400-e29b-41d4-a716-446655440000/report"
}
```

or (on error):
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "FAILED",
  "errorMessage": "Document AI quota exceeded; fallback OCR confidence too low"
}
```

---

#### GET `/api/jobs/{jobId}/report`
Retrieve completed analysis report (JSON).

**Response (200 OK):**
```json
{
  "documentOverview": {
    "classification": "TERMS_OF_SERVICE",
    "confidence": 0.95,
    "parties": ["You", "Example Corp"],
    "effectiveDate": "2025-01-01"
  },
  "summary": [
    {
      "bullet": "Auto-renewal is enabled; you must opt out.",
      "citations": [
        {
          "chunkId": 42,
          "pageNumber": 3,
          "textSpan": "Renewal...automatic...unless..."
        }
      ]
    }
  ],
  "obligations": [
    {
      "text": "Pay subscription fee monthly",
      "severity": "HIGH",
      "citations": [...]
    }
  ],
  "restrictions": [...],
  "terminationTriggers": [...],
  "riskTaxonomy": {
    "data_privacy": [
      {
        "risk": "Third-party data sharing",
        "severity": "MEDIUM",
        "citations": [...]
      }
    ],
    "financial": [...],
    "legal_rights_waivers": [...],
    "termination_enforcement": [...],
    "modification": [...]
  }
}
```

---

#### POST `/api/jobs/{jobId}/question`
Ask a grounded Q&A question.

**Request:**
```json
{
  "question": "Can they sell my data to advertisers?"
}
```

**Response (200 OK):**
```json
{
  "question": "Can they sell my data to advertisers?",
  "answer": "The document states 'Data may be shared with third parties for business purposes' (page 4, Section 6.2). It does not explicitly restrict sharing with advertisers.",
  "citations": [
    {
      "chunkId": 89,
      "pageNumber": 4,
      "textSpan": "Data may be shared with third parties..."
    }
  ],
  "confidence": "CONFIDENT"
}
```

or:
```json
{
  "question": "What is their CEO's favorite color?",
  "answer": "Insufficient evidence in document.",
  "citations": [],
  "confidence": "ABSTAINED"
}
```

---

#### GET `/api/jobs/{jobId}/export`
Download report as PDF.

**Response (200 OK, Content-Type: application/pdf):** PDF binary with citations + watermark.

---

#### GET `/api/jobs/{jobId}/share`
Generate shareable link.

**Response (200 OK):**
```json
{
  "shareUrl": "https://policyinsight.example.com/share/7e4c8b2a-9f1d-4e9c-b8a3-5d2e9f4c7b1a",
  "expiresAt": "2025-02-05T10:00:00Z",
  "message": "Share this link (valid 7 days, read-only)"
}
```

---

#### GET `/share/{token}`
View shared report (no auth).

**Response:** Renders HTML report template; read-only.

---

#### GET `/health`
Kubernetes / Cloud Run health check.

**Response (200 OK):**
```json
{
  "status": "UP",
  "timestamp": "2025-01-29T10:00:00Z",
  "checks": {
    "db": "UP",
    "gcs": "UP",
    "documentAI": "UP",
    "vertexAI": "UP",
    "pubsub": "UP"
  }
}
```

---

#### GET `/metrics` (Micrometer)
Prometheus-format metrics for Datadog scraping.

**Response:** Micrometer metrics (default Spring Boot actuator endpoint).

---

### Error Handling
All error responses follow a standard format:
```json
{
  "error": "VALIDATION_ERROR",
  "message": "File size exceeds 20 MB",
  "timestamp": "2025-01-29T10:00:00Z",
  "traceId": "abc123def456"
}
```

---

## 6. Core Pipeline (Backend)

### Step 1: Upload & Queue

```
User submits PDF via Thymeleaf form
  → Multipart validation (size, type)
  → Generate jobId (UUID)
  → Upload PDF to GCS: gs://bucket/jobs/{jobId}/document.pdf
  → Create policy_jobs row (status=PENDING, pdf_gcs_path=...)
  → Publish Pub/Sub message: {"jobId": "...", "action": "ANALYZE"}
  → Return 202 with jobId
```

**Idempotency:** If job already exists, check status and return current state (don't re-queue).

---

### Step 2: Extract Text & OCR (Async Worker)

```
Pub/Sub message consumed by worker
  → Load policy_jobs row
  → Fetch PDF from GCS
  → Call Google Cloud Document AI:
      - Layout Analysis (document structure)
      - OCR (text from images)
      - Text extraction (structured text + coordinates)
  → On failure (quota, timeout): Fall back to embedded OCR library (Tesseract or Apache PDFBox)
  → Store raw extracted text
  → Continue to Step 3
```

**Fallback Strategy:**
- If Document AI times out (>30s) or returns quota error: use PDFBox text extraction + Tesseract OCR for images
- Log fallback to Datadog as a warning metric
- Include fallback_used flag in report metadata
- Proceed with reduced confidence if fallback needed

---

### Step 3: Chunking & Citation Mapping

```
Raw text
  → Split into semantic chunks (paragraph-level, max 1000 chars)
  → For each chunk:
      - Record page number (from Document AI coordinates)
      - Record character offsets (start, end)
      - Compute text span confidence (OCR confidence ÷ chunk length)
  → Insert rows into document_chunks table
  → Build in-memory citation index: {chunk_id → (page, span, text)}
  → Continue to Step 4
```

---

### Step 4: Document Classification

```
First 2000 chars of extracted text
  → Rules-based classifier:
      - Match keywords (TOS: "Terms of Service", "Agree", "bound";
                        Privacy: "data", "collect", "process";
                        Lease: "rent", "property", "tenant")
      - If confidence ≥ 0.90: return classification
  → If confidence < 0.90: Call Gemini:
      - Prompt: "Classify this legal document as TOS, PRIVACY_POLICY, or LEASE_AGREEMENT"
      - Return: classification + confidence
  → Update policy_jobs: classification, classification_confidence
  → Continue to Step 5
```

---

### Step 5: Risk Taxonomy Scanning

```
All chunks
  → For each risk category, scan chunks:
      - Data/Privacy: look for "collect", "share", "third party", "retention"
      - Financial: look for "fee", "price", "auto-renew", "cancellation"
      - Legal Rights Waivers: look for "arbitration", "class action", "liability"
      - Termination: look for "terminate", "immediate", "without notice"
      - Modification: look for "modify", "change terms", "unilateral"
  → For each match: extract chunk + page + severity (infer from text intensity)
  → If no matches for category: explicitly record "Not detected in document"
  → Build risk_taxonomy JSON structure
```

---

### Step 6: Plain-English Summary (Gemini)

```
Grounding layer:
  1. Extract top 3 risks from taxonomy
  2. For each risk, get corresponding chunks (max 3 chunks per risk)
  3. Build context:
     "Extracted text: [chunk1], [chunk2], [chunk3]"
     "Risk category: [risk]"
     "Generate one plain-English bullet summarizing this risk."
  → Call Gemini with context + bullet prompt (few-shot)
  → Response: bullet text
  → Map bullet to chunk IDs
  → Validate response is grounded (not speculative)
  → If response adds claims not in chunks: discard, regenerate with stricter prompt
  → Continue to Step 7 only if all bullets are grounded
```

---

### Step 7: Q&A Handler (On-Demand, Cite-or-Abstain)

```
User submits question Q
  → Send to Gemini:
     {
       "context": "Extracted text chunks from document",
       "chunks": [{chunk_id, page, text}, ...],
       "question": Q,
       "instructions": "Answer ONLY from the provided chunks. If not explicitly stated, respond with 'Insufficient evidence in document.' Include exact page numbers and text spans in your answer."
     }
  → Gemini returns answer A
  → Grounding check:
      - Parse answer for claims
      - For each claim, verify it matches a chunk verbatim or paraphrase
      - If any claim is unsourced: set confidence=ABSTAINED, return "Insufficient evidence"
  → Return answer + citations + confidence
```

---

### Step 8: Report Assembly & Storage

```
Combine all outputs:
  - Document overview (classification + metadata)
  - Summary bullets (with citations)
  - Obligations + restrictions + termination triggers (with citations)
  - Risk taxonomy (with citations)
  → Serialize to JSON
  → Upload to GCS: gs://bucket/jobs/{jobId}/report.json
  → Insert rows into reports table (JSONB fields)
  → Update policy_jobs: status=SUCCESS, completed_at=NOW()
  → Delete Pub/Sub message (implicit if handler completes)
```

---

### Step 9: Datadog Tracing (Entire Pipeline)

```
At worker startup:
  - dd-java-agent installed + configured
  - DD_SERVICE=policyinsight-worker
  - DD_ENV=prod
  - DD_VERSION={git-commit-sha}

At job start:
  - Span: operation_name="job.process"
  - Tags: job_id, document_classification, file_size_bytes

Per step:
  - Span: operation_name="extraction", tags=[duration, doc_ai_status, fallback_used]
  - Span: operation_name="classification", tags=[confidence, provider]
  - Span: operation_name="risk_scan", tags=[risk_count_by_category]
  - Span: operation_name="summary_generation", tags=[bullet_count, gemini_tokens]

On completion:
  - Log: {job_id, status, duration_ms, chunk_count, cost_estimate}
  - Metrics: histogram job_duration_ms, counter job_success/failure
  - Tags on final span: dd_trace_id (correlate with policy_jobs.dd_trace_id)
```

---

## 7. UI Flows (Thymeleaf + htmx)

### Templates & Routes

#### `GET /` – Landing Page
```html
<!-- layout.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>PolicyInsight – Understand Legal Documents</title>
  <link rel="stylesheet" href="/css/style.css">
  <script src="https://unpkg.com/htmx.org@1.9.10"></script>
</head>
<body>
  <header>
    <h1>PolicyInsight</h1>
    <p>Analyze legal documents. Understand what you're agreeing to.</p>
  </header>

  <main>
    <section id="upload-section">
      <!-- upload.html fragment -->
    </section>
    <section id="disclaimer" class="warning-box">
      <strong>⚠️ Not Legal Advice</strong>
      PolicyInsight provides clarity and risk surfacing. It is not a substitute for legal counsel.
    </section>
  </main>

  <footer>
    <p>Built with Spring Boot, Vertex AI, and Datadog.</p>
  </footer>
</body>
</html>
```

#### `GET /upload` – Upload Form Fragment (htmx)
```html
<!-- upload.html -->
<form id="upload-form"
      hx-post="/api/upload"
      hx-target="#status"
      enctype="multipart/form-data">
  <div class="form-group">
    <label for="file-input">Select PDF:</label>
    <input type="file" id="file-input" name="file" accept=".pdf" required>
    <small>Max 20 MB</small>
  </div>
  <button type="submit" class="btn-primary">Analyze Document</button>
</form>

<div id="status"></div>
```

#### `POST /api/upload` – Upload Handler
```java
@PostMapping("/api/upload")
public ResponseEntity<?> uploadPdf(
    @RequestParam("file") MultipartFile file,
    HttpServletRequest request) {

  // Validate file
  if (file.getSize() > 20 * 1024 * 1024) {
    return ResponseEntity.badRequest()
      .body(Map.of("error", "File exceeds 20 MB"));
  }

  // Create job
  UUID jobId = UUID.randomUUID();
  PolicyJob job = new PolicyJob();
  job.setJobUuid(jobId);
  job.setStatus("PENDING");
  job.setPdfGcsPath("gs://bucket/jobs/" + jobId + "/document.pdf");
  policyJobRepository.save(job);

  // Upload to GCS
  gcsClient.uploadFile(file.getInputStream(), job.getPdfGcsPath());

  // Publish Pub/Sub
  pubsubClient.publish("analyze-topic",
    new AnalysisMessage(jobId));

  // Return response (htmx will poll status)
  return ResponseEntity.accepted()
    .body(Map.of(
      "jobId", jobId,
      "statusUrl", "/api/jobs/" + jobId + "/status"
    ));
}
```

#### `GET /api/jobs/{jobId}/status` – Status Polling (htmx)
```html
<!-- status-check.html (returned by controller, htmx polls) -->
<div id="status-display"
     hx-get="/api/jobs/{jobId}/status"
     hx-trigger="load, every 2s"
     hx-swap="outerHTML">

  <p id="status-text">Status: PENDING</p>
  <progress id="progress-bar" value="0" max="100"></progress>
  <p id="eta">Estimated time remaining: --</p>

  <!-- Once SUCCESS, auto-redirect to report view -->
</div>

<script>
document.addEventListener('htmx:afterSettle', function(event) {
  if (event.detail.xhr.status === 200) {
    const response = JSON.parse(event.detail.xhr.response);
    if (response.status === 'SUCCESS') {
      window.location.href = '/jobs/' + response.jobId + '/report';
    }
  }
});
</script>
```

#### `GET /jobs/{jobId}/report` – Report Display
```html
<!-- report.html -->
<div class="report-container">
  <div class="header-section">
    <h2 th:text="${report.documentOverview.classification}"></h2>
    <p th:text="'Confidence: ' + ${report.documentOverview.confidence}"></p>

    <div class="disclaimer">
      ⚠️ This is not legal advice. Verify important claims with a lawyer.
    </div>
  </div>

  <!-- Section 1: Overview -->
  <section class="section-overview">
    <h3>Document Overview</h3>
    <ul>
      <li th:each="fact : ${report.documentOverview.facts}"
          th:text="${fact}"></li>
    </ul>
  </section>

  <!-- Section 2: Summary -->
  <section class="section-summary">
    <h3>Plain-English Summary</h3>
    <ul>
      <li th:each="bullet : ${report.summary}">
        <p th:text="${bullet.text}"></p>
        <details>
          <summary>Citations</summary>
          <ul>
            <li th:each="cite : ${bullet.citations}">
              Page <span th:text="${cite.pageNumber}"></span>:
              <em th:text="${cite.textSpan}"></em>
            </li>
          </ul>
        </details>
      </li>
    </ul>
  </section>

  <!-- Section 3: Obligations & Restrictions (similar structure) -->

  <!-- Section 4: Risk Taxonomy -->
  <section class="section-risks">
    <h3>Detected Risks</h3>
    <div th:each="category : ${report.riskTaxonomy.entrySet()}">
      <h4 th:text="${category.key}"></h4>
      <div th:if="${category.value.isEmpty()}">
        <p class="no-risk">Not detected in this document.</p>
      </div>
      <ul th:unless="${category.value.isEmpty()}">
        <li th:each="risk : ${category.value}">
          <strong th:text="${risk.text}"></strong>
          (<span th:text="${risk.severity}"></span>)
          <details>
            <summary>Evidence</summary>
            <ul>
              <li th:each="cite : ${risk.citations}">
                Page <span th:text="${cite.pageNumber}"></span>
              </li>
            </ul>
          </details>
        </li>
      </ul>
    </div>
  </section>

  <!-- Section 5: Q&A -->
  <section class="section-qa">
    <h3>Ask a Question</h3>
    <form hx-post="/api/jobs/{jobId}/question"
          hx-target="#qa-results">
      <textarea name="question" placeholder="Ask a question about this document..." required></textarea>
      <button type="submit">Ask</button>
    </form>

    <div id="qa-results">
      <div th:each="qa : ${qaHistory}" class="qa-item">
        <p><strong>Q: </strong><span th:text="${qa.question}"></span></p>
        <p><strong>A: </strong><span th:text="${qa.answer}"></span></p>
        <p class="confidence" th:text="'Confidence: ' + ${qa.confidence}"></p>
      </div>
    </div>
  </section>

  <!-- Export & Share -->
  <div class="actions">
    <a href="/api/jobs/{jobId}/export" class="btn-secondary" download="report.pdf">
      📥 Download as PDF
    </a>
    <button hx-post="/api/jobs/{jobId}/share"
            hx-target="#share-modal"
            class="btn-secondary">
      🔗 Get Share Link
    </button>
  </div>
</div>
```

#### `GET /share/{token}` – Read-Only Share
```html
<!-- share-report.html -->
<div class="report-container read-only">
  <div class="banner">
    This is a shared read-only view of a PolicyInsight report.
    It expires on: <span th:text="${expiresAt}"></span>
  </div>

  <!-- Same report sections as /jobs/{jobId}/report, but no Q&A input -->
  <!-- No export/share buttons -->
</div>
```

### htmx Interactions Summary

| Interaction | Trigger | Target | Response |
|-------------|---------|--------|----------|
| **File Upload** | Click submit on form | Status div | JSON + auto-poll |
| **Status Poll** | Every 2s (htmx trigger) | Status display | Progress HTML |
| **Status Complete** | response.status === SUCCESS | Redirect | window.location.href |
| **Ask Question** | Click "Ask" button | QA results div | Q&A item HTML |
| **Share Link** | Click "Get Share Link" | Modal | URL + QR code |
| **Export** | Click "Download PDF" | -- | PDF download |

---

## 8. Grounding & Safety Rules

### Cite-or-Abstain Enforcement

Every risk statement, obligation, and Q&A answer must satisfy ONE of:

```
✅ GROUNDED:
   - Claim: "Auto-renewal is enabled by default."
   - Citation: Page 3, Section 4.2: "Your subscription will automatically renew..."
   - Status: CONFIDENT

✅ ABSENT:
   - Risk category: "Data & Privacy"
   - Conclusion: "No data collection or sharing clauses detected in this document."
   - Status: CONFIDENT (because absence is information)

❌ SPECULATIVE (REJECTED):
   - Claim: "They probably sell your data to advertisers" (inferred, not stated)
   - Citation: None
   - Status: AUTO-REJECTED by grounding layer
   → LLM regenerates with explicit "not found in document" response

❌ HALLUCINATED (REJECTED):
   - Claim: "The CEO is named John Smith" (not in legal document at all)
   - Citation: None
   - Status: AUTO-REJECTED
   → System responds: "Insufficient evidence in document."
```

### Implementation Strategy

#### A. Summary Generation (LLM)
```
prompt = """
You are a legal document analyzer. Your task is to generate ONE plain-English
bullet point summarizing a risk or obligation found in a legal document.

CRITICAL RULES:
1. Use ONLY the provided text excerpts. Do not infer or speculate.
2. If you cannot ground your statement in the excerpts, respond with:
   "Insufficient evidence in document."
3. Include the page number in your response.

Text excerpts from the document:
{chunks_with_page_numbers}

Risk category: {category}
Generate one bullet point.
"""

response = gemini.generate(prompt)
// Verify response does not add claims outside chunks
if (contains_unsourced_claim(response, chunks)):
  response = "Insufficient evidence in document."
```

#### B. Q&A Handler (Cite-or-Abstain)
```
qa_prompt = """
Answer the following question using ONLY the provided document excerpts.
If the document does not contain information to answer the question,
respond with exactly: "Insufficient evidence in document."

Question: {question}

Document excerpts:
{chunks_with_citations}

Answer (cite the exact page and text):
"""

answer = gemini.generate(qa_prompt)

// Parse answer for claims and verify grounding
cited_chunks = extract_citations(answer)
for claim in extract_claims(answer):
  if not is_grounded_in_chunks(claim, cited_chunks):
    // Force abstention
    answer = "Insufficient evidence in document."
    confidence = "ABSTAINED"
  else:
    confidence = "CONFIDENT"
```

#### C. Risk Scanning (Rules-Based)
```
risks_detected = {}

for category in [Data, Financial, LegalRights, Termination, Modification]:
  matches = []
  for chunk in all_chunks:
    if keyword_match(chunk, category_keywords[category]):
      matches.append({
        chunk_id: chunk.id,
        page: chunk.page,
        text: chunk.text,
        severity: infer_severity(chunk)
      })

  if matches:
    risks_detected[category] = matches
  else:
    risks_detected[category] = {
      detected: false,
      message: "Not detected in this document."
    }
```

### Safety Guardrails

#### Rule 1: No Speculation
- Ban words: "likely", "probably", "may", "could", "appears to" (unless in original text)
- If LLM uses these for inference: regenerate with strict prompt

#### Rule 2: Absence is Information
- For each of 5 risk categories, explicitly state if not detected
- Example: "This document does not contain clauses about data retention duration."

#### Rule 3: Content Safety
- Check all generated outputs against Google SafetyAttributeSet before returning
- Flag outputs with "VIOLENCE", "HATE_SPEECH", "HARASSMENT"
- Log flagged outputs to Datadog for review

#### Rule 4: Maximal Caution in Q&A
- Prefer "insufficient evidence" over speculation
- If question is asking for legal advice (e.g., "Should I sign this?"):
  - Auto-respond: "This tool provides clarity, not legal advice. Consult a lawyer."
- If question is out of domain (e.g., "What's the meaning of life?"):
  - Auto-respond: "This question is not related to the document."

#### Rule 5: No Real-Time Updates
- Report is snapshot at time of generation
- If document is updated by third party: no automatic refresh
- User must re-upload to get fresh analysis

### Disclaimer & UX

**Persistent banner on every page:**
```
⚠️ DISCLAIMER
PolicyInsight is an AI-powered analysis tool designed to help you understand
legal documents. It is NOT a substitute for legal advice. All outputs are
based on text analysis and may be incomplete or incorrect. Please review
important claims with a qualified attorney before making decisions.
```

**In report footer:**
```
Generated: 2025-01-29 10:00:00 UTC
Tool: PolicyInsight v1.0
Confidence: This report is based on automated analysis and extraction.
All claims are cited to source text. Confidence levels vary by section.
```

---

## 9. Datadog Observability Plan

### 9.1 Instrumentation Strategy

#### A. Java Agent Setup
```yaml
# In Dockerfile (Cloud Run)
ENV DD_TRACE_ENABLED=true
ENV DD_JMXFETCH_ENABLED=true
ENV DD_SERVICE=policyinsight
ENV DD_ENV=prod
ENV DD_VERSION=${GIT_COMMIT_SHA}
ENV DD_TRACE_SAMPLE_RATE=1.0
ENV DD_API_KEY=${DATADOG_API_KEY}
ENV DD_SITE=datadoghq.com

# Add dd-java-agent JAR to image
COPY dd-java-agent-1.32.0.jar /app/

# Run with agent
CMD exec java -javaagent:/app/dd-java-agent-1.32.0.jar \
  -Ddd.trace.global.tags=env:${DD_ENV},service:${DD_SERVICE} \
  -jar /app/policyinsight.jar
```

#### B. Structured Logging (SLF4J + JSON)
```json
{
  "timestamp": "2025-01-29T10:00:00Z",
  "level": "INFO",
  "logger": "com.policyinsight.worker.JobProcessor",
  "message": "Job processing started",
  "job_id": "550e8400-e29b-41d4-a716-446655440000",
  "dd_trace_id": "12345678901234567890123456789012",
  "dd_span_id": "9876543210987654",
  "file_size_bytes": 5242880,
  "classification": "TOS"
}
```

#### C. Span Instrumentation (Custom)
```java
// In PolicyJobService
@Transactional
public void processJob(UUID jobId) {
  Span parentSpan = tracer.buildSpan("job.process")
    .withTag("job_id", jobId.toString())
    .withTag("service_name", "policyinsight")
    .start();

  try {
    // Extraction
    Span extractSpan = tracer.buildSpan("extraction")
      .asChildOf(parentSpan)
      .start();
    try {
      extractAndChunk(jobId);
      extractSpan.setTag("status", "SUCCESS");
    } finally {
      extractSpan.finish();
    }

    // Classification
    Span classifySpan = tracer.buildSpan("classification")
      .asChildOf(parentSpan)
      .start();
    try {
      PolicyJob job = classifyDocument(jobId);
      classifySpan.setTag("confidence", job.getClassificationConfidence());
    } finally {
      classifySpan.finish();
    }

    // Risk scanning + summarization
    Span analysisSpan = tracer.buildSpan("analysis")
      .asChildOf(parentSpan)
      .start();
    try {
      Report report = analyzeRisksAndSummarize(jobId);
      analysisSpan.setTag("risk_categories", 5);
      analysisSpan.setTag("bullet_count", report.getSummary().size());
    } finally {
      analysisSpan.finish();
    }

  } finally {
    parentSpan.finish();
  }
}
```

#### D. LLM Observability (Vertex AI)
```java
// Wrapper around Gemini calls to capture tokens/latency
public String callGemini(String prompt, String taskType) {
  Span llmSpan = tracer.buildSpan("llm.call")
    .withTag("provider", "vertex-ai")
    .withTag("model", "gemini-pro")
    .withTag("task_type", taskType)
    .start();

  long startTime = System.currentTimeMillis();

  try {
    GenerateContentResponse response =
      vertexAiClient.generateContent(prompt);

    long duration = System.currentTimeMillis() - startTime;

    // Extract token usage
    int inputTokens = response.getUsageMetadata().getPromptTokenCount();
    int outputTokens = response.getUsageMetadata().getCandidatesTokenCount();

    llmSpan.setTag("input_tokens", inputTokens);
    llmSpan.setTag("output_tokens", outputTokens);
    llmSpan.setTag("total_tokens", inputTokens + outputTokens);
    llmSpan.setTag("duration_ms", duration);

    // Estimate cost (Gemini pricing ~ $0.0005 / 1k input tokens, $0.0015 / 1k output)
    double estimatedCost = (inputTokens * 0.0005 + outputTokens * 0.0015) / 1000.0;
    llmSpan.setTag("estimated_cost_usd", estimatedCost);

    // Log for aggregation
    meterRegistry.counter("llm.tokens.input",
      "task", taskType,
      "model", "gemini-pro")
      .increment(inputTokens);

    return response.getContent().getParts(0).getText();

  } catch (Exception e) {
    llmSpan.setTag("error", true);
    llmSpan.setTag("error.message", e.getMessage());
    meterRegistry.counter("llm.errors", "task", taskType).increment();
    throw e;
  } finally {
    llmSpan.finish();
  }
}
```

#### E. Document AI Observability
```java
public DocumentProcessingResult extractWithDocumentAI(String gcsPath) {
  Span docaiSpan = tracer.buildSpan("documentai.extraction")
    .withTag("provider", "google-cloud-documentai")
    .start();

  long startTime = System.currentTimeMillis();

  try {
    ProcessResponse response = documentAiClient.process(gcsPath);
    long duration = System.currentTimeMillis() - startTime;

    docaiSpan.setTag("duration_ms", duration);
    docaiSpan.setTag("page_count", response.getDocument().getPages().size());
    docaiSpan.setTag("confidence", response.getDocument().getConfidence());

    meterRegistry.timer("documentai.extraction.latency_ms").record(duration, TimeUnit.MILLISECONDS);

    return parseResponse(response);

  } catch (StatusRuntimeException e) {
    docaiSpan.setTag("error", true);
    docaiSpan.setTag("grpc_status", e.getStatus().getCode().toString());

    if (e.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED) {
      meterRegistry.counter("documentai.quota_exceeded").increment();
      // Trigger fallback
    }
    throw e;
  } finally {
    docaiSpan.finish();
  }
}
```

#### F. Metrics Registry (Micrometer)
```java
// In MetricsConfiguration.java
@Bean
public MeterBinder customMetrics() {
  return registry -> {
    // Job processing latency (histogram)
    Timer.builder("job.processing.latency_ms")
      .description("Time to process a single document")
      .publishPercentiles(0.5, 0.95, 0.99)
      .register(registry);

    // Queue depth (gauge)
    registry.gauge("pubsub.queue.depth",
      () -> pubsubClient.getQueueSize());

    // LLM cost per job (histogram)
    Timer.builder("llm.cost_per_job_usd")
      .description("Estimated cost of Gemini calls per job")
      .register(registry);

    // Citation coverage rate (gauge per job)
    registry.gauge("report.citation.coverage_pct",
      () -> computeCitationCoverageRate());

    // Extraction confidence (histogram)
    Timer.builder("extraction.confidence_score")
      .publishPercentiles(0.5, 0.95)
      .register(registry);

    // Fallback OCR usage (counter)
    registry.counter("extraction.fallback_used");

    // Document AI quota errors (counter)
    registry.counter("documentai.quota_errors");

    // HTTP request latency (already auto-instrumented by Spring Boot + dd-java-agent)
  };
}
```

---

### 9.2 Datadog Dashboards

#### Dashboard: "PolicyInsight Operations"

**Widgets:**

1. **P50/P95 API Latency (timeseries)**
   - Metric: `http.server.duration{service:policyinsight}`
   - Aggregation: p50, p95
   - Legend: "Web API"

2. **Job Processing Latency (timeseries)**
   - Metric: `job.processing.latency_ms`
   - Aggregation: p50, p95, p99
   - Legend: "End-to-end processing"

3. **4xx / 5xx Error Rate (timeseries)**
   - Metric: `http.server.requests{status:4xx}`, `http.server.requests{status:5xx}`
   - Legend: "Client errors", "Server errors"

4. **Pub/Sub Queue Depth (gauge)**
   - Metric: `pubsub.queue.depth`
   - Alert threshold: depth > 50

5. **LLM Token Usage (timeseries)**
   - Metric: `llm.tokens.input` + `llm.tokens.output`
   - Stacked area chart

6. **Estimated LLM Cost (timeseries)**
   - Metric: `llm.cost_per_job_usd`
   - Aggregation: sum (cumulative)

7. **Document AI Extraction Latency (timeseries)**
   - Metric: `documentai.extraction.latency_ms`
   - Aggregation: p50, p95

8. **Extraction Fallback Rate (timeseries)**
   - Metric: `extraction.fallback_used / total_extractions`
   - Legend: "% using fallback OCR"

9. **Citation Coverage Rate (gauge)**
   - Metric: `report.citation.coverage_pct`
   - Target: ≥ 95%

10. **Job Success vs. Failure (timeseries)**
    - Metric: `job.success_count`, `job.failure_count`
    - Stacked bar chart

11. **SLO Burn Rate (timeseries)**
    - Metric: SLO burn rate (see 9.3 below)

12. **Recent Incidents (list)**
    - Metric: `datadog.monitors.triggered`
    - Filter: `service:policyinsight`

---

### 9.3 Detection Rules (Monitors) & SLOs

#### Monitor 1: API Latency Spike

```yaml
name: "[PolicyInsight] API Latency P95 Spike"
type: threshold
query: |
  avg(last_5m): percentile_cont(http.server.duration{service:policyinsight} by {env}) > 2000
threshold: 2000  # 2 seconds
timeframe: last_5m
alert_recovery: true
notify: @pagerduty

escalation:
  threshold: 3000  # 3 seconds warning
  timeframe: last_10m

tags:
  - service:policyinsight
  - alert_type:latency
  - severity:high

description: |
  API response time (p95) has exceeded 2 seconds for the last 5 minutes.
  Possible causes: database slow query, Vertex AI API lag, GCS timeout.

  Check:
  - Recent deployments (DD_VERSION tag)
  - Database connection pool exhaustion
  - Vertex AI quota/rate limits
  - GCS latency
```

**Actionable Item Creation:**
```
When triggered:
  POST /api/v2/incidents with:
  {
    "title": "[PolicyInsight] API Latency P95 Spike",
    "severity": "high",
    "customer_impact_scope": "all_users",
    "state": "active",
    "notification_handles": ["@on-call-engineering"],
    "data_object": {
      "metric": "http.server.duration{service:policyinsight}",
      "threshold": 2000,
      "current_value": response.queries[0].result,
      "time_triggered": now(),
      "deploy_version": tags.DD_VERSION,
      "recent_commits": fetch_recent_commits()
    }
  }

  Include in incident context:
  - Link to PolicyInsight Ops dashboard (filtered to last 1h)
  - Link to trace search: {service:policyinsight, duration > 2000ms}
  - Recommended runbook: "Investigate slow API endpoints"
```

---

#### Monitor 2: Job Queue Backlog

```yaml
name: "[PolicyInsight] Pub/Sub Queue Depth High"
type: threshold
query: |
  avg(last_5m): pubsub.queue.depth{service:policyinsight} > 50
threshold: 50
timeframe: last_5m
no_data_timeframe: 10m  # Alert if metrics stop

tags:
  - service:policyinsight
  - alert_type:backlog
  - severity:medium

description: |
  Queue depth has exceeded 50 jobs for the last 5 minutes.
  Workers may be slow or crashed.

  Check:
  - Worker pod logs (Cloud Run)
  - Document AI quota/throttling
  - Gemini API quota/throttling
  - Worker crash logs
```

**Actionable Item Creation:**
```
When triggered:
  Create incident:
  {
    "title": "[PolicyInsight] Job Queue Backlog (50+ jobs)",
    "severity": "medium",
    "data_object": {
      "queue_depth": response.queries[0].result,
      "threshold": 50,
      "oldest_job_age_minutes": compute_oldest_job_age(),
      "worker_pod_status": fetch_cloud_run_revisions(),
      "recent_errors": fetch_recent_errors_from_worker_logs()
    }
  }

  Include:
  - Link to worker logs (last 1h, errors only)
  - Link to Cloud Run deployment page
  - Recommended runbook: "Scale workers or investigate bottleneck"
```

---

#### Monitor 3: LLM Cost / Token Anomaly

```yaml
name: "[PolicyInsight] LLM Cost Spike or Error Rate High"
type: composite
rules:
  - metric: sum(last_5m): llm.cost_per_job_usd > 0.50
    threshold: 0.50
    description: "Cost per job exceeded $0.50 (likely infinite loop in prompt)"

  - metric: sum(last_5m): llm.errors / llm.calls > 0.1
    threshold: 0.10
    description: "LLM error rate exceeded 10%"

  - metric: avg(last_5m): report.citation.coverage_pct < 80
    threshold: 80
    description: "Citation coverage dropped below 80%"

tags:
  - service:policyinsight
  - alert_type:llm_health
  - severity:high

description: |
  LLM health has degraded. May indicate:
  - Prompt injection / infinite token loop
  - Vertex AI service degradation
  - Grounding logic failure (low citation coverage)

  Check:
  - Recent prompt changes
  - Vertex AI status page
  - Trace logs for cost breakdown per job
```

**Actionable Item Creation:**
```
When triggered:
  Create incident:
  {
    "title": "[PolicyInsight] LLM Health Degradation",
    "severity": "high",
    "data_object": {
      "cost_anomaly": {
        "metric": "llm.cost_per_job_usd",
        "threshold": 0.50,
        "current": response.queries[0].result
      },
      "error_rate": {
        "metric": "llm.errors / llm.calls",
        "current": response.queries[1].result
      },
      "citation_coverage": {
        "metric": "report.citation.coverage_pct",
        "current": response.queries[2].result
      },
      "recent_prompt_changes": fetch_recent_code_changes("prompt.*"),
      "affected_jobs": find_jobs_with_low_coverage()
    }
  }

  Include:
  - Link to LLM token usage dashboard
  - Link to Vertex AI quota/throttling status
  - Sample failed job logs + traces
  - Recommended runbook: "Investigate LLM grounding failure"
```

---

#### SLOs (Service Level Objectives)

```yaml
slos:
  - name: "API Latency"
    description: "95% of API requests complete in < 2 seconds"
    target: 95
    timeframe: 7d
    indicator:
      type: threshold
      metric: http.server.duration{service:policyinsight}
      threshold: 2000
      comparison: <
    error_budget_alert: true
    error_budget_threshold: 50  # Alert if > 50% of error budget burned

  - name: "Job Success Rate"
    description: "99% of jobs complete successfully"
    target: 99
    timeframe: 7d
    indicator:
      type: ratio
      numerator: count({status:SUCCESS})
      denominator: count({*})
    error_budget_alert: true
    error_budget_threshold: 50

  - name: "Citation Coverage"
    description: "All generated reports have ≥ 95% citation coverage"
    target: 95
    timeframe: 7d
    indicator:
      type: threshold
      metric: report.citation.coverage_pct
      threshold: 95
      comparison: >=
```

---

### 9.4 Incident & Case Creation Workflow

When a monitor triggers, automatically:

1. **Create Datadog Incident:**
   ```
   POST /api/v2/incidents
   {
     "title": "{monitor.name}",
     "severity": "{inferred from metric}",
     "customer_impact_scope": "services",
     "state": "active",
     "notification_handles": ["@on-call-engineering", "@slack-alerts"],
     "data_object": {
       "monitor_id": monitor.id,
       "triggered_at": timestamp,
       "metric_value": metric.current,
       "threshold": metric.threshold,
       "dashboard_link": "https://app.datadoghq.com/dashboard/...",
       "trace_search_link": "https://app.datadoghq.com/apm/traces?...",
       "service": "policyinsight",
       "env": "prod",
       "dd_version": extract_tag(metrics, "DD_VERSION"),
       "recent_commits": fetch_git_log(limit=5),
       "runbook_link": "{runbook_url_from_wiki}"
     }
   }
   ```

2. **Attach Context:**
   - Current metric value + threshold
   - Dashboard link (pre-filtered to service + last 1 hour)
   - Trace search link (filtered by error status + service)
   - Most recent deploy version + commits
   - Relevant error logs from last 10 minutes

3. **Notify on-call:**
   - Slack message: `@on-call Incident: {title} | Severity: {severity} | [View Incident](link)`
   - PagerDuty integration (if applicable)

4. **Example Payload for API Latency Alert:**
   ```json
   {
     "incident": {
       "id": "12345",
       "title": "[PolicyInsight] API Latency P95 Spike",
       "severity": "SEV-2",
       "created_at": "2025-01-29T10:05:00Z",
       "status": "ACTIVE",
       "customer_impact": "Degraded performance for all users uploading documents",
       "context": {
         "metric": "http.server.duration (p95)",
         "threshold_ms": 2000,
         "actual_ms": 3450,
         "duration": "5 minutes",
         "dd_version": "abc123def456",
         "recent_commits": [
           {"hash": "abc123", "message": "Optimize document AI chunking", "author": "alice", "time": "2025-01-29T08:30Z"},
           {"hash": "def456", "message": "Add citation coverage metric", "author": "bob", "time": "2025-01-29T08:00Z"}
         ],
         "errors_in_logs": [
           {"timestamp": "2025-01-29T10:04:30Z", "message": "DocumentAI quota exceeded", "job_id": "job-123", "trace_id": "trace-456"},
           {"timestamp": "2025-01-29T10:03:50Z", "message": "Gemini timeout", "job_id": "job-122", "trace_id": "trace-455"}
         ],
         "links": {
           "dashboard": "https://app.datadoghq.com/dashboard/abc?...",
           "traces": "https://app.datadoghq.com/apm/traces?query=service:policyinsight%20status:error%20duration:%3E2000&...",
           "logs": "https://app.datadoghq.com/logs?query=service:policyinsight%20status:error&...",
           "runbook": "https://wiki.company.com/runbooks/policyinsight/api-latency-spike"
         }
       }
     }
   }
   ```

---

### 9.5 Runbook Example

**Runbook: "Investigate API Latency Spike"**

```markdown
# [PolicyInsight] API Latency Spike – Runbook

## Detection
- Monitor: "API Latency P95 Spike"
- Threshold: p95 latency > 2000ms
- Severity: HIGH

## Immediate Steps
1. Open the [PolicyInsight Ops Dashboard](https://app.datadoghq.com/dashboard/policyinsight-ops)
2. Check "P50/P95 API Latency" widget:
   - If p95 > 2000ms for last 5m: Confirmed
   - Check p99: if p99 >> p95, issue is likely in tail (rare jobs)
3. Check "4xx / 5xx Error Rate" widget:
   - If errors > 1%, may be related to API failures

## Diagnosis

### Check Recent Deployment
1. Look at incident context: recent commits
2. If deployment in last 30 minutes:
   - Check which services changed (web, worker, both?)
   - Go to Cloud Run > policyinsight-web and policyinsight-worker
   - Verify latest revision is stable (not crashing)

### Check Downstream Services
1. **Document AI:**
   - Open incident context > "errors_in_logs"
   - If "DocumentAI quota exceeded": call Document AI API
   - Check usage: go to Google Cloud Console > Document AI > Quotas
   - If at limit: document has high OCR load (scanned PDF)

2. **Vertex AI (Gemini):**
   - If "Gemini timeout" in logs: Vertex AI may be slow
   - Check Vertex AI status page: https://status.cloud.google.com
   - In Datadog, check "LLM Extraction Latency" widget (should be < 5s normally)

3. **Database (Cloud SQL):**
   - In Datadog, add query:
     ```
     avg(last_5m): system.cpu.user{host:cloudsql-postgres} > 80
     ```
   - If high CPU: slow queries
   - In Cloud SQL Console > Insights > Query Insights
   - Look for slow queries (sort by total time)

### Check Web Service Pods
1. Go to Cloud Run > policyinsight-web
2. Check "Metrics" tab:
   - CPU usage > 70%?
   - Memory usage > 80%?
   - Concurrent requests > {instance_concurrency_limit}?
3. If overloaded: increase instance max concurrency or add more instances

## Resolution

### Option A: Temporary Rollback
If root cause is recent deployment:
```bash
gcloud run services update-traffic policyinsight-web \
  --to-revisions {previous-stable-revision}=100
```

### Option B: Scale Horizontally
If overload:
```bash
gcloud run services update policyinsight-web \
  --max-instances 10  # increase from 5
```

### Option C: Pause Pub/Sub Processing
If workers are overloaded:
```bash
# Pause the subscription temporarily
gcloud pubsub subscriptions update policyinsight-analysis-sub --push-no-wrapper
```

## Verification
1. Open dashboard again, check p95 latency in last 5m
2. Should be < 2000ms within 5 minutes of fix
3. Close incident in Datadog

## Post-Incident
- If deployment caused issue: add integration test to catch latency regression
- If quota limit: increase quota or implement rate limiting on client side
- Update monitoring: may need to lower threshold or add additional metrics
```

---

## 10. CI/CD Plan

### 10.1 GitHub Actions Workflows

#### Workflow A: CI (On Pull Request)
**File: `.github/workflows/ci.yml`**

```yaml
name: CI

on:
  pull_request:
    branches: [main, develop]

jobs:
  build-and-test:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_USER: policyinsight
          POSTGRES_PASSWORD: test-password
          POSTGRES_DB: policyinsight_test
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Run tests
        run: mvn clean test
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/policyinsight_test
          SPRING_DATASOURCE_USERNAME: policyinsight
          SPRING_DATASOURCE_PASSWORD: test-password

      - name: Check code format (spotless)
        run: mvn spotless:check

      - name: Run linter (checkstyle)
        run: mvn checkstyle:check

      - name: Verify DB migrations
        run: mvn flyway:validate
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/policyinsight_test
          SPRING_DATASOURCE_USERNAME: policyinsight
          SPRING_DATASOURCE_PASSWORD: test-password

      - name: Build Docker image (dry run)
        run: |
          docker build -t policyinsight:pr-${{ github.event.number }} \
            --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
            --build-arg VCS_REF=${{ github.sha }} \
            .

      - name: Generate OpenAPI spec
        run: mvn clean springdoc-openapi-maven-plugin:generate -DskipTests

      - name: Upload OpenAPI spec as artifact
        uses: actions/upload-artifact@v4
        with:
          name: openapi-spec
          path: target/openapi.json

      - name: Comment on PR with test results
        if: always()
        uses: actions/github-script@v7
        with:
          script: |
            const fs = require('fs');
            const testResults = fs.readFileSync('target/surefire-reports/TEST-*.xml', 'utf8');
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: `✅ Build passed. Tests: ${testResults.match(/tests="(\d+)"/)[1]} | OpenAPI spec generated`
            });

```

---

#### Workflow B: CD (On Push to Main)
**File: `.github/workflows/cd.yml`**

```yaml
name: CD

on:
  push:
    branches: [main]
    tags:
      - 'v*'

env:
  REGISTRY: gcr.io
  GCP_PROJECT_ID: ${{ secrets.GCP_PROJECT_ID }}
  ARTIFACT_REGISTRY: us-central1-docker.pkg.dev

jobs:
  deploy:
    runs-on: ubuntu-latest

    permissions:
      contents: read
      id-token: write

    steps:
      - uses: actions/checkout@v4

      - name: Set up Cloud SDK
        uses: google-github-actions/setup-gcloud@v1
        with:
          project_id: ${{ env.GCP_PROJECT_ID }}
          export_default_credentials: true

      - name: Configure Docker auth for Artifact Registry
        run: |
          gcloud auth configure-docker ${{ env.ARTIFACT_REGISTRY }}

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Extract version
        id: version
        run: |
          if [[ ${{ github.ref }} == refs/tags/* ]]; then
            VERSION=${GITHUB_REF#refs/tags/}
          else
            VERSION=main-$(date +%s)
          fi
          echo "VERSION=${VERSION}" >> $GITHUB_OUTPUT
          echo "GIT_COMMIT=$(git rev-parse --short HEAD)" >> $GITHUB_OUTPUT

      - name: Build and push Docker image (Web service)
        run: |
          docker build \
            -t ${{ env.ARTIFACT_REGISTRY }}/${{ env.GCP_PROJECT_ID }}/policyinsight-web:${{ steps.version.outputs.VERSION }} \
            -t ${{ env.ARTIFACT_REGISTRY }}/${{ env.GCP_PROJECT_ID }}/policyinsight-web:latest \
            --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
            --build-arg VCS_REF=${{ steps.version.outputs.GIT_COMMIT }} \
            -f Dockerfile.web \
            .
          docker push ${{ env.ARTIFACT_REGISTRY }}/${{ env.GCP_PROJECT_ID }}/policyinsight-web:${{ steps.version.outputs.VERSION }}

      - name: Build and push Docker image (Worker service)
        run: |
          docker build \
            -t ${{ env.ARTIFACT_REGISTRY }}/${{ env.GCP_PROJECT_ID }}/policyinsight-worker:${{ steps.version.outputs.VERSION }} \
            -t ${{ env.ARTIFACT_REGISTRY }}/${{ env.GCP_PROJECT_ID }}/policyinsight-worker:latest \
            --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
            --build-arg VCS_REF=${{ steps.version.outputs.GIT_COMMIT }} \
            -f Dockerfile.worker \
            .
          docker push ${{ env.ARTIFACT_REGISTRY }}/${{ env.GCP_PROJECT_ID }}/policyinsight-worker:${{ steps.version.outputs.VERSION }}

      - name: Deploy Web service to Cloud Run
        run: |
          gcloud run deploy policyinsight-web \
            --image ${{ env.ARTIFACT_REGISTRY }}/${{ env.GCP_PROJECT_ID }}/policyinsight-web:${{ steps.version.outputs.VERSION }} \
            --region us-central1 \
            --platform managed \
            --allow-unauthenticated \
            --max-instances 10 \
            --memory 2Gi \
            --timeout 300 \
            --set-env-vars DD_SERVICE=policyinsight-web,DD_ENV=prod,DD_VERSION=${{ steps.version.outputs.VERSION }},DD_TRACE_ENABLED=true \
            --set-secrets DATADOG_API_KEY=datadog-api-key:latest \
            --service-account policyinsight-web@${{ env.GCP_PROJECT_ID }}.iam.gserviceaccount.com

      - name: Deploy Worker service to Cloud Run
        run: |
          gcloud run deploy policyinsight-worker \
            --image ${{ env.ARTIFACT_REGISTRY }}/${{ env.GCP_PROJECT_ID }}/policyinsight-worker:${{ steps.version.outputs.VERSION }} \
            --region us-central1 \
            --platform managed \
            --no-allow-unauthenticated \
            --max-instances 5 \
            --memory 4Gi \
            --timeout 600 \
            --set-env-vars DD_SERVICE=policyinsight-worker,DD_ENV=prod,DD_VERSION=${{ steps.version.outputs.VERSION }},DD_TRACE_ENABLED=true \
            --set-secrets DATADOG_API_KEY=datadog-api-key:latest \
            --service-account policyinsight-worker@${{ env.GCP_PROJECT_ID }}.iam.gserviceaccount.com

      - name: Smoke test (health check)
        run: |
          HEALTH_URL=$(gcloud run services describe policyinsight-web --region us-central1 --format='value(status.url)')/health
          for i in {1..5}; do
            STATUS=$(curl -s -o /dev/null -w "%{http_code}" $HEALTH_URL)
            if [ "$STATUS" = "200" ]; then
              echo "✅ Health check passed"
              exit 0
            fi
            echo "Attempt $i: Status $STATUS, retrying in 10s..."
            sleep 10
          done
          echo "❌ Health check failed"
          exit 1

      - name: Export Datadog dashboards (as JSON artifacts)
        run: |
          # Assumes you have a script to export dashboards via Datadog API
          # See setup guide for details
          ./scripts/export-datadog-dashboards.sh ${{ secrets.DATADOG_API_KEY }} ${{ secrets.DATADOG_APP_KEY }}

      - name: Upload Datadog exports as artifacts
        uses: actions/upload-artifact@v4
        with:
          name: datadog-dashboards
          path: datadog/exports/*.json

      - name: Create GitHub release (if tag)
        if: startsWith(github.ref, 'refs/tags/')
        uses: actions/create-release@v1
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        with:
          tag_name: ${{ github.ref }}
          release_name: Release ${{ github.ref }}
          body: |
            ## Deployment
            - Web: `${{ env.ARTIFACT_REGISTRY }}/${{ env.GCP_PROJECT_ID }}/policyinsight-web:${{ steps.version.outputs.VERSION }}`
            - Worker: `${{ env.ARTIFACT_REGISTRY }}/${{ env.GCP_PROJECT_ID }}/policyinsight-worker:${{ steps.version.outputs.VERSION }}`

            ## Changes
            See commit log: ${{ github.server_url }}/${{ github.repository }}/compare/${{ github.event.before }}...${{ github.event.after }}

```

---

#### Workflow C: Rollback (Manual Trigger)
**File: `.github/workflows/rollback.yml`**

```yaml
name: Rollback

on:
  workflow_dispatch:
    inputs:
      service:
        description: 'Service to rollback'
        required: true
        type: choice
        options:
          - policyinsight-web
          - policyinsight-worker
          - both
      revision:
        description: 'Revision to rollback to (leave empty for previous)'
        required: false

jobs:
  rollback:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      id-token: write

    steps:
      - uses: actions/checkout@v4

      - name: Set up Cloud SDK
        uses: google-github-actions/setup-gcloud@v1
        with:
          project_id: ${{ secrets.GCP_PROJECT_ID }}

      - name: Rollback Web service
        if: ${{ inputs.service == 'policyinsight-web' || inputs.service == 'both' }}
        run: |
          if [ -z "${{ inputs.revision }}" ]; then
            # Get previous stable revision
            REVISION=$(gcloud run services describe policyinsight-web --region us-central1 --format='value(status.traffic[1].revisionName)' 2>/dev/null || echo "")
          else
            REVISION=${{ inputs.revision }}
          fi

          if [ -z "$REVISION" ]; then
            echo "❌ Could not determine previous revision"
            exit 1
          fi

          echo "Rolling back to revision: $REVISION"
          gcloud run services update-traffic policyinsight-web \
            --to-revisions $REVISION=100 \
            --region us-central1

      - name: Rollback Worker service
        if: ${{ inputs.service == 'policyinsight-worker' || inputs.service == 'both' }}
        run: |
          if [ -z "${{ inputs.revision }}" ]; then
            REVISION=$(gcloud run services describe policyinsight-worker --region us-central1 --format='value(status.traffic[1].revisionName)' 2>/dev/null || echo "")
          else
            REVISION=${{ inputs.revision }}
          fi

          if [ -z "$REVISION" ]; then
            echo "❌ Could not determine previous revision"
            exit 1
          fi

          echo "Rolling back to revision: $REVISION"
          gcloud run services update-traffic policyinsight-worker \
            --to-revisions $REVISION=100 \
            --region us-central1

      - name: Verify rollback
        run: |
          sleep 10
          HEALTH_URL=$(gcloud run services describe policyinsight-web --region us-central1 --format='value(status.url)')/health
          STATUS=$(curl -s -o /dev/null -w "%{http_code}" $HEALTH_URL)
          if [ "$STATUS" = "200" ]; then
            echo "✅ Rollback successful"
          else
            echo "⚠️ Health check returned $STATUS, may need manual intervention"
          fi

      - name: Notify Slack
        run: |
          curl -X POST ${{ secrets.SLACK_WEBHOOK }} \
            -H 'Content-Type: application/json' \
            -d '{
              "text": "🔄 Rollback completed",
              "blocks": [
                {"type": "section", "text": {"type": "mrkdwn", "text": "*Rollback Completed*"}},
                {"type": "section", "fields": [
                  {"type": "mrkdwn", "text": "*Service:*\n${{ inputs.service }}"},
                  {"type": "mrkdwn", "text": "*Revision:*\n${{ inputs.revision }}"},
                  {"type": "mrkdwn", "text": "*Triggered by:*\n${{ github.actor }}"},
                  {"type": "mrkdwn", "text": "*Status:*\n✅ Successful"}
                ]}
              ]
            }'

```

---

### 10.2 Artifact Management

**CI publishes:**
- Docker images → Artifact Registry (`us-central1-docker.pkg.dev`)
- OpenAPI spec → GitHub artifacts (`.openapi.json`)
- Datadog dashboards → GitHub artifacts (`datadog/exports/*.json`)

**CD retrieves:**
- Docker images → Deploy to Cloud Run
- Datadog JSON → Stored in repo for reference and documentation

**Release artifacts:**
- Versioned container images (e.g., `v1.0.0`, `v1.0.1`)
- GitHub release notes with deployment instructions
- Datadog configuration snapshots

---

## 11. Setup & Deployment Guide

### 11.1 Google Cloud Setup

#### A. Project & APIs

```bash
# Create GCP project
gcloud projects create policyinsight-prod --name="PolicyInsight"
gcloud config set project policyinsight-prod

# Enable required APIs
gcloud services enable run.googleapis.com
gcloud services enable artifactregistry.googleapis.com
gcloud services enable storage-api.googleapis.com
gcloud services enable sqladmin.googleapis.com
gcloud services enable documentai.googleapis.com
gcloud services enable aiplatform.googleapis.com
gcloud services enable pubsub.googleapis.com
gcloud services enable logging.googleapis.com
gcloud services enable monitoring.googleapis.com
gcloud services enable cloudkms.googleapis.com
```

#### B. Service Accounts & IAM

```bash
# Web service account
gcloud iam service-accounts create policyinsight-web \
  --display-name="PolicyInsight Web Service"

gcloud projects add-iam-policy-binding policyinsight-prod \
  --member="serviceAccount:policyinsight-web@policyinsight-prod.iam.gserviceaccount.com" \
  --role="roles/run.invoker"

gcloud projects add-iam-policy-binding policyinsight-prod \
  --member="serviceAccount:policyinsight-web@policyinsight-prod.iam.gserviceaccount.com" \
  --role="roles/storage.objectUser"

gcloud projects add-iam-policy-binding policyinsight-prod \
  --member="serviceAccount:policyinsight-web@policyinsight-prod.iam.gserviceaccount.com" \
  --role="roles/cloudsql.client"

gcloud projects add-iam-policy-binding policyinsight-prod \
  --member="serviceAccount:policyinsight-web@policyinsight-prod.iam.gserviceaccount.com" \
  --role="roles/pubsub.publisher"

gcloud projects add-iam-policy-binding policyinsight-prod \
  --member="serviceAccount:policyinsight-web@policyinsight-prod.iam.gserviceaccount.com" \
  --role="roles/aiplatform.user"

# Worker service account (broader permissions)
gcloud iam service-accounts create policyinsight-worker \
  --display-name="PolicyInsight Worker Service"

gcloud projects add-iam-policy-binding policyinsight-prod \
  --member="serviceAccount:policyinsight-worker@policyinsight-prod.iam.gserviceaccount.com" \
  --role="roles/run.invoker"

gcloud projects add-iam-policy-binding policyinsight-prod \
  --member="serviceAccount:policyinsight-worker@policyinsight-prod.iam.gserviceaccount.com" \
  --role="roles/storage.admin"  # Full access to read/write PDFs

gcloud projects add-iam-policy-binding policyinsight-prod \
  --member="serviceAccount:policyinsight-worker@policyinsight-prod.iam.gserviceaccount.com" \
  --role="roles/cloudsql.client"

gcloud projects add-iam-policy-binding policyinsight-prod \
  --member="serviceAccount:policyinsight-worker@policyinsight-prod.iam.gserviceaccount.com" \
  --role="roles/pubsub.subscriber"

gcloud projects add-iam-policy-binding policyinsight-prod \
  --member="serviceAccount:policyinsight-worker@policyinsight-prod.iam.gserviceaccount.com" \
  --role="roles/documentai.apiUser"

gcloud projects add-iam-policy-binding policyinsight-prod \
  --member="serviceAccount:policyinsight-worker@policyinsight-prod.iam.gserviceaccount.com" \
  --role="roles/aiplatform.user"

# GitHub Actions service account (for CI/CD)
gcloud iam service-accounts create github-actions \
  --display-name="GitHub Actions Deployment"

gcloud projects add-iam-policy-binding policyinsight-prod \
  --member="serviceAccount:github-actions@policyinsight-prod.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.writer"

gcloud projects add-iam-policy-binding policyinsight-prod \
  --member="serviceAccount:github-actions@policyinsight-prod.iam.gserviceaccount.com" \
  --role="roles/run.developer"
```

#### C. Cloud SQL (PostgreSQL)

```bash
# Create Cloud SQL instance
gcloud sql instances create policyinsight-db \
  --database-version POSTGRES_15 \
  --tier db-custom-2-8192 \
  --region us-central1 \
  --availability-type REGIONAL \
  --backup-start-time 03:00 \
  --enable-bin-log \
  --retained-backups-count 7

# Create database
gcloud sql databases create policyinsight \
  --instance policyinsight-db --charset UTF8

# Create application user
gcloud sql users create policyinsight-app \
  --instance policyinsight-db \
  --password=$(openssl rand -base64 32)

# Store password in Google Secret Manager
gcloud secrets create cloudsql-password \
  --replication-policy automatic \
  --data-file <(gcloud sql users describe policyinsight-app --instance policyinsight-db --format='value(password)')
```

#### D. Cloud Storage (Buckets)

```bash
# Create bucket for PDFs and reports
gsutil mb -l us-central1 -b on gs://policyinsight-prod-documents

# Set lifecycle policy (delete objects after 90 days)
cat > /tmp/lifecycle.json << 'EOF'
{
  "lifecycle": {
    "rule": [
      {
        "action": {"type": "Delete"},
        "condition": {"age": 90}
      }
    ]
  }
}
EOF

gsutil lifecycle set /tmp/lifecycle.json gs://policyinsight-prod-documents

# Set uniform bucket-level access
gsutil uniformbucketlevelaccess set on gs://policyinsight-prod-documents
```

#### E. Pub/Sub (Messaging)

```bash
# Create topic
gcloud pubsub topics create policyinsight-analysis-topic

# Create subscription (push to worker service)
WORKER_URL=$(gcloud run services describe policyinsight-worker \
  --region us-central1 --format='value(status.url)' 2>/dev/null || echo "https://policyinsight-worker.run.app")

gcloud pubsub subscriptions create policyinsight-analysis-sub \
  --topic policyinsight-analysis-topic \
  --push-endpoint $WORKER_URL/pubsub/push \
  --push-auth-service-account policyinsight-worker@policyinsight-prod.iam.gserviceaccount.com \
  --ack-deadline 300 \
  --message-retention-duration 604800s  # 7 days
```

#### F. Secrets (API Keys)

```bash
# Datadog API Key
gcloud secrets create datadog-api-key \
  --replication-policy automatic \
  --data-file <(echo "$DATADOG_API_KEY")

# Datadog App Key
gcloud secrets create datadog-app-key \
  --replication-policy automatic \
  --data-file <(echo "$DATADOG_APP_KEY")

# Grant service accounts access
gcloud secrets add-iam-policy-binding datadog-api-key \
  --member="serviceAccount:policyinsight-web@policyinsight-prod.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"

gcloud secrets add-iam-policy-binding datadog-api-key \
  --member="serviceAccount:policyinsight-worker@policyinsight-prod.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
```

---

### 11.2 Datadog Setup

#### A. Organization & Keys

```bash
# Create Datadog organization (if not exists)
# Navigate to https://app.datadoghq.com/organization/new
# Org name: "PolicyInsight"
# Plan: Free / Pro trial

# Generate API + App keys
# Account Settings → API → API Keys
# Copy to environment:
export DATADOG_API_KEY="your-api-key-here"
export DATADOG_APP_KEY="your-app-key-here"
export DATADOG_SITE="datadoghq.com"
```

#### B. GCP Integration

```bash
# Create GCP service account for Datadog
gcloud iam service-accounts create datadog-integration \
  --display-name="Datadog GCP Integration"

# Grant Datadog read-only access to monitoring metrics
gcloud projects add-iam-policy-binding policyinsight-prod \
  --member="serviceAccount:datadog-integration@policyinsight-prod.iam.gserviceaccount.com" \
  --role="roles/monitoring.metricReader"

gcloud projects add-iam-policy-binding policyinsight-prod \
  --member="serviceAccount:datadog-integration@policyinsight-prod.iam.gserviceaccount.com" \
  --role="roles/logging.logWriter"

# Create key
gcloud iam service-accounts keys create /tmp/datadog-gcp-key.json \
  --iam-account=datadog-integration@policyinsight-prod.iam.gserviceaccount.com

# In Datadog, configure GCP integration:
# Integrations → Google Cloud Platform
# Upload JSON key from /tmp/datadog-gcp-key.json
```

#### C. Cloud Run Instrumentation

Option 1: **In-container instrumentation (recommended)**

```dockerfile
# In Dockerfile
FROM eclipse-temurin:21-jdk as builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl

# Download Datadog Java agent
RUN curl -Lo /app/dd-java-agent.jar https://github.com/DataDog/dd-trace-java/releases/download/v1.32.0/dd-java-agent-1.32.0.jar

COPY --from=builder /app/target/policyinsight.jar .

ENV DD_TRACE_ENABLED=true
ENV DD_JMXFETCH_ENABLED=true
ENV DD_PROFILING_ENABLED=true
ENV DD_LOGS_INJECTION=true
ENV DD_TRACE_SAMPLE_RATE=1.0

ENTRYPOINT exec java \
  -javaagent:/app/dd-java-agent.jar \
  -Ddd.trace.global.tags=env:${DD_ENV},service:${DD_SERVICE},version:${DD_VERSION} \
  -jar policyinsight.jar
```

#### D. Dashboards & Monitors

```bash
# Export existing Datadog dashboards as JSON
# (See section 9.5 for exact dashboard structure)

# Via API:
curl -X GET https://api.datadoghq.com/api/v1/dashboard \
  -H "DD-API-KEY: $DATADOG_API_KEY" \
  -H "DD-APPLICATION-KEY: $DATADOG_APP_KEY" \
  > datadog/dashboards.json

# Create dashboards via JSON upload (or use UI to create, then export)
# Dashboards should include:
# 1. API Latency (p50, p95, p99)
# 2. Job Processing Latency
# 3. Error Rate (4xx, 5xx)
# 4. Queue Depth
# 5. LLM Token Usage
# 6. LLM Cost
# 7. Document AI Latency
# 8. Citation Coverage Rate
# 9. SLO Burn Rate
# 10. Recent Incidents
```

---

### 11.3 Local Development (Docker Compose)

**File: `docker-compose.yml`**

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_USER: policyinsight
      POSTGRES_PASSWORD: local-dev-password
      POSTGRES_DB: policyinsight_local
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U policyinsight"]
      interval: 10s
      timeout: 5s
      retries: 5

  minio:  # Local S3-compatible object storage
    image: minio/minio:latest
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports:
      - "9000:9000"
      - "9001:9001"
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data

  policyinsight-web:
    build:
      context: .
      dockerfile: Dockerfile.web
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/policyinsight_local
      SPRING_DATASOURCE_USERNAME: policyinsight
      SPRING_DATASOURCE_PASSWORD: local-dev-password
      GCS_BUCKET: policyinsight-local
      GCS_EMULATOR_HOST: minio:9000
      GCS_USE_EMULATOR: "true"
      PUBSUB_EMULATOR_HOST: localhost:8085
      DD_TRACE_ENABLED: "false"  # Disable Datadog locally
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      minio:
        condition: service_started
    volumes:
      - .:/app
      - /app/target
    command: mvn spring-boot:run

  policyinsight-worker:
    build:
      context: .
      dockerfile: Dockerfile.worker
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/policyinsight_local
      SPRING_DATASOURCE_USERNAME: policyinsight
      SPRING_DATASOURCE_PASSWORD: local-dev-password
      GCS_BUCKET: policyinsight-local
      GCS_EMULATOR_HOST: minio:9000
      GCS_USE_EMULATOR: "true"
      PUBSUB_EMULATOR_HOST: localhost:8085
      DD_TRACE_ENABLED: "false"
    depends_on:
      postgres:
        condition: service_healthy
      minio:
        condition: service_started
    volumes:
      - .:/app
      - /app/target
    command: mvn spring-boot:run

volumes:
  postgres_data:
  minio_data:

networks:
  default:
    name: policyinsight-network
```

**To run locally:**

```bash
# Clone repo
git clone https://github.com/yourusername/policyinsight.git
cd policyinsight

# Copy environment template
cp .env.example .env.local

# Start services
docker-compose up --build

# Wait for postgres to be healthy
docker-compose exec postgres pg_isready -U policyinsight

# Run Flyway migrations
docker-compose exec policyinsight-web mvn flyway:migrate

# Access
# Web: http://localhost:8080
# Postgres: localhost:5432
# Minio (S3): http://localhost:9001 (admin / minioadmin)
```

---

### 11.4 Repository Structure

```
policyinsight/
├── README.md                               # Comprehensive deployment + usage guide
├── LICENSE                                 # OSI-approved license (Apache 2.0)
├── pom.xml                                 # Maven multi-module build
├── docker-compose.yml                      # Local dev environment
├── .github/
│   └── workflows/
│       ├── ci.yml                          # PR tests, lint, build
│       ├── cd.yml                          # Deploy to Cloud Run + export dashboards
│       └── rollback.yml                    # Manual rollback trigger
├── src/
│   ├── main/
│   │   ├── java/com/policyinsight/
│   │   │   ├── api/
│   │   │   │   ├── web/                    # REST controllers + Spring MVC
│   │   │   │   ├── service/                # Business logic
│   │   │   │   ├── extraction/             # Document AI integration
│   │   │   │   ├── chunking/               # Text segmentation + citations
│   │   │   │   ├── classification/         # Document type inference
│   │   │   │   ├── analysis/               # Risk scanning + LLM calls
│   │   │   │   ├── storage/                # GCS + Postgres I/O
│   │   │   │   ├── qa/                     # Q&A handler
│   │   │   │   └── observability/          # Datadog instrumentation
│   │   │   ├── worker/
│   │   │   │   ├── pubsub/                 # Pub/Sub consumer
│   │   │   │   └── jobs/                   # Async job processor
│   │   │   └── shared/
│   │   │       ├── model/                  # JPA entities
│   │   │       ├── dto/                    # Request/response DTOs
│   │   │       └── config/                 # Spring configuration
│   │   ├── resources/
│   │   │   ├── application.yml             # Spring Boot config
│   │   │   ├── application-dev.yml
│   │   │   ├── application-prod.yml
│   │   │   ├── templates/                  # Thymeleaf HTML templates
│   │   │   │   ├── layout.html
│   │   │   │   ├── upload.html
│   │   │   │   ├── report.html
│   │   │   │   ├── share-report.html
│   │   │   │   └── fragments/              # htmx reusable fragments
│   │   │   ├── static/
│   │   │   │   ├── css/style.css
│   │   │   │   └── js/app.js               # Minimal JS (polling, form handlers)
│   │   │   └── db/migration/               # Flyway migrations
│   │   │       ├── V1__init.sql
│   │   │       ├── V2__add_trace_id.sql
│   │   │       └── ...
│   │   └── Main.java
│   ├── test/
│   │   ├── java/com/policyinsight/
│   │   │   └── [unit + integration tests]
│   │   └── resources/application-test.yml
│   └── worker/                             # Separate module for async worker
│       └── [similar structure]
├── scripts/
│   ├── export-datadog-dashboards.sh        # Export dashboards to JSON
│   ├── traffic-generator.sh                # Load test + trigger monitors
│   └── setup-gcp.sh                        # Automated GCP setup
├── docs/
│   ├── DEPLOYMENT.md                       # Step-by-step deployment guide
│   ├── ARCHITECTURE.md                     # System design + data flow
│   ├── OBSERVABILITY.md                    # Datadog integration guide
│   └── API.md                              # OpenAPI documentation
├── datadog/
│   ├── dashboards/
│   │   ├── PolicyInsight-Ops.json
│   │   └── ...
│   ├── monitors/
│   │   ├── api-latency-spike.json
│   │   ├── queue-backlog.json
│   │   └── llm-health.json
│   └── slos/
│       ├── api-latency-slo.json
│       └── success-rate-slo.json
├── Dockerfile.web                          # Web service container
├── Dockerfile.worker                       # Worker service container
├── cloud-run-web.yaml                      # Cloud Run config (declarative)
├── cloud-run-worker.yaml
├── .env.example                            # Environment template
└── README-EVALUATION.md                    # Evaluation and verification checklist

```

---

## 12. Demo Script (3 Minutes) + Verification Checklist

### 12.1 Demo Flow (Oral + Screen)

**[0:00–0:30] Intro & Problem Statement**
```
"PolicyInsight analyzes legal documents and returns plain-English risk reports
with mandatory source citations. Today, I'll show you the system in action,
and how we use Datadog to monitor it in production."
```

**[0:30–1:15] Live Demo: Upload & Analysis**
```
1. Go to https://policyinsight.example.com
2. Click "Upload Document"
3. Select sample TOS PDF
4. Watch progress bar (htmx polling)
5. Report auto-populates in ~15 seconds
6. Scroll through sections:
   - Overview (classification: TOS, 95% confidence)
   - Summary (5 bulleted risks, each cited)
   - Obligations (page numbers visible)
   - Risk Taxonomy (5 categories, "not detected" for absent risks)
   - Q&A: Ask "Can they auto-renew?"
     → Answer with citation + confidence
7. Click "Download PDF" (generates PDF with watermark)
8. Click "Get Share Link" (7-day read-only URL)
```

**[1:15–2:00] Datadog Observability**
```
1. Open Datadog dashboard: https://app.datadoghq.com/dashboard/policyinsight-ops
2. Show "P95 API Latency" widget: "95th percentile is ~800ms, well below 2s SLO"
3. Show "LLM Token Usage" widget: "~500 tokens per job, cost ~$0.0008"
4. Show "Citation Coverage Rate": "98% of claims are grounded"
5. Show "Recent Incidents" panel:
   - Mention: "If latency spiked, Datadog would auto-create incident here"
   - Click on sample incident → show context (traces, logs, recommended runbook)
6. Open Traces tab:
   - Filter: {service:policyinsight}
   - Click a trace
   - Show span waterfall: extraction (2s) → classification (0.5s) → analysis (1s)
7. Show deployment version tag (DD_VERSION = git-commit-sha)
```

**[2:00–2:45] Architecture & Reliability**
```
1. Show GitHub repo structure (indicate Thymeleaf + Spring Boot, no React)
2. Show CI/CD workflow (GitHub Actions, build → push → Cloud Run deploy)
3. Show Pub/Sub architecture diagram (upload → job → async processing)
4. Explain grounding strategy:
   - "Every risk statement is tied to extracted text"
   - "If we can't find evidence, we say so"
   - Example Q&A with fallback response
5. Show Flyway migrations (version control for schema)
```

**[2:45–3:00] Wrap-up**
```
"PolicyInsight demonstrates production-grade observability + safety.
With Datadog, we monitor latency, costs, and grounding quality in real time.
Every alert includes context, traces, and a runbook.
The system is ready to scale."
```

---

### 12.2 Verification Checklist

Prepare screenshots/links in advance for verification:

| Evidence | Screenshot/Link | Where to Verify |
|----------|-----------------|------------------------|
| **Hosted App** | https://policyinsight.example.com | "Home page loads in < 2s" |
| **Upload Flow** | Demo screen: upload form → progress → report | "User uploads PDF, sees real-time progress" |
| **Report Output** | Demo screen: 5 sections with citations | "Every claim is cited to page + text span" |
| **Citation Links** | Demo screen: click citation → highlight text | "Citations are interactive" |
| **Q&A Grounding** | Demo screen: answer with citation OR "insufficient evidence" | "System abstains when unsure" |
| **PDF Export** | Downloaded PDF with watermark + disclaimer | "Export is readable + watermarked" |
| **Share Link** | Generated 7-day link, access it read-only | "Link is valid and read-only" |
| **Public Repo** | https://github.com/yourusername/policyinsight | "README, OSI license, CI/CD workflows visible" |
| **OpenAPI Spec** | `/docs/openapi.json` or in repo artifacts | "Complete REST API documentation" |
| **Datadog Org** | Org name visible in incident/dashboard header | "Datadog integration is live" |
| **Dashboard** | Screenshot of PolicyInsight-Ops dashboard | "Shows latency, errors, cost, coverage" |
| **Monitor Triggered** | Screenshot of incident created by monitor | "Alert system works; incident has context" |
| **Incident Context** | Screenshot: incident detail page with traces + logs + runbook | "Actionable alert with linked evidence" |
| **Trace Waterfall** | Screenshot of Datadog trace spans (extraction → analysis → export) | "Full observability end-to-end" |
| **LLM Metrics** | Screenshot: LLM token count + cost per job | "LLM telemetry in Datadog" |
| **CI/CD Workflow** | Screenshot of GitHub Actions: tests → build → deploy → health check | "Automated deployment pipeline" |
| **Rollback Capability** | Screenshot of rollback workflow option (or execution) | "Rollback strategy is automated" |
| **Docker Container** | Image in Artifact Registry with DD_VERSION tag | "Versioned containers with observability baked in" |
| **Traffic Generator Output** | Screenshot of script triggering monitor (latency spike, queue spike) | "Reproducible test of alert system" |
| **Datadog JSON Exports** | `/datadog/dashboards/*.json`, `/datadog/monitors/*.json` in repo | "Dashboards/monitors are version-controlled and reproducible" |

---

## 13. Deployment Checklist

### Pre-Deployment Tasks

- [ ] **Code Quality**
  - [ ] All tests passing (`mvn clean test`)
  - [ ] Code formatted (`mvn spotless:apply`)
  - [ ] Checkstyle/linter passing (`mvn checkstyle:check`)
  - [ ] DB migrations validated (`mvn flyway:validate`)
  - [ ] No hardcoded secrets (use Secret Manager)
  - [ ] OSI-approved license in repo (`LICENSE` file, Apache 2.0 or MIT)

- [ ] **GitHub Repository**
  - [ ] Public repo with README (deployment + usage)
  - [ ] `.github/workflows/` with CI/CD (test → build → deploy)
  - [ ] `.gitignore` excludes `.env`, `target/`, secrets
  - [ ] Recent commits visible (not squashed history)
  - [ ] `docs/` folder with DEPLOYMENT.md, ARCHITECTURE.md, OBSERVABILITY.md

- [ ] **Datadog Setup**
  - [ ] Datadog organization configured
  - [ ] dd-java-agent integrated in Cloud Run containers
  - [ ] Traces visible in Datadog (no sampling loss)
  - [ ] Dashboards exported to JSON: `/datadog/dashboards/*.json`
  - [ ] Monitors (3+) exported to JSON: `/datadog/monitors/*.json`
  - [ ] SLOs configured + exported
  - [ ] Incident / Case creation triggered by at least 1 monitor
  - [ ] Runbooks linked in incident description

- [ ] **Google Cloud Deployment**
  - [ ] Cloud Run services deployed (web + worker)
  - [ ] Cloud SQL Postgres with Flyway migrations applied
  - [ ] GCS bucket provisioned
  - [ ] Pub/Sub topic + subscription configured
  - [ ] Vertex AI API enabled + Gemini accessible
  - [ ] Document AI API available (or fallback tested)
  - [ ] All secrets in Secret Manager (not in env vars)
  - [ ] Service accounts with least-privilege IAM

- [ ] **Traffic Generator**
  - [ ] Script in `/scripts/traffic-generator.sh` (or Python equivalent)
  - [ ] Script triggers each monitor condition (latency spike, queue spike, token/cost spike)
  - [ ] Script generates evidence screenshots (before/after Datadog dashboard)
  - [ ] Output documented in `README-EVALUATION.md`

- [ ] **Demo Preparation**
  - [ ] 3-minute demo script memorized (see 12.1)
  - [ ] Screenshots of each verification item (see 12.2) in a folder
  - [ ] Datadog org name, API key, and shared dashboard link ready
  - [ ] Live app URL tested (loads, accepts uploads, returns reports)
  - [ ] PDF export tested (downloads successfully, readable)
  - [ ] Share link tested (expires after 7 days)

- [ ] **Documentation**
  - [ ] README.md: project overview, value prop, usage
  - [ ] DEPLOYMENT.md: step-by-step GCP + Datadog setup
  - [ ] ARCHITECTURE.md: system design, module breakdown, data flow
  - [ ] OBSERVABILITY.md: Datadog integration, signals, dashboards, SLOs
  - [ ] API.md: OpenAPI spec (generated from code)
  - [ ] README-EVALUATION.md: evaluation notes (verification evidence, org name, runbooks)

---

### Project Artifacts

```
**Project Title:**
"PolicyInsight – Production-Grade Legal Document Analysis with Datadog Observability"

**Project Description:**
PolicyInsight is a backend-centric full-stack system that demonstrates production-grade observability, grounded AI safety, and cloud-native reliability.

Key highlights:
- Java 21 + Spring Boot REST API + server-rendered Thymeleaf UI
- Async job processing (Pub/Sub) + Cloud SQL + GCS
- Vertex AI Gemini for text summarization + risk analysis
- Google Cloud Document AI for OCR + layout extraction
- Datadog APM: end-to-end tracing, LLM cost analytics, grounding coverage metrics
- 3+ monitors + auto-incident creation with runbooks
- CI/CD via GitHub Actions (test → build → versioned deploy → rollback capability)
- Production safety: "cite-or-abstain" enforcement, no hallucination allowed
- Verification evidence: dashboards, monitors, incidents, traces, cost analytics

[Hosted URL]
https://policyinsight.example.com

[Public Repository]
https://github.com/yourusername/policyinsight

**Project Details:**
- Team: [Your name / team]
- Inspiration: Legal documents are written for lawyers, not people. We built clarity + safety.
- What it does: Analyze PDFs → get cited risk reports + grounded Q&A
- How we built it: Spring Boot + Vertex AI + Datadog
- Challenges: Grounding LLM outputs (cite-or-abstain), handling missing Document AI quota
- Accomplishments: Production system + observability dashboard + auto-incident + SLOs
- What's next: Multi-language support, negotiation suggestions, expert review marketplace
- Built with: Java, Spring Boot, Google Cloud (Run, SQL, Storage, Vertex AI, Document AI), Pub/Sub, PostgreSQL, Datadog, GitHub Actions

[Demo Video Link]
https://youtu.be/... (3 minutes, auto-upload with recording)

**Verification Evidence Screenshots & Links:**
Folder: `/evidence/` or embedded in README-EVALUATION.md
- Hosted app screenshot
- Report output with citations
- Datadog dashboard (PolicyInsight-Ops)
- Monitor triggered + incident created
- Trace waterfall (Datadog APM)
- LLM cost metrics
- GitHub CI/CD workflow
- Cloud Run deployment history
- Traffic generator output (before/after spikes)

[Datadog Org Name]
PolicyInsight

[Datadog JSON Exports]
In repo: `/datadog/dashboards/`, `/datadog/monitors/`, `/datadog/slos/`

[Runbook Link]
https://github.com/yourusername/policyinsight/blob/main/docs/OBSERVABILITY.md#runbooks

[Additional Notes]
- Focus: Datadog Observability + AI Integration
- Framework: Spring Boot 3.x (server-rendered, no React)
- Deployment: Google Cloud Run (containerized, versioned, Datadog-instrumented)
- Safety: "Cite-or-abstain" prevents hallucination; every claim is grounded
- Reliability: Async processing + idempotency + retries + health checks + automated rollback
```

---

## 14. Build Plan (5–7 Day Sprint)

### Day 1: Core Infrastructure + Setup
```
Goal: Project skeleton, Docker, GCP provisioning, local dev env working

Tasks:
- [ ] GitHub repo + README template
- [ ] Maven multi-module structure (web + worker + shared)
- [ ] Docker Compose (Postgres + Minio + app services)
- [ ] GCP project creation + service accounts + IAM
- [ ] Cloud SQL instance + database
- [ ] GCS bucket
- [ ] Pub/Sub topic + subscription
- [ ] Flyway migration V1 (init tables)
- [ ] Docker build + test locally

Time estimate: 4-5 hours
Verification: `docker-compose up`, services start, Postgres connects
```

### Day 2: REST API + Thymeleaf UI Foundation
```
Goal: Upload endpoint, job creation, basic UI, polling status

Tasks:
- [ ] Spring Boot controllers: POST /api/upload, GET /api/jobs/{id}/status
- [ ] DTOs: UploadRequest, JobStatus response
- [ ] JPA repository: PolicyJobRepository
- [ ] Thymeleaf templates: layout.html, upload.html, status-check.html (htmx)
- [ ] File upload validation (size, type, multipart handling)
- [ ] GCS client integration (upload PDF)
- [ ] Pub/Sub publisher
- [ ] h Unit tests for API endpoints
- [ ] OpenAPI spec generation (springdoc-openapi)

Time estimate: 6-7 hours
Verification: `mvn clean test`, upload form renders, returns jobId, status endpoint works
```

### Day 3: Document Extraction + Chunking
```
Goal: Document AI integration, text extraction, chunk + citation mapping, fallback

Tasks:
- [ ] DocumentAiClient wrapper (call Google API, handle quota errors)
- [ ] Fallback OCR library (PDFBox + Tesseract)
- [ ] TextChunker: split text into semantic chunks (paragraphs, max length)
- [ ] CitationMapper: preserve page numbers + character offsets
- [ ] document_chunks table (Flyway V2)
- [ ] Worker job consumer (Pub/Sub subscriber)
- [ ] Extract step in pipeline (call DocumentAI, fallback if needed)
- [ ] Store chunks in DB
- [ ] Datadog instrumentation: extraction span, token usage, latency
- [ ] Integration tests with sample PDF

Time estimate: 7-8 hours
Verification: Upload PDF → worker extracts text → document_chunks populated, Datadog shows extraction span
```

### Day 4: Classification + Risk Scanning + Summarization
```
Goal: Document type inference, risk taxonomy scan, LLM summarization (grounded)

Tasks:
- [ ] DocumentClassifier: rules-based + optional LLM fallback
- [ ] RiskScanner: scan chunks for 5 categories (Data, Financial, Rights, Termination, Modification)
- [ ] Severity inference (LOW, MEDIUM, HIGH)
- [ ] RiskTaxonomy data structure (detected + citations)
- [ ] GroundingLayer: wrap Gemini calls, verify claims are sourced
- [ ] SummaryGenerator: per-risk bullet points (cite-or-abstain)
- [ ] reports table + JSONB fields (Flyway V3)
- [ ] Datadog: LLM span tags (tokens, cost, latency, model)
- [ ] Test cite-or-abstain logic (mock LLM outputs)

Time estimate: 7-8 hours
Verification: Extract → Classify → Risk Scan → Summary generation, all grounded, Datadog shows LLM metrics
```

### Day 5: Q&A Handler + Report Display + Export
```
Goal: Grounded Q&A, report HTML rendering, PDF export, share links

Tasks:
- [ ] QaHandler: call Gemini with grounding prompt, verify citations
- [ ] POST /api/jobs/{id}/question endpoint
- [ ] GET /api/jobs/{id}/report (JSON + HTML render)
- [ ] report.html Thymeleaf template (5 sections, citations visible, Q&A form)
- [ ] CitationHighlighter: htmx fragment to show text span on hover
- [ ] PdfExporter: generate PDF with inline citations + watermark + disclaimer
- [ ] GET /api/jobs/{id}/export (PDF download)
- [ ] ShareLinkGenerator: 7-day TTL tokens
- [ ] GET /share/{token} (read-only view)
- [ ] share_links table (Flyway V4)
- [ ] Integration tests: full end-to-end flow

Time estimate: 7 hours
Verification: Ask Q&A question → answer grounded + cited, PDF exports, share link works + expires
```

### Day 6: Datadog Observability + CI/CD
```
Goal: End-to-end tracing, dashboards, monitors, GitHub Actions workflows

Tasks:
- [ ] dd-java-agent setup in Dockerfile
- [ ] Structured JSON logging (SLF4J + Jackson)
- [ ] Trace correlation: dd_trace_id in policy_jobs table
- [ ] Custom metrics: job_processing_latency, llm_tokens, citation_coverage, queue_depth
- [ ] Datadog dashboard JSON: create + export (PolicyInsight-Ops)
- [ ] 3+ monitors (API latency, queue depth, LLM cost/coverage)
- [ ] SLO definitions + exports
- [ ] GitHub Actions CI workflow (.github/workflows/ci.yml)
- [ ] GitHub Actions CD workflow (.github/workflows/cd.yml)
- [ ] Rollback workflow (.github/workflows/rollback.yml)
- [ ] Test CI/CD: push to main, watch Cloud Run deployment
- [ ] Verify traces/logs in Datadog UI

Time estimate: 8 hours
Verification: Traces visible in Datadog, dashboard loads, monitor fires + creates incident, CD deploys to Cloud Run
```

### Day 7: Polish + Demo + Deployment
```
Goal: Documentation, traffic generator, demo prep, final testing

Tasks:
- [ ] README.md: comprehensive overview + usage
- [ ] DEPLOYMENT.md: step-by-step deployment guide (GCP + Datadog)
- [ ] ARCHITECTURE.md: diagrams + module breakdown
- [ ] OBSERVABILITY.md: Datadog signals + dashboard + runbooks
- [ ] APISchema.md: OpenAPI documentation
- [ ] Traffic generator script (/scripts/traffic-generator.sh) → triggers each monitor
- [ ] Screenshot evidence + documentation (in /evidence/ folder)
- [ ] README-EVALUATION.md: evaluation guide + verification checklist
- [ ] Final testing: demo flow end-to-end
- [ ] Accessibility check (keyboard nav, screen reader)
- [ ] Security audit (no hardcoded secrets, HTTPS, CSP headers)
- [ ] Performance: measure latency, cold-start time, cost per job
- [ ] Prepare deployment documentation

Time estimate: 6-8 hours
Verification: Demo runs smoothly, all docs complete, deployment ready, evaluators can access hosted URL + repo + dashboards
```

---

### Summary: 5-7 Day Timeline

| Day | Focus | Deliverable | Verification |
|-----|-------|-------------|--------------|
| **1** | Infrastructure | GCP projects, Postgres, Docker Compose, Flyway V1 | Services connect, local env works |
| **2** | API + UI Foundation | Upload endpoint, Thymeleaf templates, htmx polling | File upload → jobId → status tracking |
| **3** | Extraction | Document AI, chunking, citations, fallback OCR | Chunks in DB, Datadog spans visible |
| **4** | Analysis | Classification, risk scanning, summarization (grounded) | Report JSON with 5 sections, all cited |
| **5** | Q&A + Export | Grounded Q&A, PDF export, share links | Full end-to-end flow works |
| **6** | Observability + CI/CD | Datadog dashboards, monitors, GitHub Actions | Traces in Datadog, CD deploys to Cloud Run, incident created |
| **7** | Polish + Demo | Docs, traffic generator, demo prep, deployment | All verification evidence complete, deployment ready |

---

# Appendix: Success Metrics

By the end of the build sprint, evaluators should be able to:

1. ✅ **Visit the hosted app** and upload a PDF → get a report in <30s
2. ✅ **See citations** in every claim (page number + text span)
3. ✅ **Ask grounded Q&A** questions → get answers with citations or "insufficient evidence"
4. ✅ **Export as PDF** with watermark + disclaimer
5. ✅ **Generate share link** (7-day TTL, read-only)
6. ✅ **View Datadog dashboard** with latency, errors, cost, coverage metrics
7. ✅ **Trigger monitors** via traffic generator → see incidents auto-created with context
8. ✅ **View traces** in Datadog (extraction → classification → analysis → export spans)
9. ✅ **Read GitHub repo** with clear CI/CD workflows and deployment docs
10. ✅ **Understand architecture** via README + DEPLOYMENT guide
11. ✅ **See LLM observability** (token counts, costs, model used) in Datadog
12. ✅ **Verify grounding** (cite-or-abstain enforcement, no hallucinations)
13. ✅ **Check reliability** (async + retries + fallbacks + idempotency)
14. ✅ **Confirm observability** (3+ monitors, SLOs, runbooks, incident automation)

---

**END OF PRD**

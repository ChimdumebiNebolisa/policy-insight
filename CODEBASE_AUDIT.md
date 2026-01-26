# PolicyInsight Codebase Audit

**Audit Date:** 2026-01-03
**Auditor:** Codebase Analysis
**Scope:** Repository audit based on code + configs referenced below

---

## A) Repository Map

### Language/Framework Stack

- **Language:** Java 21 (`pom.xml:22-24`)
- **Framework:** Spring Boot 3.3.0 (`pom.xml:10-12`)
- **Build Tool:** Maven (`pom.xml`, `mvnw`, `mvnw.cmd`)
- **Web Framework:** Spring MVC with Thymeleaf (`pom.xml:57-59`, `application.yml:33-38`)
- **Database:** PostgreSQL 15 (`pom.xml:87-91`, `docker-compose.yml`)
- **Schema Management:** Flyway (`pom.xml:78-85`, `application.yml:27-31`)
- **Observability:** Datadog (APM, metrics, logs) via Micrometer StatsD (`pom.xml:108-111`, `application.yml:82-87`)
- **Cloud:** Google Cloud Platform (Cloud Run, Cloud SQL, GCS, Pub/Sub, Vertex AI)

### Folder Structure

```
policy-insight/
├── src/main/java/com/policyinsight/
│   ├── PolicyInsightApplication.java          # Main entry point
│   ├── api/                                   # REST API controllers
│   │   ├── DocumentController.java           # Upload/status endpoints
│   │   ├── ExportController.java             # PDF export
│   │   ├── QaController.java                 # Q&A endpoints
│   │   ├── ShareController.java              # Share link generation
│   │   ├── PubSubController.java             # Pub/Sub push handler
│   │   ├── messaging/                        # Job publisher (Pub/Sub/local)
│   │   ├── storage/                          # Storage service (GCS/local)
│   │   └── ShareLinkService.java             # Share link management
│   ├── web/                                   # Web controllers (Thymeleaf)
│   │   ├── HomeController.java               # Landing page
│   │   ├── ReportController.java             # Report display
│   │   ├── ShareReportController.java        # Shared report view
│   │   └── ErrorController.java              # Error handling
│   ├── config/                                # Configuration
│   │   ├── WorkerConfig.java                 # Worker bean configuration
│   │   ├── HealthController.java             # Health endpoints
│   │   ├── GlobalExceptionHandler.java       # Exception handling
│   │   └── ...
│   ├── processing/                            # Core processing logic
│   │   ├── LocalDocumentProcessingWorker.java # Local worker (polling)
│   │   ├── DocumentJobProcessor.java          # Processing interface
│   │   ├── GeminiService.java                 # Vertex AI Gemini client
│   │   ├── ReportGenerationService.java       # Report generation
│   │   ├── ReportGroundingValidator.java      # Citation validation
│   │   ├── TextChunkerService.java            # Text chunking
│   │   ├── RiskAnalysisService.java           # Risk analysis
│   │   └── ...
│   ├── shared/                                # Shared models/repositories
│   │   ├── model/                             # JPA entities
│   │   └── repository/                        # Spring Data repositories
│   ├── observability/                         # Datadog integration
│   │   ├── DatadogMetricsService.java         # Metrics service
│   │   ├── TracingService.java                # Tracing service
│   │   └── *Stub.java                         # Stub implementations
│   └── util/                                  # Utilities
├── src/main/resources/
│   ├── application.yml                        # Main configuration
│   ├── application-local.yml                  # Local overrides
│   ├── application-cloudrun.yml               # Cloud Run profile
│   ├── db/migration/                          # Flyway migrations
│   │   └── V1__init.sql                       # Schema definition
│   └── templates/                             # Thymeleaf templates
│       ├── index.html                         # Upload page
│       ├── report.html                        # Report display
│       ├── share-report.html                  # Shared report
│       └── fragments/                         # HTMX fragments
├── src/test/java/                             # Tests
│   └── com/policyinsight/
│       ├── api/
│       │   └── LocalProcessingIntegrationTest.java # Integration test
│       └── ...
├── datadog/                                   # Datadog assets
│   ├── dashboards/                            # Dashboard definitions
│   ├── monitors/                              # Monitor definitions
│   ├── slos/                                  # SLO definitions
│   └── templates/                             # Templates
├── scripts/                                   # Utility scripts
│   ├── datadog/
│   │   └── traffic-generator.py               # Load generator
│   └── ...
├── infra/                                     # Infrastructure configs
│   └── cloudrun/                              # Cloud Run YAMLs
├── pom.xml                                    # Maven dependencies
├── README.md                                  # Main documentation
├── DEPLOYMENT.md                              # Deployment guide
├── docs/
│   ├── SECURITY.md                            # Security guide
│   └── OBSERVABILITY.md                       # Observability docs
└── docker-compose.yml                         # Local PostgreSQL

```

### Runtime Components

1. **Web Application** (`PolicyInsightApplication.java:8-13`)
   - Spring Boot application
   - Serves REST API and Thymeleaf templates
   - Port: 8080 (configurable via `SERVER_PORT`)

2. **Worker Service** (`LocalDocumentProcessingWorker.java:44-45`)
   - Conditional bean: `policyinsight.worker.enabled=true`
   - Polls database for PENDING jobs (`@Scheduled` annotation)
   - Processes jobs in batches

3. **Shared Libraries**
   - Processing services (text extraction, chunking, LLM, report generation)
   - Data access layer (repositories, entities)
   - Observability services (metrics, tracing)

---

## B) Architecture + Data Flow

### End-to-End Data Flow (ASCII Diagram)

```
┌─────────────────────────────────────────────────────────────────┐
│                    CLIENT (Browser)                              │
│  Upload PDF → Poll Status → View Report → Export/Share/Q&A      │
└────────────────────┬────────────────────────────────────────────┘
                     │ HTTP POST /api/documents/upload
                     │ (multipart/form-data)
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│              WEB SERVICE (Spring Boot)                           │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ DocumentController.uploadDocument()                       │  │
│  │  1. Validate file (size ≤50MB, PDF MIME type)            │  │
│  │  2. Generate jobId (UUID)                                │  │
│  │  3. Upload to storage (StorageService.uploadFile)        │  │
│  │  4. Create PolicyJob record (status=PENDING)             │  │
│  │  5. Publish job event (JobPublisher.publishJobQueued)    │  │
│  │  6. Return 202 Accepted with jobId                       │  │
│  └───────────────────────────────────────────────────────────┘  │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     │ StorageService (GCS or Local)
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│              STORAGE (GCS or Local File System)                  │
│  PDF stored at: {bucket}/jobs/{jobId}/document.pdf              │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     │ JobPublisher (Pub/Sub or Noop)
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│        MESSAGING (Pub/Sub Topic or Local Queue)                  │
│  Message: {jobId, storagePath, requestId}                       │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     │ (Async processing)
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│           WORKER (LocalDocumentProcessingWorker)                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ pollAndProcessJobs() [@Scheduled, every 2s]              │  │
│  │  1. Find PENDING jobs (FOR UPDATE SKIP LOCKED)          │  │
│  │  2. Claim job (status=PENDING → PROCESSING)             │  │
│  │  3. processDocument(jobId)                               │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ processDocumentWithSpans()                                │  │
│  │                                                           │  │
│  │  STAGE 1: Text Extraction                                │  │
│  │    ├─ Download PDF from storage                          │  │
│  │    └─ FallbackOcrService.extractText() (PDFBox)          │  │
│  │                                                           │  │
│  │  STAGE 2: Chunking                                        │  │
│  │    ├─ TextChunkerService.chunkText()                     │  │
│  │    └─ Store chunks in document_chunks table              │  │
│  │       (id=auto, job_uuid, chunk_index, text, page_number)│  │
│  │                                                           │  │
│  │  STAGE 3: Classification                                  │  │
│  │    ├─ DocumentClassifierService.classify()               │  │
│  │    ├─ Call GeminiService.generateContent()               │  │
│  │    └─ Update PolicyJob (classification, confidence)      │  │
│  │                                                           │  │
│  │  STAGE 4: Risk Analysis                                   │  │
│  │    ├─ RiskAnalysisService.analyzeRisks()                 │  │
│  │    ├─ Call Gemini for each risk category                 │  │
│  │    └─ Return risk taxonomy JSON                          │  │
│  │                                                           │  │
│  │  STAGE 5: Report Generation                               │  │
│  │    ├─ ReportGenerationService.generateDocumentOverview() │  │
│  │    ├─ ReportGenerationService.generateSummary()          │  │
│  │    ├─ ReportGenerationService.generateObligations...()   │  │
│  │    └─ All call GeminiService.generateContent()           │  │
│  │                                                           │  │
│  │  STAGE 6: Grounding Validation                            │  │
│  │    ├─ ReportGroundingValidator.validateReport()          │  │
│  │    ├─ Validates chunk_ids against stored chunks          │  │
│  │    └─ Replaces invalid citations with "Not detected"     │  │
│  │                                                           │  │
│  │  STAGE 7: Storage & Completion                            │  │
│  │    ├─ Create Report entity (JSONB fields)                │  │
│  │    ├─ Upload report JSON to storage (optional, non-fatal)│  │
│  │    ├─ Save Report to database                            │  │
│  │    └─ Update PolicyJob (status=SUCCESS)                  │  │
│  └───────────────────────────────────────────────────────────┘  │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     │ Database Updates
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│              DATABASE (PostgreSQL)                               │
│  Tables: policy_jobs, document_chunks, reports,                │
│          qa_interactions, share_links                           │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     │ Status Polling (HTMX)
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│              UI (Thymeleaf + HTMX)                               │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ GET /documents/{id}/report                                │  │
│  │   ReportController.viewReport()                           │  │
│  │   - Fetch PolicyJob, Report, DocumentChunk, QaInteraction │  │
│  │   - Render report.html template                           │  │
│  │                                                           │  │
│  │ GET /api/documents/{id}/export/pdf                        │  │
│  │   ExportController.exportPdf()                           │  │
│  │   - PdfExportService.generatePdf()                       │  │
│  │                                                           │  │
│  │ POST /api/documents/{id}/share                            │  │
│  │   ShareController.generateShareLink()                    │  │
│  │   - Create ShareLink (token, expires_at=+7 days)         │  │
│  │                                                           │  │
│  │ POST /api/questions                                       │  │
│  │   QaController.submitQuestion()                          │  │
│  │   - QaService.answerQuestion() (grounded Q&A)            │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Key Classes/Services by Stage

| Stage | Class/Service | Location | Key Methods |
|-------|--------------|----------|-------------|
| Upload | `DocumentController` | `src/main/java/com/policyinsight/api/DocumentController.java:60-192` | `uploadDocument()` |
| Storage | `StorageService` (interface) | `src/main/java/com/policyinsight/api/storage/StorageService.java` | `uploadFile()`, `downloadFile()` |
| Storage (GCS) | `GcsStorageService` | `src/main/java/com/policyinsight/api/storage/GcsStorageService.java` | Implements `StorageService` |
| Storage (Local) | `LocalStorageService` | `src/main/java/com/policyinsight/api/storage/LocalStorageService.java` | Implements `StorageService` |
| Messaging | `JobPublisher` (interface) | `src/main/java/com/policyinsight/api/messaging/JobPublisher.java` | `publishJobQueued()` |
| Messaging (Pub/Sub) | `PubSubService` | `src/main/java/com/policyinsight/api/messaging/PubSubService.java` | Implements `JobPublisher` |
| Messaging (Local) | `NoopJobPublisher` | `src/main/java/com/policyinsight/api/messaging/NoopJobPublisher.java` | No-op implementation |
| Worker | `LocalDocumentProcessingWorker` | `src/main/java/com/policyinsight/processing/LocalDocumentProcessingWorker.java:100-137` | `pollAndProcessJobs()`, `processDocument()` |
| Extraction | `FallbackOcrService` | `src/main/java/com/policyinsight/processing/FallbackOcrService.java` | `extractText()` |
| Chunking | `TextChunkerService` | `src/main/java/com/policyinsight/processing/TextChunkerService.java:33-96` | `chunkText()` |
| Classification | `DocumentClassifierService` | `src/main/java/com/policyinsight/processing/DocumentClassifierService.java` | `classify()` |
| Risk Analysis | `RiskAnalysisService` | `src/main/java/com/policyinsight/processing/RiskAnalysisService.java` | `analyzeRisks()` |
| Report Generation | `ReportGenerationService` | `src/main/java/com/policyinsight/processing/ReportGenerationService.java` | `generateDocumentOverview()`, `generateSummary()`, etc. |
| LLM | `GeminiService` | `src/main/java/com/policyinsight/processing/GeminiService.java:116-255` | `generateContent()` |
| Grounding | `ReportGroundingValidator` | `src/main/java/com/policyinsight/processing/ReportGroundingValidator.java:53-85` | `validateReport()` |
| Report Display | `ReportController` | `src/main/java/com/policyinsight/web/ReportController.java:58-119` | `viewReport()` |
| Export | `ExportController` | `src/main/java/com/policyinsight/api/ExportController.java:55-120` | `exportPdf()` |
| Share | `ShareLinkService` | `src/main/java/com/policyinsight/api/ShareLinkService.java:35-57` | `generateShareLink()`, `validateAndAccessShareLink()` |
| Q&A | `QaService` | `src/main/java/com/policyinsight/processing/QaService.java` | `answerQuestion()` |

---

## C) APIs and UI Behavior

### HTTP Endpoints

#### Verified Endpoints (Annotation Evidence)

| Method | Path | Controller | Handler Method | Class-Level Mapping | Method-Level Mapping |
|--------|------|------------|----------------|---------------------|---------------------|
| GET | `/` | `HomeController` | `index()` | `src/main/java/com/policyinsight/web/HomeController.java:10-11` (no class-level mapping) | `src/main/java/com/policyinsight/web/HomeController.java:13-14` (`@GetMapping("/")`) |
| POST | `/api/documents/upload` | `DocumentController` | `uploadDocument()` | `src/main/java/com/policyinsight/api/DocumentController.java:34` (`@RequestMapping("/api/documents")`) | `src/main/java/com/policyinsight/api/DocumentController.java:60-67` (`@PostMapping("/upload")`) |
| GET | `/api/documents/{id}/status` | `DocumentController` | `getDocumentStatus()` | `src/main/java/com/policyinsight/api/DocumentController.java:34` (`@RequestMapping("/api/documents")`) | `src/main/java/com/policyinsight/api/DocumentController.java:194-201` (`@GetMapping("/{id}/status")`) |
| GET | `/documents/{id}/report` | `ReportController` | `viewReport()` | `src/main/java/com/policyinsight/web/ReportController.java:30-31` (no class-level mapping) | `src/main/java/com/policyinsight/web/ReportController.java:58-59` (`@GetMapping("/documents/{id}/report")`) |
| GET | `/api/documents/{id}/export/pdf` | `ExportController` | `exportPdf()` | `src/main/java/com/policyinsight/api/ExportController.java:33` (`@RequestMapping("/api/documents")`) | `src/main/java/com/policyinsight/api/ExportController.java:55` (`@GetMapping("/{id}/export/pdf")`) |
| POST | `/api/documents/{id}/share` | `ShareController` | `generateShareLink()` | `src/main/java/com/policyinsight/api/ShareController.java:26` (`@RequestMapping("/api/documents")`) | `src/main/java/com/policyinsight/api/ShareController.java:45` (`@PostMapping("/{id}/share")`) |
| GET | `/documents/{id}/share/{token}` | `ShareReportController` | `viewSharedReport()` | `src/main/java/com/policyinsight/web/ShareReportController.java:44-45` (no class-level mapping) | `src/main/java/com/policyinsight/web/ShareReportController.java:51` (`@GetMapping("/documents/{id}/share/{token}")`) |
| POST | `/api/questions` | `QaController` | `submitQuestion()` | `src/main/java/com/policyinsight/api/QaController.java:30` (`@RequestMapping("/api/questions")`) | `src/main/java/com/policyinsight/api/QaController.java:53-61` (`@PostMapping(...)`) |
| GET | `/api/questions/{document_id}` | `QaController` | `getQaInteractions()` | `src/main/java/com/policyinsight/api/QaController.java:30` (`@RequestMapping("/api/questions")`) | `src/main/java/com/policyinsight/api/QaController.java:148-153` (`@GetMapping("/{document_id}")`) |
| POST | `/internal/pubsub` | `PubSubController` | `handlePubSubMessage()` | `src/main/java/com/policyinsight/api/PubSubController.java:27` (`@RequestMapping("/internal")`) | `src/main/java/com/policyinsight/api/PubSubController.java:72-74` (`@PostMapping("/pubsub")`) |
| GET | `/error` | `ErrorController` | `handleError()` | `src/main/java/com/policyinsight/web/ErrorController.java:10-11` (no class-level mapping) | `src/main/java/com/policyinsight/web/ErrorController.java:13-14` (`@RequestMapping("/error")`) |
| GET | `/health` | `HealthController` | `health()` | `src/main/java/com/policyinsight/config/HealthController.java:12-13` (`@RestController`) | `src/main/java/com/policyinsight/config/HealthController.java:21-22` (`@GetMapping("/health")`) |

#### Framework-Provided Endpoints (Unverified)

| Method | Path | Source | Enabling Evidence |
|--------|------|--------|-------------------|
| GET | `/actuator/readiness` | Spring Boot Actuator | `pom.xml:68-69` (spring-boot-starter-actuator dependency), `src/main/resources/application.yml:69-72` (management.endpoints.web.exposure.include includes "readiness") |
| GET | `/swagger-ui.html` | SpringDoc OpenAPI | `pom.xml:95-98` (springdoc-openapi-starter-webmvc-ui dependency), `src/main/resources/application.yml:89-94` (springdoc configuration) |
| GET | `/v3/api-docs` | SpringDoc OpenAPI | `pom.xml:95-98` (springdoc-openapi-starter-webmvc-ui dependency), `src/main/resources/application.yml:89-94` (springdoc configuration) |

### UI Rendering Approach

**Technology:** Thymeleaf (server-side templating) + HTMX (progressive enhancement)

**Evidence:**
- Thymeleaf configuration: `src/main/resources/application.yml:33-38`
- Thymeleaf dependency: `pom.xml:57-59`
- HTMX script: `src/main/resources/templates/index.html:8`
- Templates: `src/main/resources/templates/` (`.html` files)

**Polling Mechanism:** HTMX polling for job status updates

**Evidence:**
- Polling configuration: `src/main/resources/templates/fragments/job-status.html:5-10`
  ```html
  hx-get='/api/documents/' + ${jobId} + '/status'
  hx-trigger="load, every 2s"
  hx-target="#status"
  hx-swap="innerHTML"
  ```
- Upload status polling: `src/main/resources/templates/fragments/upload-started.html:5-8`
- Controller supports HTMX: `src/main/java/com/policyinsight/api/DocumentController.java:69-70,152-155,220-240`

**UI Flow:**
1. User uploads PDF via form (`index.html:19-36`)
2. Form submits via HTMX POST to `/api/documents/upload`
3. Controller returns HTML fragment (`fragments/upload-started.html`)
4. Fragment includes polling div that polls `/api/documents/{id}/status` every 2 seconds
5. When status=SUCCESS, HX-Redirect header triggers redirect to `/documents/{id}/report` (`DocumentController.java:230`)
6. Report page renders with all sections (`report.html`)

---

## D) Persistence Model

### Database Technology

- **DBMS:** PostgreSQL 15 (`pom.xml:87-91`, `docker-compose.yml`)
- **ORM:** Spring Data JPA (`pom.xml:72-74`)
- **Schema Management:** Flyway (`pom.xml:78-85`, `application.yml:27-31`)
- **Connection Pool:** HikariCP (default Spring Boot, configured in `application.yml:10-15`)

### Schema Definition

**Migration File:** `src/main/resources/db/migration/V1__init.sql`

### Core Entities/Tables

#### 1. `policy_jobs`

**Entity:** `src/main/java/com/policyinsight/shared/model/PolicyJob.java`

**Schema:** `src/main/resources/db/migration/V1__init.sql:5-29`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGSERIAL | Primary key |
| `job_uuid` | UUID | Unique job identifier (exposed as "jobId" in API) |
| `status` | VARCHAR(20) | PENDING, PROCESSING, SUCCESS, FAILED |
| `error_message` | TEXT | Error details if status=FAILED |
| `created_at` | TIMESTAMP | Job creation time |
| `updated_at` | TIMESTAMP | Last update time |
| `started_at` | TIMESTAMP | Processing start time |
| `completed_at` | TIMESTAMP | Processing completion time |
| `pdf_gcs_path` | VARCHAR(255) | Storage path to PDF file |
| `pdf_filename` | VARCHAR(255) | Original filename |
| `file_size_bytes` | BIGINT | File size |
| `classification` | VARCHAR(50) | TOS, PRIVACY_POLICY, LEASE |
| `classification_confidence` | DECIMAL(3,2) | Classification confidence (0.00-1.00) |
| `doc_type_detected_page` | INT | Page where type was detected |
| `report_gcs_path` | VARCHAR(255) | Storage path to report JSON (optional) |
| `chunks_json_gcs_path` | VARCHAR(255) | Unused (reserved) |
| `dd_trace_id` | VARCHAR(255) | Datadog trace ID for correlation |

**Indexes:** `idx_uuid` (job_uuid), `idx_status_created` (status, created_at DESC)

#### 2. `document_chunks`

**Entity:** `src/main/java/com/policyinsight/shared/model/DocumentChunk.java`

**Schema:** `src/main/resources/db/migration/V1__init.sql:34-49`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGSERIAL | Primary key (used as chunkId in citations) |
| `job_uuid` | UUID | Foreign key to policy_jobs.job_uuid |
| `chunk_index` | INT | Sequential index within document (0-based) |
| `text` | TEXT | Extracted text content |
| `page_number` | INT | Source page number |
| `start_offset` | INT | Character offset within page |
| `end_offset` | INT | End character offset |
| `span_confidence` | DECIMAL(3,2) | Extraction confidence (0.00-1.00) |
| `created_at` | TIMESTAMP | Creation timestamp |

**Indexes:** `idx_document_chunks_job_uuid` (job_uuid)

**Key Finding:** `chunkId` in citations refers to `document_chunks.id` (BIGSERIAL), NOT a computed hash. Evidence: `ReportGroundingValidator.java:299-304` uses `DocumentChunk::getId` to build valid chunk IDs set.

#### 3. `reports`

**Entity:** `src/main/java/com/policyinsight/shared/model/Report.java`

**Schema:** `src/main/resources/db/migration/V1__init.sql:51-68`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGSERIAL | Primary key |
| `job_uuid` | UUID | Foreign key to policy_jobs.job_uuid (UNIQUE) |
| `document_overview` | JSONB | Overview metadata (classification, etc.) |
| `summary_bullets` | JSONB | Array of summary bullets with chunk_ids and page_refs |
| `obligations` | JSONB | Array of obligations with citations |
| `restrictions` | JSONB | Array of restrictions with citations |
| `termination_triggers` | JSONB | Array of termination triggers with citations |
| `risk_taxonomy` | JSONB | Risk categories (Data_Privacy, Financial, Legal_Rights_Waivers, Termination, Modification) |
| `generated_at` | TIMESTAMP | Report generation time |
| `gcs_path` | VARCHAR(255) | Storage path to report JSON (optional) |

**Indexes:** `idx_reports_job_uuid` (job_uuid)

**JSONB Structure Example (summary_bullets):**
```json
{
  "bullets": [
    {
      "text": "Summary point...",
      "chunk_ids": [1, 5, 12],
      "page_refs": [1, 2, 3]
    }
  ]
}
```

#### 4. `qa_interactions`

**Entity:** `src/main/java/com/policyinsight/shared/model/QaInteraction.java`

**Schema:** `src/main/resources/db/migration/V1__init.sql:70-83`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGSERIAL | Primary key |
| `job_uuid` | UUID | Foreign key to policy_jobs.job_uuid |
| `question` | TEXT | User question |
| `answer` | TEXT | LLM-generated answer |
| `cited_chunks` | JSONB | Array of cited chunks: `[{chunk_id, page_num, text}]` |
| `confidence` | VARCHAR(20) | CONFIDENT or ABSTAINED |
| `created_at` | TIMESTAMP | Interaction timestamp |

**Indexes:** `idx_qa_interactions_job_uuid` (job_uuid)

#### 5. `share_links`

**Entity:** `src/main/java/com/policyinsight/shared/model/ShareLink.java`

**Schema:** `src/main/resources/db/migration/V1__init.sql:85-98`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGSERIAL | Primary key |
| `job_uuid` | UUID | Foreign key to policy_jobs.job_uuid |
| `share_token` | UUID | Unique share token (UNIQUE) |
| `created_at` | TIMESTAMP | Creation time |
| `expires_at` | TIMESTAMP | Expiration time (7 days from creation) |
| `access_count` | INT | Access counter (incremented on view) |

**Indexes:** `idx_token` (share_token), `idx_expires_at` (expires_at)

**TTL Enforcement:** `ShareLink.isExpired()` checks `Instant.now().isAfter(expiresAt)` (`ShareLink.java:111-113`)

### Relationships

- `policy_jobs.job_uuid` ← `document_chunks.job_uuid` (one-to-many)
- `policy_jobs.job_uuid` ← `reports.job_uuid` (one-to-one, UNIQUE)
- `policy_jobs.job_uuid` ← `qa_interactions.job_uuid` (one-to-many)
- `policy_jobs.job_uuid` ← `share_links.job_uuid` (one-to-many)

**Note on Cascade Behavior:** Foreign keys do NOT include `ON DELETE CASCADE`. Evidence:
- `document_chunks.job_uuid` FK: `src/main/resources/db/migration/V1__init.sql:46` (`FOREIGN KEY (job_uuid) REFERENCES policy_jobs(job_uuid)`)
- `reports.job_uuid` FK: `src/main/resources/db/migration/V1__init.sql:64` (`FOREIGN KEY (job_uuid) REFERENCES policy_jobs(job_uuid)`)
- `qa_interactions.job_uuid` FK: `src/main/resources/db/migration/V1__init.sql:80` (`FOREIGN KEY (job_uuid) REFERENCES policy_jobs(job_uuid)`)
- `share_links.job_uuid` FK: `src/main/resources/db/migration/V1__init.sql:94` (`FOREIGN KEY (job_uuid) REFERENCES policy_jobs(job_uuid)`)

JPA cascade (if configured via `@OneToMany(cascade = CascadeType.ALL)`) operates at the application layer when entities are managed, while database-level `ON DELETE CASCADE` operates at the SQL level when rows are deleted directly. The schema does not include database-level cascade deletes, so manual cleanup or application-level deletion is required.

### ChunkId Generation

**Implementation:** `chunkId` = `document_chunks.id` (database-generated BIGSERIAL)

**Evidence:**
- Chunks stored: `LocalDocumentProcessingWorker.java:317-326` creates `DocumentChunk` entities and saves them
- IDs assigned by database: `DocumentChunk.java:21-23` uses `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- Validation uses IDs: `ReportGroundingValidator.java:299-304` builds valid chunk IDs from `DocumentChunk::getId`
- Citations reference IDs: Report JSONB contains `chunk_ids` arrays that reference `document_chunks.id`

**Note:** PRD mentions "Compute chunk_id = hash(document_id + chunk_index)" (`PolicyInsight_Hackathon_PRD.md:644`), but implementation uses database IDs instead. This is more reliable for validation.

---

## D.1) Config/Profile Truth Table

| Configuration Aspect | Local Profile (`application-local.yml`) | Test Profile (`application-test.yml`) | CloudRun Profile (`application-cloudrun.yml`) |
|---------------------|-----------------------------------------|---------------------------------------|------------------------------------------------|
| **Worker Mode** | `policyinsight.worker.enabled=true` (`application-local.yml:23-24`) | Not explicitly set (defaults to `false` per `application.yml:140`) | `POLICYINSIGHT_WORKER_ENABLED=false` (default, `application-cloudrun.yml:49`) |
| **Pub/Sub Mode** | `app.messaging.mode=local` (default, `application.yml:71`) | Not explicitly set (defaults to `local`) | `PUBSUB_ENABLED=true` (default, `application-cloudrun.yml:28`) |
| **Storage Backend** | `app.storage.mode=local` (default, `application.yml:74`) | Not explicitly set (defaults to `local`) | GCS (via `GCS_BUCKET_NAME` env var, `application-cloudrun.yml:24`) |
| **Vertex AI Enabled** | `vertexai.enabled=false` (default, `application.yml:146`) | Not explicitly set (defaults to `false`) | `VERTEX_AI_ENABLED` env var (defaults to `false` per `application.yml:146`) |
| **Stub Triggers** | Stub mode when `vertexai.enabled=false` OR `projectId="local-project"` (`GeminiService.java:67-71`) | Stub mode when `vertexai.enabled=false` (default) | Real mode when `VERTEX_AI_ENABLED=true` AND valid GCP project ID |
| **Polling** | Local worker polls every 2s (`application.yml:65`) when `policyinsight.worker.enabled=true` | No polling (worker disabled) | No polling (worker disabled by default; Pub/Sub push used instead) |

**Evidence:**
- Local config: `src/main/resources/application-local.yml:22-24`
- Test config: `src/test/resources/application-test.yml` (no worker/polling config)
- CloudRun config: `src/main/resources/application-cloudrun.yml:47-51,27-31,23-24`
- Defaults: `src/main/resources/application.yml:62-66,71,74,140,146`
- Stub logic: `src/main/java/com/policyinsight/processing/GeminiService.java:67-71,149-167`

---

## E) Background Processing and Reliability

### Job Queue/Execution Model

**Two Modes:**

1. **Local Mode** (`app.processing.mode=local`)
   - **Implementation:** `LocalDocumentProcessingWorker` (`src/main/java/com/policyinsight/processing/LocalDocumentProcessingWorker.java`)
   - **Mechanism:** Database polling with `@Scheduled` annotation
   - **Poll Interval:** `app.local-worker.poll-ms` (default: 2000ms) (`application.yml:65`)
   - **Batch Size:** `app.local-worker.batch-size` (default: 5) (`application.yml:66`)
   - **Activation:** `policyinsight.worker.enabled=true` (`application.yml:140`)
   - **Query:** `PolicyJobRepository.findOldestPendingJobsForUpdate(batchSize)` uses `FOR UPDATE SKIP LOCKED` for atomic locking

2. **GCP Mode** (`app.processing.mode=gcp`)
   - **Implementation:** Pub/Sub push subscription
   - **Endpoint:** `POST /internal/pubsub` (`PubSubController.java:72`)
   - **Handler:** `PubSubController.handlePubSubMessage()` processes messages
   - **Authentication:** OIDC/JWT token verification (`PubSubTokenVerifier.java`)

**Evidence:**
- Local worker: `LocalDocumentProcessingWorker.java:100-137` (polling logic)
- Pub/Sub handler: `PubSubController.java:72-120`
- Configuration: `application.yml:62-66,140`

### Idempotency Protections

1. **Job Claiming:** Atomic status update from PENDING to PROCESSING
   - **Implementation:** `LocalDocumentProcessingWorker.claimJob()` (`LocalDocumentProcessingWorker.java:145-168`)
   - **Mechanism:** Transactional update with status check (only claim if PENDING)
   - **Database Locking:** `FOR UPDATE SKIP LOCKED` in query prevents race conditions

2. **Duplicate Processing Check:** Workers check status before processing
   - **Evidence:** `LocalDocumentProcessingWorker.java:180-184` checks if job is already SUCCESS/FAILED

3. **Idempotent Storage:** GCS uploads use deterministic paths (`{jobId}/{filename}`)
   - **Path construction:** `src/main/java/com/policyinsight/api/storage/GcsStorageService.java:60` constructs path as `jobId + "/" + filename`
   - **Upload behavior:** `src/main/java/com/policyinsight/api/storage/GcsStorageService.java:69` uses `storage.createFrom()` - overwrite/collision behavior unverified (code does not explicitly set preconditions or handle collisions; relies on GCS default behavior)
   - **Evidence:** `src/main/java/com/policyinsight/api/storage/GcsStorageService.java:58-77`, `src/main/java/com/policyinsight/api/storage/LocalStorageService.java:58-86` (similar pattern for local storage)

### Retry Behavior

**No explicit retry logic found in codebase.**

**Evidence:**
- Worker processes jobs once (`LocalDocumentProcessingWorker.java:125` calls `processDocument()` directly)
- No retry annotations or retry mechanisms in processing code
- Failures result in status=FAILED (`LocalDocumentProcessingWorker.java:231-234`)

**Note:** Pub/Sub provides automatic retries for unacknowledged messages (ack happens on successful completion).

### Failure Modes and Error Surfacing

**Failure Handling:**

1. **Processing Failures:**
   - **Implementation:** `LocalDocumentProcessingWorker.java:229-249`
   - **Behavior:** Exception caught, job status set to FAILED, error message stored
   - **User Visibility:** Status endpoint returns `status=FAILED` with `errorMessage` (`DocumentController.java:232-233,252-253`)

2. **Storage Failures (Report JSON Upload):**
   - **Implementation:** `LocalDocumentProcessingWorker.java:498-525`
   - **Behavior:** Try-catch around GCS upload, logs warning, continues processing (non-fatal)
   - **Evidence:** `logger.warn("Failed to upload report JSON to storage, continuing without storage path: {}", e.getMessage())`

3. **GCS Upload Failure (Non-Fatal):**
   - **Evidence:** `LocalDocumentProcessingWorker.java:520-524` - Report JSON upload failure is caught and logged, but processing continues

**Error Propagation to UI:**
- Status endpoint: `DocumentController.java:194-262` returns status and errorMessage in JSON/HTML
- UI displays error: `fragments/job-status.html:4` shows errorMessage if present

---

## E.1) Reliability & Delivery Semantics

### Pub/Sub Push Delivery Semantics

**Processing Model:** Synchronous processing - job is processed completely before returning HTTP response. Evidence: `src/main/java/com/policyinsight/api/PubSubController.java:199` calls `documentJobProcessor.processDocument(jobId)` directly (no `@Async`, no `CompletableFuture`); `src/main/java/com/policyinsight/api/PubSubController.java:214` returns `HttpStatus.NO_CONTENT` after processing completes.

**HTTP Status Code Handling:**
- **204 No Content:** Returned after successful processing completion (`src/main/java/com/policyinsight/api/PubSubController.java:214`) or when job already processed (`src/main/java/com/policyinsight/api/PubSubController.java:190`)
- **5xx (Internal Server Error):** Returned for processing exceptions (`src/main/java/com/policyinsight/api/PubSubController.java:221,232`). Code comment at `src/main/java/com/policyinsight/api/PubSubController.java:197` states "Pub/Sub will retry if we return 500"
- **4xx (Bad Request, Unauthorized):** Returned for validation/auth failures (`src/main/java/com/policyinsight/api/PubSubController.java:95,104,110,128,137,147,163,171,227`)

### Worker Crash Mid-Job Handling

**Stuck PROCESSING Status:**
- **Problem:** If a worker crashes while processing a job, the job remains in `PROCESSING` status indefinitely
- **Current Behavior:** No automatic recovery mechanism found
- **Local Worker:** Jobs are only claimed if status is `PENDING` (`PolicyJobRepository.updateStatusIfPending()` at `PubSubController.java:184`, `LocalDocumentProcessingWorker.java:145-168`)
- **Pub/Sub Worker:** Idempotency check prevents reprocessing (`PubSubController.java:184-191`), but does not recover stuck jobs

**Evidence:**
- Atomic claim: `PolicyJobRepository.updateStatusIfPending()` only updates if status is `PENDING` (`PolicyJobRepository.java:69-73`)
- No timeout mechanism: No `started_at` timeout check found in codebase
- No cleanup job: No scheduled task found to reset stale `PROCESSING` jobs

**Recovery Gap:** Jobs stuck in `PROCESSING` status (e.g., from worker crash) will never be reprocessed without manual intervention (database update or job status reset).

### Rerun Duplicate Prevention

**Chunks:**
- **Storage:** Chunks are saved via `documentChunkRepository.save()` without deletion of existing chunks (`LocalDocumentProcessingWorker.java:317-326`)
- **No Unique Constraint:** No unique constraint on `(job_uuid, chunk_index)` in schema (`V1__init.sql:34-49`)
- **Behavior:** Reruns would create duplicate chunks (same job_uuid, different IDs)

**Reports:**
- **Unique Constraint:** `reports.job_uuid` has `UNIQUE` constraint (`V1__init.sql:65`, `Report.java:27`)
- **Storage:** Uses `reportRepository.save()` (`LocalDocumentProcessingWorker.java:527`)
- **Behavior:** Reruns would fail with unique constraint violation OR overwrite existing report (if JPA uses save/merge)

**Evidence:**
- Report unique constraint: `V1__init.sql:65`, `Report.java:27-29`
- No chunk unique constraint: `V1__init.sql:34-49` (no unique constraint on job_uuid + chunk_index)
- No deletion before save: No `deleteByJobUuid()` calls found before chunk/report creation in `LocalDocumentProcessingWorker.java:317-326,527`

**Conclusion:** Reruns would create duplicate chunks but would fail or overwrite reports due to the unique constraint. This is not fully idempotent for chunks.

---

## F) LLM Integration + Grounding

### Vertex AI Gemini Client

**SDK Used:** Google GenAI SDK (`com.google.genai:google-genai:1.32.0`)

**Evidence:**
- Dependency: `pom.xml:161-165`
- Import: `GeminiService.java:5-7`
- Client initialization: `GeminiService.java:74-90`
- Client usage: `GeminiService.java:177` (`client.models.generateContent(model, prompt, null)`)

**Configuration:**
- **Enabled Flag:** `vertexai.enabled` (`application.yml:146`, `GeminiService.java:49`)
- **Project ID:** `vertexai.project-id` (defaults to `GOOGLE_CLOUD_PROJECT`) (`application.yml:147`)
- **Location:** `vertexai.location` (default: `us-central1`) (`application.yml:148`)
- **Model:** `vertexai.model` (default: `gemini-2.0-flash-exp`) (`application.yml:149`)

**Evidence:**
- Configuration: `src/main/resources/application.yml:145-149`
- Constructor: `GeminiService.java:48-72`

### Prompt Construction

**Multiple Services Generate Prompts:**

1. **Document Classification:** `DocumentClassifierService.classify()` (implementation not reviewed, but calls `GeminiService`)
2. **Risk Analysis:** `RiskAnalysisService.analyzeRisks()` (calls Gemini for each risk category)
3. **Report Generation:** `ReportGenerationService.generateDocumentOverview()`, `generateSummary()`, `generateObligationsAndRestrictions()` (each calls Gemini)
4. **Q&A:** `QaService.answerQuestion()` (calls Gemini with chunks as context)

**Generic Call Pattern:** All services call `GeminiService.generateContent(prompt, timeoutSeconds, taskType)`

**Evidence:**
- GeminiService interface: `GeminiService.java:116-255`
- Usage examples: Called by multiple services (see processing package)

### Model Selection and Response Parsing

**Model:** Configurable via `vertexai.model` (default: `gemini-2.0-flash-exp`)

**Response Parsing:**
- **Text Response:** `GeminiService.java:180` (`response.text()`)
- **JSON Parsing:** `GeminiService.parseJsonResponse()` (`GeminiService.java:265-285`) handles markdown code blocks

**Evidence:**
- Model selection: `GeminiService.java:52,58`
- Response handling: `GeminiService.java:174-219`
- JSON parsing: `GeminiService.java:265-285`

### Grounding: ChunkId Validation and Citations

**Implementation:** `ReportGroundingValidator` (`src/main/java/com/policyinsight/processing/ReportGroundingValidator.java`)

**Validation Process:**

1. **Build Valid Chunk IDs Set:** `ReportGroundingValidator.java:299-304`
   - Extracts IDs from stored `DocumentChunk` entities
   - Creates `Set<Long>` of valid chunk IDs

2. **Validate Report Sections:** `ReportGroundingValidator.java:53-85`
   - Validates `summary_bullets`, `obligations`, `restrictions`, `termination_triggers`, `risk_taxonomy`
   - For each item, extracts `chunk_ids` array
   - Checks if all chunk IDs exist in valid set (`ReportGroundingValidator.java:292-294`)

3. **Abstain on Invalid Citations:** `ReportGroundingValidator.java:107-111,178-186`
   - If `chunk_ids` is empty or contains invalid IDs, replaces text with "Not detected / Not stated"
   - Clears `chunk_ids` and `page_refs` arrays
   - Adds violation to validation result

4. **Page References:** `ReportGroundingValidator.java:114-120,189-195`
   - Maps chunk IDs to page numbers from stored chunks
   - Populates `page_refs` array

**Evidence:**
- Validation entry point: `ReportGroundingValidator.java:53-85`
- Chunk ID validation: `ReportGroundingValidator.java:292-294`
- Abstain logic: `ReportGroundingValidator.java:107-111`
- Usage: `LocalDocumentProcessingWorker.java:448-461`

**Citation Attachment:**
- LLM prompts instruct model to include `chunk_ids` in JSON responses
- Validation ensures all citations are valid
- Invalid citations are replaced with abstain statements

**Q&A Grounding:**
- `QaService.answerQuestion()` (implementation not fully reviewed, but referenced in `QaController.java:103`)
- Returns `confidence` field: CONFIDENT or ABSTAINED
- Evidence: `QaController.java:110`, `QaInteraction.java` entity has `confidence` field

### Stub/Demo Mode

**Stub Mode Activation:**

**Condition:** `vertexai.enabled=false` OR `projectId="local-project"`

**Evidence:**
- Check: `GeminiService.java:67-71`
- Stub method: `GeminiService.generateStubResponse()` (`GeminiService.java:287-318`)
- Stub usage: `GeminiService.java:149-167`

**Stub Behavior:**
- Returns deterministic JSON responses based on prompt keywords
- Examples:
  - Classification: `{"type": "TOS", "confidence_score": 0.85}`
  - Risk analysis: `{"detected": false, "items": []}`
  - Summary: `{"bullets": [{"text": "...", "chunk_ids": [1]}]}`

**Ensuring Real Mode:**
1. Set `VERTEX_AI_ENABLED=true` environment variable
2. Set `GOOGLE_CLOUD_PROJECT` to real GCP project ID (not "local-project")
3. Ensure Google Application Default Credentials (ADC) are configured
4. Verify: Logs should show "Google Gen AI SDK client initialized successfully" (not "stub mode")

**Evidence:**
- Stub check: `GeminiService.java:67-71,149-167`
- Real mode initialization: `GeminiService.java:74-90`
- Configuration: `application.yml:146-149`, `application-cloudrun.yml`

**Other Stub Implementations:**
- `DatadogMetricsServiceStub` and `TracingServiceStub` when Datadog is disabled

### F.2) Grounding Limitations

**The grounding validation system has two distinct responsibilities:**

1. **Preventing Fake Chunk IDs** (Implemented)
   - **What it does:** Ensures that all `chunk_ids` referenced in report JSONB fields exist in the `document_chunks` table
   - **Implementation:** `ReportGroundingValidator.areChunkIdsValid()` checks `chunkIds.stream().allMatch(validChunkIds::contains)` (`ReportGroundingValidator.java:292-294`)
   - **Evidence:** `ReportGroundingValidator.java:53-85` validates all report sections, `ReportGroundingValidator.java:107-111,178-186` replaces invalid citations with "Not detected / Not stated"
   - **Limitation:** Only validates existence of chunk IDs, not semantic correctness

2. **Verifying Semantic Support** (NOT Implemented)
   - **What it does NOT do:** Does not verify that the text content of a report claim is actually supported by the text content of the cited chunks
   - **Example Gap:** A report bullet could claim "Users must pay $100/month" and cite chunk ID 5, but chunk 5 might only say "Subscription fees apply." The validator would pass (chunk ID 5 exists), but the claim is not semantically grounded
   - **Current Behavior:** The validator only checks that chunk IDs exist, not that the LLM's interpretation of the chunk text is accurate
   - **Evidence:** `ReportGroundingValidator.java:292-294` only checks set membership, no text comparison or semantic validation found

**Conclusion:** The grounding validator prevents hallucinated chunk IDs (citations to non-existent chunks) but does not prevent semantic hallucinations (claims that are not supported by the actual text in the cited chunks). This is a limitation of the current implementation.

---

## G) Tests

### Test Types

1. **Integration Tests**
   - **File:** `src/test/java/com/policyinsight/api/LocalProcessingIntegrationTest.java`
   - **Framework:** JUnit 5, Spring Boot Test, Testcontainers
   - **Database:** Testcontainers PostgreSQL (`LocalProcessingIntegrationTest.java:53-58`)
   - **Profile:** `@ActiveProfiles("test")`

2. **Unit Tests** (limited)
   - `WorkerConfigTest.java`
   - `LocalDocumentProcessingWorkerTest.java`
   - `PolicyJobRepositoryTest.java`
   - `PubSubControllerTest.java`
   - `PubSubControllerContractTest.java`

### Testcontainers Usage

**Evidence:**
- Dependency: `pom.xml:221-230`
- Container definition: `LocalProcessingIntegrationTest.java:53-58`
- Dynamic properties: `LocalProcessingIntegrationTest.java:60-70`

**Configuration:**
- Image: `postgres:15-alpine`
- Database: `policyinsight_test`
- Credentials: `postgres/postgres`

### PDF Fixtures

**Test PDF Factory:** `src/test/java/com/policyinsight/TestPdfFactory.java`

**Usage:** `LocalProcessingIntegrationTest.java:134` generates PDF bytes for testing

**Evidence:**
- Factory: `TestPdfFactory.minimalPdfBytes()` called in integration test
- Test PDF: Contains sentinel text for verification (`LocalProcessingIntegrationTest.java:127-134`)

### Test Assertions

**Integration Test Assertions** (`LocalProcessingIntegrationTest.java:123-180`):

1. **Valid PDF Processing:**
   - Job reaches SUCCESS status
   - Chunks are created (`chunkCount > 0`)
   - Extracted text contains sentinel text
   - Job has `started_at` and `completed_at` timestamps

2. **Invalid PDF Processing:**
   - Job reaches FAILED status
   - Error message is non-empty
   - `completed_at` is set

3. **Export/Share Endpoints:**
   - Return 409 Conflict when job is not SUCCESS
   - Return 404 when job doesn't exist
   - Return 400 for invalid UUID format

**Evidence:**
- Test methods: `LocalProcessingIntegrationTest.java:123-346`
- Assertions: Uses AssertJ (`assertThat()`)

**Test Coverage:** Limited - only one integration test covers the full processing pipeline. Unit tests are minimal.

---

## H) Observability + Deployment

### Logging/Metrics/Tracing Approach

**Logging:**
- **Framework:** SLF4J + Logback (`pom.xml:102-105`)
- **Format:** JSON logging via `logstash-logback-encoder` (`pom.xml:102-105`)
- **Correlation IDs:** MDC (Mapped Diagnostic Context) for `job_id` and `request_id`
- **Evidence:** `DocumentController.java:116-118,198-199`, `LocalDocumentProcessingWorker.java:198-200`

**Metrics:**
- **Framework:** Micrometer (`pom.xml:108-111`)
- **Exporter:** StatsD (Datadog flavor) (`application.yml:82-87`)
- **Custom Metrics Service:** `DatadogMetricsService` (`src/main/java/com/policyinsight/observability/DatadogMetricsService.java`)
- **Metrics Tracked:**
  - **LLM latency:** Tracked (verified) - `GeminiService.java:198` calls `metricsService.recordLlmLatency()`
  - **LLM token/cost tracking:** Estimated only (not extracted from API) - `GeminiService.java:191-194` estimates tokens as `prompt.length() / 4` and `responseText.length() / 4`; cost computed from estimates (`GeminiService.java:193-194`). Note: Google Gen AI SDK does not expose usage metadata (`GeminiService.java:189` comment). Metrics emission verified: `GeminiService.java:199-201` calls `recordLlmCost()` and `recordLlmTokens()` with estimated values
  - **Job duration, success/failure counts:** Verified - `LocalDocumentProcessingWorker.java:219-222,237-240`
- **Evidence:** `GeminiService.java:186-201`, `LocalDocumentProcessingWorker.java:219-222,237-240`, `DatadogMetricsService.java:118-131,207-241`

**Tracing:**
- **Framework:** OpenTelemetry API (`pom.xml:114-123`)
- **Custom Spans:** `TracingService` (`src/main/java/com/policyinsight/observability/TracingService.java`)
- **Spans Created:** `upload`, `job.process`, `extraction`, `classification`, `risk_scan`, `llm.call`, `export`
- **Evidence:** `DocumentController.java:73-81`, `LocalDocumentProcessingWorker.java:205-213,273-279,337-344,367-374,407-415,464-471`

### Datadog Assets

**Dashboards:**
- `datadog/dashboards/policyinsight-ops.json:L1-L5`
- Template: `datadog/templates/dashboards/policyinsight-ops.json` (exact line range unverified)

**Monitors:**
- Application monitors: `datadog/monitors/policyinsight-api-latency-p95--2000ms.json:L1-L5`, `datadog/monitors/policyinsight-llm-cost-anomaly.json:L1-L5`, `datadog/monitors/policyinsight-queue-backlog--50-jobs.json:L1-L5`
- Examples of system monitors: `datadog/monitors/cpu-usage-is-high-for-host-hostname.json`, `datadog/monitors/memory-space-is-low-for-host-hostname.json`, `datadog/monitors/disk-usage-is-high-for-host-hostname-on-device-devicename.json`
- Examples of error monitors: `datadog/monitors/operationservletrequest-high-error-rate-on-servicename.json`, `datadog/monitors/operationspringhandler-high-error-rate-on-servicename.json`

**SLOs:**
- `datadog/slos/policyinsight-api-availability-slo.json:L1-L5`
- `datadog/slos/policyinsight-api-latency-slo.json:L1-L5`

### Load/Traffic Generator Scripts

**Script:** `scripts/datadog/traffic-generator.py`

**Scenarios:**
1. Latency spike (concurrent requests)
2. Job backlog (many uploads)
3. LLM cost spike (Q&A requests)

**Evidence:**
- Script: `scripts/datadog/traffic-generator.py:1-206`
- Usage: Command-line arguments for scenario and duration

### Deployment Targets/Config

**Platform:** Google Cloud Run

**Configuration Files:**
- `infra/cloudrun/web.yaml` - Web service configuration
- `infra/cloudrun/worker.yaml` - Worker service configuration
- `DEPLOYMENT.md` - Deployment guide

**Services:**
1. **Web Service** (`policyinsight-web`)
   - Handles HTTP requests (upload, status, report, export, share, Q&A)
   - Ingress: `all` (public)
   - Worker disabled: `POLICYINSIGHT_WORKER_ENABLED=false`

2. **Worker Service** (`policyinsight-worker`)
   - Processes jobs (Pub/Sub push or polling)
   - Ingress: `internal` (authenticated only)
   - Worker enabled: `POLICYINSIGHT_WORKER_ENABLED=true`

**Dependencies:**
- **Cloud SQL:** PostgreSQL database
- **GCS:** Document and report storage
- **Pub/Sub:** Job queue (optional, for GCP mode)
- **Vertex AI:** LLM API
- **Artifact Registry:** Docker image storage

**Evidence:**
- Deployment guide: `DEPLOYMENT.md:1-583`
- Cloud Run configs: `infra/cloudrun/*.yaml`
- Configuration: `application-cloudrun.yml`

**CI/CD:**
- GitHub Actions workflows (`.github/workflows/`)
- Deployment: `.github/workflows/cd.yml` (referenced in `DEPLOYMENT.md:394`)

---

## I) Security Review

### Authentication/Authorization Model

**Current State:** **No authentication/authorization implemented**

**Evidence:**
- No Spring Security dependency in `pom.xml`
- No `@PreAuthorize` or security annotations in controllers
- All endpoints are publicly accessible (except Pub/Sub push endpoint uses OIDC token verification)

**Access Model:**
- **Upload:** Anyone can upload documents
- **View Reports:** Anyone with jobId can view reports (UUID is unguessable but not secret)
- **Export/Share:** Anyone with jobId can export or generate share links
- **Q&A:** Anyone with jobId can ask questions (limited to 3 per document)

**Pub/Sub Push Authentication:**
- **Implementation:** `PubSubTokenVerifier` (`src/main/java/com/policyinsight/api/PubSubTokenVerifier.java`)
- **Mechanism:** OIDC/JWT token verification using Google's `GoogleIdTokenVerifier`
- **Evidence:** `PubSubController.java:85-94`

### Share Links: Token Format, TTL, Revocation, Access Checks

**Token Format:** UUID (`ShareLink.java:29-31`)

**TTL Enforcement:**
- **Default TTL:** 7 days (`ShareLink.java:54-55`)
- **Expiration Check:** `ShareLink.isExpired()` compares `Instant.now()` with `expiresAt` (`ShareLink.java:111-113`)
- **Validation:** `ShareLinkService.validateAndAccessShareLink()` checks expiration (`ShareLinkService.java:78-82`)
- **Evidence:** `ShareReportController.java:71-76`

**Revocation:**
- **Not Implemented:** No explicit revocation mechanism
- **Workaround:** Delete `ShareLink` record from database (manual)
- **Evidence:** No revocation method in `ShareLinkService`

**Access Checks:**
- Token validation: `ShareLinkService.validateAndAccessShareLink()` (`ShareLinkService.java:67-91`)
- Job UUID matching: `ShareReportController.java:79-84` verifies token's `job_uuid` matches URL parameter
- Expiration check: Performed in validation
- Access counting: `ShareLink.incrementAccessCount()` (`ShareLinkService.java:85-86`)

**Evidence:**
- Share link creation: `ShareLinkService.java:35-57`
- Validation: `ShareLinkService.java:67-91`
- Controller: `ShareReportController.java:51-130`

### Secrets Handling

**Configuration:** All secrets use environment variables

**Evidence:**
- Database credentials: `src/main/resources/application.yml:6-8` uses `${DB_USER}`, `${DB_PASSWORD}`
- Vertex AI project ID: `src/main/resources/application.yml:147` uses `${GOOGLE_CLOUD_PROJECT}`
- Cloud Run config: `src/main/resources/application-cloudrun.yml:7-9,24,29` uses `${DB_USER}`, `${DB_PASSWORD}`, `${GCS_BUCKET_NAME}`, `${PUBSUB_ENABLED}`
- GCP credentials: Uses Application Default Credentials (ADC), no hardcoded keys
- Datadog: `DD_API_KEY`, `DD_APP_KEY` (not in code)
- Security guide: `docs/SECURITY.md:64-71` documents env var usage

**Secret Scanning:**
- **Env var placeholders used:** Configuration files use `${VAR}` syntax. Evidence: `src/main/resources/application.yml:6-8,147`, `src/main/resources/application-cloudrun.yml:7-9,24,29`
- **gitleaks scripts/hooks exist:** Pre-commit hooks and scripts exist. Evidence: `docs/SECURITY.md:128-167` (gitleaks documentation), `scripts/pre-commit-secret-scan.ps1:1-30` (gitleaks invocation at line 23), `scripts/pre-commit-secret-scan.sh:1-30` (gitleaks invocation at line 21)
- **No committed scan report found:** No repo-wide scan output/report file found in repository

### File Upload Risks

**Size Limits:**
- **Configuration:** `application.yml:42-44`
- **Max File Size:** 50 MB (`spring.servlet.multipart.max-file-size: 50MB`)
- **Max Request Size:** 50 MB (`spring.servlet.multipart.max-request-size: 50MB`)
- **Validation:** `DocumentController.java:94-98` enforces limit

**Content-Type Checks:**
- **Validation:** `DocumentController.java:100-105`
- **Required Type:** `application/pdf` only
- **Evidence:** `DocumentController.java:42,101-105`

**PDF Parsing Safety:**
- **Library:** Apache PDFBox 3.0.1 (`pom.xml:175-178`)
- **Extraction:** PDFBox text extraction in the worker pipeline (`LocalDocumentProcessingWorker.java`)
- **Error Handling:** Invalid PDFs result in FAILED status with error message (`LocalDocumentProcessingWorker.java:229-234`)

**Virus Scanning:** Not implemented (mentioned as optional in PRD, not found in code)

---

## J) "Truth Table" Verification

| Claim | Verdict | Evidence | Notes |
|-------|---------|----------|-------|
| 1. System ingests PDFs, extracts text into stored "chunks", generates multi-section risk/report using Vertex AI Gemini, renders cited report in UI | **Verified** | `DocumentController.java:60-192` (upload), `LocalDocumentProcessingWorker.java:258-542` (processing), `ReportController.java:58-119` (render), `GeminiService.java:116-255` (LLM calls) | Full pipeline implemented |
| 2. Worker/background pipeline generates multiple report sections, persists results, optionally uploads report JSON to GCS; GCS upload failure is non-fatal | **Verified** | `LocalDocumentProcessingWorker.java:421-525` (report generation), `LocalDocumentProcessingWorker.java:498-525` (GCS upload with try-catch, continues on failure) | Non-fatal GCS upload confirmed |
| 3. Grounding logic validates cited chunkIds against stored chunks; if invalid/empty, abstains rather than hallucinating | **Verified** | `ReportGroundingValidator.java:53-85` (validation), `ReportGroundingValidator.java:107-111,178-186` (abstain logic), `LocalDocumentProcessingWorker.java:448-461` (usage) | Abstain mechanism confirmed |
| 4. Vertex AI Gemini integration exists via Google GenAI SDK (`generateContent` or equivalent) | **Verified** | `pom.xml:161-165` (dependency), `GeminiService.java:5-7,74-90,177` (client usage: `client.models.generateContent()`) | Uses Google GenAI SDK, not deprecated Vertex AI SDK |
| 5. Integration tests using Testcontainers and deterministic PDF fixtures | **Verified** | `pom.xml:221-230` (Testcontainers), `LocalProcessingIntegrationTest.java:53-58` (container), `TestPdfFactory.java` (fixtures), `LocalProcessingIntegrationTest.java:134` (usage) | Testcontainers and fixtures present |
| 6. Datadog dashboards/monitors/SLOs and/or load/traffic generator scripts committed | **Verified** | `datadog/dashboards/policyinsight-ops.json`, `datadog/monitors/*.json` (14 monitors), `datadog/slos/*.json` (2 SLOs), `scripts/datadog/traffic-generator.py` | All assets present |
| 7. PDF export and shareable read-only links with TTL (e.g., 7 days) | **Verified** | `ExportController.java:55-120` (PDF export), `ShareController.java:45-87` (share generation), `ShareLink.java:54-55,111-113` (7-day TTL, expiration check) | 7-day TTL confirmed |
| 8. README/status may be stale vs actual features | **Partially Verified** | `README.md:176` says "Current Status: Milestone 1", but codebase has full implementation (upload, processing, reports, export, share, Q&A) | README is outdated |
| 9. "Stub/demo mode" paths that can silently activate based on config/profile/projectId | **Verified** | `GeminiService.java:67-71,149-167` (stub mode when `vertexai.enabled=false` or `projectId="local-project"`), `GeminiService.java:287-318` (stub responses) | Stub mode exists, activates when disabled |
| 10. UI may use polling (e.g., HTMX) to refresh job status | **Verified** | `src/main/resources/templates/fragments/job-status.html:5-10` (HTMX polling: `hx-trigger="load, every 2s"`), `DocumentController.java:69-70,220-240` (HTMX request handling) | HTMX polling every 2 seconds confirmed |

---

## K) Prioritized Fixes

### 1. **Add Authentication/Authorization** (Critical Security Risk)

**Why it matters:** Currently, anyone with a jobId can view, export, or share any report. UUIDs are unguessable but not secret (may be leaked via logs, URLs, etc.). No protection against unauthorized access.

**File targets:**
- Add Spring Security dependency to `pom.xml`
- Create `SecurityConfig.java` in `src/main/java/com/policyinsight/config/`
- Add `@PreAuthorize` annotations to controllers (or method-level security)
- Update `DocumentController`, `ReportController`, `ExportController`, `ShareController`, `QaController`

**Suggested approach:** Implement session-based authentication with optional OAuth2/OIDC for production. For MVP, add basic session management with user registration/login. Alternatively, use API keys or JWT tokens for API access.

---

### 2. **Add Retry Logic for Job Processing** (Reliability)

**Why it matters:** Transient failures (network issues, temporary API errors) cause jobs to fail permanently. No retry mechanism means users must re-upload documents.

**File targets:**
- `src/main/java/com/policyinsight/processing/LocalDocumentProcessingWorker.java:229-249` (error handling)
- Consider using Spring Retry (`@Retryable`) or manual retry loops

**Suggested approach:** Implement exponential backoff retry (max 3 retries) for transient errors. Distinguish between retryable errors (network timeouts, rate limits) and non-retryable errors (invalid PDF, authentication failures). Update job status only after final failure.

---

### 3. **Implement Share Link Revocation** (Feature Completeness)

**Why it matters:** Once a share link is generated, it cannot be revoked until expiration. If a link is accidentally shared or compromised, there's no way to revoke access.

**File targets:**
- `src/main/java/com/policyinsight/api/ShareLinkService.java` (add `revokeShareLink()` method)
- `src/main/java/com/policyinsight/api/ShareController.java` (add revocation endpoint)
- `src/main/java/com/policyinsight/shared/model/ShareLink.java` (add `revoked` boolean field, or use `expires_at` manipulation)

**Suggested approach:** Add `revoked_at` TIMESTAMP column to `share_links` table. Create migration `V2__add_share_link_revocation.sql`. Update `validateAndAccessShareLink()` to check revocation status. Add `POST /api/documents/{id}/share/{token}/revoke` endpoint.

---

### 4. **Update README to Reflect Current State** (Documentation)

**Why it matters:** README says "Milestone 1" but codebase has full implementation. Misleading for new developers or judges evaluating the project.

**File targets:**
- `README.md:176` (update status)
- Add sections for export, share, Q&A features
- Update API documentation to match actual endpoints

**Suggested approach:** Update status to "Production-ready" or "MVP Complete". List all implemented features. Add links to deployment guide, security guide, observability docs.

---

### 5. **Add Input Validation for Q&A Questions** (Security/Reliability)

**Why it matters:** Q&A endpoint accepts questions without length/sanitization checks (except 500-char limit mentioned in comments). Could allow abuse or injection attacks.

**File targets:**
- `src/main/java/com/policyinsight/api/QaController.java:53-142` (add validation)
- `src/main/java/com/policyinsight/shared/dto/QuestionRequest.java` (add validation annotations)

**Suggested approach:** Add `@Size(max=500)` validation on question field. Sanitize HTML/script tags. Add rate limiting per IP/jobId to prevent abuse.

---

### 6. **Implement Database Connection Retry on Startup** (Reliability)

**Why it matters:** If database is temporarily unavailable at startup, application fails to start. No retry mechanism means manual intervention required.

**File targets:**
- `src/main/resources/application.yml:10-15` (HikariCP configuration)
- Consider Spring Boot's `spring.datasource.hikari.initialization-fail-timeout`

**Suggested approach:** Configure HikariCP connection retry properties. Add health check retry logic. Use Spring Boot's `@Retryable` on database initialization if needed.

---

### 7. **Add Metrics for Share Link Usage** (Observability)

**Why it matters:** No metrics track share link generation, access counts, or expiration. Cannot monitor usage patterns or detect abuse.

**File targets:**
- `src/main/java/com/policyinsight/api/ShareLinkService.java:35-57,67-91`
- `src/main/java/com/policyinsight/observability/DatadogMetricsService.java` (add metrics methods)

**Suggested approach:** Add counters for `share_link.generated`, `share_link.accessed`, `share_link.expired`, `share_link.revoked`. Track access count distribution.

---

### 8. **Implement Job Cleanup/Archival** (Operational)

**Why it matters:** Jobs and reports accumulate indefinitely. No automatic cleanup means database and storage grow unbounded. PRD mentions 30-day retention but not implemented.

**File targets:**
- Create `JobCleanupService.java` in `src/main/java/com/policyinsight/processing/`
- Add `@Scheduled` method to delete old jobs
- `src/main/resources/application.yml` (add cleanup configuration)

**Suggested approach:** Add scheduled task (daily) to delete jobs older than 30 days (or configurable retention period). Explicitly delete child records via repository methods since database-level `ON DELETE CASCADE` is not present in schema (`src/main/resources/db/migration/V1__init.sql:46,64,80,94`). Repository methods available: `DocumentChunkRepository.deleteByJobUuid()` (`src/main/java/com/policyinsight/shared/repository/DocumentChunkRepository.java:44`), `ReportRepository.deleteByJobUuid()` (`src/main/java/com/policyinsight/shared/repository/ReportRepository.java:34`). Note: `QaInteractionRepository` and `ShareLinkRepository` do not have `deleteByJobUuid()` methods (verified by grep search). Alternatively, add database-level `ON DELETE CASCADE` via a new migration (e.g., `V2__add_cascade_deletes.sql`). Archive to cold storage before deletion if needed.

---

### 9. **Add Comprehensive Error Messages for User-Facing Failures** (UX)

**Why it matters:** Some error messages are generic ("Failed to process document"). Users cannot understand what went wrong or how to fix it.

**File targets:**
- `src/main/java/com/policyinsight/processing/LocalDocumentProcessingWorker.java:231-234` (error message storage)
- `src/main/java/com/policyinsight/config/GlobalExceptionHandler.java` (exception handling)

**Suggested approach:** Map exceptions to user-friendly messages. Include error codes for support. Log detailed errors internally, return sanitized messages to users. Add error code enum for consistency.

---

### 10. **Add Integration Tests for Export, Share, and Q&A Endpoints** (Test Coverage)

**Why it matters:** Only one integration test covers the full pipeline. Export, share, and Q&A endpoints are not tested end-to-end. Risk of regressions.

**File targets:**
- `src/test/java/com/policyinsight/api/LocalProcessingIntegrationTest.java` (add test methods)
- Or create separate test classes: `ExportIntegrationTest.java`, `ShareIntegrationTest.java`, `QaIntegrationTest.java`

**Suggested approach:** Add integration tests that:
- Upload PDF, wait for SUCCESS, then test export endpoint
- Generate share link, validate token, access shared report, verify expiration
- Submit Q&A questions, verify answers and citations, test 3-question limit

---

## Summary

**Overall Assessment:** The codebase is **MVP/demo-ready** with a complete implementation of the document analysis pipeline. Key strengths include grounding validation, non-fatal GCS upload handling, comprehensive observability, and proper use of environment variables for secrets.

**Production Blockers:**
1. **No authentication/authorization** - All endpoints are publicly accessible. Evidence: No Spring Security dependency in `pom.xml` (searched lines 1-251); no `@EnableWebSecurity`, `@PreAuthorize`, or `SecurityConfig` class found (grep search of `src/main/java` returned no matches)
2. **No retry logic for transient failures** - Jobs fail permanently on transient errors. Evidence: `LocalDocumentProcessingWorker.java:229-234` sets status to FAILED with no retry loop; no Spring Retry dependency in `pom.xml` (searched lines 1-251)
3. **No stuck-job recovery** - Jobs stuck in PROCESSING status from worker crashes are not automatically recovered. Evidence: Status transitions to PROCESSING in `LocalDocumentProcessingWorker.java:145-168` (claimJob); no scheduled "reset stale PROCESSING" logic found (grep search for "stale", "timeout", "reset", "PROCESSING" handling, "started_at" comparisons in `src/main/java` returned only LLM timeout-related code, no job recovery logic)
4. **Incomplete idempotency** - Reruns create duplicate chunks. Evidence: `src/main/resources/db/migration/V1__init.sql:34-49` shows no `UNIQUE(job_uuid, chunk_index)` constraint; `LocalDocumentProcessingWorker.java:317-326` inserts chunks via `documentChunkRepository.save()` without deleting existing chunks
5. **Limited E2E test coverage** - Test classes found: `src/test/java/com/policyinsight/api/LocalProcessingIntegrationTest.java:1-30` (integration test), `src/test/java/com/policyinsight/config/WorkerConfigTest.java:1-20` (unit test), `src/test/java/com/policyinsight/processing/LocalDocumentProcessingWorkerTest.java:1-20` (unit test), `src/test/java/com/policyinsight/shared/repository/PolicyJobRepositoryTest.java:1-20` (unit test), `src/test/java/com/policyinsight/api/PubSubControllerTest.java:1-20` (unit test), `src/test/java/com/policyinsight/api/PubSubControllerContractTest.java` (unit test). Export/share/Q&A endpoints not covered by integration tests

**Strengths:**
- Comprehensive grounding validation
- Proper error handling (non-fatal GCS uploads)
- Good observability (Datadog integration)
- Clean separation of concerns
- Proper use of environment variables

**Recommendations:**
- Prioritize authentication before production deployment
- Add retry logic for improved reliability
- Expand test coverage for all endpoints
- Update documentation to reflect current state

### Unverified Claims

The following claims could not be proven with repository citations:
- GCS object overwrite behavior (relies on default GCS behavior, not explicitly configured in code)
- Pub/Sub automatic retry rules beyond the code comment stating "Pub/Sub will retry if we return 500"
- JPA cascade delete behavior (entity relationships not fully reviewed for `@OneToMany(cascade = ...)` annotations)
- Share link revocation mechanism (no explicit revocation method found, only expiration-based access control)
- Q&A service implementation details (QaService class implementation not fully reviewed, only controller usage verified)

---

## Change Log

**Audit Updates (2026-01-03):**

1. **Scope section:** Changed "Complete repository analysis" to "Repository audit based on code + configs referenced below"
2. **Summary > Production Blockers:** Replaced circular evidence with repo citations:
   - Auth blocker: Cited `pom.xml` (no Spring Security) + grep evidence (no security annotations)
   - Retry blocker: Cited `LocalDocumentProcessingWorker.java:229-234` (sets FAILED) + no Spring Retry in `pom.xml`
   - Stuck-job recovery: Cited absence proof (grep search results) + status transition code
   - Idempotency: Cited `V1__init.sql:34-49` (no UNIQUE constraint) + worker code
   - Test coverage: Listed all test classes with paths and line citations
3. **Cascade delete section:** Strengthened FK citations with full paths (`src/main/resources/db/migration/V1__init.sql:L46,L64,L80,L94`); fixed Job Cleanup recommendation to cite repository methods explicitly; noted that `QaInteractionRepository` and `ShareLinkRepository` do not have `deleteByJobUuid()` methods
4. **HTTP Endpoints table:** Rebuilt with annotation-proof citations (class-level + method-level mappings with line ranges); verified `HealthController` as custom controller; moved framework endpoints to separate "Framework-Provided Endpoints" table with enabling config citations
5. **Pub/Sub delivery semantics:** Removed broad retry rules; kept only code-proven claims (synchronous processing, HTTP status codes with exact line citations, code comment about retry behavior)
6. **Secrets handling:** Added citations to `application.yml` and `application-cloudrun.yml` line ranges; cited `docs/SECURITY.md:128-167` and script files with line ranges; removed "no hardcoded secrets" claim, replaced with "No committed scan report found"
7. **Datadog assets:** Replaced "Presence verified" blobs with explicit file citations using `L1-L5` format; used "Examples include..." phrasing for system monitors
8. **Storage path:** Verified path construction citation (`GcsStorageService.java:60`); marked overwrite/collision behavior as unverified
9. **Unverified claims section:** Added new subsection listing claims that could not be proven with repo citations


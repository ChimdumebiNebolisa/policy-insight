# PolicyInsight API Metrics Specification

**Generated:** 2026-01-14
**Purpose:** Contract specification for building a k6 metrics harness

---

## Section A: Runtime and Entry Points

### Tech Stack
- **Spring Boot version:** 3.3.0
- **Java version:** 21
- **Build tool:** Maven (Maven wrapper: `mvnw` / `mvnw.cmd`)

**Source:** `pom.xml` lines 9-12, 22

### How to Run Locally

**Windows:**
```bash
.\mvnw.cmd spring-boot:run
```

**Linux/Mac:**
```bash
./mvnw spring-boot:run
```

**Or with Maven installed:**
```bash
mvn spring-boot:run
```

**Prerequisites:** PostgreSQL must be running (via `docker-compose up -d`)

**Source:** `README.md` lines 34-49

### Default Server Port and Base Path
- **Port:** 8080 (configurable via `SERVER_PORT` env var)
- **Base URL:** `http://localhost:8080` (configurable via `APP_BASE_URL` env var)
- **Base path:** `/` (no base path prefix)

**Source:** `src/main/resources/application.yml` lines 46-47, 56

### Authentication Required for Local Calls
**Yes** - for protected endpoints (upload response token required for status/export/Q&A)

**How to obtain token:**
- Token is returned in JSON response from `POST /api/documents/upload` in the `token` field
- Token must be provided in `X-Job-Token` header for API calls
- For HTMX requests, token is set as cookie: `pi_job_token_{jobId}`

**Source:** `src/main/java/com/policyinsight/api/DocumentController.java` lines 172, 209, 329-349

---

## Section B: The 3 Key Endpoints for the Value Path

### 1) Upload or Ingest PDF

**Method and Path:** `POST /api/documents/upload`

**Request Content Type:** `multipart/form-data`

**Required Headers:**
- `Content-Type: multipart/form-data` (automatically set by curl)

**Request Body Schema:**
- Form field name: `file`
- Type: Binary (PDF file)
- Constraints:
  - Max size: 50 MB (52,428,800 bytes)
  - Content-Type must be `application/pdf`
  - Must pass PDF magic bytes validation (`%PDF-`)

**Response Schema (JSON when not HTMX):**
```json
{
  "jobId": "string (UUID)",
  "token": "string (capability token)",
  "status": "PENDING",
  "statusUrl": "/api/documents/{jobId}/status",
  "message": "Document uploaded successfully. Processing will begin shortly."
}
```

**Response Status:** 202 Accepted (when successful)

**Example curl command:**
```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@sample.pdf" \
  -H "Content-Type: multipart/form-data"
```

**Synchronous or Async:** Async
- Upload is synchronous (returns immediately)
- Processing happens asynchronously via job queue
- Job status transitions: PENDING → PROCESSING → SUCCESS/FAILED

**Source:** `src/main/java/com/policyinsight/api/DocumentController.java` lines 72-242

### 2) Parse/Extract (Gemini/Vertex Step)

**Note:** There is no direct REST endpoint to trigger extraction. Extraction happens automatically as part of job processing after upload.

**How extraction works:**
1. Upload creates a job with status `PENDING`
2. Background worker polls for `PENDING` jobs (local) or Pub/Sub message triggers (GCP)
3. Worker performs extraction using:
   - Document AI (if enabled) or PDFBox fallback
   - Text chunking into `document_chunks` table
   - Gemini/Vertex AI for classification and analysis
4. Results stored in:
   - `document_chunks` table (extracted text chunks)
   - `reports` table (JSONB analysis results)

**Job Processing Model:**
- **How to create job:** POST to `/api/documents/upload` (creates job automatically)
- **How to poll job status:** `GET /api/documents/{id}/status`
- **How to fetch final extraction result:** Data is in database tables; no direct JSON API endpoint exists. The HTML report endpoint `/documents/{id}/report` renders the data, but there is no JSON API endpoint to retrieve the raw extraction output.

**Status Polling Endpoint:**

**Method and Path:** `GET /api/documents/{id}/status`

**Required Headers:**
- `X-Job-Token: {token}` (from upload response)

**Path Parameter:**
- `id`: UUID string (the `jobId` from upload response)

**Response Schema:**
```json
{
  "jobId": "string (UUID)",
  "status": "PENDING|PROCESSING|SUCCESS|FAILED",
  "message": "string",
  "reportUrl": "/documents/{id}/report" (present if status=SUCCESS),
  "errorMessage": "string" (present if status=FAILED),
  "errorCode": "string" (present if status=FAILED),
  "attemptCount": 0 (present if status=PROCESSING),
  "leaseExpiresAt": "ISO8601 timestamp" (present if status=PROCESSING)
}
```

**Example curl command:**
```bash
curl -X GET http://localhost:8080/api/documents/{jobId}/status \
  -H "X-Job-Token: {token_from_upload_response}"
```

**Source:** `src/main/java/com/policyinsight/api/DocumentController.java` lines 244-323

### 3) Search

**Note:** There is no dedicated "search" endpoint in the traditional sense. The closest functionality is the Q&A endpoint which performs semantic search with grounding.

**Method and Path:** `POST /api/questions`

**Request Content Type:** `application/json` or `application/x-www-form-urlencoded`

**Required Headers:**
- `X-Job-Token: {token}` (from upload response)
- `Content-Type: application/json` (for JSON requests)

**Request Body Schema (JSON):**
```json
{
  "document_id": "string (UUID)",
  "question": "string (max 500 characters)"
}
```

**Response Schema:**
```json
{
  "jobId": "string (UUID)",
  "question": "string",
  "answer": "string",
  "confidence": "CONFIDENT|ABSTAINED",
  "citations": [
    {
      "chunkId": "integer",
      "pageNumber": "integer",
      "textSpan": "string"
    }
  ]
}
```

**Response Status:** 200 OK

**Example curl command:**
```bash
curl -X POST http://localhost:8080/api/questions \
  -H "Content-Type: application/json" \
  -H "X-Job-Token: {token_from_upload_response}" \
  -d '{
    "document_id": "{jobId}",
    "question": "What are the data retention policies?"
  }'
```

**Synchronous or Async:** Synchronous (3-second timeout per question)

**Rate Limits:**
- 20 Q&A requests/hour per IP
- Max 3 questions per job

**Source:** `src/main/java/com/policyinsight/api/QaController.java` lines 64-206

---

## Section C: Document Size Handling

### Max Upload Size
- **50 MB** (52,428,800 bytes) per file
- Configured in: `application.yml` line 43 (`spring.servlet.multipart.max-file-size`)
- Also enforced in code: `DocumentController.java` line 44

**Source:** `src/main/resources/application.yml` lines 42-44

### Streaming, Chunking, or Storage

**Storage:**
- **Local mode (default):** Files stored in `.local-storage/` directory (configurable via `APP_STORAGE_LOCAL_DIR`)
- **GCP mode:** Files stored in Google Cloud Storage bucket (configurable via `APP_STORAGE_MODE=gcp` and `GCS_BUCKET_NAME`)

**Storage path format:** `{jobId}/document.pdf` (filename is normalized to `document.pdf` for security)

**Chunking:**
- Extracted text is chunked into `document_chunks` table
- Each chunk has: `chunk_index`, `text`, `page_number`, `start_offset`, `end_offset`, `span_confidence`

**Source:**
- `src/main/resources/application.yml` lines 66-68
- `src/main/java/com/policyinsight/api/storage/StorageService.java`
- `src/main/java/com/policyinsight/shared/model/DocumentChunk.java`

### OCR Pipeline or Scanned PDF Handling

**Yes** - OCR support exists but is conditional:

**Document AI (GCP):**
- Primary method if `DOCUMENT_AI_ENABLED=true`
- Configured via `documentai.enabled` property
- Uses Google Cloud Document AI processor

**Fallback OCR:**
- If Document AI not available: `FallbackOcrService` uses PDFBox (text extraction only, no OCR)
- For actual OCR: Tess4j library is available (dependency in `pom.xml` line 188-190) but **UNKNOWN** if actively used in code path
- Confidence scores: Document AI provides confidence, fallback uses fixed 0.5 confidence

**Location:**
- `src/main/java/com/policyinsight/processing/DocumentAiService.java`
- `src/main/java/com/policyinsight/processing/FallbackOcrService.java`
- `src/main/resources/application.yml` lines 148-154

**Note:** For production OCR on scanned PDFs, Document AI should be enabled. Fallback uses PDFBox which only extracts text from text-based PDFs.

---

## Section D: Extraction Output Contract

### Extraction Output Location

The extraction output is stored in the `reports` table with the following structure. **There is no dedicated JSON API endpoint to fetch this data** - it must be queried directly from the database or accessed via the HTML report view (`/documents/{id}/report`).

### Full JSON Shape (Database Schema)

The `reports` table has these JSONB columns:

#### Top-Level Fields
- `job_uuid`: UUID (primary key, foreign key to `policy_jobs`)
- `document_overview`: JSONB object
- `summary_bullets`: JSONB object
- `obligations`: JSONB object
- `restrictions`: JSONB object
- `termination_triggers`: JSONB object
- `risk_taxonomy`: JSONB object
- `generated_at`: ISO8601 timestamp
- `gcs_path`: string (optional, storage path for report JSON)

**Source:** `src/main/resources/db/migration/V1__init.sql` lines 51-66

#### Nested Object Structures

**Note:** The exact JSON schema for each JSONB field is **UNKNOWN** from code inspection. The database schema comments indicate expected structure:

- `summary_bullets`: `[{text, chunk_ids, page_refs}]` (array of objects)
- `obligations`: `[{text, severity, citations}]` (array of objects)
- `restrictions`: Structure **UNKNOWN** (assumed similar to obligations)
- `termination_triggers`: Structure **UNKNOWN**
- `risk_taxonomy`: `{Data, Financial, LegalRights, Termination, Modification}` (object with category keys)

**Source:** `src/main/resources/db/migration/V1__init.sql` lines 56-60

**Additional Data:**
- `document_chunks` table contains extracted text chunks:
  - `chunk_index`: integer
  - `text`: string
  - `page_number`: integer
  - `start_offset`: integer
  - `end_offset`: integer
  - `span_confidence`: decimal (3,2)

**Source:** `src/main/resources/db/migration/V1__init.sql` lines 34-47

### Minimum Required Fields

**UNKNOWN** - Cannot be determined from code inspection without:
1. Querying an actual completed job's `reports` table row
2. Examining the `ReportGenerationService` code to see what it always generates
3. Inspecting sample report data in test fixtures

**Note:** The `reports` table requires `job_uuid` (NOT NULL) but JSONB fields can be NULL.

### Optional Fields

All JSONB fields (`document_overview`, `summary_bullets`, `obligations`, `restrictions`, `termination_triggers`, `risk_taxonomy`) are nullable in the database schema.

### Fields Derived by Gemini/Vertex vs Deterministic Code

**UNKNOWN** - Would require inspection of:
- `ReportGenerationService` (lines where Gemini prompts are constructed)
- `RiskAnalysisService` (classification and analysis logic)
- Actual Gemini API response structure

**Likely derived by Gemini/Vertex:**
- `document_overview`
- `summary_bullets`
- `obligations` content
- `restrictions` content
- `termination_triggers` content
- `risk_taxonomy` categorization

**Likely deterministic code:**
- `job_uuid` (from upload)
- `generated_at` (timestamp set by service)
- `gcs_path` (storage path generation)

**Source:** Need to inspect `src/main/java/com/policyinsight/processing/ReportGenerationService.java` and `RiskAnalysisService.java` for exact mapping.

---

## Section E: Test Fixture Hooks

### Test Endpoints, Mock Modes, or Local Fixtures

**No explicit test endpoints found** for:
- Mock modes
- Test fixture injection
- Bypass authentication for testing

**Local fixtures:**
- Local worker polling is enabled via `APP_PROCESSING_MODE=local` (default)
- Local storage mode via `APP_STORAGE_MODE=local` (default)

**Source:** `src/main/resources/application.yml` lines 67, 72

### Sample PDFs Already in Repo

**Yes:**
- `tools/metrics/sample.pdf` exists

**Source:** File system inspection

### Existing Load Test Tooling or Scripts

**No k6 scripts found**

**Python script exists:**
- `scripts/datadog/traffic-generator.py` - Generates traffic for Datadog observability testing
- **UNKNOWN** if this covers load testing scenarios

**Source:** File system inspection

### Logging or Metrics Already Present

**Yes:**

**Micrometer:**
- Configured with StatsD export for Datadog
- Enabled via `datadog.enabled` property
- Exported to `DD_AGENT_HOST:8125` (Datadog DogStatsD port)

**Spring Boot Actuator:**
- Endpoints exposed: `health`, `readiness`, `info`, `metrics`, `prometheus`
- Accessible at `/actuator/{endpoint}`

**OpenTelemetry:**
- Custom spans via `TracingServiceInterface`
- Used for correlation IDs in logs (MDC)

**Source:**
- `pom.xml` lines 107-123
- `src/main/resources/application.yml` lines 92-111

---

## Section F: What We Should Measure

### Endpoints to Load Test with k6

1. **Primary Value Path:**
   - `POST /api/documents/upload` - Upload endpoint
   - `GET /api/documents/{id}/status` - Status polling (until SUCCESS)
   - `POST /api/questions` - Q&A search (after job completes)

2. **Supporting Endpoints:**
   - `GET /health` - Health check (baseline)
   - `GET /actuator/readiness` - Readiness check

### Stages for End-to-End Timing

1. **Upload stage:** Time from POST request start to 202 response received
2. **Queue stage:** Time from upload completion to job status = PROCESSING (poll `GET /status`)
3. **Processing stage:** Time from PROCESSING to SUCCESS (poll `GET /status`)
   - Sub-stages (if measurable via status endpoint):
     - Text extraction
     - Classification
     - Report generation
4. **Search stage:** Time from POST to `/api/questions` to response received

**Note:** Current status endpoint does not expose sub-stage progress. Only top-level status (`PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`) is available.

### Unique Identifier for Job Correlation

**Primary identifier:**
- `jobId` (UUID string) - Returned from upload endpoint, used in all subsequent calls

**Secondary identifiers:**
- `request_id` - UUID generated during upload (in logs, not exposed in API response)
- `dd_trace_id` - Datadog trace ID (stored in `policy_jobs.dd_trace_id`, not exposed in API)

**Source:** `src/main/java/com/policyinsight/api/DocumentController.java` lines 148-149

### Fields to Validate for Schema Pass and Null Rate

**For extraction output validation (via database query):**

**Report table:**
- `job_uuid` (must exist, not null)
- `document_overview` (null rate)
- `summary_bullets` (null rate)
- `obligations` (null rate)
- `restrictions` (null rate)
- `termination_triggers` (null rate)
- `risk_taxonomy` (null rate)
- `generated_at` (must exist for SUCCESS jobs)

**Document chunks table:**
- Count of chunks per `job_uuid` (should be > 0 for SUCCESS)
- Average `span_confidence` (should be in range 0.0-1.0)

**For API response validation:**

**Upload response:**
- `jobId` (required, UUID format)
- `token` (required, non-empty string)
- `status` (must be "PENDING")

**Status response:**
- `jobId` (required, matches request path)
- `status` (required, one of: PENDING, PROCESSING, SUCCESS, FAILED)
- `reportUrl` (required if status=SUCCESS)

**Q&A response:**
- `jobId` (required, matches request)
- `question` (required, matches request)
- `answer` (required, non-empty string)
- `confidence` (required, one of: CONFIDENT, ABSTAINED)
- `citations` (required, array - may be empty)

**Note:** Full JSON schema validation requires:
1. Inspecting actual API responses from test runs
2. Querying database for completed jobs to see report JSON structure
3. Examining `ReportGenerationService` code to understand Gemini output format

---

## Ready for Harness Checklist

### ✅ BASE_URL
- **Value:** `http://localhost:8080`
- **Configurable via:** `APP_BASE_URL` environment variable

### ✅ Upload Endpoint Details
- **Method/Path:** `POST /api/documents/upload`
- **Content-Type:** `multipart/form-data`
- **Request:** Form field `file` (PDF, max 50MB)
- **Response:** JSON with `jobId` (UUID) and `token` (string)
- **Auth:** None required (rate limited by IP)

### ✅ Extract Endpoint Details
- **Direct endpoint:** None (extraction happens automatically after upload)
- **Status polling:** `GET /api/documents/{id}/status`
- **Auth:** `X-Job-Token` header required
- **Response:** JSON with `status` field (PENDING|PROCESSING|SUCCESS|FAILED)

### ✅ Search Endpoint Details
- **Method/Path:** `POST /api/questions`
- **Content-Type:** `application/json`
- **Request:** `{"document_id": "UUID", "question": "string (max 500 chars)"}`
- **Response:** JSON with `answer`, `confidence`, `citations[]`
- **Auth:** `X-Job-Token` header required

### ✅ Async Polling Endpoints
- **Status:** `GET /api/documents/{id}/status`
- **Polling strategy:** Poll every 2-5 seconds until `status != PENDING` and `status != PROCESSING`
- **Final state:** SUCCESS or FAILED
- **No webhook/callback mechanism** - polling only

### ⚠️ Extraction JSON Schema Fields List

**Partially Known:**
- Database columns: `document_overview`, `summary_bullets`, `obligations`, `restrictions`, `termination_triggers`, `risk_taxonomy` (all JSONB)
- Chunks table: `chunk_index`, `text`, `page_number`, `start_offset`, `end_offset`, `span_confidence`

**Unknown (requires further investigation):**
- Exact JSON structure of each JSONB field
- Required vs optional fields within JSONB objects
- Gemini output format before database storage
- **No JSON API endpoint** exists to fetch extraction results - data only in database or HTML view

**Recommendation:**
1. Run a test job to completion
2. Query the `reports` table directly to inspect actual JSON structure
3. Examine `ReportGenerationService` to understand Gemini prompts and expected output format
4. Consider adding a `GET /api/documents/{id}/report/json` endpoint for metrics harness access

---

## Additional Notes

### Swagger UI Access
- **URL:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs
- **Status:** Available (springdoc-openapi configured)

### Missing for Complete Harness
1. **JSON API endpoint for extraction results** - Currently only available via HTML view or database query
2. **Exact JSON schema** for `reports` table JSONB fields - Requires inspecting actual data or `ReportGenerationService` code
3. **Sub-stage timing** - Status endpoint doesn't expose extraction/classification/report-generation sub-stages

### Recommended Next Steps
1. Inspect `ReportGenerationService.java` to document expected JSON structure
2. Query a completed job's `reports` row to capture actual JSON shape
3. Consider adding `GET /api/documents/{id}/report/json` endpoint for metrics access
4. Document exact Gemini prompt and response structure for extraction validation


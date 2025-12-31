# PolicyInsight MUP (Minimum Usable Product) Gap Report

**Generated:** 2025-01-27
**Purpose:** Map existing functionality vs. MUP demo requirements (single happy path: upload → status → report → Q&A)

---

## MUP Demo Requirements

**Target:** In <10 minutes, a judge can locally:
1. Open UI `/`
2. Upload a PDF
3. See progress/status update (polling or progress bar)
4. Land on a **Report page** showing:
   - document title/type
   - **5 risk sections** (even if minimal content)
   - **citations** (page numbers + chunk references)
5. Ask **one question** and get either:
   - cited answer, OR
   - "Insufficient evidence in document."

---

## Repository Inventory

### UI Routes (Thymeleaf/MVC Controllers)

| Route | Controller | Template | Status |
|-------|-----------|----------|--------|
| `GET /` | **MISSING** | `index.html` exists | ⚠️ No explicit controller found. Spring Boot *might* auto-serve via Thymeleaf, but this is unreliable. |
| `GET /error` | `ErrorController` | `error.html` | ✅ Exists |
| `GET /documents/{id}/report` | `ReportController.viewReport()` | `report.html` | ✅ Exists (line 52) |
| `GET /documents/{id}/share/{token}` | `ShareReportController` | `share-report.html` | ✅ Exists (out of MUP scope) |

**Files:**
- `src/main/java/com/policyinsight/web/ErrorController.java` (line 13: `@RequestMapping("/error")`)
- `src/main/java/com/policyinsight/web/ReportController.java` (line 52: `@GetMapping("/documents/{id}/report")`)
- `src/main/resources/templates/index.html` (exists, but no controller maps to `/`)
- `src/main/resources/templates/error.html` (exists)
- `src/main/resources/templates/report.html` (exists)
- `src/main/resources/templates/share-report.html` (exists)

---

### API Routes (REST Endpoints)

| Endpoint | Controller | Method | Status |
|----------|-----------|--------|--------|
| `POST /api/documents/upload` | `DocumentController.uploadDocument()` | ✅ Exists | Returns JSON (`Map<String, Object>`) |
| `GET /api/documents/{id}/status` | `DocumentController.getDocumentStatus()` | ✅ Exists | Returns JSON |
| `GET /api/documents/{id}/report` | **MISSING** | ❌ No REST API version | Only web route exists |
| `POST /api/questions` | `QaController.submitQuestion()` | ✅ Exists | Supports JSON + form data (htmx) |
| `GET /health` | `HealthController.health()` | ✅ Exists | Out of MUP scope |
| `GET /readiness` | `ReadinessEndpoint` | ✅ Exists | Out of MUP scope |

**Files:**
- `src/main/java/com/policyinsight/api/DocumentController.java` (lines 54, 178)
- `src/main/java/com/policyinsight/api/QaController.java` (line 53: `@PostMapping`)
- `src/main/java/com/policyinsight/config/HealthController.java`

**Note:** Upload endpoint returns `ResponseEntity<Map<String, Object>>` (JSON), but `index.html` uses `hx-target="#status"` expecting HTML fragment.

---

### Worker Routes (Internal)

| Route | Controller | Purpose | Status |
|-------|-----------|---------|--------|
| `POST /internal/pubsub` | `PubSubController.handlePubSubMessage()` | Pub/Sub push handler | ✅ Exists (line 71) |

**File:**
- `src/main/java/com/policyinsight/api/PubSubController.java`

---

### Templates

| Template | Purpose | Status | Notes |
|----------|---------|--------|-------|
| `index.html` | Landing page with upload form | ✅ Exists | Uses htmx for upload + status polling |
| `error.html` | Error page | ✅ Exists | |
| `report.html` | Report display page | ✅ Exists | Shows 5 sections (Overview, Summary, Obligations, Risk Taxonomy, Q&A) |
| `share-report.html` | Share link view | ✅ Exists | Out of MUP scope |
| `layout.html` | Base template (unused) | ✅ Exists | Not referenced by other templates |

**Location:** `src/main/resources/templates/`

---

### htmx Usage

**Found in `index.html`:**
- Line 20: `hx-post="/api/documents/upload"` (form submission)
- Line 21: `hx-target="#status"` (target div)
- Line 22: `hx-swap="innerHTML"`
- Line 41: `hx-get="/api/documents/{jobId}/status"` ⚠️ **PLACEHOLDER `{jobId}` - not dynamic**
- Line 42: `hx-trigger="load, every 2s"`
- Line 43: `hx-swap="innerHTML"`
- Line 44: `style="display: none;"` ⚠️ **Hidden by default**

**Found in `report.html`:**
- Line 132: `hx-post="/api/questions"` (Q&A form)
- Line 133: `hx-target="#qa-results"`
- Line 134: `hx-swap="beforeend"`

**Issues:**
1. Status polling uses literal `{jobId}` string instead of actual job ID variable
2. Status polling div is hidden (`display: none`) and never shown
3. Upload endpoint returns JSON, but htmx expects HTML fragment

---

### Sample PDFs / Fixtures

| File | Location | Status |
|------|----------|--------|
| `tiny.pdf` | Root directory | ✅ Exists (not in static/) |
| `too_big.pdf` | Root directory | ✅ Exists (not in static/) |
| `report.pdf` | Root directory | ✅ Exists (not in static/) |

**Required for MUP:** Sample PDF should be in `src/main/resources/static/sample.pdf` for easy access.

---

### Processing Pipeline

**Local Processing Worker:**
- **File:** `src/main/java/com/policyinsight/processing/LocalDocumentProcessingWorker.java`
- **Condition:** Requires `@ConditionalOnProperty(prefix = "policyinsight.worker", name = "enabled", havingValue = "true")`
- **Mechanism:** `@Scheduled(fixedDelayString = "${app.local-worker.poll-ms:2000}")` polling
- **Status:** ⚠️ **Worker not enabled by default** - requires `policyinsight.worker.enabled=true` config

**Configuration:**
- `src/main/resources/application.yml` (line 63: `app.processing.mode: local`)
- **No `policyinsight.worker.enabled` setting found in application.yml**

**Files:**
- `src/main/java/com/policyinsight/config/WorkerConfig.java` (just logs enabled status)
- `src/main/java/com/policyinsight/processing/DocumentJobProcessor.java` (interface)

---

## Gap Analysis: MUP Demo Steps vs. Current State

### Step 1: Open UI `/`

**Required:** GET `/` route that renders `index.html`

**Current State:**
- ✅ `index.html` template exists
- ❌ **No controller with `@GetMapping("/")`**
- ⚠️ Spring Boot *might* auto-serve `index.html` via Thymeleaf view resolver, but this is unreliable

**Blocker:** **P0** - No explicit route handler for root path

**Fix:** Add `@GetMapping("/")` method in a web controller (or create new `HomeController`)

---

### Step 2: Upload a PDF

**Required:**
- Upload form posts to `/api/documents/upload`
- Endpoint accepts multipart file
- Returns job ID and status URL

**Current State:**
- ✅ Form exists in `index.html` (line 19-36)
- ✅ Endpoint exists: `POST /api/documents/upload` (DocumentController, line 54)
- ✅ Multipart handling: `@RequestParam("file") MultipartFile file`
- ✅ Returns: `jobId`, `status`, `statusUrl`, `message` (line 137-141)
- ⚠️ **Returns JSON, but htmx expects HTML** (line 21: `hx-target="#status"` expects HTML fragment swap)

**Blocker:** **P0** - Response format mismatch (JSON vs HTML expected by htmx)

**Fix:**
- Option A: Return HTML fragment for htmx requests (check `HX-Request` header)
- Option B: Use JavaScript to parse JSON and update DOM
- Option C: Return HTML fragment always (simpler for MUP)

---

### Step 3: See Progress/Status Update

**Required:**
- Visible status polling/progress indicator
- Status endpoint returns job status (PENDING → PROCESSING → SUCCESS/FAILED)
- UI updates as status changes

**Current State:**
- ✅ Status endpoint exists: `GET /api/documents/{id}/status` (DocumentController, line 178)
- ✅ Returns status, message, and reportUrl when SUCCESS
- ❌ **Status polling div has placeholder `{jobId}`** (line 41: `hx-get="/api/documents/{jobId}/status"`)
- ❌ **Status polling div is hidden** (`display: none`, line 44)
- ❌ **No logic to show polling div or inject actual job ID**

**Blocker:** **P0** - Status polling completely broken (placeholder URL, hidden div, no job ID injection)

**Fix:**
1. After upload success, inject actual `jobId` into polling URL
2. Show the polling div (`display: block` or remove `display: none`)
3. Create HTML fragment endpoint or use JavaScript to parse JSON status response

---

### Step 4: Land on Report Page

**Required:**
- Route: `/documents/{id}/report` (or similar)
- Renders 5 sections:
  1. Document Overview (title/type)
  2. Plain-English Summary (with citations)
  3. Obligations & Restrictions (with citations)
  4. Risk Taxonomy (5 categories: Data/Privacy, Financial, Legal Rights Waivers, Termination, Modification)
  5. Q&A form
- Citations show page numbers + chunk references

**Current State:**
- ✅ Route exists: `GET /documents/{id}/report` (ReportController, line 52)
- ✅ Template exists: `report.html`
- ✅ Controller fetches: `job`, `report`, `chunks`, `qaInteractions` (lines 64-96)
- ✅ Template structure exists with all 5 sections (lines 22-121 in report.html)
- ⚠️ **Report must exist in DB** (line 74-80: returns error if report is null)
- ⚠️ **Processing must complete** (job status must be SUCCESS)

**Blocker:** **P0** - Report page exists but depends on processing pipeline completing successfully

**Verification Needed:**
- Does processing create report records?
- Do all 5 risk sections get populated?
- Are citations properly formatted?

---

### Step 5: Ask One Question

**Required:**
- Q&A form on report page
- POST `/api/questions` with `document_id` and `question`
- Returns answer + citations OR "Insufficient evidence in document."

**Current State:**
- ✅ Form exists in `report.html` (lines 131-152)
- ✅ Endpoint exists: `POST /api/questions` (QaController, line 53)
- ✅ Supports both JSON and form data (htmx) (lines 61-83)
- ✅ Returns HTML fragment for htmx requests (line 125-128)
- ✅ Returns answer + citations or abstention (line 106-119)
- ⚠️ **Limits to 3 questions** (line 95-99) - acceptable for MUP

**Blocker:** **P1** - Functionality exists, but depends on:
- Document chunks existing in DB
- QaService working correctly
- LLM/Gemini service availability (may need mock/stub for local demo)

---

## Processing Pipeline Status

**Required for MUP:** Jobs must transition: `PENDING → PROCESSING → SUCCESS` deterministically

**Current State:**
- ✅ Upload creates job with status `PENDING` (DocumentController, line 125)
- ✅ `LocalDocumentProcessingWorker` exists and polls for PENDING jobs
- ❌ **Worker not enabled by default** (`@ConditionalOnProperty` requires `policyinsight.worker.enabled=true`)
- ⚠️ **Processing depends on:** DocumentAiService, GeminiService, RiskAnalysisService, ReportGenerationService
- ⚠️ **Local mode config exists** (`app.processing.mode: local` in application.yml, line 63) but worker still needs explicit enable

**Blocker:** **P0** - Worker not enabled, so jobs will never process locally

**Fix:**
- Option A: Enable worker via config (`policyinsight.worker.enabled=true`)
- Option B: Add `DEMO_MODE=true` flag that:
  - Enables local worker
  - Uses simplified/stubbed processing (minimal chunks, minimal report)
  - Ensures deterministic SUCCESS status

---

## Blockers Summary (Ranked by Severity)

### P0 (Prevents Demo Completely)

1. **No GET `/` controller** (Step 1 fails)
   - File: Missing controller
   - Fix: Add `@GetMapping("/")` in web controller
   - Location: Create `HomeController` or add to existing controller

2. **Upload endpoint returns JSON, htmx expects HTML** (Step 2 broken)
   - File: `src/main/java/com/policyinsight/api/DocumentController.java` (line 148)
   - Fix: Return HTML fragment for htmx requests (check `HX-Request` header) or use JavaScript

3. **Status polling broken** (Step 3 fails)
   - File: `src/main/resources/templates/index.html` (line 40-45)
   - Issues:
     - Placeholder `{jobId}` instead of actual job ID
     - Div is hidden (`display: none`)
     - No logic to show/inject job ID
   - Fix: Inject job ID after upload, show div, create HTML fragment endpoint or parse JSON

4. **Local worker not enabled** (Processing never starts)
   - File: `src/main/java/com/policyinsight/processing/LocalDocumentProcessingWorker.java` (line 45)
   - Config: No `policyinsight.worker.enabled=true` in application.yml
   - Fix: Add config or DEMO_MODE flag

5. **Report page requires completed processing** (Step 4 may fail)
   - File: `src/main/java/com/policyinsight/web/ReportController.java` (line 74-80)
   - Issue: Returns error if report is null (processing incomplete)
   - Fix: Ensure processing pipeline completes successfully OR add stub data for demo

---

### P1 (Demo Works But Ugly/Flaky)

1. **No sample PDF bundled** (User must provide their own)
   - Fix: Add `src/main/resources/static/sample.pdf` and "Try sample PDF" button

2. **Status endpoint returns JSON, polling expects HTML** (Step 3 works but requires JavaScript)
   - File: `DocumentController.getDocumentStatus()` returns JSON
   - Fix: Create HTML fragment view or use JavaScript to parse JSON

3. **Q&A depends on LLM service** (Step 5 may fail if Gemini unavailable)
   - Fix: Add stub/mock for local demo mode

4. **No progress indicator** (User sees "Processing..." but no percentage)
   - Fix: Add progress bar or percentage display

---

## Recommended Fix Order (Minimal MUP Path)

1. **Add GET `/` controller** → Enables Step 1
2. **Fix upload response (HTML fragment)** → Enables Step 2
3. **Fix status polling (inject job ID, show div, HTML response)** → Enables Step 3
4. **Enable local worker** → Enables processing
5. **Verify/ensure processing completes** → Enables Step 4
6. **Add sample PDF + demo button** → Makes demo easier
7. **Verify Q&A works (or add stub)** → Enables Step 5

---

## Additional Findings

- **No DEMO_MODE flag** - Consider adding `DEMO_MODE=true` env var that:
  - Enables local worker
  - Uses simplified processing (stubbed data if needed)
  - Ensures deterministic success

- **Report template expects 5 sections** - Template structure is correct, but need to verify data is populated

- **Static files:** CSS exists at `src/main/resources/static/css/style.css`

- **Database schema:** All required tables exist (policy_jobs, document_chunks, reports, qa_interactions)

---

## Next Steps

1. Create `MUP_GAP_REPORT.md` ✅ (this file)
2. Implement fixes in order (P0 first)
3. Test end-to-end happy path
4. Add sample PDF
5. Create `DEMO.md` with exact steps
6. Create smoke test script


# PolicyInsight Repo Audit Report (Deployability + Functionality)

Date: 2026-01-24
Workspace: `C:\Users\Chimdumebi\policy-insight`

Commands executed (evidence):
- `docker compose up -d` (failed: Docker Desktop engine not reachable)
- `.\mvnw.cmd test` (failed: Testcontainers could not find Docker)
- `.\mvnw.cmd spring-boot:run -D"spring-boot.run.profiles=local"` (app failed to start: DB auth error)

## Repo map and stack identification

Key directories and files (repo map):
- `src/main/java/com/policyinsight/` – Spring Boot app code
  - `api/` REST controllers and services
  - `web/` Thymeleaf web controllers
  - `processing/` document processing pipeline + workers
  - `security/` capability token filter + rate limiting
  - `observability/` metrics/tracing
  - `config/` app configuration and health endpoints
- `src/main/resources/` – configuration, migrations, templates, static assets
  - `application.yml`, `application-local.yml`, `application-cloudrun.yml`
  - `db/migration/` Flyway SQL migrations
  - `templates/` Thymeleaf HTML
  - `static/css/style.css`
- `pom.xml` – Java 21 + Spring Boot 3.3 + dependencies
- `Dockerfile`, `docker-compose.yml`, `docker-compose.datadog.yml`
- `.github/workflows/ci.yml` – CI runs `./mvnw test`

Stack summary (evidence: `pom.xml`, `README.md`):
- Backend: Java 21 + Spring Boot 3.3
- UI: Thymeleaf server‑rendered HTML + HTMX (`templates/index.html`)
- DB: PostgreSQL 15 + Flyway (`src/main/resources/db/migration`)
- Observability: Micrometer + Datadog (StatsD), OpenTelemetry API
- Cloud: Cloud Run + GCS + Pub/Sub + Vertex AI (Gemini) config stubs

## 1) App identity

**Name:** PolicyInsight (`README.md`, `pom.xml`)
**Purpose:** Analyze legal PDFs and generate grounded risk reports with citations (`README.md`, `DocumentController`, `ReportController`, `ReportGenerationService`).
**Primary users:** Legal/compliance users or anyone reviewing contracts (landing page copy in `templates/index.html`).
**Main flow:**
1. Upload PDF via `/api/documents/upload` (HTMX form in `templates/index.html`).
2. Job created in DB; token issued; job queued or processed locally (`DocumentController`, `PolicyJobRepository`, `JobPublisher`).
3. Worker processes: extract text → chunk → classify → risk scan → report generation → save report (`LocalDocumentProcessingWorker`).
4. User polls status, then views report HTML at `/documents/{id}/report` and can ask Q&A, export PDF, or create share link (`ReportController`, `QaController`, `ExportController`, `ShareController`).

## 2) How to run it (local)

### Prerequisites
- Java 21 JDK (`pom.xml` → `java.version` 21)
- Maven 3.8+ or wrapper (`mvnw.cmd`)
- Docker Desktop (PostgreSQL via compose) (`README.md`, `docker-compose.yml`)

### Commands (local)
From README:
- Start Postgres: `docker compose up -d` (`docker-compose.yml`)
- Run app: `.\mvnw.cmd spring-boot:run` (Windows) (`README.md`)

Attempted local run (evidence from commands above):
- `docker compose up -d` failed: Docker Desktop engine not reachable.
  Evidence: command output error: `open //./pipe/dockerDesktopLinuxEngine`
- `.\mvnw.cmd spring-boot:run -D"spring-boot.run.profiles=local"` started Spring but failed during Flyway DB connect: `FATAL: password authentication failed for user "postgres"`
  Evidence: Spring Boot run output (Flyway initialization failure).

### Required environment variables
Defined via config and code:
- DB: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` (`application.yml`, `application-local.yml`, `DebugConfig`)
- Server: `SERVER_PORT`, `PORT` (Cloud Run) (`application.yml`, `application-cloudrun.yml`)
- App security: `APP_TOKEN_SECRET`, `APP_ALLOWED_ORIGINS` (`application.yml`, `TokenService`, `JobTokenInterceptor`)
- Rate limits: `APP_RATE_LIMIT_UPLOAD_MAX_PER_HOUR`, `APP_RATE_LIMIT_QA_MAX_PER_HOUR`, `APP_RATE_LIMIT_QA_MAX_PER_JOB` (`application.yml`)
- Storage: `APP_STORAGE_MODE`, `APP_STORAGE_LOCAL_DIR`, `GCS_BUCKET_NAME`, `STORAGE_EMULATOR_HOST` (`application.yml`, `GcsStorageService`)
- Pub/Sub: `APP_MESSAGING_MODE`, `PUBSUB_TOPIC_NAME`, `PUBSUB_SUBSCRIPTION_NAME`, `PUBSUB_EMULATOR_HOST`, `PUBSUB_PUSH_MODE` (`application.yml`, `PubSubService`, `DocumentProcessingWorker`)
- Pub/Sub push auth: `PUBSUB_PUSH_VERIFICATION_ENABLED`, `PUBSUB_PUSH_EXPECTED_EMAIL`, `PUBSUB_PUSH_EXPECTED_AUDIENCE` (`application.yml`, `PubSubTokenVerifier`)
- GCP project: `GOOGLE_CLOUD_PROJECT` (`application.yml`, `GeminiService`, `PubSubService`)
- Vertex AI: `VERTEX_AI_ENABLED`, `VERTEX_AI_LOCATION`, `VERTEX_AI_MODEL` (`application.yml`, `GeminiService`)
- Worker: `POLICYINSIGHT_WORKER_ENABLED` (`application.yml`, `application-local.yml`)
- Datadog: `DATADOG_ENABLED`, `DD_AGENT_HOST`, `DD_DOGSTATSD_PORT`, `DD_SERVICE`, `DD_ENV`, `DD_VERSION`, `DD_LOGS_INJECTION` (`application.yml`, `DatadogMetricsConfig`, `logback-spring.xml`)
- Datadog agent container: `DD_API_KEY`, `DD_SITE` (`docker-compose.datadog.yml`)

### Docker / Docker Compose support
- `docker-compose.yml` only starts PostgreSQL; the app still runs via `mvnw` or `java -jar` (`docker-compose.yml`).
- `Dockerfile` builds a Java 21 image and runs `app.jar` (`Dockerfile`).
- `docker-compose.datadog.yml` adds Datadog agent + Postgres; still no app container (`docker-compose.datadog.yml`).

### Common failure points during boot (observed + code-backed)
- Docker not running → tests and DB startup fail (Testcontainers + Docker Compose).
- DB auth mismatch → Flyway fails to connect (`application.yml` defaults vs actual DB credentials).
- If `APP_TOKEN_SECRET` left default → warning logged (`TokenService`).

## 3) What works vs what is broken (verified)

**Note:** “Works” requires live endpoint verification. The app did not start due to DB auth failure, so all runtime features remain unverified.

| Feature | Expected behavior | Evidence | Works? | Notes / Fix hint |
|---|---|---|---|---|
| Upload PDF | Accept PDF and create job | `DocumentController`, `templates/index.html` | No (not verified) | App failed to start; fix DB + rerun |
| Job status | Status poll via `/api/documents/{id}/status` | `DocumentController` | No (not verified) | Requires running app |
| Report view | Render report HTML | `ReportController`, `templates/report.html` | No (not verified) | Requires successful processing |
| Q&A | POST `/api/questions` with grounded answer | `QaController`, `QaService` | No (not verified) | Requires running app + processed report |
| PDF export | `/api/documents/{id}/export/pdf` | `ExportController`, `PdfExportService` | No (not verified) | Requires successful processing |
| Share link | Generate + view share link | `ShareController`, `ShareReportController` | No (not verified) | Requires successful processing |
| Health endpoints | `/health`, `/readiness` | `HealthController`, `ReadinessEndpoint` | No (not verified) | App failed to start |
| Swagger/OpenAPI | `/swagger-ui.html`, `/v3/api-docs` | `application.yml`, `OpenApiConfig` | No (not verified) | App failed to start |

## 4) System architecture

Modules / packages (evidence: `src/main/java/com/policyinsight/...`):
- **API layer:** `api/` controllers for upload, status, Q&A, share, export, Pub/Sub push.
- **Web UI:** `web/` controllers + Thymeleaf templates for landing page and report views.
- **Processing pipeline:** `processing/` does extraction, chunking, classification, risk analysis, report generation, Q&A.
- **Security & rate limiting:** `security/` contains `JobTokenInterceptor`, `TokenService`, `RateLimitService`.
- **Observability:** `observability/` Datadog metrics + tracing stubs.

Request flow (main upload path):
1. Client POSTs `/api/documents/upload` → saves PDF to storage, creates `policy_jobs` row, returns jobId + token (JSON) or sets cookie (HTMX) (`DocumentController`, `StorageService`, `PolicyJobRepository`).
2. Job is queued or processed locally:
   - Local: scheduled worker polls DB (`LocalDocumentProcessingWorker`).
   - Cloud: Pub/Sub push hits `/internal/pubsub` (`PubSubController` + `DocumentProcessingWorker`).
3. Worker pipeline:
   - Download PDF → validate page count and text length (`StorageService`, `PdfValidator`).
   - Extract text (PDFBox text extraction) (`FallbackOcrService`).
   - Chunk text (`TextChunkerService`).
   - Classify document (`DocumentClassifierService`).
   - Risk analysis and summary via Gemini (`RiskAnalysisService`, `ReportGenerationService`, `GeminiService`).
   - Grounding validation (cite‑or‑abstain) (`ReportGroundingValidator`).
   - Save report + update job (`ReportRepository`, `PolicyJobRepository`).

Background jobs:
- Local worker polling (`LocalDocumentProcessingWorker`, scheduled).
- Stuck job reaper (`JobReaperService`, scheduled).
- Retention cleanup (`RetentionCleanupTask`, scheduled).
- Rate limit counter cleanup (`RateLimitCleanupTask`, scheduled).

## 5) Database

DB tech: PostgreSQL (runtime driver + migrations) (`pom.xml`, `application.yml`).
Migrations: Flyway SQL in `src/main/resources/db/migration/` (V1–V6).
Auto‑migrate: Flyway enabled on startup with validate‑on‑migrate (`application.yml`).

Main tables (from migrations and entities):
- `policy_jobs` (`PolicyJob`, `V1__init.sql`, `V2__add_access_token.sql`, `V4__add_lease_fields.sql`)
- `document_chunks` (`DocumentChunk`, `V1__init.sql`, `V5__add_chunk_unique_constraint.sql`)
- `reports` (`Report`, `V1__init.sql`)
- `qa_interactions` (`QaInteraction`, `V1__init.sql`)
- `share_links` (`ShareLink`, `V1__init.sql`, `V6__add_share_revocation.sql`)
- `rate_limit_counters` (`RateLimitCounter`, `V3__add_rate_limiting.sql`)

Connection config (local defaults):
`jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:policyinsight}` with `DB_USER`/`DB_PASSWORD` (`application.yml`).

## 6) API surface area

**Note:** OpenAPI JSON exists at `docs/openapi.json` but is incomplete (only `upload`, `health`, `readiness`, `status` paths shown). Use controllers as source of truth.

### `DocumentController` (`/api/documents`)
- `POST /api/documents/upload`
  Auth: public (`JobTokenInterceptor` public paths).
  Request: `multipart/form-data` with `file` (PDF) (`DocumentController`, `PdfValidator`).
  Response: JSON with `jobId`, `token`, `status`, `statusUrl` or HTMX fragment.
- `GET /api/documents/{id}/status`
  Auth: job token required (`JobTokenInterceptor`).
  Response: JSON status or HTMX fragment.
- `GET /api/documents/{id}/report-json`
  Auth: requires `X-Job-Token` header (`DocumentController`).

### `ExportController` (`/api/documents`)
- `GET /api/documents/{id}/export/pdf`
  Auth: job token required (`JobTokenInterceptor`).
  Response: PDF bytes with `Content-Disposition` attachment.

### `ShareController` (`/api/documents`)
- `POST /api/documents/{id}/share`
  Auth: job token required (`JobTokenInterceptor`).
  Response: `ShareLinkResponse` with URL + expiry.
- `POST /api/documents/{id}/share/revoke`
  Auth: **not enforced by interceptor** (path not matched).
  Response: JSON message or 404 (`ShareController`).
  **Security risk**: token check is missing in filter (see Security section).

### `QaController` (`/api/questions`)
- `POST /api/questions`
  Auth: job token required (cookie or `X-Job-Token`; `JobTokenInterceptor` + controller checks).
  Request: JSON body or `application/x-www-form-urlencoded` with `document_id` + `question`.
  Response: JSON `QuestionResponse` or HTML fragment when HTMX header present.
- `GET /api/questions/{document_id}`
  Auth: job token required (`JobTokenInterceptor` + controller check).

### `PubSubController` (`/internal`)
- `POST /internal/pubsub`
  Auth: OIDC JWT from Pub/Sub push (`PubSubTokenVerifier`).
  Response: 204 on success, 4xx/5xx on error.

### `web` controllers (HTML)
- `GET /` – landing page (`HomeController`, `templates/index.html`)
  Auth: public.
- `GET /documents/{id}/report` – report view (`ReportController`)
  Auth: job token required (`JobTokenInterceptor`).
- `GET /documents/{id}/share/{token}` – shared report view (`ShareReportController`)
  Auth: public share token (validated in service).

### Health and readiness
- `GET /health` (`HealthController`)
  Auth: public.
- `GET /readiness` (`ReadinessEndpoint`)
  Auth: public.
- Actuator endpoints: `/actuator/health`, `/actuator/readiness`, `/actuator/metrics`, `/actuator/prometheus` (`application.yml`).

## 7) External integrations

| Integration | Where configured | Required creds | Failure mode | Local fallback |
|---|---|---|---|---|
| Google Cloud Storage | `application.yml`, `GcsStorageService` | ADC or `GOOGLE_APPLICATION_CREDENTIALS` (implicit) | Upload/download throws IO exceptions | Local storage service (`LocalStorageService`) |
| Google Pub/Sub | `application.yml`, `PubSubService`, `DocumentProcessingWorker` | ADC + Pub/Sub topic/subscription | Publisher/subscriber init fails | No‑op publisher (`NoopJobPublisher`) |
| Pub/Sub Push Auth | `PubSubTokenVerifier` | `PUBSUB_PUSH_EXPECTED_EMAIL`, `PUBSUB_PUSH_EXPECTED_AUDIENCE` | Rejects push if missing/invalid | Can disable verification with `PUBSUB_PUSH_VERIFICATION_ENABLED=false` |
| Vertex AI Gemini | `GeminiService` | ADC + `VERTEX_AI_ENABLED=true` + project/location | Throws if client init fails | Stub mode when `VERTEX_AI_ENABLED=false` |
| Datadog | `DatadogMetricsConfig`, `logback-spring.xml`, `docker-compose.datadog.yml` | `DD_*` + `DD_API_KEY` for agent | Metrics/logs not exported | Disabled by default |

## 8) Observability and ops readiness

- Logging: console pattern by default; JSON logs when `datadog.enabled=true` (`logback-spring.xml`).
- Correlation IDs: `CorrelationIdFilter` adds `X-Request-ID` and MDC fields.
- Tracing: OpenTelemetry API usage with Datadog stub when disabled (`TracingServiceStub`).
- Metrics: Micrometer + StatsD registry when Datadog enabled (`DatadogMetricsConfig`, `DatadogMetricsService`).
- Health/readiness: `/health`, `/readiness` + actuator endpoints (`HealthController`, `ReadinessEndpoint`, `application.yml`).

## 9) Security and privacy quick scan

Implemented controls (evidence):
- File size limit 50MB and PDF content type check (`DocumentController`).
- PDF magic bytes validation (`PdfValidator`).
- Max pages and max text length validation (`PdfValidator`).
- Capability tokens stored as HMAC, not plaintext (`TokenService`, `V2__add_access_token.sql`).
- HttpOnly, SameSite=Strict cookie for browser token (`DocumentController`).
- CSRF protection via Origin/Referer checks for state‑changing requests (`JobTokenInterceptor`).
- Rate limiting stored in DB (`RateLimitService`, `V3__add_rate_limiting.sql`).
- Path traversal checks for local storage downloads (`LocalStorageService`).

Findings / risks:
- **Share revoke endpoint is not protected by job token.**
  `POST /api/documents/{id}/share/revoke` does not match any protected pattern, so it bypasses `JobTokenInterceptor`. Any caller can revoke a share link if they know the job ID (`JobTokenInterceptor`, `ShareController`).
- **Token secret default** (`APP_TOKEN_SECRET=change-me-in-production`) is logged as a warning; if not overridden, tokens are weak (`TokenService`).
- **CSRF allow‑origin logic is unusual.** `origin.startsWith(allowed.replace("localhost",""))` can match more than intended if `allowed` is not strict (`JobTokenInterceptor`).
- **DebugConfig writes DB connection metadata to disk** under `.cursor\debug.log`. Not a secret leak of passwords but can expose infrastructure details (`DebugConfig`).
- No evidence of antivirus scanning or content disarm/scan; PDFs go directly into storage (`DocumentController`, `StorageService`).

## 10) Deployability assessment

**Can it be deployed as-is?**
Yes, if a PostgreSQL instance is available, Docker is running (for local), and required env vars are set. Some security gaps still exist.

**What’s missing for a real deploy:**
- Verified DB credentials / secrets management.
- Fix for share‑revoke endpoint protection.
- Clear production env documentation for Google ADC and Datadog.

**Prioritized punch list**

P0 (blockers):
1. Protect `POST /api/documents/{id}/share/revoke` with job token (security bug) (`JobTokenInterceptor` patterns).
2. Local run requires correct DB credentials; Docker/DB must be running (startup failure observed).

P1 (should fix):
1. Tighten CSRF origin matching logic (avoid broad `startsWith` match) (`JobTokenInterceptor`).
2. Document and validate required cloud credentials for GCS/Pub/Sub/Vertex AI.
3. Improve OpenAPI spec coverage (current `docs/openapi.json` incomplete).

P2 (nice to have):
1. Add antivirus scanning or PDF sanitization step.
2. Add a local dev compose file with app + DB for one‑command start.
3. Clarify retention and data privacy policies in docs.

## 11) P0 fixes verification log

Milestone 0 (preflight):
- Docker engine reachable: `docker info` succeeded.
- Postgres container started from `docker-compose.yml`: `docker compose up -d postgres` succeeded (warning: orphan containers reported).
- `.env` not created (not required for the preflight checks; compose defaults used).

Milestone 1 (job-token enforcement):
- `.\mvnw.cmd test` passed. Surefire summary: `Tests run: 70, Failures: 0, Errors: 0, Skipped: 0`.
- Verified unauthorized revoke is blocked: `POST /api/documents/{id}/share/revoke` returns 401 without `X-Job-Token` (covered by `ShareLinkRevocationTest`).

Milestone A3 (repo hygiene verification):
- Commands run: `.\mvnw.cmd test`, `powershell -ExecutionPolicy Bypass -File scripts/verify-local.ps1 -PdfPath "src/test/resources/valid.pdf"`.
- Surefire summary: `Tests run: 71, Failures: 0, Errors: 0, Skipped: 0`.
- verify-local highlights: `Postgres is healthy after 6 attempt(s).` `App is healthy after 1 attempt(s).` `App is ready after 1 attempt(s).` `Migrations are ready after 1 attempt(s).` `Status: SUCCESS`.

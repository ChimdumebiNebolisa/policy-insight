# PolicyInsight

PolicyInsight is a document analysis service that ingests PDFs, extracts grounded evidence, and delivers plain-English risk reports with citations, optimized for fast response time and operational observability.

## Live demo

**URL:** https://policy-insight-828177954618.us-central1.run.app

**Try it in 3 steps:**
1. Open the live demo URL in a browser.
2. Upload a PDF policy/contract (<= 50 MB).
3. Wait for processing, then view the report and shareable link.

## Architecture

- Browser/UI -> Cloud Run web service (Spring Boot + Thymeleaf)
- Cloud SQL (PostgreSQL) for jobs, reports, and capability tokens
- GCS for PDF storage
- Pub/Sub for async processing
- Cloud Run worker -> Vertex AI (Gemini) for analysis
- Datadog for logs, traces, and metrics

## Core endpoints

- `POST /api/documents/upload` Upload a PDF and receive a job token
- `GET /api/documents/{id}/status` Processing status (token required)
- `GET /documents/{id}/report` Rendered report with citations (token required)
- `POST /api/questions` Grounded Q&A for a document (token required)
- `POST /api/documents/{id}/share` Create a share link
- `GET /documents/{id}/share/{token}` Public shared report

### Sample demo (offline, no DB)

These endpoints work without Cloud SQL, Pub/Sub, Vertex AI, or any external calls. Use them to try the app when the live pipeline is unavailable or in sleep mode.

- `GET /sample-report` Renders a static sample report (citations, sections, risk bullets). No DB or upload required.
- `GET /sample-pdf` Serves a bundled sample PDF with correct `Content-Type: application/pdf`.

The homepage includes **Try sample report** and **Download sample PDF** buttons that use these routes.

## Local dev

1. Start Postgres: `docker compose up -d`
2. Run the app: `.\mvnw.cmd spring-boot:run` (Windows) or `./mvnw spring-boot:run`
3. Open: `http://localhost:8080`

### Demo sleep mode (no DB required)

When `DEMO_SLEEP=true` and profile `demSleep` is active (`SPRING_PROFILES_ACTIVE=cloudrun,demSleep`), the web service starts without requiring Cloud SQL. The revision becomes Ready and `GET /` returns 200 with a sleep landing page.

**Sleep mode disclaimer:** When in sleep mode, the homepage shows a red banner: *"Live demo may be paused to reduce costs. If upload fails, refresh later or view sample report."* Use **Try sample report** and **Download sample PDF** on the homepage to try the app without upload or DB.

**Quick verification (local):**

- Windows: `$env:DEMO_SLEEP="true"; $env:SPRING_PROFILES_ACTIVE="demSleep"; .\mvnw.cmd spring-boot:run`
- Linux/macOS: `DEMO_SLEEP=true SPRING_PROFILES_ACTIVE=demSleep ./mvnw spring-boot:run`

Then in another terminal (or after startup):

- `GET /` → 200, sleep landing page
- `GET /health` → 200, status UP
- `GET /readiness` → 200, status UP
- `GET /sample-report` → 200 (offline sample report)
- `GET /sample-pdf` → 200 (bundled PDF)

**Note:** Stopping Cloud SQL should not affect `GET /` returning 200 when the web service is in sleep mode.

## Deployment

Preferred: push to `main` and let `.github/workflows/cd.yml` deploy the web and worker services.

Minimal manual steps:
1. Enable APIs: `run`, `sqladmin`, `storage`, `pubsub`, `artifactregistry`, `secretmanager`
2. Create Cloud SQL, GCS bucket, and Pub/Sub topic
3. Store DB/app secrets in Secret Manager
4. Deploy:
   - `gcloud run deploy policyinsight-web ...`
   - `gcloud run deploy policyinsight-worker ...` (no unauth, Pub/Sub push)
5. Configure Pub/Sub push to `https://<worker-url>/internal/pubsub`

## CI/CD highlight

- Reduced production risk during deployments by enforcing a 90/10 canary rollout with automated post-deploy health checks before full traffic promotion (GitHub Actions, Cloud Run, Docker)

Evidence: `.github/workflows/cd.yml` lines `139-178`, `186-208`, `388-392`

## Performance impact (live demo)

Delivered faster results for users during public demo traffic by reducing tail latency.

- Tail latency improved: p99 reduced by 25% (1.28s → 0.97s)
- Typical latency improved: p50 reduced by 30% (467ms → 325ms)
- p95 improved: 998ms → 957ms (4%)

**Before (baseline):**
- p50: 467ms
- p95: 998ms
- p99: 1.283s

**After (production-like demo load):**
- p50: 325ms
- p95: 957ms
- p99: 0.968s

**How this was measured**
- Latency improved by removing a worst-case request path and shortening the median execution path, measured via Google Cloud Run `request_latencies` percentiles (p50, p95, p99) for the same service.

## Troubleshooting

- **App fails to start**: Check `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` and Cloud SQL connectivity.
- **Uploads fail**: Ensure the file is a PDF, <= 50 MB, and the request is `multipart/form-data`.
- **401/403 on report or status**: Supply the job token via `X-Job-Token` or `pi_job_token_{id}` cookie.
- **Pub/Sub push failures**: Verify the worker service account has `roles/run.invoker` and Pub/Sub OIDC config uses the worker URL as audience.
- **Slow processing**: Inspect worker logs and Vertex AI quota/rate limits.

## Roadmap

- Add a minimal React front end for faster UX iteration.
- Support batch uploads and templated report exports.
- Expand policy-specific heuristics and citation ranking.

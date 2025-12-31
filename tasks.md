# PolicyInsight Implementation Tasks

This file tracks the implementation milestones for PolicyInsight, derived from the PRD requirements.

## Milestone 1: Repo scaffold + local run + CI

**Scope:**
- Spring Boot 3 foundation with Java 21
- Flyway migrations for baseline schema (all 5 tables)
- Health endpoints (/health, /readiness)
- Minimal UI routes (GET /, GET /error)
- Minimal API routes with stubbed responses (POST /api/documents/upload, GET /api/documents/{id}/status)
- OpenAPI documentation (springdoc) at /v3/api-docs and Swagger UI
- GitHub Actions CI: build + test on PRs (no deploy yet)

**Acceptance Criteria:**
- ✅ Spring Boot app starts locally with one command (docker-compose up OR ./mvnw spring-boot:run with Postgres running)
- ✅ Flyway migrations run and create baseline tables from the PRD schema
- ✅ Health endpoints: /health and /readiness return JSON
- ✅ Minimal UI routes: GET / (upload page placeholder), GET /error friendly page
- ✅ Minimal API routes with stubbed responses: POST /api/documents/upload, GET /api/documents/{id}/status
- ✅ OpenAPI available (springdoc) at /v3/api-docs and Swagger UI enabled
- ✅ GitHub Actions CI: build + test on PRs (no deploy yet)

**Demo Evidence:**
- Screenshot: App starts locally, health endpoint returns 200
- Screenshot: Swagger UI showing API endpoints
- Screenshot: GitHub Actions CI passing on a PR
- Screenshot: Database tables created (via psql or pgAdmin)

---

## Milestone 2: Database schema + JPA layer

**Scope:**
- Complete Flyway migrations (all 5 tables: policy_jobs, document_chunks, reports, qa_interactions, share_links)
- JPA entities for all tables with proper relationships
- Spring Data JPA repositories
- DTOs for request/response validation
- Input validation annotations

**Acceptance Criteria:**
- ✅ All 5 tables created via Flyway with proper indexes and foreign keys
- ✅ JPA entities map correctly to database schema
- ✅ Repositories support CRUD operations
- ✅ DTOs with validation annotations (Bean Validation)
- ✅ Exception handling for validation errors

**Demo Evidence:**
- Screenshot: Database schema diagram showing all tables and relationships
- Screenshot: JPA entity classes with annotations
- Screenshot: Repository test passing

---

## Milestone 3: Document upload + cloud storage

**Scope:**
- GCS integration for PDF storage
- Pub/Sub topic and subscription setup
- Multipart file upload handling with validation (size, type)
- Job creation and status tracking
- GCS client configuration

**Acceptance Criteria:**
- ✅ PDF upload validates file size (20 MB max) and type
- ✅ Uploaded PDFs stored in GCS with proper path structure
- ✅ Job record created in policy_jobs table with PENDING status
- ✅ Pub/Sub message published on successful upload
- ✅ Job status endpoint returns real-time status

**Demo Evidence:**
- Screenshot: Successful PDF upload via API
- Screenshot: GCS bucket showing uploaded file
- Screenshot: Pub/Sub message in topic
- Screenshot: Job record in database

---

## Milestone 4: Document processing pipeline

**Scope:**
- Google Cloud Document AI integration for text extraction and OCR
- Fallback OCR/text extraction (PDFBox + Tesseract)
- Text chunking with semantic boundaries
- Citation mapping (page numbers, character offsets)
- Document classification (rules-based + optional LLM)

**Acceptance Criteria:**
- ✅ Document AI extracts text and OCR from PDFs (stub implementation, fallback used by default)
- ✅ Fallback mechanism works when Document AI unavailable
- ✅ Text chunked into semantic segments with page/offset tracking
- ✅ Chunks stored in document_chunks table
- ✅ Document classified as TOS, PRIVACY_POLICY, or LEASE_AGREEMENT
- ✅ Classification confidence stored

**Demo Evidence:**
- Screenshot: Document AI processing result
- Screenshot: Chunks table with page numbers and offsets
- Screenshot: Classification result in job record
- Screenshot: Fallback OCR triggered (test scenario)

---

## Milestone 5: Risk analysis + report generation

**Scope:**
- Risk taxonomy scanning (5 categories: Data/Privacy, Financial, Legal Rights Waivers, Termination, Modification)
- Vertex AI Gemini integration for report generation
- Grounded report generation with citation enforcement
- JSONB storage for report sections
- Report assembly and GCS storage

**Acceptance Criteria:**
- ✅ Risk taxonomy scan identifies risks in all 5 categories
- ✅ "Not detected" explicitly recorded for absent risks
- ✅ Gemini generates plain-English summary bullets with citations
- ✅ Every claim in report references source chunk + page number
- ✅ Report stored in reports table (JSONB) and GCS
- ✅ Job status updated to SUCCESS on completion

**Demo Evidence:**
- Screenshot: Risk taxonomy JSON structure
- Screenshot: Generated report with citations
- Screenshot: Report stored in database (JSONB fields)
- Screenshot: Gemini API call logs/traces

---

## Milestone 6: Q&A system + UI foundation

**Scope:**
- Cite-or-abstain Q&A handler with Gemini
- Grounding validation (claims must match chunks)
- Thymeleaf templates for UI pages
- htmx integration for upload progress and status polling
- Report display page with all 5 sections

**Acceptance Criteria:**
- ✅ Q&A endpoint accepts questions and returns cited answers or "insufficient evidence"
- ✅ Answers validated against document chunks
- ✅ UI shows upload form with htmx progress updates
- ✅ Status polling updates automatically
- ✅ Report page displays all 5 sections with citations
- ✅ Citations are clickable/interactive

**Demo Evidence:**
- Screenshot: Q&A interaction with citation
- Screenshot: Q&A interaction with "insufficient evidence" response
- Screenshot: UI showing upload progress
- Screenshot: Report page with all sections

---

## Milestone 7: Export & sharing

**Scope:**
- PDF export with inline citations and watermark
- Shareable link generation with 7-day TTL
- Read-only access for shared links
- "Not legal advice" disclaimer on all exports
- Share link expiration handling

**Acceptance Criteria:**
- ✅ PDF export includes all report sections with citations
- ✅ Watermark and disclaimer visible on exported PDF
- ✅ Share link generated with unique token
- ✅ Share link expires after 7 days
- ✅ Shared report is read-only
- ✅ Access count tracked

**Demo Evidence:**
- Screenshot: Exported PDF with citations and watermark
- Screenshot: Share link generation
- Screenshot: Shared report view (read-only)
- Screenshot: Expired link handling

---

## Milestone 8: Datadog observability

**Scope:**
- Datadog APM instrumentation (dd-java-agent)
- Structured JSON logging with correlation IDs
- Datadog dashboards (PolicyInsight-Ops) with key metrics
- 3+ monitors (API latency, queue backlog, LLM cost anomaly)
- SLO definitions and tracking
- Incident automation (auto-create incidents from monitors)
- Dashboard and monitor JSON exports in /datadog/

**Acceptance Criteria:**
- ✅ Traces visible in Datadog for all requests
- ✅ Structured logs with correlation IDs
- ✅ Dashboard shows latency, errors, cost, coverage metrics
- ✅ 3+ monitors configured and exported as JSON
- ✅ SLOs defined and exported
- ✅ Monitor triggers create incidents automatically
- ✅ All Datadog configs exported to /datadog/ directory

**Demo Evidence:**
- Screenshot: Datadog trace waterfall
- Screenshot: Dashboard with all metrics
- Screenshot: Monitor triggered and incident created
- Screenshot: Incident context with traces and logs
- Screenshot: /datadog/ directory with JSON exports

---

## Milestone 9: Cloud deployment + CI/CD

**Scope:**
- Cloud Run deployment for web and worker services
- Cloud SQL Postgres setup with Flyway migrations
- GCS bucket provisioning
- Pub/Sub topic and subscription configuration
- GitHub Actions CD workflow (deploy on main branch)
- Rollback strategy and versioning
- Health check validation after deployment

**Decisions:**
- **Auth:** Workload Identity Federation (OIDC) via `google-github-actions/auth@v1` (no JSON keys). Workflow permissions: `contents: read`, `id-token: write`.
- **Deploy Strategy:** Deploy new revision with `--no-traffic` and attach revision tag (e.g., `web-${VERSION}`). Smoke tests hit tag URL derived from service's actual `status.url` (format: `https://TAG---HOST` where HOST is extracted from service URL). Promote traffic after verification.
- **Worker Model:** Cloud Run service (not worker pool) with ingress `all` (default, allows Pub/Sub push) and `--no-allow-unauthenticated` (IAM auth required). Pub/Sub push subscription with authenticated invocation to `/internal/pubsub` endpoint. Worker is secured via IAM (not ingress restriction) - unauthenticated requests return 401/403.
- **Traffic Rules:** WEB may canary (90/10) when previous revision exists; WORKER always promotes 100% (no canary).
- **Cloud SQL:** Cloud Run service attached via `--add-cloudsql-instances` annotation, which provides Unix socket at `/cloudsql/INSTANCE_CONNECTION_NAME`. Application uses standard PostgreSQL JDBC driver with TCP connection to `DB_HOST:DB_PORT` (Cloud SQL instance's private IP via VPC connector or public IP). Connection details stored in Secret Manager. Flyway runs automatically at Spring Boot startup; verify via `flyway_schema_history` table after deploy using Cloud SQL Auth Proxy or direct connection.
- **Pub/Sub Push Auth:** Requires IAM bindings: (1) Grant `roles/iam.serviceAccountTokenCreator` to Pub/Sub service agent `service-${PROJECT_NUMBER}@gcp-sa-pubsub.iam.gserviceaccount.com` on the push-auth service account (worker service account), (2) Grant `roles/run.invoker` on the worker service to the push-auth service account (worker service account).

**Acceptance Criteria (with verification commands):**

1. **Cloud Run services deployed (web + worker)**
   - Verification: `gcloud run services list --region=us-central1 --format="table(metadata.name,status.url)"` shows both `policyinsight-web` and `policyinsight-worker`
   - Verification: `curl -f $(gcloud run services describe policyinsight-web --region=us-central1 --format='value(status.url)')/health` returns 200
   - Verification: Unauthenticated request to worker returns 401/403: `curl -v $(gcloud run services describe policyinsight-worker --region=us-central1 --format='value(status.url)')/health` shows 401 or 403 (proves IAM auth is required)
   - Verification: Authenticated worker health check succeeds: `gcloud run services proxy policyinsight-worker --region=us-central1 --port=8080 & sleep 2 && curl -f http://localhost:8080/health && pkill -f "gcloud run services proxy"`

2. **Cloud SQL instance with database and migrations applied**
   - Verification: `gcloud sql instances describe policyinsight-db --format='value(name,connectionName)'` shows instance exists
   - Verification: `gcloud sql databases list --instance=policyinsight-db --format='value(name)'` includes `policyinsight`
   - Verification: After deployment, connect via Cloud SQL Auth Proxy and run `SELECT COUNT(*) FROM flyway_schema_history;` shows migration count > 0 (or verify service startup succeeded, which indicates Flyway ran)

3. **GCS bucket accessible from services**
   - Verification: `gsutil ls gs://policyinsight-prod-documents` succeeds
   - Verification: Upload test file via API, then `gsutil ls gs://policyinsight-prod-documents/` shows the file

4. **Pub/Sub configured and working**
   - Verification: `gcloud pubsub topics list --format='value(name)'` includes `policyinsight-analysis-topic`
   - Verification: `gcloud pubsub subscriptions list --format='value(name)'` includes `policyinsight-analysis-sub`
   - Verification: `gcloud pubsub subscriptions describe policyinsight-analysis-sub --format='value(pushConfig.pushEndpoint)'` shows worker service URL with `/internal/pubsub` path
   - Verification: `gcloud pubsub subscriptions describe policyinsight-analysis-sub --format='value(pushConfig.pushAuthServiceAccount)'` shows worker service account (authenticated push)
   - Verification: Upload document via API, verify Pub/Sub message published and worker processes it: `gcloud pubsub subscriptions pull policyinsight-analysis-sub --limit=1 --format=json`

5. **GitHub Actions CD deploys on merge to main**
   - Verification: Push to main branch, workflow `.github/workflows/cd.yml` runs successfully
   - Verification: New revision deployed with `--no-traffic` and tag (e.g., `web-abc1234`), smoke tests pass using tag URL, traffic promoted

6. **Tagged revision smoke tests use correct tag URL format**
   - Verification: CD workflow derives tag URL from service's actual `status.url`: `WEB_URL=$(gcloud run services describe policyinsight-web --region=$REGION --format='value(status.url)')`, `HOST=$(echo "$WEB_URL" | sed 's#https://##')`, `TAG_URL="https://web-${VERSION}---${HOST}"`
   - Verification: Smoke test hits `$TAG_URL/health` and `$TAG_URL/readiness` successfully

7. **Deployment includes version tags (DD_VERSION)**
   - Verification: `gcloud run services describe policyinsight-web --region=us-central1 --format='value(spec.template.spec.containers[0].env)' | grep DD_VERSION` shows `DD_VERSION=abc1234` (git commit SHA)
   - Verification: Datadog traces show `version` tag matching git commit SHA

8. **Rollback workflow handles traffic shift correctly**
   - Verification: Run `.github/workflows/rollback.yml` manually with `service=web`, verify traffic shifts to previous revision
   - Verification: Rollback workflow lists revisions, selects previous revision deterministically, fails clearly if none exists
   - Verification: `gcloud run services describe policyinsight-web --region=us-central1 --format='value(status.traffic)'` shows previous revision receiving traffic
   - Verification: Health checks pass after rollback

9. **Health checks pass after deployment**
   - Verification: `curl -f $(gcloud run services describe policyinsight-web --region=us-central1 --format='value(status.url)')/health` returns 200 with `{"status":"UP"}`
   - Verification: `curl -f $(gcloud run services describe policyinsight-web --region=us-central1 --format='value(status.url)')/readiness` returns 200 with `{"status":"UP"}`

10. **Pub/Sub push authentication IAM bindings configured**
    - Verification: `gcloud iam service-accounts get-iam-policy policyinsight-worker@${PROJECT_ID}.iam.gserviceaccount.com --format='value(bindings.role,bindings.members)' | grep serviceAccountTokenCreator` shows Pub/Sub service agent
    - Verification: `gcloud run services get-iam-policy policyinsight-worker --region=us-central1 --format='value(bindings.role,bindings.members)' | grep run.invoker` shows worker service account

**Implementation Files:**
- `.github/workflows/cd.yml` - CD workflow with WIF auth, build, deploy with tags, smoke tests (tag URL derivation), traffic promotion
- `.github/workflows/rollback.yml` - Manual rollback workflow with traffic shift (handles missing previous revision)
- `infra/cloudrun/web.yaml` - Cloud Run web service config (DD_VERSION, Cloud SQL annotation)
- `infra/cloudrun/worker.yaml` - Cloud Run worker service config (DD_VERSION, ingress all for Pub/Sub push, Cloud SQL annotation)
- `DEPLOYMENT.md` - Complete GCP setup guide with WIF, Cloud SQL, GCS, Pub/Sub, IAM bindings (corrected push auth)

**Demo Evidence:**
- Screenshot: Cloud Run services running
- Screenshot: Cloud SQL database with tables
- Screenshot: GitHub Actions CD workflow success
- Screenshot: Deployment version tags (DD_VERSION in service env vars)
- Screenshot: Rollback execution (test scenario)

---

## Progress Tracking

- [x] Milestone 1: Repo scaffold + local run + CI
- [x] Milestone 2: Database schema + JPA layer
- [x] Milestone 3: Document upload + cloud storage
- [x] Milestone 4: Document processing pipeline
- [x] Milestone 5: Risk analysis + report generation
- [x] Milestone 6: Q&A system + UI foundation
- [x] Milestone 7: Export & sharing
- [x] Milestone 8: Datadog observability
- [x] Milestone 9: Cloud deployment + CI/CD


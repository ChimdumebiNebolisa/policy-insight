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

**Acceptance Criteria:**
- ✅ Cloud Run services deployed (web + worker)
- ✅ Cloud SQL instance with database and migrations applied
- ✅ GCS bucket accessible from services
- ✅ Pub/Sub configured and working
- ✅ GitHub Actions CD deploys on merge to main
- ✅ Deployment includes version tags (DD_VERSION)
- ✅ Rollback workflow tested
- ✅ Health checks pass after deployment

**Demo Evidence:**
- Screenshot: Cloud Run services running
- Screenshot: Cloud SQL database with tables
- Screenshot: GitHub Actions CD workflow success
- Screenshot: Deployment version tags
- Screenshot: Rollback execution (test scenario)

---

## Progress Tracking

- [x] Milestone 1: Repo scaffold + local run + CI
- [x] Milestone 2: Database schema + JPA layer
- [x] Milestone 3: Document upload + cloud storage
- [x] Milestone 4: Document processing pipeline
- [ ] Milestone 5: Risk analysis + report generation
- [ ] Milestone 6: Q&A system + UI foundation
- [ ] Milestone 7: Export & sharing
- [ ] Milestone 8: Datadog observability
- [ ] Milestone 9: Cloud deployment + CI/CD


# PolicyInsight

[![CI](https://github.com/ChimdumebiNebolisa/policy-insight/actions/workflows/ci.yml/badge.svg?branch=rebuild%2Fsimple-gemini-railway)](https://github.com/ChimdumebiNebolisa/policy-insight/actions/workflows/ci.yml?query=branch%3Arebuild%2Fsimple-gemini-railway)

## What this is

PolicyInsight is a **Spring Boot 3** web application for reviewing **policies, agreements, and contracts**. Users can **upload a PDF** or **paste plain text**; the backend extracts text, splits it into stored source sections, runs **AI-backed or mock** report generation, **validates citations** against those chunks, and serves a **structured report** with **source-backed evidence** in the browser. **Owner cookies** gate access to private jobs and reports; **expiring share links** expose read-only reports. **Grounded Q&A** is available on completed **user-submitted** reports (not on the deterministic sample-only path).

## Problem it solves

People often need to understand long policies, agreements, or contracts without reading every line. PolicyInsight reduces that friction by **extracting text**, **chunking** source material, producing a **structured report**, **checking cited chunk IDs** against stored excerpts, and surfacing **evidence** next to claims so results are easier to **review**, **share**, and **follow up with questions** tied to the saved document text.

## Demo

Live demo:

Not currently included in this README.

Screenshots:

Not currently included in this README.

Video/GIF:

Not currently included in this README.

## Features

Implemented in this repository:

- **PDF upload**: validate file type/size, extract text with **Apache PDFBox**, chunk into source sections; **original PDF bytes are not persisted** (only extracted text in PostgreSQL).
- **Pasted text intake**: `POST /paste` with configurable maximum length (`APP_PASTE_MAX_CHARS` / `app.paste.max-chars`).
- **Structured reports** for uploads: async pipeline with persisted job statuses (`UPLOADED`, `TEXT_EXTRACTED`, `BUILDING_AI_REPORT`, `VALIDATING_CITATIONS`, `COMPLETED`, `FAILED`, etc.).
- **Citation validation** against stored `document_chunks` before persisting the final report JSON.
- **Source evidence** in the report UI (cited excerpts aligned with report sections).
- **Deterministic fictional samples** (no live Gemini): bundled PDF and text under `src/main/resources/samples/`. Entry points include `GET /sample`, `GET /sample-report`, and `GET /sample/{sampleKey}` for keys such as `vendor-agreement` (default), `privacy-policy`, `employment-policy`, and `campus-student-policy`.
- **Grounded Q&A** on **upload-generated** reports via `POST /qa/{reportId}` (owner cookie required).
- **Share links**: create, revoke, regenerate; **read-only** shared view at `GET /shared/{token}`.
- **Markdown export** for owners: `GET /report/{reportId}/export.md`.
- **Upload failure UX**: safe error messages, optional **retry** (`POST /retry/{jobId}`) and **demo-style fallback** from extracted text (`POST /fallback/{jobId}`) when analysis fails.
- **Owner-cookie access model** (`PI_OWNER_<jobIdWithoutDashes>`) plus **HMAC-stored** share token hashes (not raw tokens in the database).
- **In-memory per-IP rate limiting** for upload, paste, and Q&A (MVP-style; resets on restart and is per instance).
- **Scheduled cleanup** for old jobs, related data (cascades), and expired share links (configurable retention and delays).
- **JSON job status API** for polling: `GET /api/jobs/{jobId}/status` (same owner cookie as HTML status).
- **CI**: GitHub Actions runs **Maven verify** (unit + Testcontainers integration profile) and a **Docker image build**; see [`.github/workflows/ci.yml`](.github/workflows/ci.yml).
- **Container deploy**: multi-stage [`Dockerfile`](Dockerfile); [`render.yaml`](render.yaml) for **Render**; [`railway.json`](railway.json) for **Railway**-style Dockerfile deploy with `/health` (no claim that either platform is currently provisioned for you).

## Tech stack

Frontend:

Thymeleaf, HTMX, CSS (`src/main/resources/static/css/`).

Backend:

Java **21**, **Spring Boot 3.3** (Maven, `mvnw` / `mvnw.cmd`).

Database:

**PostgreSQL** with **Flyway** migrations (`src/main/resources/db/migration/`). **H2** is used for the default **Surefire** test profile (`src/test/resources/application.yml`). **Testcontainers** PostgreSQL is used when the **integration-tests** Maven profile runs with Docker available.

AI/API:

**Google Gemini** when `APP_AI_PROVIDER=gemini` (see `app.gemini.*` in [`application.yml`](src/main/resources/application.yml)); **mock** analyzer for local/CI defaults (`APP_AI_PROVIDER=mock`). Default model property is `gemini-2.5-flash` unless overridden by `GEMINI_MODEL`.

Authentication:

**No full user accounts.** Access is **HTTP-only owner cookies** set on upload/paste/sample flows, plus **unguessable share tokens** for read-only shared reports. This is session-style ownership, not OAuth or password login.

Deployment:

**Docker** (see `Dockerfile`, `docker compose` for local Postgres). **Render** blueprint (`render.yaml`) and **Railway** Dockerfile config (`railway.json`) are present; you supply secrets and database URLs on the host. `DATABASE_URL` (non-JDBC) is mapped to Spring datasource properties via `DatabaseUrlEnvironmentPostProcessor` when `SPRING_DATASOURCE_URL` is not set.

Other tools:

Apache **PDFBox**, **Maven Wrapper**, **GitHub Actions**, **Flyway**, **JUnit 5**.

## Setup

### 1. Clone the repo

```bash
git clone https://github.com/ChimdumebiNebolisa/policy-insight.git
cd policy-insight
```

### 2. Prerequisites

- **Java 21** (Temurin or equivalent).
- **Docker** (recommended): for `docker compose` local PostgreSQL and for `./mvnw verify -Pintegration-tests` (Testcontainers). Plain `./mvnw test` uses H2 and does not require Docker.

### 3. Local database (optional but typical for dev)

From the repo root:

```powershell
docker compose up -d
```

Compose maps host port **55432** → container `5432` (avoids clashing with a local Postgres on `5432`). Credentials match [`docker-compose.yml`](docker-compose.yml): database/user/password `policyinsight`.

### 4. Environment variables

Spring reads [`src/main/resources/application.yml`](src/main/resources/application.yml). Commonly set:

| Variable | Role |
|----------|------|
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | JDBC connection (defaults target `localhost:5432` if unset; use `55432` with the provided Compose mapping). |
| `DATABASE_URL` | Render/Heroku-style URL; applied when Spring datasource URL is not already set (see `DatabaseUrlEnvironmentPostProcessor`). |
| `APP_TOKEN_SECRET` | HMAC secret for owner tokens; must meet app validation (not short/default in production-style runs). |
| `APP_AI_PROVIDER` | `mock` or `gemini`. |
| `GEMINI_API_KEY`, `GEMINI_MODEL`, `GEMINI_TIMEOUT_SECONDS` | Gemini client configuration when `APP_AI_PROVIDER=gemini`. |
| `APP_UPLOAD_MAX_BYTES`, `APP_PASTE_MAX_CHARS` | Upload and paste size limits. |
| `APP_OWNER_TOKEN_TTL_MINUTES`, `APP_SHARE_TTL_DAYS` | Owner cookie lifetime and default share expiry. |
| `APP_CLEANUP_ENABLED`, `APP_RETENTION_*`, `APP_JOB_STALE_MINUTES`, `APP_CLEANUP_FIXED_DELAY_MS` | Scheduled cleanup behavior. |
| `PORT` | HTTP port (default `8080`; Docker `ENTRYPOINT` respects `PORT`). |

**Local mock example (PowerShell):**

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:55432/policyinsight"
$env:SPRING_DATASOURCE_USERNAME="policyinsight"
$env:SPRING_DATASOURCE_PASSWORD="policyinsight"
$env:APP_TOKEN_SECRET="replace-with-a-long-random-secret-that-is-not-short"
$env:APP_AI_PROVIDER="mock"
```

**Gemini example (illustrative; set on your host, not in CI):**

```text
APP_AI_PROVIDER=gemini
GEMINI_API_KEY=<your key>
GEMINI_MODEL=gemini-2.5-flash-lite
GEMINI_TIMEOUT_SECONDS=180
```

### 5. Run the application

```powershell
.\mvnw.cmd spring-boot:run
```

On Linux/macOS use `./mvnw`. Open `http://localhost:8080` (or the host/port your platform assigns).

## Testing

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean test
```

Runs **Surefire** with **H2** (no Docker required).

```powershell
.\mvnw.cmd verify -Pintegration-tests
```

Runs unit tests plus **Failsafe** / Testcontainers-backed PostgreSQL checks when Docker is available; integration tests skip cleanly without Docker where configured.

**CI (Ubuntu):** `./mvnw --batch-mode verify -Pintegration-tests`, then `docker build -t policy-insight:ci .` in a dependent job. Failed runs upload Surefire/Failsafe report directories as an artifact.

## HTTP surface (summary)

| Method | Path | Notes |
|--------|------|--------|
| `GET` | `/` | Landing: upload + paste + sample links. |
| `POST` | `/upload` | Multipart PDF; returns HTMX upload-started fragment; sets owner cookie. |
| `POST` | `/paste` | Form URL-encoded text; same pattern. |
| `GET` | `/status/{jobId}` | Owner-only status fragment (HTMX polling when in progress). |
| `POST` | `/retry/{jobId}`, `/fallback/{jobId}` | Owner-only; failed-job retry or deterministic fallback report. |
| `GET` | `/report/{reportId}` | Owner-only report page. |
| `GET` | `/report/{reportId}/export.md` | Owner-only Markdown export. |
| `GET` | `/sample`, `/sample-report` | Redirect to default deterministic sample report. |
| `GET` | `/sample/{sampleKey}` | Named deterministic sample (see Features). |
| `POST` | `/share/{reportId}` (+ `/revoke`, `/regenerate`) | Owner-only share management fragments. |
| `GET` | `/shared/{token}` | Read-only shared report. |
| `POST` | `/qa/{reportId}` | Owner-only Q&A fragment. |
| `GET` | `/api/jobs/{jobId}/status` | JSON job status (owner cookie). |
| `GET` | `/health` | Liveness-style check (Actuator health exposed as configured). |

Most responses are **HTML** (pages or HTMX fragments), not a full public REST API.

## How it works (short)

1. **Upload or paste** creates a `policy_jobs` row, stores **chunked** `document_chunks`, and kicks off **async** report generation.
2. **Gemini or mock** returns structured report JSON; the service validates **cited chunk IDs**, then saves a `reports` row and marks the job complete.
3. The **Thymeleaf + HTMX** UI polls `/status/{jobId}` until completion or failure; owners can **share**, **export**, or **ask questions** on completed uploads.
4. **Samples** use fixed classpath documents and a **deterministic builder**—separate code path from live Gemini uploads.

## Retention and cleanup

- Cleanup is **in-process** (`@Scheduled`), gated by `APP_CLEANUP_ENABLED` and `APP_CLEANUP_FIXED_DELAY_MS`.
- Stale non-demo in-progress jobs can be marked failed (`APP_JOB_STALE_MINUTES`).
- Completed and failed jobs are deleted after retention windows; **CASCADE** removes chunks, reports, Q&A, and share links. Demo jobs are identified by **`demo_key`** and excluded from destructive cleanup paths as implemented.

## Security notes (high level)

- No raw share tokens in the database (hashes only). Owner tokens are hashed similarly.
- Thymeleaf escapes dynamic content in normal templates.
- Rate limits are **best-effort per instance**; not a substitute for edge rate limiting at scale.

## Known limitations

- **No OCR** for scanned PDFs (PDFBox text extraction only).
- **Citation validation** checks chunk ID references, not deep semantic entailment.
- **Async work and cleanup** run inside the web process—no external queue or worker tier in this repo.
- **Gemini** availability, quotas, and latency depend on your key and model settings.
- **Free-tier** hosts may cold-start or run slowly.

## Useful commands

```powershell
docker compose up -d
docker compose down
.\mvnw.cmd test
.\mvnw.cmd clean test
.\mvnw.cmd verify -Pintegration-tests
.\mvnw.cmd spring-boot:run
docker build -t policy-insight:local .
```

Use `./mvnw` on Unix-like systems.

## License

MIT License. See [LICENSE](LICENSE).

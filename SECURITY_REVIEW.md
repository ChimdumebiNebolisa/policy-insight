# Security Review

Review date: 2026-04-27

Security aid used: `vibe-security` skill was already installed locally at `C:\Users\Chimdumebi\.agents\skills\vibe-security`. I used its checklist and references for secrets, environment handling, AI integration, rate limiting, deployment, and data access. I did not run `npx skills add ...` because the skill was already available in the environment.

## Critical Findings

None found.

## High Findings

None remaining.

## Medium Findings

- In-memory rate limiting is intentionally simple for MVP. It resets on deploy/restart and is per application instance, so it is not sufficient for high-traffic or multi-instance abuse protection.
- Citation validation is referential only. It verifies that cited `chunkIds` exist for the job, but it does not verify that the cited chunk semantically supports the AI claim.

## Low Findings

- `gitleaks` was not installed in this environment, so a dedicated secret-scanner run could not be completed. Manual `git grep` checks found no tracked `.env` files or real secret-looking tokens.
- Gemini provider billing caps and Google-side quota settings were not verifiable from this repository. They should be configured in the Gemini/Google AI console before live deployment.

## Fixes Applied

- Removed the old GCP, Cloud Run, Cloud SQL, GCS, Pub/Sub, Vertex AI SDK, Datadog, worker, local storage, and legacy deployment files.
- Added `.gitignore` rules for `.env`, local secrets, credentials, key material, generated PDFs, and local artifacts.
- Kept `.env.example` placeholder-only.
- Added PDF validation for empty files, max file size, and PDF magic bytes before PDFBox extraction.
- Kept uploaded PDFs in memory only; the app stores extracted text chunks and does not persist the original PDF.
- Used Spring Data JPA repositories only; no raw SQL, native queries, or string-built SQL were introduced.
- Added owner-token protection for `/report/{reportId}` and `/status/{jobId}` so direct report URLs are not public by ID alone.
- Added public access only through `/shared/{token}` with secure random tokens, HMAC token hashes in the database, and expiration.
- Added explicit SameSite=Lax owner cookies with conditional Secure handling for HTTPS deployments.
- Rendered AI/user-controlled text through escaped Thymeleaf expressions; no `th:utext` or raw HTML rendering.
- Added in-memory per-IP rate limiting for upload and Q&A endpoints.
- Added Gemini missing-key and malformed-output handling.
- Added safe AI failure handling so provider errors are logged server-side without exposing raw Gemini messages to users.
- Added Railway/live-Gemini validation that rejects the default or short `APP_TOKEN_SECRET`.
- Added scheduled retention cleanup for expired share links, stale processing jobs, and old failed/completed non-demo jobs.
- Preserved demo/sample jobs during cleanup by excluding rows with a non-null `demo_key`.

## Checks Run

- `git ls-files .env .env.local`: no tracked environment files.
- `git grep -n -E "sk_live_|sk_test_|AKIA|ghp_|glpat-|xoxb-|Bearer |AIza[0-9A-Za-z_-]{20,}|postgres://[^ ]+:[^ ]+@"`: only documented placeholder syntax in `README.md`.
- `git grep -n -E "th:utext|utext|innerHTML|document\\.write"`: no raw HTML rendering; only HTMX `hx-swap="innerHTML"` into server-rendered escaped fragments.
- `git grep -n -E "createNativeQuery|@Query|Statement|executeQuery|queryForObject|jdbcTemplate"`: no raw SQL usage found.
- Full Maven test suite completed with all recorded Surefire summaries passing: 60 tests, 0 failures, 0 errors.
- Optional PostgreSQL/Testcontainers integration profile completed successfully with `.\mvnw.cmd verify -Pintegration-tests`; Docker was unavailable in this environment, so `PostgresIntegrationIT` was skipped by Testcontainers.

## Remaining Risks

- Live Gemini behavior was not tested against a real API key in this environment.
- The app does not scan PDFs for malware; it only validates size/type and extracts text with PDFBox.
- In-memory rate limiting is acceptable for the requested MVP but should be replaced with shared infrastructure if the app is scaled horizontally.
- Stronger semantic citation support validation is intentionally deferred.
- Async report generation is in-process. It improves the current status-polling UX but is not a durable external job queue.
- Scheduled cleanup is also in-process. It only runs while the app is up, and it is not a durable cleanup worker for multi-instance production deployments.

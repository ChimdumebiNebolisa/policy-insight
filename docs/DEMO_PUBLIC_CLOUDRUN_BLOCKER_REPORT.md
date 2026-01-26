# PolicyInsight Public Demo (Cloud Run) Blocker Report

### 1) Definition of Done (public demo)
- [ ] Public URL loads without requiring Google login
- [ ] Upload works from a normal browser
- [ ] Processing completes without operator steps
- [ ] User can view report

### 2) Current deployed reality (what exists now)
Cloud Run services (repo evidence):
- `policyinsight-web` and `policyinsight-worker` exist in `infra/cloudrun/web.yaml` and `infra/cloudrun/worker.yaml`.
- Milestone 3 execution report uses a single service name `policy-insight` in deploy commands (`docs/MILESTONE_3_EXECUTION_REPORT.md`), which likely reflects the last actual deploy.

Endpoints and UI flow (repo evidence):
- Upload: `POST /api/documents/upload` (HTMX form in `index.html`).
- Status: `GET /api/documents/{id}/status`.
- Report page: `GET /documents/{id}/report`.
- Report JSON: `GET /api/documents/{id}/report-json` (requires `X-Job-Token`).
- Share link API: `POST /api/documents/{id}/share`.
- Share report page: `GET /documents/{id}/share/{token}` (public share view).
- Pub/Sub push: `POST /internal/pubsub` (Cloud Run push handler).
- Health: `GET /health` and readiness via `/actuator/readiness`.

Pub/Sub push required or optional:
- Default config uses local processing: `app.messaging.mode=local` and `app.processing.mode=local` (`application.yml`).
- Cloud Run profile enables Pub/Sub settings but does not force `APP_PROCESSING_MODE=gcp`; that depends on env vars.
- Worker is disabled by default (`policyinsight.worker.enabled=false`), and web service explicitly disables scheduling in `infra/cloudrun/web.yaml` via `SPRING_AUTOCONFIGURE_EXCLUDE` (which blocks local scheduled worker).
- If `APP_PROCESSING_MODE=gcp` + `PUBSUB_PUSH_MODE=true` is set (as seen in `.github/workflows/cd.yml` for the worker), Pub/Sub push to `/internal/pubsub` is required.

Job token requirement (how it is issued/validated):
- Upload endpoint generates a token and stores its HMAC on the job; returns the token in JSON for API clients or sets an HttpOnly cookie for browser HTMX flow.
- Protected endpoints (status/report/export/share and `/documents/{id}/report`) require `X-Job-Token` or cookie via `JobTokenInterceptor`.
- `/internal/pubsub` is excluded from job token enforcement.

### 3) The single biggest blocker (pick ONE)
**Blocker: Public access not guaranteed (unauthenticated invocation may be off).**

- Symptom for random users: visiting the Cloud Run URL prompts for Google login or returns `403/401`, so the public demo page never loads.
- Root cause: Cloud Run service must allow unauthenticated invocation (`roles/run.invoker` to `allUsers`), and the repo does not hard-code that in YAML; it is only a deploy-time flag or IAM binding.
- Smallest fix: Grant unauthenticated invocation on the web service (one IAM binding or redeploy with `--allow-unauthenticated`).

### 4) Minimum viable public demo architecture (pick ONE)
Cloud Run autoscaling reality check (why not in-process `@Scheduled`):
- Cloud Run can scale to zero.
- By default, Cloud Run only allocates CPU while handling requests.
- Therefore an in-process `@Scheduled` worker is not reliable for background processing in a public demo unless you force min instances and CPU allocation.
- Because we want the easiest reliable demo, we are choosing Pub/Sub push + worker.

Chosen plan: **Path 1 (Cloud Run web + Cloud Run worker + Pub/Sub push)**.
- Minimum reason: it runs processing reliably without needing always-on instances.
- The single-service approach can remain as an alternate path, but it is **not recommended for demo** unless you force min instances and CPU behavior.

### 5) Exact “make it public + demoable” steps (commands + files)
Do this (tight checklist):
1) Make `policyinsight-web` public (unauthenticated)
```
gcloud run services add-iam-policy-binding policyinsight-web --region $REGION --project $PROJECT --member="allUsers" --role="roles/run.invoker"
```
Org policy may block `allUsers`; fallback: keep it private and rely on share-token flow, or disable Invoker IAM checks at the org level (if allowed).

#### Make web service public for demo
## Public demo: make policyinsight-web unauthenticated

### Option A (preferred): allow unauthenticated at deploy time
gcloud run deploy policyinsight-web \
  --allow-unauthenticated \
  --region us-central1 \
  --project $PROJECT

### Option B: make an existing service public via IAM binding
gcloud run services add-iam-policy-binding policyinsight-web \
  --member="allUsers" \
  --role="roles/run.invoker" \
  --region us-central1 \
  --project $PROJECT

### If org policy blocks allUsers (Domain Restricted Sharing)
Use Cloud Run “disable invoker IAM check” instead of allUsers:
- New service:
  gcloud run deploy policyinsight-web --no-invoker-iam-check --region us-central1 --project $PROJECT
- Existing service:
  gcloud run services update policyinsight-web --no-invoker-iam-check --region us-central1 --project $PROJECT

### Verify public access
gcloud run services describe policyinsight-web --region us-central1 --project $PROJECT

2) Confirm Pub/Sub push acknowledgement and auth expectations
- Ack only on: **102 / 200 / 201 / 202 / 204** (any other code triggers redelivery).
- Pub/Sub push can use authenticated push with a JWT in the `Authorization` header.

3) One end-to-end demo proof
- Browser upload
- Poll status until `SUCCESS`
- Open report URL

Minimal curl proof commands (API flow):
```
# Upload PDF (captures jobId + token)
curl -s -X POST "$SERVICE_URL/api/documents/upload" \
  -F "file=@/path/to/sample.pdf" | tee /tmp/upload.json

# Extract jobId + token (example using jq)
JOB_ID=$(jq -r ".jobId" /tmp/upload.json)
TOKEN=$(jq -r ".token" /tmp/upload.json)

# Check status (token header required by interceptor)
curl -s -H "X-Job-Token: $TOKEN" "$SERVICE_URL/api/documents/$JOB_ID/status"

# Report JSON (token required)
curl -s -H "X-Job-Token: $TOKEN" "$SERVICE_URL/api/documents/$JOB_ID/report-json"
```

### 6) Demo UX: how a random person uses it
Use the existing HTMX UI for a seamless public demo:
1. Visit the public home page (`/`).
2. Upload a PDF in the form.
3. Wait for status to update; HTMX polls and auto-redirects to `/documents/{id}/report` when `SUCCESS`.
4. View report in-browser.

This works because the upload sets an HttpOnly job-token cookie, and the interceptor accepts that cookie for status/report endpoints. If you need a shareable link, use the “Get Share Link” button on the report page; the share URL is public (`/documents/{id}/share/{token}`).

### 7) Proof checklist (resume-grade, minimal)
- One public URL
- One upload request (curl + browser)
- One final SUCCESS status
- One report page URL
- One screenshot or short recording instructions (no need to implement recording)

---

## Required repo scans (command + trimmed output)

### A) Public demo surface and auth gates
Command:
```
rg -n "@PostMapping|@GetMapping|/api/documents|/documents/.*/report|/health|/internal/pubsub" src --hidden
```
Trimmed output:
```
src\main\java\com\policyinsight\web\ReportController.java:58:    @GetMapping("/documents/{id}/report")
src\main\java\com\policyinsight\web\ShareReportController.java:51:    @GetMapping("/documents/{id}/share/{token}")
src\main\java\com\policyinsight\api\DocumentController.java:83:    @PostMapping("/upload")
src\main\java\com\policyinsight\api\DocumentController.java:255:    @GetMapping("/{id}/status")
src\main\java\com\policyinsight\api\DocumentController.java:336:    @GetMapping("/{id}/report-json")
src\main\java\com\policyinsight\api\PubSubController.java:76:    @PostMapping("/pubsub")
src\main\java\com\policyinsight\config\HealthController.java:21:    @GetMapping("/health")
```

Command:
```
rg -n "X-Job-Token|JobToken|TokenService|Interceptor|Share" src --hidden
```
Trimmed output:
```
src\main\java\com\policyinsight\security\JobTokenInterceptor.java:33:    private static final String TOKEN_HEADER = "X-Job-Token";
src\main\java\com\policyinsight\config\JobTokenInterceptorConfig.java:19:        registry.addInterceptor(jobTokenInterceptor)
src\main\java\com\policyinsight\api\DocumentController.java:182:                String token = tokenService.generateToken();
src\main\java\com\policyinsight\api\DocumentController.java:342:            @RequestHeader(value = "X-Job-Token", required = false) String tokenHeader,
src\main\java\com\policyinsight\web\ShareReportController.java:51:    @GetMapping("/documents/{id}/share/{token}")
```

Command:
```
rg -n "multipart|MaxFileSize|MaxRequestSize|50MB" src/main/resources --hidden
```
Trimmed output:
```
src/main/resources/application.yml:41:    multipart:
src/main/resources/application.yml:43:      max-file-size: 50MB
src/main/resources/application.yml:44:      max-request-size: 50MB
src/main/resources/templates/index.html:23:                  hx-encoding="multipart/form-data"
```

### B) Processing modes and async boundary
Command:
```
rg -n "APP_PROCESSING_MODE|APP_MESSAGING_MODE|WORKER_ENABLED|@Scheduled|PubSub" src/main/resources src .github --hidden
```
Trimmed output:
```
src/main/resources/application.yml:70:    mode: ${APP_MESSAGING_MODE:local}
src/main/resources/application.yml:72:    mode: ${APP_PROCESSING_MODE:local}
src/main/resources/application.yml:150:  enabled: ${WORKER_ENABLED:false}
src/main/resources/application.yml:156:    enabled: ${POLICYINSIGHT_WORKER_ENABLED:false}
.github/workflows/cd.yml:121:            ... APP_MESSAGING_MODE=gcp ... PUBSUB_PUSH_MODE=true ...
src/main/java/com/policyinsight/processing/LocalDocumentProcessingWorker.java:113:    @Scheduled(fixedDelayString = "${app.local-worker.poll-ms:2000}")
```

Command:
```
rg -n "/internal/pubsub|push|PubSubController|TokenVerifier" src --hidden
```
Trimmed output:
```
src\main\java\com\policyinsight\api\PubSubController.java:23: * Controller for handling Pub/Sub push messages.
src\main\java\com\policyinsight\api\PubSubController.java:76:    @PostMapping("/pubsub")
src\main\java\com\policyinsight\api\PubSubTokenVerifier.java:18: * Verifies OIDC ID tokens sent by Google Pub/Sub push subscriptions.
src\main\resources\application.yml:142:  push:
```

### C) Cloud Run + secrets wiring (repo-side)
Command:
```
rg -n "update-secrets|set-secrets|Secret Manager|secretmanager" .github infra docs src --hidden
```
Trimmed output:
```
docs/MILESTONE_3_EXECUTION_REPORT.md:593:& $GCLOUD run deploy $SERVICE ... --set-secrets "DB_PASSWORD=db-password:latest,APP_TOKEN_SECRET=app-token-secret:latest"
.github/workflows/cd.yml:103:            --update-secrets DB_HOST=cloudsql-host:latest,DB_PORT=cloudsql-port:latest,DB_NAME=cloudsql-database:latest,DB_USER=cloudsql-username:latest,DB_PASSWORD=cloudsql-password:latest,APP_TOKEN_SECRET=app-token-secret:latest
```

### D) Frontend presence (if any)
Command:
```
ls
```
Trimmed output:
```
docs
infra
markdown
src
```

Command:
```
ls src/main/resources/templates
```
Trimmed output:
```
error.html
fragments
index.html
layout.html
report.html
share-report.html
```

Command:
```
ls src/main/resources/static
```
Trimmed output:
```
css
```

---

Sources:
https://docs.cloud.google.com/run/docs/about-instance-autoscaling
https://docs.cloud.google.com/run/docs/authenticating/public
https://docs.cloud.google.com/run/docs/securing/managing-access
https://docs.cloud.google.com/run/docs/configuring/services/secrets
https://docs.cloud.google.com/pubsub/docs/push
https://docs.cloud.google.com/pubsub/docs/authenticate-push-subscriptions

---

## Evidence (public demo run)

- WEB_URL: https://policy-insight-828177954618.us-central1.run.app
- jobId: 320c520a-8169-432b-a207-bd46380f4509
- token (redacted): xgYTOMtq...SbAlE
- report URL: https://policy-insight-828177954618.us-central1.run.app/documents/320c520a-8169-432b-a207-bd46380f4509/report

Final status JSON:
```
{"jobId":"320c520a-8169-432b-a207-bd46380f4509","reportUrl":"/documents/320c520a-8169-432b-a207-bd46380f4509/report","message":"Analysis completed successfully","status":"SUCCESS"}
```

Pub/Sub pushConfig:
```
pushConfig:
  oidcToken:
    serviceAccountEmail: policyinsight-worker@policy-insight.iam.gserviceaccount.com
  pushEndpoint: https://policyinsight-worker-828177954618.us-central1.run.app/internal/pubsub
```

Command used to make web public:
```
gcloud run services add-iam-policy-binding policyinsight-web --region us-central1 --project policy-insight --member="allUsers" --role="roles/run.invoker"
```

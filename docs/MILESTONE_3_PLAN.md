# Milestone 3 Plan (Cloud Run + Cloud SQL + Secret Manager)

This plan is a PowerShell-first, execution-ready runbook for deploying PolicyInsight to Cloud Run with Cloud SQL (PostgreSQL) and Secret Manager. It is designed to be followed line by line with evidence captured in `docs/MILESTONE_3_EXECUTION_REPORT.md`.

## Decisions locked for Milestone 3
- Deploy from source using `gcloud run deploy --source .`
- Storage integration: GCS only (`APP_STORAGE_MODE=gcp`)
- Optional add-ons (not required): Pub/Sub push + OIDC, Vertex AI (Gemini), Document AI OCR

## Operator Runbook (Cursor execution)

### Phase 0: Preflight (hard gate)
Commands:
```powershell
Test-Path docs/MILESTONE_3_PLAN.md
$PLACEHOLDER_PATTERN = ("T","O","D","O","|","Y","O","U","R","_","|","R","E","P","L","A","C","E","_","M","E") -join ""
Select-String -Path docs/MILESTONE_3_PLAN.md -Pattern $PLACEHOLDER_PATTERN | Where-Object { $_.Line -notmatch "Select-String -Path docs/MILESTONE_3_PLAN.md" }
git status
```
Expected output / verification:
- `Test-Path` returns `True`
- No placeholder matches
- `git status` shows clean working tree
Evidence to paste into `docs/MILESTONE_3_EXECUTION_REPORT.md`:
- Output of all three commands
Stop conditions:
- If file is missing or placeholders are found, STOP and list each match.
- If `git status` is not clean, go to Phase A.

### Phase A: If working tree is dirty, clean it by committing small commits
#### A0) Show changes
Commands:
```powershell
git status
git diff
```
Expected output / verification:
- Full diff shown for modified files
Evidence to paste:
- Output of `git status` and `git diff`

#### A1) If `pom.xml` is modified, commit ONLY that
Commands:
```powershell
git diff -- pom.xml
git add pom.xml
git commit -m "fix: update deploy deps/config for Cloud Run + Cloud SQL"
git show --name-only --pretty=oneline HEAD
```
Expected output / verification:
- Commit created with only `pom.xml`
Evidence to paste:
- `git show --name-only --pretty=oneline HEAD`

#### A2) If `docs/MILESTONE_3_EXECUTION_REPORT.md` is modified/untracked, commit ONLY that
Commands:
```powershell
git add docs/MILESTONE_3_EXECUTION_REPORT.md
git commit -m "docs: add Milestone 3 execution evidence (progress log)"
git show --name-only --pretty=oneline HEAD
```
Expected output / verification:
- Commit created with only `docs/MILESTONE_3_EXECUTION_REPORT.md`
Evidence to paste:
- `git show --name-only --pretty=oneline HEAD`

#### A3) Push and confirm clean tree
Commands:
```powershell
git push
if ($LASTEXITCODE -ne 0) { git fetch origin; git rebase origin/milestone-3-cloudrun-execution; git push }
git status
```
Expected output / verification:
- Push succeeds and `git status` is clean
Evidence to paste:
- Full `git push` output and `git status`
Stop conditions:
- If push fails after rebase, STOP and fix before continuing.

### Phase 1: Commit and push the plan
Commands:
```powershell
git status
git add docs/MILESTONE_3_PLAN.md
git commit -m "docs: finalize Milestone 3 Cloud Run deployment plan"
git push
git log -1 --oneline
```
Expected output / verification:
- Commit created and pushed
Evidence to paste:
- `git log -1 --oneline`
Stop conditions:
- If `git push` fails, STOP and fix, then re-run `git push`.

### Phase 2: Create or confirm the execution branch
Commands:
```powershell
git fetch origin
git branch --list milestone-3-cloudrun-execution
git switch milestone-3-cloudrun-execution 2>$null
if ($LASTEXITCODE -ne 0) { git switch -c milestone-3-cloudrun-execution }
git push -u origin milestone-3-cloudrun-execution
git status
```
Expected output / verification:
- Current branch is `milestone-3-cloudrun-execution` with upstream set
Evidence to paste:
- Output of `git status`
Stop conditions:
- If `git push -u` fails, STOP and fix before continuing.

### Phase 3: Execute the deployment runbook line by line
Rules:
- Execute steps exactly in order.
- After each major step, append an evidence block to `docs/MILESTONE_3_EXECUTION_REPORT.md`.
- On failure: STOP → diagnose → fix → re-run only the failed step → record failure + fix.

Required evidence in execution report:
- `gcloud config get-value project`
- `gcloud config get-value run/region`
- API enablement output
- Private networking setup output (VPC access + private services access)
- Cloud SQL instance details + connection name
- DB creation + user creation output
- Secret creation output + IAM policy binding output
- Cloud Run deploy command + full deploy output
- Cloud Run service URL
- Smoke tests: `/health`, upload response with `jobId` + `token`, polling output showing `SUCCESS`

Failure handling rule:
- If any command fails, STOP and record the failure, fix, and the rerun output.

### Phase 4: Commit execution evidence
Commands:
```powershell
git add docs/MILESTONE_3_EXECUTION_REPORT.md
git commit -m "docs: add Milestone 3 Cloud Run execution evidence"
git push
git log -2 --oneline
git status
```
Expected output / verification:
- Commit created and pushed; working tree clean
Evidence to paste:
- `git log -2 --oneline` and `git status`

## Deployment Checklist (linear, copy-paste)

### 1) Preflight: verify gcloud + project + region
Commands:
```powershell
gcloud --version
$PROJECT = (gcloud config get-value project).Trim()
$REGION = (gcloud config get-value run/region).Trim()
$PROJECT
$REGION
```
Expected output / verification:
- `gcloud` version printed
- `$PROJECT` and `$REGION` are non-empty
Evidence to paste:
- Outputs of the above commands
Stop conditions:
- If `$PROJECT` is empty, STOP and set the project in gcloud config, then re-run this step.
- If `$REGION` is empty, set it and re-run:
```powershell
gcloud config set run/region us-central1
$REGION = (gcloud config get-value run/region).Trim()
```
Evidence to paste (after setting region):
- Output of `gcloud config set run/region us-central1` and the new `$REGION`

### 2) Define names and constants (single source of truth)
Commands:
```powershell
$SERVICE = "policy-insight"
$SQL_INSTANCE = "policy-insight-db"
$DB_NAME = "policyinsight"
$DB_USER = "policyinsight"
$BUCKET = "$PROJECT-policyinsight"
$RUNTIME_SA_NAME = "policy-insight-runner"
$RUNTIME_SA = "$RUNTIME_SA_NAME@$PROJECT.iam.gserviceaccount.com"
$SECRET_DB_PASSWORD = "db-password"
$SECRET_APP_TOKEN = "app-token-secret"
$VPC_CONNECTOR = "policy-insight-connector"
$PRIVATE_SERVICE_RANGE = "policy-insight-psa"
$PUBSUB_TOPIC = "policy-insight-topic"
$PUBSUB_SUB = "policy-insight-push"
```
Expected output / verification:
- All variables resolved and non-empty
Evidence to paste:
```powershell
"PROJECT=$PROJECT"
"REGION=$REGION"
"SERVICE=$SERVICE"
"SQL_INSTANCE=$SQL_INSTANCE"
"DB_NAME=$DB_NAME"
"DB_USER=$DB_USER"
"BUCKET=$BUCKET"
"RUNTIME_SA=$RUNTIME_SA"
"VPC_CONNECTOR=$VPC_CONNECTOR"
```
Stop conditions:
- If any value is empty, STOP and fix before proceeding.

### 3) Enable required APIs
Commands:
```powershell
gcloud services enable run.googleapis.com cloudbuild.googleapis.com sqladmin.googleapis.com secretmanager.googleapis.com storage.googleapis.com artifactregistry.googleapis.com vpcaccess.googleapis.com servicenetworking.googleapis.com --project $PROJECT
```
Expected output / verification:
- APIs enabled (success output)
Evidence to paste:
- Full output from `gcloud services enable ...`
Stop conditions:
- If API enablement fails, STOP and fix (missing permissions or billing) before continuing.

### 4) Configure private services access for Cloud SQL (one-time per project/VPC)
Commands:
```powershell
gcloud compute addresses create $PRIVATE_SERVICE_RANGE --global --purpose=VPC_PEERING --prefix-length=16 --network=default --project $PROJECT
gcloud services vpc-peerings connect --service=servicenetworking.googleapis.com --ranges=$PRIVATE_SERVICE_RANGE --network=default --project $PROJECT
```
Expected output / verification:
- PSA range created and VPC peering connected (Already Exists is acceptable)
Evidence to paste:
- Full output of both commands
Stop conditions:
- If either command fails for reasons other than already exists, STOP and fix.

### 5) Create Serverless VPC Access connector
Commands:
```powershell
gcloud compute networks vpc-access connectors create $VPC_CONNECTOR --region $REGION --network default --range 10.8.0.0/28 --project $PROJECT
```
Expected output / verification:
- Connector created (Already Exists is acceptable)
Evidence to paste:
- Full output of the create command
Stop conditions:
- If creation fails for reasons other than already exists, STOP and fix.

### 6) Create Cloud SQL instance, database, and user (private IP only)
Commands:
```powershell
gcloud sql instances create $SQL_INSTANCE --database-version=POSTGRES_15 --tier=db-custom-1-3840 --region $REGION --network=default --no-assign-ip --project $PROJECT
gcloud sql databases create $DB_NAME --instance $SQL_INSTANCE --project $PROJECT
$DB_PASSWORD = -join ((48..57 + 65..90 + 97..122) | Get-Random -Count 32 | ForEach-Object { [char]$_ })
gcloud sql users create $DB_USER --instance $SQL_INSTANCE --password $DB_PASSWORD --project $PROJECT
gcloud sql instances describe $SQL_INSTANCE --project $PROJECT --format="value(connectionName)"
```
Expected output / verification:
- Instance, database, and user created
- `connectionName` printed
Evidence to paste:
- Output of each command
Stop conditions:
- If any command fails, STOP and fix before proceeding.

### 7) Create GCS bucket for uploads/reports
Commands:
```powershell
gcloud storage buckets create "gs://$BUCKET" --location $REGION --project $PROJECT --uniform-bucket-level-access
```
Expected output / verification:
- Bucket created
Evidence to paste:
- Output of bucket creation
Stop conditions:
- If bucket creation fails, STOP and fix (permissions or naming) before continuing.

### 8) Create Cloud Run runtime service account
Commands:
```powershell
gcloud iam service-accounts create $RUNTIME_SA_NAME --project $PROJECT
```
Expected output / verification:
- Service account created
Evidence to paste:
- Output showing the service account creation
Stop conditions:
- If creation fails, STOP and fix before continuing.

### 9) Grant least-privilege IAM roles to runtime service account
Commands:
```powershell
gcloud projects add-iam-policy-binding $PROJECT --member "serviceAccount:$RUNTIME_SA" --role roles/cloudsql.client
gcloud projects add-iam-policy-binding $PROJECT --member "serviceAccount:$RUNTIME_SA" --role roles/secretmanager.secretAccessor
gcloud storage buckets add-iam-policy-binding "gs://$BUCKET" --member "serviceAccount:$RUNTIME_SA" --role roles/storage.objectAdmin
```
Expected output / verification:
- Each binding added (or already present)
Evidence to paste:
- Output of each IAM binding command
Stop conditions:
- If any IAM binding fails, STOP and fix before continuing.

### 10) Create secrets in Secret Manager (no newline in secret values)
Commands:
```powershell
gcloud secrets create $SECRET_DB_PASSWORD --replication-policy=automatic --project $PROJECT
$tmp = New-TemporaryFile
Set-Content -Path $tmp -Value $DB_PASSWORD -NoNewline
gcloud secrets versions add $SECRET_DB_PASSWORD --data-file=$tmp --project $PROJECT
Remove-Item $tmp

$APP_TOKEN_SECRET = -join ((48..57 + 65..90 + 97..122) | Get-Random -Count 32 | ForEach-Object { [char]$_ })
$tmp = New-TemporaryFile
Set-Content -Path $tmp -Value $APP_TOKEN_SECRET -NoNewline
gcloud secrets create $SECRET_APP_TOKEN --replication-policy=automatic --project $PROJECT
gcloud secrets versions add $SECRET_APP_TOKEN --data-file=$tmp --project $PROJECT
Remove-Item $tmp
```
Expected output / verification:
- Secrets created and latest versions added
Evidence to paste:
- Output of secret creation and version creation (both secrets)
Stop conditions:
- If secret creation fails, STOP and fix before continuing.

### 11) Deploy Cloud Run from source (required method)
Commands:
```powershell
$CONNECTION_NAME = (gcloud sql instances describe $SQL_INSTANCE --project $PROJECT --format="value(connectionName)").Trim()
$SPRING_DATASOURCE_URL = "jdbc:postgresql:///${DB_NAME}?cloudSqlInstance=${CONNECTION_NAME}&socketFactory=com.google.cloud.sql.postgres.SocketFactory"
gcloud run deploy $SERVICE --source . --region $REGION --project $PROJECT --service-account $RUNTIME_SA --add-cloudsql-instances $CONNECTION_NAME --vpc-connector $VPC_CONNECTOR --vpc-egress=private-ranges-only --min-instances 0 --allow-unauthenticated --set-env-vars "SPRING_PROFILES_ACTIVE=cloudrun,SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL,DB_HOST=/cloudsql/$CONNECTION_NAME,DB_PORT=5432,DB_NAME=$DB_NAME,DB_USER=$DB_USER,APP_STORAGE_MODE=gcp,GCS_BUCKET_NAME=$BUCKET,APP_MESSAGING_MODE=local,APP_PROCESSING_MODE=local,POLICYINSIGHT_WORKER_ENABLED=true,APP_RATE_LIMIT_UPLOAD_MAX_PER_HOUR=10,APP_RATE_LIMIT_QA_MAX_PER_HOUR=20,APP_RATE_LIMIT_QA_MAX_PER_JOB=3,APP_PROCESSING_MAX_TEXT_LENGTH=1000000,APP_PROCESSING_STAGE_TIMEOUT_SECONDS=300,APP_VALIDATION_PDF_MAX_PAGES=100,APP_VALIDATION_PDF_MAX_TEXT_LENGTH=1048576,APP_RETENTION_DAYS=30,APP_LOCAL_WORKER_POLL_MS=2000,APP_LOCAL_WORKER_BATCH_SIZE=5,APP_JOB_LEASE_DURATION_MINUTES=30,APP_JOB_MAX_ATTEMPTS=3,APP_GEMINI_RETRY_MAX_ATTEMPTS=3,APP_GEMINI_RETRY_BASE_DELAY_MS=1000" --set-secrets "DB_PASSWORD=$SECRET_DB_PASSWORD:latest,APP_TOKEN_SECRET=$SECRET_APP_TOKEN:latest"
```
Expected output / verification:
- Build and deploy succeed
- Service URL printed
Evidence to paste:
- Full deploy command output (including the service URL)
Stop conditions:
- If deploy fails, STOP and fix before continuing.

### 12) Safe env var update pattern (critical warning)
`gcloud run services update --set-env-vars` replaces ALL env vars unless you reapply the full set. Use one of these safe patterns:

Commands (merge update):
```powershell
$SERVICE_URL = (gcloud run services describe $SERVICE --region $REGION --project $PROJECT --format="value(status.url)").Trim()
gcloud run services update $SERVICE --region $REGION --project $PROJECT --update-env-vars "APP_BASE_URL=$SERVICE_URL,APP_ALLOWED_ORIGINS=$SERVICE_URL"
```
Expected output / verification:
- Update completes successfully
Evidence to paste:
- Output of the update command and the resolved `$SERVICE_URL`

Commands (full reapply, if required):
```powershell
gcloud run services update $SERVICE --region $REGION --project $PROJECT --set-env-vars "SPRING_PROFILES_ACTIVE=cloudrun,SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL,DB_HOST=/cloudsql/$CONNECTION_NAME,DB_PORT=5432,DB_NAME=$DB_NAME,DB_USER=$DB_USER,APP_BASE_URL=$SERVICE_URL,APP_ALLOWED_ORIGINS=$SERVICE_URL,APP_STORAGE_MODE=gcp,GCS_BUCKET_NAME=$BUCKET,APP_MESSAGING_MODE=local,APP_PROCESSING_MODE=local,POLICYINSIGHT_WORKER_ENABLED=true,APP_RATE_LIMIT_UPLOAD_MAX_PER_HOUR=10,APP_RATE_LIMIT_QA_MAX_PER_HOUR=20,APP_RATE_LIMIT_QA_MAX_PER_JOB=3,APP_PROCESSING_MAX_TEXT_LENGTH=1000000,APP_PROCESSING_STAGE_TIMEOUT_SECONDS=300,APP_VALIDATION_PDF_MAX_PAGES=100,APP_VALIDATION_PDF_MAX_TEXT_LENGTH=1048576,APP_RETENTION_DAYS=30,APP_LOCAL_WORKER_POLL_MS=2000,APP_LOCAL_WORKER_BATCH_SIZE=5,APP_JOB_LEASE_DURATION_MINUTES=30,APP_JOB_MAX_ATTEMPTS=3,APP_GEMINI_RETRY_MAX_ATTEMPTS=3,APP_GEMINI_RETRY_BASE_DELAY_MS=1000"
```
Expected output / verification:
- Update completes successfully with full env set
Evidence to paste:
- Output of the update command

### 13) Smoke tests (PowerShell)
These commands assume `valid.pdf` exists in the repo root.

Commands:
```powershell
$SERVICE_URL = (gcloud run services describe $SERVICE --region $REGION --project $PROJECT --format="value(status.url)").Trim()
curl.exe -i "$SERVICE_URL/health"
$UPLOAD_RESPONSE = curl.exe -s -X POST "$SERVICE_URL/api/documents/upload" -H "Accept: application/json" -F "file=@valid.pdf;type=application/pdf"
$JOB_ID = ($UPLOAD_RESPONSE | ConvertFrom-Json).jobId
$JOB_TOKEN = ($UPLOAD_RESPONSE | ConvertFrom-Json).token
$UPLOAD_RESPONSE
for ($i = 0; $i -lt 30; $i++) {
  $STATUS = (curl.exe -s "$SERVICE_URL/api/documents/$JOB_ID/status" -H "Accept: application/json" -H "X-Job-Token: $JOB_TOKEN" | ConvertFrom-Json).status
  "$i`t$STATUS"
  if ($STATUS -eq "SUCCESS" -or $STATUS -eq "FAILED") { break }
  Start-Sleep -Seconds 5
}
```
Expected output / verification:
- `/health` returns 200 and DB status is `UP`
- Upload returns `202` with `jobId` + `token`
- Polling output ends with `SUCCESS`
Evidence to paste:
- `/health` response
- Upload response JSON (redact token partially if needed)
- Polling output ending with `SUCCESS`
Stop conditions:
- If `/health` is not 200 or DB is not `UP`, STOP and fix.
- If upload is not `202` or no `jobId`/`token`, STOP and fix.
- If polling ends in `FAILED`, STOP and diagnose.

### 14) Optional: Pub/Sub push delivery with OIDC (disabled by default)
Only enable if explicitly required for this milestone.

Commands:
```powershell
$PUBSUB_PUSH_SA_NAME = "policy-insight-pubsub-push"
$PUBSUB_PUSH_SA = "$PUBSUB_PUSH_SA_NAME@$PROJECT.iam.gserviceaccount.com"
gcloud iam service-accounts create $PUBSUB_PUSH_SA_NAME --project $PROJECT
gcloud run services add-iam-policy-binding $SERVICE --region $REGION --project $PROJECT --member "serviceAccount:$PUBSUB_PUSH_SA" --role roles/run.invoker
gcloud pubsub topics create $PUBSUB_TOPIC --project $PROJECT
gcloud pubsub subscriptions create $PUBSUB_SUB --topic $PUBSUB_TOPIC --push-endpoint "$SERVICE_URL/api/pubsub/ingest" --push-auth-service-account $PUBSUB_PUSH_SA --project $PROJECT
```
Expected output / verification:
- Topic and push subscription created
- Service account granted `roles/run.invoker`
Evidence to paste:
- Output of each command
Stop conditions:
- If any command fails, STOP and fix before continuing.

### 15) Optional: Vertex AI (Gemini) processing (disabled by default)
Only enable if explicitly required for this milestone.

Commands:
```powershell
gcloud services enable aiplatform.googleapis.com --project $PROJECT
gcloud projects add-iam-policy-binding $PROJECT --member "serviceAccount:$RUNTIME_SA" --role roles/aiplatform.user
gcloud run services update $SERVICE --region $REGION --project $PROJECT --update-env-vars "VERTEX_AI_ENABLED=true,VERTEX_AI_LOCATION=$REGION,VERTEX_AI_MODEL=gemini-2.0-flash-exp"
```
Expected output / verification:
- API enabled, IAM binding added, env vars updated
Evidence to paste:
- Output of each command
Stop conditions:
- If any command fails, STOP and fix before continuing.

### 16) Optional: Document AI OCR (disabled by default)
Only enable if explicitly required for this milestone.

Commands:
```powershell
gcloud services enable documentai.googleapis.com --project $PROJECT
gcloud projects add-iam-policy-binding $PROJECT --member "serviceAccount:$RUNTIME_SA" --role roles/documentai.apiUser
$DOCUMENT_AI_PROCESSOR = (gcloud documentai processors list --location us --project $PROJECT --format="value(name)" | Select-Object -First 1).Trim()
$DOCUMENT_AI_PROCESSOR_ID = ($DOCUMENT_AI_PROCESSOR -split "/") | Select-Object -Last 1
gcloud run services update $SERVICE --region $REGION --project $PROJECT --update-env-vars "DOCUMENT_AI_ENABLED=true,DOCUMENT_AI_LOCATION=us,DOCUMENT_AI_PROCESSOR_ID=$DOCUMENT_AI_PROCESSOR_ID"
```
Expected output / verification:
- API enabled, IAM binding added, processor ID resolved, env vars updated
Evidence to paste:
- Output of each command
Stop conditions:
- If any command fails or `DOCUMENT_AI_PROCESSOR_ID` is empty, STOP and create a processor, then re-run this step.

## Production environment variables (explicit list)

| Env var | Description | Secret Manager? |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Must be `cloudrun` to activate Cloud Run profile. | No |
| `PORT` | Cloud Run injected port (usually `8080`). Do not override. | No |
| `SPRING_DATASOURCE_URL` | JDBC URL using Cloud SQL socket factory. | No |
| `DB_HOST` | Cloud SQL socket path `/cloudsql/$CONNECTION_NAME`. | No |
| `DB_PORT` | PostgreSQL port `5432`. | No |
| `DB_NAME` | Database name `policyinsight`. | No |
| `DB_USER` | Database user `policyinsight`. | No |
| `DB_PASSWORD` | Database password. | Yes (`db-password`) |
| `APP_BASE_URL` | Public base URL for share links. | No |
| `APP_ALLOWED_ORIGINS` | CORS allowed origin. | No |
| `APP_TOKEN_SECRET` | HMAC secret for capability tokens. | Yes (`app-token-secret`) |
| `APP_STORAGE_MODE` | Storage mode `gcp`. | No |
| `GCS_BUCKET_NAME` | GCS bucket name for uploads/reports. | No |
| `APP_MESSAGING_MODE` | Messaging mode `local` for Milestone 3. | No |
| `APP_PROCESSING_MODE` | Processing mode `local`. | No |
| `POLICYINSIGHT_WORKER_ENABLED` | In-process worker enabled `true`. | No |
| `APP_LOCAL_WORKER_POLL_MS` | Worker polling interval (ms). | No |
| `APP_LOCAL_WORKER_BATCH_SIZE` | Worker batch size per poll. | No |
| `APP_JOB_LEASE_DURATION_MINUTES` | Lease duration for job processing. | No |
| `APP_JOB_MAX_ATTEMPTS` | Max job retry attempts. | No |
| `APP_RATE_LIMIT_UPLOAD_MAX_PER_HOUR` | Upload rate limit per IP. | No |
| `APP_RATE_LIMIT_QA_MAX_PER_HOUR` | Q&A rate limit per IP. | No |
| `APP_RATE_LIMIT_QA_MAX_PER_JOB` | Max Q&A per job. | No |
| `APP_PROCESSING_MAX_TEXT_LENGTH` | Max extracted text length (chars). | No |
| `APP_PROCESSING_STAGE_TIMEOUT_SECONDS` | Timeout per processing stage. | No |
| `APP_VALIDATION_PDF_MAX_PAGES` | PDF page limit. | No |
| `APP_VALIDATION_PDF_MAX_TEXT_LENGTH` | PDF text length (bytes). | No |
| `APP_RETENTION_DAYS` | Retention window for jobs and reports. | No |
| `APP_GEMINI_RETRY_MAX_ATTEMPTS` | Max LLM retry attempts. | No |
| `APP_GEMINI_RETRY_BASE_DELAY_MS` | Base delay for LLM retries (ms). | No |
| `VERTEX_AI_ENABLED` | Enable Gemini via Vertex AI (optional). | No |
| `VERTEX_AI_LOCATION` | Vertex AI location (optional). | No |
| `VERTEX_AI_MODEL` | Gemini model name (optional). | No |
| `DOCUMENT_AI_ENABLED` | Enable Document AI OCR (optional). | No |
| `DOCUMENT_AI_LOCATION` | Document AI location (optional). | No |
| `DOCUMENT_AI_PROCESSOR_ID` | Document AI processor ID (optional). | No |

## Worker execution decision (Milestone 3)
Decision: run the background worker inside the Cloud Run service (no separate worker service).

Enforced by:
- `POLICYINSIGHT_WORKER_ENABLED=true`
- `APP_MESSAGING_MODE=local`

## Rollback plan (exact commands)
Commands:
```powershell
gcloud run revisions list --service $SERVICE --region $REGION --project $PROJECT
$PREVIOUS_REVISION = (gcloud run revisions list --service $SERVICE --region $REGION --project $PROJECT --sort-by="~metadata.creationTimestamp" --format="value(metadata.name)" | Select-Object -Skip 1 -First 1)
gcloud run services update-traffic $SERVICE --region $REGION --project $PROJECT --to-revisions "$PREVIOUS_REVISION=100"
$BACKUP_ID = (gcloud sql backups list --instance $SQL_INSTANCE --project $PROJECT --limit 1 --sort-by="~endTime" --format="value(id)")
gcloud sql backups restore $BACKUP_ID --restore-instance $SQL_INSTANCE --backup-instance $SQL_INSTANCE --project $PROJECT
```
Expected output / verification:
- Revision list shown, traffic updated, backup restore started
Evidence to paste:
- Output of the rollback commands

## Blockers (repo-specific)
- If `POLICYINSIGHT_WORKER_ENABLED` is not `true`, jobs remain `PENDING`.
- If `APP_TOKEN_SECRET` is missing, token validation falls back to an insecure default.
- If `APP_STORAGE_MODE=gcp` but the bucket is missing or IAM is wrong, uploads fail.
- If DB credentials or Cloud SQL socket config are wrong, the service will fail readiness checks.

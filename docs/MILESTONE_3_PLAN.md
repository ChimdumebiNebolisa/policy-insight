# Milestone 3 Plan (Cloud Run + Cloud SQL + Secret Manager)

This plan is a PowerShell-first, copy-paste runbook for deploying PolicyInsight to Cloud Run with Cloud SQL (PostgreSQL) and Secret Manager. Pub/Sub is optional and disabled for the default Milestone 3 path.

Region is fixed: `us-central1`. Project is assumed already set in `gcloud` config.

## Operator Runbook (Cursor execution)

### Phase 0: Preflight
Commands:
```
Test-Path docs/MILESTONE_3_PLAN.md
$PLACEHOLDER_PATTERN = "T" + "ODO|F" + "IXME|CHANGE" + "ME|\\?\\?\\?\\?"
$PLACEHOLDERS = Select-String -Path docs/MILESTONE_3_PLAN.md -Pattern $PLACEHOLDER_PATTERN | Where-Object { $_.Line -notmatch "Select-String -Path docs/MILESTONE_3_PLAN.md" }
$PLACEHOLDERS
git status
```

Required outputs:
- `Test-Path` result `True`
- Any placeholder matches (must be none)
- `git status` output

Stop conditions:
- If `Test-Path` is `False`, STOP and report missing file.
- If placeholders are found, STOP and list each match.
- If `git status` is not clean, STOP and list modified/untracked files.

### Phase 1: Commit and push the plan
Commands:
```
git status
git add docs/MILESTONE_3_PLAN.md
git commit -m "docs: finalize Milestone 3 Cloud Run deployment plan"
git push
git log -1 --oneline
```

Required outputs:
- Full `git commit` output
- Full `git push` output
- `git log -1 --oneline`

Stop conditions:
- If `git push` fails, STOP and fix, then re-run `git push`.

### Phase 2: Create the Milestone 3 execution branch
Commands:
```
git fetch origin
git switch main
git pull --rebase
git switch -c milestone-3-cloudrun-execution
git push -u origin milestone-3-cloudrun-execution
git status
```

Required outputs:
- Output of each command
- Branch name + upstream tracking confirmation

Stop conditions:
- If `git pull --rebase` fails, STOP and fix before continuing.
- If `git push -u` fails, STOP and fix before continuing.

### Phase 3: Execute the plan line by line
Rules:
- Execute steps exactly in order.
- After each major step, append an evidence block to `docs/MILESTONE_3_EXECUTION_REPORT.md`.
- On failure: STOP → diagnose → fix → re-run only the failed step → record failure + fix.

Required evidence in execution report:
- `gcloud config get-value project`
- `gcloud config get-value run/region`
- API enablement output
- Cloud SQL instance details + connection name
- DB creation + user creation output
- Secret creation output + IAM policy binding output
- Cloud Run deploy command + full deploy output
- Cloud Run service URL
- Smoke tests: `/health`, upload response with `jobId` + `token`, polling output showing `SUCCESS`

Stop conditions:
- If any `gcloud` command fails, STOP and record failure + fix before moving on.

### Phase 4: Commit execution evidence
Commands:
```
git add docs/MILESTONE_3_EXECUTION_REPORT.md
git commit -m "docs: add Milestone 3 Cloud Run execution evidence"
git push
git log -2 --oneline
```

Required outputs:
- Full `git commit` output
- Full `git push` output
- `git log -2 --oneline`

Stop conditions:
- If `git push` fails, STOP and fix, then re-run `git push`.

## Deployment Checklist (linear, copy-paste)

### 1) Preflight: verify gcloud + project + region
Commands:
```
gcloud --version
gcloud config get-value project
gcloud config get-value run/region
gcloud config set run/region us-central1
```

Evidence to capture:
- Output of `gcloud --version`
- Output of `gcloud config get-value project`
- Output of `gcloud config get-value run/region`
- Output of `gcloud config set run/region us-central1`

Stop conditions:
- If `gcloud` is not found, STOP and install the Google Cloud SDK.
- If project is empty, STOP and set it with `gcloud config set project policy-insight` if that is the intended project ID.

### 2) Define names and constants (single source of truth)
Commands:
```
$REGION = "us-central1"
$SERVICE = "policy-insight"
$SQL_INSTANCE = "policy-insight-db"
$DB_NAME = "policyinsight"
$DB_USER = "policyinsight"
$BUCKET = "$(gcloud config get-value project)-policyinsight"
$RUNTIME_SA_NAME = "policy-insight-runner"
$RUNTIME_SA = "$RUNTIME_SA_NAME@$(gcloud config get-value project).iam.gserviceaccount.com"
$SECRET_DB_PASSWORD = "db-password"
$SECRET_APP_TOKEN = "app-token-secret"
$AR_REPO_SOURCE = "cloud-run-source-deploy"
$AR_REPO_CONTAINER = "policy-insight-app"
```

Evidence to capture:
- Echo the resolved values (PowerShell):
```
"REGION=$REGION"
"SERVICE=$SERVICE"
"SQL_INSTANCE=$SQL_INSTANCE"
"DB_NAME=$DB_NAME"
"DB_USER=$DB_USER"
"BUCKET=$BUCKET"
"RUNTIME_SA_NAME=$RUNTIME_SA_NAME"
"RUNTIME_SA=$RUNTIME_SA"
"SECRET_DB_PASSWORD=$SECRET_DB_PASSWORD"
"SECRET_APP_TOKEN=$SECRET_APP_TOKEN"
"AR_REPO_SOURCE=$AR_REPO_SOURCE"
"AR_REPO_CONTAINER=$AR_REPO_CONTAINER"
```

Stop conditions:
- If any value is empty, STOP and fix before proceeding.

### 3) Enable required APIs
Commands:
```
gcloud services enable run.googleapis.com cloudbuild.googleapis.com sqladmin.googleapis.com secretmanager.googleapis.com storage.googleapis.com artifactregistry.googleapis.com --project $(gcloud config get-value project)
```

Evidence to capture:
- Full output from `gcloud services enable ...`

Stop conditions:
- If API enablement fails, STOP and fix (missing permissions or billing) before continuing.

### 4) Ensure Artifact Registry repo for source deploy exists
Commands:
```
gcloud artifacts repositories create $AR_REPO_SOURCE --repository-format=docker --location $REGION --project $(gcloud config get-value project)
```

Evidence to capture:
- Full output (success or Already Exists error is acceptable)

Stop conditions:
- If creation fails for reasons other than “already exists,” STOP and fix.

Note:
- If `gcloud run deploy --source .` prompts to create a repo, answer `Y`. It must be:
  - repo name: `cloud-run-source-deploy`
  - format: `docker`
  - location: `us-central1`

### 5) Create Cloud SQL instance, database, and user
Commands:
```
gcloud sql instances create $SQL_INSTANCE --database-version=POSTGRES_15 --tier=db-custom-1-3840 --region $REGION --project $(gcloud config get-value project)
gcloud sql databases create $DB_NAME --instance $SQL_INSTANCE --project $(gcloud config get-value project)
$DB_PASSWORD = -join ((48..57 + 65..90 + 97..122) | Get-Random -Count 32 | ForEach-Object { [char]$_ })
gcloud sql users create $DB_USER --instance $SQL_INSTANCE --password $DB_PASSWORD --project $(gcloud config get-value project)
gcloud sql instances describe $SQL_INSTANCE --project $(gcloud config get-value project) --format="value(connectionName)"
```

Evidence to capture:
- Output of instance creation
- Output of database creation
- Output of user creation
- Output of `connectionName`

Stop conditions:
- If any of the above commands fail, STOP and fix before proceeding.

### 6) Create GCS bucket for uploads/reports
Commands:
```
gcloud storage buckets create "gs://$BUCKET" --location $REGION --project $(gcloud config get-value project) --uniform-bucket-level-access
```

Evidence to capture:
- Output of bucket creation

Stop conditions:
- If bucket creation fails, STOP and fix (permissions or naming) before continuing.

### 7) Create Cloud Run runtime service account
Commands:
```
gcloud iam service-accounts create $RUNTIME_SA_NAME --project $(gcloud config get-value project)
```

Evidence to capture:
- Output showing the service account creation

Stop conditions:
- If creation fails, STOP and fix before continuing.

### 8) Grant IAM roles to runtime service account
Commands:
```
gcloud projects add-iam-policy-binding $(gcloud config get-value project) --member "serviceAccount:$RUNTIME_SA" --role roles/cloudsql.client
gcloud projects add-iam-policy-binding $(gcloud config get-value project) --member "serviceAccount:$RUNTIME_SA" --role roles/secretmanager.secretAccessor
gcloud storage buckets add-iam-policy-binding "gs://$BUCKET" --member "serviceAccount:$RUNTIME_SA" --role roles/storage.objectAdmin
```

Evidence to capture:
- Output of each IAM binding command

Stop conditions:
- If any IAM binding fails, STOP and fix before continuing.

### 9) Create secrets in Secret Manager (no newline in secret values)
Commands:
```
gcloud secrets create $SECRET_DB_PASSWORD --replication-policy=automatic --project $(gcloud config get-value project)
$tmp = New-TemporaryFile
Set-Content -Path $tmp -Value $DB_PASSWORD -NoNewline
gcloud secrets versions add $SECRET_DB_PASSWORD --data-file=$tmp --project $(gcloud config get-value project)
Remove-Item $tmp

$APP_TOKEN_SECRET = -join ((48..57 + 65..90 + 97..122) | Get-Random -Count 32 | ForEach-Object { [char]$_ })
$tmp = New-TemporaryFile
Set-Content -Path $tmp -Value $APP_TOKEN_SECRET -NoNewline
gcloud secrets create $SECRET_APP_TOKEN --replication-policy=automatic --project $(gcloud config get-value project)
gcloud secrets versions add $SECRET_APP_TOKEN --data-file=$tmp --project $(gcloud config get-value project)
Remove-Item $tmp
```

Evidence to capture:
- Output of secret creation and version creation (both secrets)

Stop conditions:
- If secret creation fails, STOP and fix before continuing.

### 10) Deploy Cloud Run (Method A: source deploy, default)
Commands:
```
$CONNECTION_NAME = (gcloud sql instances describe $SQL_INSTANCE --project $(gcloud config get-value project) --format="value(connectionName)").Trim()
$SPRING_DATASOURCE_URL = "jdbc:postgresql:///${DB_NAME}?cloudSqlInstance=${CONNECTION_NAME}&socketFactory=com.google.cloud.sql.postgres.SocketFactory"
gcloud run deploy $SERVICE --source . --region $REGION --project $(gcloud config get-value project) --service-account $RUNTIME_SA --add-cloudsql-instances $CONNECTION_NAME --min-instances 0 --allow-unauthenticated --set-env-vars "SPRING_PROFILES_ACTIVE=cloudrun,SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL,DB_HOST=/cloudsql/$CONNECTION_NAME,DB_PORT=5432,DB_NAME=$DB_NAME,DB_USER=$DB_USER,APP_STORAGE_MODE=gcp,GCS_BUCKET_NAME=$BUCKET,APP_MESSAGING_MODE=local,APP_PROCESSING_MODE=local,POLICYINSIGHT_WORKER_ENABLED=true,APP_RATE_LIMIT_UPLOAD_MAX_PER_HOUR=10,APP_RATE_LIMIT_QA_MAX_PER_HOUR=20,APP_RATE_LIMIT_QA_MAX_PER_JOB=3,APP_PROCESSING_MAX_TEXT_LENGTH=1000000,APP_PROCESSING_STAGE_TIMEOUT_SECONDS=300,APP_VALIDATION_PDF_MAX_PAGES=100,APP_VALIDATION_PDF_MAX_TEXT_LENGTH=1048576,APP_RETENTION_DAYS=30,APP_LOCAL_WORKER_POLL_MS=2000,APP_LOCAL_WORKER_BATCH_SIZE=5,APP_JOB_LEASE_DURATION_MINUTES=30,APP_JOB_MAX_ATTEMPTS=3,APP_GEMINI_RETRY_MAX_ATTEMPTS=3,APP_GEMINI_RETRY_BASE_DELAY_MS=1000" --set-secrets "DB_PASSWORD=$SECRET_DB_PASSWORD:latest,APP_TOKEN_SECRET=$SECRET_APP_TOKEN:latest"
```

Evidence to capture:
- Full deploy command output
- Service URL line

Stop conditions:
- If deploy fails (container failed to start, build failure), STOP and fix before continuing.

### 11) Set base URL and allowed origins (safe env update)
Do not use `--set-env-vars` alone because it replaces all env vars. Use `--update-env-vars` to merge.

Commands:
```
$SERVICE_URL = (gcloud run services describe $SERVICE --region $REGION --project $(gcloud config get-value project) --format="value(status.url)").Trim()
gcloud run services update $SERVICE --region $REGION --project $(gcloud config get-value project) --update-env-vars "APP_BASE_URL=$SERVICE_URL,APP_ALLOWED_ORIGINS=$SERVICE_URL"
```

Evidence to capture:
- Output of the update command
- Service URL value

Stop conditions:
- If update fails, STOP and fix before continuing.

### 12) Deploy Cloud Run (Method B: container deploy, optional)
Only use this if you prefer pre-built images.

Commands:
```
gcloud artifacts repositories create $AR_REPO_CONTAINER --repository-format=docker --location $REGION --project $(gcloud config get-value project)
$IMAGE = "$REGION-docker.pkg.dev/$(gcloud config get-value project)/$AR_REPO_CONTAINER/policy-insight:$(Get-Date -Format yyyyMMdd-HHmmss)"
gcloud builds submit --tag $IMAGE --project $(gcloud config get-value project)
gcloud run deploy $SERVICE --image $IMAGE --region $REGION --project $(gcloud config get-value project) --service-account $RUNTIME_SA --add-cloudsql-instances $CONNECTION_NAME --min-instances 0 --allow-unauthenticated --set-env-vars "SPRING_PROFILES_ACTIVE=cloudrun,SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL,DB_HOST=/cloudsql/$CONNECTION_NAME,DB_PORT=5432,DB_NAME=$DB_NAME,DB_USER=$DB_USER,APP_STORAGE_MODE=gcp,GCS_BUCKET_NAME=$BUCKET,APP_MESSAGING_MODE=local,APP_PROCESSING_MODE=local,POLICYINSIGHT_WORKER_ENABLED=true,APP_RATE_LIMIT_UPLOAD_MAX_PER_HOUR=10,APP_RATE_LIMIT_QA_MAX_PER_HOUR=20,APP_RATE_LIMIT_QA_MAX_PER_JOB=3,APP_PROCESSING_MAX_TEXT_LENGTH=1000000,APP_PROCESSING_STAGE_TIMEOUT_SECONDS=300,APP_VALIDATION_PDF_MAX_PAGES=100,APP_VALIDATION_PDF_MAX_TEXT_LENGTH=1048576,APP_RETENTION_DAYS=30,APP_LOCAL_WORKER_POLL_MS=2000,APP_LOCAL_WORKER_BATCH_SIZE=5,APP_JOB_LEASE_DURATION_MINUTES=30,APP_JOB_MAX_ATTEMPTS=3,APP_GEMINI_RETRY_MAX_ATTEMPTS=3,APP_GEMINI_RETRY_BASE_DELAY_MS=1000" --set-secrets "DB_PASSWORD=$SECRET_DB_PASSWORD:latest,APP_TOKEN_SECRET=$SECRET_APP_TOKEN:latest"
```

Evidence to capture:
- Output of repo creation
- Output of `gcloud builds submit`
- Full deploy output + service URL line

Stop conditions:
- If build or deploy fails, STOP and fix before continuing.

### 13) Smoke tests (PowerShell)
These commands assume `valid.pdf` exists in the current directory.

Health check (curl):
```
$SERVICE_URL = (gcloud run services describe $SERVICE --region $REGION --project $(gcloud config get-value project) --format="value(status.url)").Trim()
curl.exe -i "$SERVICE_URL/health"
```

Health check (Invoke-RestMethod):
```
Invoke-RestMethod -Method Get -Uri "$SERVICE_URL/health"
```

Upload document (curl):
```
$UPLOAD_RESPONSE = curl.exe -s -X POST "$SERVICE_URL/api/documents/upload" -H "Accept: application/json" -F "file=@valid.pdf;type=application/pdf"
$JOB_ID = ($UPLOAD_RESPONSE | ConvertFrom-Json).jobId
$JOB_TOKEN = ($UPLOAD_RESPONSE | ConvertFrom-Json).token
$UPLOAD_RESPONSE
```

Upload document (Invoke-RestMethod):
```
$UPLOAD_RESPONSE = Invoke-RestMethod -Method Post -Uri "$SERVICE_URL/api/documents/upload" -Form @{ file = Get-Item .\valid.pdf } -Headers @{ Accept = "application/json" }
$JOB_ID = $UPLOAD_RESPONSE.jobId
$JOB_TOKEN = $UPLOAD_RESPONSE.token
$UPLOAD_RESPONSE
```

Polling loop (curl):
```
for ($i = 0; $i -lt 30; $i++) {
  $STATUS = (curl.exe -s "$SERVICE_URL/api/documents/$JOB_ID/status" -H "Accept: application/json" -H "X-Job-Token: $JOB_TOKEN" | ConvertFrom-Json).status
  "$i`t$STATUS"
  if ($STATUS -eq "SUCCESS" -or $STATUS -eq "FAILED") { break }
  Start-Sleep -Seconds 5
}
```

Evidence to capture:
- `/health` response (status code + JSON)
- Upload response JSON with `jobId` + `token`
- Polling output ending with `SUCCESS`

Stop conditions:
- If `/health` is not 200 or DB is not `UP`, STOP and fix.
- If upload is not `202` or no `jobId`/`token`, STOP and fix.
- If polling ends in `FAILED`, STOP and diagnose.

### 14) Optional: Pub/Sub push delivery (disabled by default)
Only enable if explicitly required for this milestone.

Commands:
```
gcloud pubsub topics create policy-insight-topic --project $(gcloud config get-value project)
gcloud pubsub subscriptions create policy-insight-push --topic policy-insight-topic --push-endpoint "$SERVICE_URL/api/pubsub/ingest" --push-auth-service-account $RUNTIME_SA --project $(gcloud config get-value project)
```

Evidence to capture:
- Output of topic creation
- Output of subscription creation

Stop conditions:
- If Pub/Sub creation fails, STOP and fix before continuing.

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

## Worker execution decision (Milestone 3)

Decision: run the background worker inside the Cloud Run service (no separate worker service).

Enforced by:
- `POLICYINSIGHT_WORKER_ENABLED=true`
- `APP_MESSAGING_MODE=local`

## Rollback plan (exact commands)

1) List revisions:
```
gcloud run revisions list --service $SERVICE --region $REGION --project $(gcloud config get-value project)
```

2) Route 100% traffic to previous revision:
```
$PREVIOUS_REVISION = (gcloud run revisions list --service $SERVICE --region $REGION --project $(gcloud config get-value project) --sort-by="~metadata.creationTimestamp" --format="value(metadata.name)" | Select-Object -Skip 1 -First 1)
gcloud run services update-traffic $SERVICE --region $REGION --project $(gcloud config get-value project) --to-revisions "$PREVIOUS_REVISION=100"
```

3) Restore DB backup if needed:
```
$BACKUP_ID = (gcloud sql backups list --instance $SQL_INSTANCE --project $(gcloud config get-value project) --limit 1 --sort-by="~endTime" --format="value(id)")
gcloud sql backups restore $BACKUP_ID --restore-instance $SQL_INSTANCE --backup-instance $SQL_INSTANCE --project $(gcloud config get-value project)
```

## Blockers (repo-specific)

- If `POLICYINSIGHT_WORKER_ENABLED` is not `true`, jobs remain `PENDING`.
- If `APP_TOKEN_SECRET` is missing, token validation falls back to an insecure default.
- If `APP_STORAGE_MODE=gcp` but the bucket is missing or IAM is wrong, uploads fail.
- If DB credentials or Cloud SQL socket config are wrong, the service will fail readiness checks.

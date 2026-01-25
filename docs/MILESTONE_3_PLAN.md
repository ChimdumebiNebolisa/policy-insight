# Milestone 3 Plan (Cloud Run + Cloud SQL + Secret Manager)

This plan is a copy-paste runbook for deploying this repo to Google Cloud Run with Cloud SQL (PostgreSQL) and Secret Manager. It keeps Pub/Sub, Vertex AI, and Document AI disabled for Milestone 3.

## Deployment Checklist (linear, copy-paste)

1) Authenticate and set project/region (PowerShell):
```
gcloud auth login
$PROJECT_ID = (gcloud config get-value project)
gcloud config set project $PROJECT_ID
$REGION = "us-central1"
```

2) Define names used by all commands:
```
$SERVICE = "policy-insight"
$SQL_INSTANCE = "policy-insight-db"
$DB_NAME = "policyinsight"
$DB_USER = "policyinsight"
$BUCKET = "$PROJECT_ID-policyinsight"
$RUNTIME_SA_NAME = "policy-insight-runner"
$RUNTIME_SA = "$RUNTIME_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com"
```

3) Enable required APIs:
```
gcloud services enable run.googleapis.com cloudbuild.googleapis.com sqladmin.googleapis.com secretmanager.googleapis.com storage.googleapis.com --project $PROJECT_ID
```

4) Create Cloud SQL (Postgres 15) instance, database, and user:
```
gcloud sql instances create $SQL_INSTANCE --database-version=POSTGRES_15 --tier=db-custom-1-3840 --region $REGION --project $PROJECT_ID
gcloud sql databases create $DB_NAME --instance $SQL_INSTANCE --project $PROJECT_ID
$DB_PASSWORD = python -c "import secrets,base64; print(base64.urlsafe_b64encode(secrets.token_bytes(24)).decode())"
gcloud sql users create $DB_USER --instance $SQL_INSTANCE --password $DB_PASSWORD --project $PROJECT_ID
```

5) Create GCS bucket for uploads/reports:
```
gcloud storage buckets create gs://$BUCKET --location $REGION --project $PROJECT_ID --uniform-bucket-level-access
```

6) Create the Cloud Run runtime service account:
```
gcloud iam service-accounts create $RUNTIME_SA_NAME --project $PROJECT_ID
```

7) Grant IAM roles to the runtime service account:
```
gcloud projects add-iam-policy-binding $PROJECT_ID --member "serviceAccount:$RUNTIME_SA" --role roles/cloudsql.client
gcloud projects add-iam-policy-binding $PROJECT_ID --member "serviceAccount:$RUNTIME_SA" --role roles/secretmanager.secretAccessor
gcloud storage buckets add-iam-policy-binding gs://$BUCKET --member "serviceAccount:$RUNTIME_SA" --role roles/storage.objectAdmin
```

8) Create secrets in Secret Manager:
```
gcloud secrets create db-password --replication-policy=automatic --project $PROJECT_ID
$DB_PASSWORD | gcloud secrets versions add db-password --data-file=- --project $PROJECT_ID
$APP_TOKEN_SECRET = python -c "import secrets,base64; print(base64.urlsafe_b64encode(secrets.token_bytes(32)).decode())"
gcloud secrets create app-token-secret --replication-policy=automatic --project $PROJECT_ID
$APP_TOKEN_SECRET | gcloud secrets versions add app-token-secret --data-file=- --project $PROJECT_ID
```

9) Deploy Cloud Run from source (Cloud Build handles build artifacts implicitly):
```
$CONNECTION_NAME = (gcloud sql instances describe $SQL_INSTANCE --project $PROJECT_ID --format="value(connectionName)")
gcloud run deploy $SERVICE --source . --region $REGION --project $PROJECT_ID --service-account $RUNTIME_SA --add-cloudsql-instances $CONNECTION_NAME --min-instances 0 --allow-unauthenticated --set-env-vars "SPRING_PROFILES_ACTIVE=cloudrun,DB_HOST=/cloudsql/$CONNECTION_NAME,DB_PORT=5432,DB_NAME=$DB_NAME,DB_USER=$DB_USER,APP_STORAGE_MODE=gcp,GCS_BUCKET_NAME=$BUCKET,APP_MESSAGING_MODE=local,APP_PROCESSING_MODE=local,POLICYINSIGHT_WORKER_ENABLED=true,APP_RATE_LIMIT_UPLOAD_MAX_PER_HOUR=10,APP_RATE_LIMIT_QA_MAX_PER_HOUR=20,APP_RATE_LIMIT_QA_MAX_PER_JOB=3,APP_PROCESSING_MAX_TEXT_LENGTH=1000000,APP_PROCESSING_STAGE_TIMEOUT_SECONDS=300,APP_VALIDATION_PDF_MAX_PAGES=100,APP_VALIDATION_PDF_MAX_TEXT_LENGTH=1048576,APP_RETENTION_DAYS=30,APP_LOCAL_WORKER_POLL_MS=2000,APP_LOCAL_WORKER_BATCH_SIZE=5,APP_JOB_LEASE_DURATION_MINUTES=30,APP_JOB_MAX_ATTEMPTS=3,APP_GEMINI_RETRY_MAX_ATTEMPTS=3,APP_GEMINI_RETRY_BASE_DELAY_MS=1000" --set-secrets "DB_PASSWORD=db-password:latest,APP_TOKEN_SECRET=app-token-secret:latest"
```

10) Set base URL and allowed origins after deploy:
```
$SERVICE_URL = (gcloud run services describe $SERVICE --region $REGION --project $PROJECT_ID --format="value(status.url)")
gcloud run services update $SERVICE --region $REGION --project $PROJECT_ID --set-env-vars "APP_BASE_URL=$SERVICE_URL,APP_ALLOWED_ORIGINS=$SERVICE_URL"
```

## Production environment variables (explicit list)

Each variable is listed with its exact name, description, and whether it is a Secret Manager secret.

| Env var | Description | Secret Manager? |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Must be `cloudrun` to activate Cloud Run profile. | No |
| `PORT` | Cloud Run injected port (usually `8080`). Do not override unless required. | No |
| `DB_HOST` | Cloud SQL socket path: `/cloudsql/$CONNECTION_NAME`. | No |
| `DB_PORT` | PostgreSQL port (use `5432`). | No |
| `DB_NAME` | Database name (`policyinsight`). | No |
| `DB_USER` | Database user (`policyinsight`). | No |
| `DB_PASSWORD` | Database password. | Yes (`db-password`) |
| `APP_BASE_URL` | Public base URL for share links and absolute URLs (Cloud Run service URL). | No |
| `APP_ALLOWED_ORIGINS` | CORS allowed origin (set to Cloud Run service URL). | No |
| `APP_TOKEN_SECRET` | HMAC secret for capability tokens (must be unique per env). | Yes (`app-token-secret`) |
| `APP_STORAGE_MODE` | Storage mode (`gcp` for Cloud Storage). | No |
| `GCS_BUCKET_NAME` | GCS bucket name for uploads/reports. | No |
| `APP_STORAGE_LOCAL_DIR` | Local storage path (unused in prod, default `.local-storage`). | No |
| `APP_MESSAGING_MODE` | Messaging mode (`local` for Milestone 3, no Pub/Sub). | No |
| `APP_PROCESSING_MODE` | Processing mode (`local`; kept for config completeness). | No |
| `POLICYINSIGHT_WORKER_ENABLED` | Enables in-process worker (`true` in this plan). | No |
| `APP_LOCAL_WORKER_POLL_MS` | Worker polling interval (ms). | No |
| `APP_LOCAL_WORKER_BATCH_SIZE` | Worker batch size per poll. | No |
| `APP_JOB_LEASE_DURATION_MINUTES` | Lease duration for job processing. | No |
| `APP_JOB_MAX_ATTEMPTS` | Max job retry attempts. | No |
| `APP_RATE_LIMIT_UPLOAD_MAX_PER_HOUR` | Upload rate limit per IP. | No |
| `APP_RATE_LIMIT_QA_MAX_PER_HOUR` | Q&A rate limit per IP. | No |
| `APP_RATE_LIMIT_QA_MAX_PER_JOB` | Max Q&A per job. | No |
| `APP_PROCESSING_MAX_TEXT_LENGTH` | Max extracted text length (characters). | No |
| `APP_PROCESSING_STAGE_TIMEOUT_SECONDS` | Timeout per processing stage. | No |
| `APP_VALIDATION_PDF_MAX_PAGES` | Worker-side PDF page limit. | No |
| `APP_VALIDATION_PDF_MAX_TEXT_LENGTH` | Worker-side max text length (bytes). | No |
| `APP_RETENTION_DAYS` | Retention window for jobs and reports. | No |
| `APP_GEMINI_RETRY_MAX_ATTEMPTS` | Max LLM retry attempts (used even without Vertex AI). | No |
| `APP_GEMINI_RETRY_BASE_DELAY_MS` | Base delay for LLM retries (ms). | No |

## Worker execution decision (Milestone 3)

Decision: **Run the background worker inside the Cloud Run service** (no separate worker service).

Enforced by:
- `POLICYINSIGHT_WORKER_ENABLED=true`
- `APP_MESSAGING_MODE=local` (no Pub/Sub)

Why acceptable for Milestone 3: it validates Cloud Run + Cloud SQL + GCS + Secret Manager integration end-to-end without introducing Pub/Sub. The local in-process worker processes `PENDING` jobs using scheduled polling, which is sufficient to prove the system on Cloud Run for this milestone.

## Service accounts and IAM bindings

Runtime service account: `policy-insight-runner@$PROJECT_ID.iam.gserviceaccount.com`

Bindings required:
- `roles/cloudsql.client` on the project for Cloud SQL socket access.
- `roles/secretmanager.secretAccessor` on the project for Secret Manager access.
- `roles/storage.objectAdmin` on the bucket `gs://$BUCKET` for uploads/downloads.

## Smoke tests (copy-paste)

These commands assume `valid.pdf` exists in the current working directory.

1) Health check (expects `200` and JSON status UP):
```
$SERVICE_URL = (gcloud run services describe $SERVICE --region $REGION --project $PROJECT_ID --format="value(status.url)")
curl.exe -i "$SERVICE_URL/health"
```

2) Upload a document (expects `202` and JSON with `jobId` + `token`):
```
$UPLOAD_RESPONSE = curl.exe -s -X POST "$SERVICE_URL/api/documents/upload" -H "Accept: application/json" -F "file=@valid.pdf;type=application/pdf"
$JOB_ID = ($UPLOAD_RESPONSE | ConvertFrom-Json).jobId
$JOB_TOKEN = ($UPLOAD_RESPONSE | ConvertFrom-Json).token
$UPLOAD_RESPONSE
```

Success conditions:
- HTTP status `202 Accepted`.
- Response JSON includes `jobId`, `token`, and `status`=`PENDING`.

3) Poll status with job token (expects `200`; eventually `SUCCESS`):
```
curl.exe -i "$SERVICE_URL/api/documents/$JOB_ID/status" -H "Accept: application/json" -H "X-Job-Token: $JOB_TOKEN"
```

Optional polling loop (stops on SUCCESS or FAILED):
```
for ($i = 0; $i -lt 30; $i++) {
  $STATUS = (curl.exe -s "$SERVICE_URL/api/documents/$JOB_ID/status" -H "Accept: application/json" -H "X-Job-Token: $JOB_TOKEN" | ConvertFrom-Json).status
  "$i`t$STATUS"
  if ($STATUS -eq "SUCCESS" -or $STATUS -eq "FAILED") { break }
  Start-Sleep -Seconds 5
}
```

## Rollback plan (exact commands)

1) List revisions and pick a previous known-good revision:
```
gcloud run revisions list --service $SERVICE --region $REGION --project $PROJECT_ID
```

2) Route 100% traffic back to the previous revision:
```
$PREVIOUS_REVISION = (gcloud run revisions list --service $SERVICE --region $REGION --project $PROJECT_ID --sort-by="~metadata.creationTimestamp" --format="value(metadata.name)" | Select-Object -Skip 1 -First 1)
gcloud run services update-traffic $SERVICE --region $REGION --project $PROJECT_ID --to-revisions "$PREVIOUS_REVISION=100"
```

3) If Flyway migrations already ran and must be reverted:
- Restore the most recent Cloud SQL backup made before deploy:
```
$BACKUP_ID = (gcloud sql backups list --instance $SQL_INSTANCE --project $PROJECT_ID --limit 1 --sort-by="~endTime" --format="value(id)")
gcloud sql backups restore $BACKUP_ID --restore-instance $SQL_INSTANCE --backup-instance $SQL_INSTANCE --project $PROJECT_ID
```
- Then redeploy the previous Cloud Run revision (step 2) after DB restore completes.

## Blockers (repo-specific)

- If `POLICYINSIGHT_WORKER_ENABLED` is not `true`, jobs remain `PENDING` and never progress.
- If `APP_TOKEN_SECRET` is missing, token validation uses the default insecure value (`change-me-in-production`).
- If `APP_STORAGE_MODE=gcp` but the bucket does not exist or the runtime service account lacks GCS access, uploads will fail.
- If `DB_HOST` is not the Cloud SQL socket path or DB credentials are wrong, the service will fail readiness checks and 500 on DB access.


# PolicyInsight Deployment Guide

This guide provides step-by-step instructions for deploying PolicyInsight to Google Cloud Platform (GCP) using Cloud Run, Cloud SQL, GCS, and Pub/Sub.

## Prerequisites

- GCP project with billing enabled
- `gcloud` CLI installed and authenticated
- GitHub repository with Actions enabled
- Required GCP APIs enabled (see below)

## 1. Enable Required APIs

```bash
export PROJECT_ID=your-project-id
gcloud config set project $PROJECT_ID

gcloud services enable \
  run.googleapis.com \
  sqladmin.googleapis.com \
  storage-api.googleapis.com \
  pubsub.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  iamcredentials.googleapis.com
```

## 2. Workload Identity Federation (WIF) Setup

WIF allows GitHub Actions to authenticate to GCP without long-lived JSON keys.

### 2.1 Create WIF Pool

```bash
gcloud iam workload-identity-pools create github-pool \
  --project=$PROJECT_ID \
  --location=global \
  --display-name="GitHub Actions Pool"
```

### 2.2 Create WIF Provider

```bash
export REPO_OWNER=your-github-username
export REPO_NAME=policy-insight

gcloud iam workload-identity-pools providers create-oidc github-provider \
  --project=$PROJECT_ID \
  --location=global \
  --workload-identity-pool=github-pool \
  --issuer-uri=https://token.actions.githubusercontent.com \
  --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository=='$REPO_OWNER/$REPO_NAME'"
```

### 2.3 Get WIF Provider Resource Name

```bash
gcloud iam workload-identity-pools providers describe github-provider \
  --project=$PROJECT_ID \
  --location=global \
  --workload-identity-pool=github-pool \
  --format='value(name)'
```

Save this value as `WIF_PROVIDER` GitHub secret.

### 2.4 Create Service Account for GitHub Actions

```bash
gcloud iam service-accounts create github-actions-deploy \
  --display-name="GitHub Actions Deployment"
```

### 2.5 Grant IAM Roles to GitHub Actions Service Account

```bash
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:github-actions-deploy@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/run.admin"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:github-actions-deploy@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.writer"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:github-actions-deploy@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/iam.serviceAccountUser"
```

### 2.6 Allow GitHub to Impersonate Service Account

```bash
export WIF_PROVIDER_ID=$(gcloud iam workload-identity-pools providers describe github-provider \
  --project=$PROJECT_ID \
  --location=global \
  --workload-identity-pool=github-pool \
  --format='value(name)' | cut -d'/' -f4)

gcloud iam service-accounts add-iam-policy-binding \
  github-actions-deploy@$PROJECT_ID.iam.gserviceaccount.com \
  --project=$PROJECT_ID \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')/locations/global/workloadIdentityPools/github-pool/attribute.repository/$REPO_OWNER/$REPO_NAME"
```

Save `github-actions-deploy@$PROJECT_ID.iam.gserviceaccount.com` as `WIF_SERVICE_ACCOUNT` GitHub secret.

## 3. Artifact Registry Setup

```bash
gcloud artifacts repositories create policyinsight-repo \
  --repository-format=docker \
  --location=us-central1 \
  --description="PolicyInsight Docker images"
```

## 4. Cloud SQL Setup

### 4.1 Create Cloud SQL Instance

```bash
gcloud sql instances create policyinsight-db \
  --database-version=POSTGRES_15 \
  --tier=db-f1-micro \
  --region=us-central1 \
  --root-password=$(openssl rand -base64 32)
```

### 4.2 Get Connection Name

```bash
gcloud sql instances describe policyinsight-db \
  --format='value(connectionName)'
```

Save this as `CLOUDSQL_INSTANCE` (format: `PROJECT_ID:REGION:INSTANCE_NAME`).

### 4.3 Create Database

```bash
gcloud sql databases create policyinsight \
  --instance=policyinsight-db
```

### 4.4 Create Application User

```bash
export DB_PASSWORD=$(openssl rand -base64 32)
gcloud sql users create policyinsight-app \
  --instance=policyinsight-db \
  --password=$DB_PASSWORD
```

### 4.5 Store Database Credentials in Secret Manager

```bash
# Create secret for Cloud SQL connection details
echo -n "$(gcloud sql instances describe policyinsight-db --format='value(ipAddresses[0].ipAddress)')" | \
  gcloud secrets create cloudsql-host --data-file=-

echo -n "5432" | \
  gcloud secrets create cloudsql-port --data-file=-

echo -n "policyinsight" | \
  gcloud secrets create cloudsql-database --data-file=-

echo -n "policyinsight-app" | \
  gcloud secrets create cloudsql-username --data-file=-

echo -n "$DB_PASSWORD" | \
  gcloud secrets create cloudsql-password --data-file=-
```

**Note:** Cloud Run services are attached to Cloud SQL via the `--add-cloudsql-instances` annotation. This annotation enables Cloud SQL connectivity and provides a Unix socket at `/cloudsql/INSTANCE_CONNECTION_NAME`, but the application in this repo uses TCP connection to the Cloud SQL instance's IP address. The application connects using standard JDBC URL format `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}` where `DB_HOST` is the Cloud SQL instance's private IP (via VPC connector) or public IP, and `DB_PORT` is 5432. The `DB_HOST` and `DB_PORT` environment variables are set from Secret Manager. Service accounts require `roles/cloudsql.client` IAM role to connect to Cloud SQL.

## 5. GCS Bucket Setup

```bash
gsutil mb -p $PROJECT_ID -l us-central1 gs://policyinsight-prod-documents

# Set lifecycle policy (delete after 90 days)
cat > /tmp/lifecycle.json << 'EOF'
{
  "lifecycle": {
    "rule": [
      {
        "action": {"type": "Delete"},
        "condition": {"age": 90}
      }
    ]
  }
}
EOF
gsutil lifecycle set /tmp/lifecycle.json gs://policyinsight-prod-documents

# Set uniform bucket-level access
gsutil uniformbucketlevelaccess set on gs://policyinsight-prod-documents
```

## 6. Pub/Sub Setup

### 6.1 Create Topic

```bash
gcloud pubsub topics create policyinsight-analysis-topic
```

### 6.2 Create Service Accounts for Web and Worker

```bash
# Web service account
gcloud iam service-accounts create policyinsight-web \
  --display-name="PolicyInsight Web Service"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:policyinsight-web@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/storage.objectUser"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:policyinsight-web@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/pubsub.publisher"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:policyinsight-web@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/cloudsql.client"

# Worker service account
gcloud iam service-accounts create policyinsight-worker \
  --display-name="PolicyInsight Worker Service"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:policyinsight-worker@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/storage.objectAdmin"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:policyinsight-worker@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/pubsub.subscriber"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:policyinsight-worker@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/cloudsql.client"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:policyinsight-worker@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/documentai.apiUser"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:policyinsight-worker@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/aiplatform.user"
```

**Note:** Worker service uses ingress `all` (not `internal`) to allow Pub/Sub push. Security is enforced via IAM (`--no-allow-unauthenticated`) rather than ingress restriction.

**Environment Variables:**
- `APP_MESSAGING_MODE=gcp` - Enables DocumentProcessingWorker bean (required for PubSubController)
- `PUBSUB_PUSH_MODE=true` - Prevents DocumentProcessingWorker from starting a subscriber (push-only mode)
- `PUBSUB_PUSH_VERIFICATION_ENABLED=true` - Enables token verification in PubSubController
- `PUBSUB_PUSH_EXPECTED_EMAIL` - Worker service account email for token verification
- `PUBSUB_PUSH_EXPECTED_AUDIENCE` - Worker service URL for token verification (set after deployment)

### 6.3 Create Push Subscription with Authenticated Invocation

**Note:** The CD workflow (`.github/workflows/cd.yml`) automatically configures the Pub/Sub push subscription and IAM bindings. The following steps are for manual setup or verification.

**Required IAM bindings for Pub/Sub push authentication:**

The push subscription uses the worker service account as the push-auth service account. Two IAM bindings are required:

1. Grant Pub/Sub service agent permission to create tokens for the push-auth service account (worker service account):
```bash
# Get project number
PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')
PUBSUB_SA=service-${PROJECT_NUMBER}@gcp-sa-pubsub.iam.gserviceaccount.com
WORKER_SA=policyinsight-worker@$PROJECT_ID.iam.gserviceaccount.com

# Grant TokenCreator role to Pub/Sub service agent on worker service account
# Check if binding already exists to avoid duplicate binding errors, but fail on other errors
set +e
POLICY_OUTPUT=$(gcloud iam service-accounts get-iam-policy "$WORKER_SA" --format='value(bindings.role,bindings.members)' 2>&1)
POLICY_EXIT_CODE=$?
set -e
if [ $POLICY_EXIT_CODE -ne 0 ]; then
  echo "ERROR: Failed to get IAM policy for $WORKER_SA: $POLICY_OUTPUT"
  exit 1
fi
if ! echo "$POLICY_OUTPUT" | grep -q "roles/iam.serviceAccountTokenCreator.*$PUBSUB_SA"; then
  gcloud iam service-accounts add-iam-policy-binding "$WORKER_SA" \
    --member="serviceAccount:$PUBSUB_SA" \
    --role="roles/iam.serviceAccountTokenCreator"
else
  echo "✓ Token Creator binding already exists for Pub/Sub service agent"
fi
```

2. Grant push-auth service account (worker service account) permission to invoke the worker service:
```bash
WORKER_SA=policyinsight-worker@$PROJECT_ID.iam.gserviceaccount.com

# Check if binding already exists to avoid duplicate binding errors, but fail on other errors
set +e
RUN_POLICY_OUTPUT=$(gcloud run services get-iam-policy policyinsight-worker --region=us-central1 --format='value(bindings.role,bindings.members)' 2>&1)
RUN_POLICY_EXIT_CODE=$?
set -e
if [ $RUN_POLICY_EXIT_CODE -ne 0 ]; then
  echo "ERROR: Failed to get IAM policy for policyinsight-worker: $RUN_POLICY_OUTPUT"
  exit 1
fi
if ! echo "$RUN_POLICY_OUTPUT" | grep -q "roles/run.invoker.*$WORKER_SA"; then
  gcloud run services add-iam-policy-binding policyinsight-worker \
    --region=us-central1 \
    --member="serviceAccount:$WORKER_SA" \
    --role="roles/run.invoker"
else
  echo "✓ Run Invoker binding already exists for worker service account"
fi
```

**Note:** The worker service must be deployed first to create the IAM binding in step 2. Deploy worker service, then create subscription:

```bash
# After worker is deployed, get its URL
WORKER_URL=$(gcloud run services describe policyinsight-worker \
  --region=us-central1 \
  --format='value(status.url)')

# Create push subscription with authenticated invocation
# Endpoint path is /internal/pubsub (not /pubsub/push)
# Set explicit audience for token verification (stable, matches worker URL)
PUSH_AUDIENCE=$WORKER_URL
WORKER_SA=policyinsight-worker@$PROJECT_ID.iam.gserviceaccount.com

# Check if subscription exists
if gcloud pubsub subscriptions describe policyinsight-analysis-sub &>/dev/null; then
  # Update existing subscription
  gcloud pubsub subscriptions modify-push-config policyinsight-analysis-sub \
    --push-endpoint=$WORKER_URL/internal/pubsub \
    --push-auth-service-account=$WORKER_SA \
    --push-auth-token-audience=$PUSH_AUDIENCE
else
  # Create new subscription
  gcloud pubsub subscriptions create policyinsight-analysis-sub \
    --topic=policyinsight-analysis-topic \
    --push-endpoint=$WORKER_URL/internal/pubsub \
    --push-auth-service-account=$WORKER_SA \
    --push-auth-token-audience=$PUSH_AUDIENCE \
    --ack-deadline=300 \
    --message-retention-duration=604800s
fi

# Verify subscription configuration
SUB_DESC=$(gcloud pubsub subscriptions describe policyinsight-analysis-sub --format=json)

# Verify push endpoint
PUSH_ENDPOINT=$(echo "$SUB_DESC" | jq -r '.pushConfig.pushEndpoint // empty')
if [ "$PUSH_ENDPOINT" != "$WORKER_URL/internal/pubsub" ]; then
  echo "ERROR: Push endpoint mismatch. Expected: $WORKER_URL/internal/pubsub, Got: $PUSH_ENDPOINT"
  exit 1
fi
echo "✓ Push endpoint matches: $PUSH_ENDPOINT"

# Verify push auth service account
PUSH_AUTH_SA=$(echo "$SUB_DESC" | jq -r '.pushConfig.oidcToken.serviceAccountEmail // empty')
if [ "$PUSH_AUTH_SA" != "$WORKER_SA" ]; then
  echo "ERROR: Push auth service account mismatch. Expected: $WORKER_SA, Got: $PUSH_AUTH_SA"
  exit 1
fi
echo "✓ Push auth service account matches: $PUSH_AUTH_SA"

# Verify push auth audience
PUSH_AUTH_AUD=$(echo "$SUB_DESC" | jq -r '.pushConfig.oidcToken.audience // empty')
if [ -n "$PUSH_AUDIENCE" ] && [ "$PUSH_AUTH_AUD" != "$PUSH_AUDIENCE" ]; then
  echo "ERROR: Push auth audience mismatch. Expected: $PUSH_AUDIENCE, Got: $PUSH_AUTH_AUD"
  exit 1
fi
if [ -n "$PUSH_AUTH_AUD" ]; then
  echo "✓ Push auth audience matches: $PUSH_AUTH_AUD"
fi
```

## 7. GitHub Secrets Configuration

Configure the following secrets in GitHub repository settings (Settings → Secrets and variables → Actions):

- `GCP_PROJECT_ID`: Your GCP project ID
- `WIF_PROVIDER`: Full resource name from step 2.3 (format: `projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/providers/github-provider`)
- `WIF_SERVICE_ACCOUNT`: `github-actions-deploy@PROJECT_ID.iam.gserviceaccount.com`

## 8. Deployment Process

### 8.1 Automatic Deployment (CD Workflow)

The `.github/workflows/cd.yml` workflow automatically deploys on push to `main` branch:

1. **Build:** Builds Docker image and tags with git commit SHA
2. **Push:** Pushes image to Artifact Registry
3. **Deploy (No Traffic):** Deploys web and worker services with `--no-traffic` and revision tags
4. **Smoke Tests:** Tests health/readiness endpoints on tagged revisions
5. **Migrations:** Flyway runs automatically at Spring Boot startup (verified via successful service startup)
6. **Promote Traffic:**
   - Web: 90/10 canary if previous revision exists, else 100%
   - Worker: Always 100% (no canary)
7. **Configure Pub/Sub:** Sets up push subscription with authenticated invocation and explicit audience
8. **Configure IAM Bindings:** Grants required permissions for Pub/Sub push authentication (with proper error handling - fails on permission errors, skips if binding already exists)
9. **Verify Subscription:** Validates push endpoint, auth service account, and audience match expected values (fails workflow if any assertion fails)
10. **Final Health Check:** Verifies services are healthy on default URLs

### 8.2 Manual Rollback

Use `.github/workflows/rollback.yml` workflow:

1. Go to Actions → Rollback → Run workflow
2. Select service (web/worker/both)
3. Optionally specify revision (leave empty for previous)
4. Set traffic percentage (default 100%)
5. Workflow shifts traffic and verifies health

**Manual rollback via gcloud:**
```bash
# List revisions
gcloud run revisions list --service=policyinsight-web --region=us-central1

# Rollback to specific revision
gcloud run services update-traffic policyinsight-web \
  --region=us-central1 \
  --to-revisions=REVISION_NAME=100
```

## 9. Verification Commands

### 9.1 Cloud Run Services

```bash
# List services
gcloud run services list --region=us-central1

# Check web service health
curl -f $(gcloud run services describe policyinsight-web --region=us-central1 --format='value(status.url)')/health

# Verify unauthenticated access is blocked (should return 401/403)
WORKER_URL=$(gcloud run services describe policyinsight-worker --region=us-central1 --format='value(status.url)')
curl -v "$WORKER_URL/health"  # Should show 401 or 403

# Check worker service health (via authenticated proxy)
gcloud run services proxy policyinsight-worker --region=us-central1 --port=8080 &
sleep 2
curl -f http://localhost:8080/health
pkill -f "gcloud run services proxy"
```

### 9.2 Cloud SQL

```bash
# Verify instance
gcloud sql instances describe policyinsight-db

# Verify database
gcloud sql databases list --instance=policyinsight-db

# Connect and verify migrations (requires Cloud SQL Auth Proxy)
# Option 1: Using Cloud SQL Auth Proxy
cloud-sql-proxy $PROJECT_ID:us-central1:policyinsight-db &
sleep 2
psql -h 127.0.0.1 -U policyinsight-app -d policyinsight -c "SELECT COUNT(*) FROM flyway_schema_history;"
pkill cloud-sql-proxy

# Option 2: Direct connection to instance IP (if public IP enabled and authorized networks configured)
# DB_HOST=$(gcloud secrets versions access latest --secret=cloudsql-host)
# psql -h $DB_HOST -U policyinsight-app -d policyinsight -c "SELECT COUNT(*) FROM flyway_schema_history;"
```

### 9.3 GCS Bucket

```bash
# List bucket contents
gsutil ls gs://policyinsight-prod-documents/
```

### 9.4 Pub/Sub

```bash
# List topics
gcloud pubsub topics list

# List subscriptions
gcloud pubsub subscriptions list

# Verify push subscription configuration
WORKER_URL=$(gcloud run services describe policyinsight-worker --region=us-central1 --format='value(status.url)')
WORKER_SA=policyinsight-worker@$PROJECT_ID.iam.gserviceaccount.com
PUSH_AUDIENCE=$WORKER_URL

SUB_DESC=$(gcloud pubsub subscriptions describe policyinsight-analysis-sub --format=json)

# Verify push endpoint
PUSH_ENDPOINT=$(echo "$SUB_DESC" | jq -r '.pushConfig.pushEndpoint // empty')
if [ "$PUSH_ENDPOINT" != "$WORKER_URL/internal/pubsub" ]; then
  echo "ERROR: Push endpoint mismatch. Expected: $WORKER_URL/internal/pubsub, Got: $PUSH_ENDPOINT"
else
  echo "✓ Push endpoint matches: $PUSH_ENDPOINT"
fi

# Verify push auth service account
PUSH_AUTH_SA=$(echo "$SUB_DESC" | jq -r '.pushConfig.oidcToken.serviceAccountEmail // empty')
if [ "$PUSH_AUTH_SA" != "$WORKER_SA" ]; then
  echo "ERROR: Push auth service account mismatch. Expected: $WORKER_SA, Got: $PUSH_AUTH_SA"
else
  echo "✓ Push auth service account matches: $PUSH_AUTH_SA"
fi

# Verify push auth audience
PUSH_AUTH_AUD=$(echo "$SUB_DESC" | jq -r '.pushConfig.oidcToken.audience // empty')
if [ "$PUSH_AUTH_AUD" != "$PUSH_AUDIENCE" ]; then
  echo "ERROR: Push auth audience mismatch. Expected: $PUSH_AUDIENCE, Got: $PUSH_AUTH_AUD"
else
  echo "✓ Push auth audience matches: $PUSH_AUTH_AUD"
fi

# Pull messages (for testing)
gcloud pubsub subscriptions pull policyinsight-analysis-sub --limit=1
```

### 9.5 DD_VERSION Verification

```bash
# Check env var in deployed service
gcloud run services describe policyinsight-web \
  --region=us-central1 \
  --format='value(spec.template.spec.containers[0].env)' | grep DD_VERSION
```

## 10. Troubleshooting

### Service Won't Start

- Check Cloud Run logs: `gcloud run services logs read policyinsight-web --region=us-central1`
- Verify Cloud SQL connection: Check `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` env vars
- Verify Flyway migrations: Check if `flyway_schema_history` table exists

### Pub/Sub Push Fails

- Verify IAM bindings (step 6.3)
- Check worker service URL is correct in subscription
- Verify worker service is accessible (internal ingress)

### Health Checks Fail

- Check service logs for errors
- Verify database connectivity
- Check if required env vars are set

## 11. Key gcloud Commands Reference

### Deploy with Tag (No Traffic)
```bash
gcloud run deploy SERVICE_NAME \
  --image IMAGE_URL \
  --region REGION \
  --tag TAG_NAME \
  --no-traffic
```

### Smoke Test Tagged Revision
```bash
TAG_URL="https://TAG_NAME---SERVICE_NAME-REGION-PROJECT_ID.a.run.app"
curl -f "$TAG_URL/health"
```

### Promote Traffic
```bash
gcloud run services update-traffic SERVICE_NAME \
  --region REGION \
  --to-revisions=NEW_REV=90,OLD_REV=10
```

### Rollback Traffic
```bash
gcloud run services update-traffic SERVICE_NAME \
  --region REGION \
  --to-revisions=OLD_REV=100
```
=======
# Deployment Runbook

## Bootstrap

## Deploy

## Verify

## Rollback


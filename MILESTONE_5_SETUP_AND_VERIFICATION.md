# Milestone 5 Setup and Verification Guide

## F) Local-only + Real GCP Setup Requirements

### Path A: Local-only (No GCP Credentials)

**Environment Variables:**
```bash
# Windows PowerShell
$env:VERTEX_AI_ENABLED="false"
$env:WORKER_ENABLED="false"
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="policyinsight"
$env:DB_USER="postgres"
$env:DB_PASSWORD="postgres"
```

**Docker Compose Steps:**
```bash
# Start PostgreSQL
docker-compose up -d postgres

# Wait for database to be ready
Start-Sleep -Seconds 5

# Run migrations (if needed)
.\mvnw.cmd flyway:migrate

# Start application (without worker)
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

**How to run worker path without GCP:**
- Set `worker.enabled=false` in application-local.yml (default)
- Worker will not start (conditionally loaded via @ConditionalOnProperty)
- Upload endpoint works, but processing requires worker to be enabled
- For local processing without Pub/Sub, you can call `processDocument()` directly in tests

### Path B: Real GCP Setup (Vertex AI + GCS + Pub/Sub)

**Enable Required APIs:**
```bash
# Set your project ID
$env:GOOGLE_CLOUD_PROJECT="your-project-id"
gcloud config set project $env:GOOGLE_CLOUD_PROJECT

# Enable required APIs
gcloud services enable aiplatform.googleapis.com
gcloud services enable storage.googleapis.com
gcloud services enable pubsub.googleapis.com
gcloud services enable cloudbuild.googleapis.com
gcloud services enable run.googleapis.com
```

**Minimum IAM Roles for Service Account:**
```bash
# Create service account (if not exists)
gcloud iam service-accounts create policyinsight-app \
  --display-name="PolicyInsight App Service Account"

# Grant Vertex AI User role (for Gemini API)
gcloud projects add-iam-policy-binding $env:GOOGLE_CLOUD_PROJECT \
  --member="serviceAccount:policyinsight-app@${env:GOOGLE_CLOUD_PROJECT}.iam.gserviceaccount.com" \
  --role="roles/aiplatform.user"

# Grant Storage Admin role (for GCS)
gcloud projects add-iam-policy-binding $env:GOOGLE_CLOUD_PROJECT \
  --member="serviceAccount:policyinsight-app@${env:GOOGLE_CLOUD_PROJECT}.iam.gserviceaccount.com" \
  --role="roles/storage.objectAdmin"

# Grant Pub/Sub Editor role (for Pub/Sub)
gcloud projects add-iam-policy-binding $env:GOOGLE_CLOUD_PROJECT \
  --member="serviceAccount:policyinsight-app@${env:GOOGLE_CLOUD_PROJECT}.iam.gserviceaccount.com" \
  --role="roles/pubsub.editor"
```

**Application Configuration:**
```yaml
# application.yml or environment variables
vertexai:
  enabled: true
  project-id: ${GOOGLE_CLOUD_PROJECT}
  location: us-central1
  model: gemini-2.0-flash-exp

worker:
  enabled: true  # Enable worker for processing

pubsub:
  enabled: true
  project-id: ${GOOGLE_CLOUD_PROJECT}
  topic-name: document-analysis-topic
  subscription-name: document-analysis-sub

gcs:
  bucket-name: your-bucket-name
```

**Authentication:**
- Cloud Run: Uses service account attached to Cloud Run service automatically
- Local: Use `gcloud auth application-default login` or set `GOOGLE_APPLICATION_CREDENTIALS` env var

## E) Tests & Verification

### Test Results

**Run Tests:**
```bash
.\mvnw.cmd test
```

**Output:**
```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

All existing tests pass. The implementation maintains backward compatibility.

### Verification Commands

**1. Happy-path Upload (within size/type):**
```bash
# Create a small test PDF or use existing
curl -X POST http://localhost:8080/api/documents/upload `
  -F "file=@test-document.pdf" `
  -H "Content-Type: multipart/form-data"
```

**Expected Response:**
```json
{
  "session_id": "uuid-here",
  "document_id": "uuid-here",
  "status": "pending",
  "polling_url": "/api/documents/{uuid}/status"
}
```

**2. Negative-path Uploads (violating PRD constraints):**

**File too large (>50MB):**
```bash
# Create or use a file >50MB
curl -X POST http://localhost:8080/api/documents/upload `
  -F "file=@large-file.pdf" `
  -H "Content-Type: multipart/form-data"
```

**Expected Response:** `400 Bad Request` with error message about file size

**Unsupported file type:**
```bash
curl -X POST http://localhost:8080/api/documents/upload `
  -F "file=@document.txt" `
  -H "Content-Type: multipart/form-data"
```

**Expected Response:** `400 Bad Request` with error message about file type

**3. Fetch Report Call (showing citations with chunk_id + page number):**

```bash
# After job completes, fetch report
curl http://localhost:8080/api/documents/{document_id}/report
```

**Expected Report Excerpt:**
```json
{
  "summary_bullets": {
    "bullets": [
      {
        "text": "This document contains terms regarding data collection...",
        "chunk_ids": [1, 3, 5],
        "page_refs": [1, 2, 3]
      }
    ]
  },
  "risk_taxonomy": {
    "Data_Privacy": {
      "detected": true,
      "items": [
        {
          "text": "User data is collected and shared with third parties",
          "severity": "high",
          "chunk_ids": [7, 8],
          "page_refs": [4, 5]
        }
      ]
    }
  }
}
```

## Key Verification Points

1. ✅ **Stub mode works without GCP credentials** - Set `vertexai.enabled=false`
2. ✅ **Real Vertex AI works with ADC** - Set `vertexai.enabled=true` with valid credentials
3. ✅ **All claims have citations** - Validator ensures chunk_id + page number present
4. ✅ **Abstain when citations missing** - Uncited claims replaced with "Not detected / Not stated"
5. ✅ **Page numbers propagate** - All citations include page_refs from DocumentChunk.pageNumber
6. ✅ **Report stored in DB** - JSONB fields in reports table
7. ✅ **Report uploaded to GCS** - When GCS enabled, with deterministic path
8. ✅ **Job SUCCESS only after validation** - Validation happens before status update


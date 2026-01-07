# PolicyInsight Demo Guide

This guide covers running demos for PolicyInsight, both locally and in production.

## Local Development Demo

### Prerequisites

- Java 21+
- Maven 3.8+
- PostgreSQL running locally (default: localhost:5432)
- Database created: `policyinsight`

### Setup

1. **Start PostgreSQL** (if not already running):
   ```bash
   docker-compose up -d
   ```

2. **Run database migrations**:
   ```bash
   ./mvnw flyway:migrate
   # Or on Windows: .\mvnw.cmd flyway:migrate
   ```

3. **Start the application with local profile**:
   ```bash
   # Windows
   $env:SPRING_PROFILES_ACTIVE="local"; .\mvnw.cmd spring-boot:run

   # Linux/Mac
   SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
   ```

   The local profile enables the local worker for processing jobs.

4. **Verify the application is running**:
   - Web UI: http://localhost:8080
   - Health endpoint: http://localhost:8080/health

### Demo Steps

1. **Open the landing page**: Navigate to http://localhost:8080
2. **Upload a PDF**: Select a PDF file (max 50 MB) and click "Analyze Document"
3. **Watch status updates**: Status updates automatically every 2 seconds (PENDING → PROCESSING → SUCCESS)
4. **View report**: When status reaches SUCCESS, you're redirected to the report page with:
   - Document overview
   - Plain-English summary with citations
   - Obligations and restrictions
   - Risk taxonomy
   - Q&A section
5. **Ask a question**: Submit a question in the Q&A section to get a cited answer or "Insufficient evidence" response

### Automated Smoke Test

Run the automated smoke test to verify the complete flow:

**Windows (PowerShell):**
```powershell
# Start the application first
$env:SPRING_PROFILES_ACTIVE="local"; .\mvnw.cmd spring-boot:run

# In another terminal
pwsh scripts\mup-smoke.ps1 tiny.pdf
```

**Linux/Mac:**
```bash
# Start the application first
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run

# In another terminal
bash scripts/mup-smoke.sh tiny.pdf
```

The smoke test validates: upload, status polling, report generation, and Q&A functionality.

## Production Demo

For production demos, see [DEPLOYMENT.md](./DEPLOYMENT.md) for deployment details.

### Quick Production Test

```bash
# Upload a document
curl -X POST https://your-web-service-url/api/documents/upload \
  -F "file=@test-document.pdf"

# Poll status (replace JOB_ID with the UUID from upload response)
curl https://your-web-service-url/api/documents/${JOB_ID}/status

# Check logs
gcloud logging read "resource.labels.service_name=policyinsight-worker AND jsonPayload.job_id=${JOB_ID}" \
  --limit=50 \
  --format=json \
  --region=us-central1
```

Or use the automated smoke test scripts with the production URL:
```bash
BASE_URL=https://your-web-service-url bash scripts/smoke_test.sh tiny.pdf
```

## Troubleshooting

### Worker Not Processing Jobs (Local)

- Verify `SPRING_PROFILES_ACTIVE=local` is set
- Check logs for "Worker enabled = true"
- Ensure database is accessible and migrations ran successfully

### Status Stuck on PENDING

- Check application logs for errors
- Verify worker is enabled (local) or Pub/Sub is configured (production)
- Check database connection

### Upload Fails

- Verify PDF file is valid and < 50 MB
- Check file input accepts `.pdf` files only
- Review application logs for validation errors

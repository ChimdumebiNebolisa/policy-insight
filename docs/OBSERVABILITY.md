# PolicyInsight Observability Guide

This guide covers how to set up and use Datadog observability for PolicyInsight, including local development, Cloud Run deployment, and monitoring.

## Table of Contents

- [Overview](#overview)
- [Local Development Setup](#local-development-setup)
- [Cloud Run Deployment](#cloud-run-deployment)
- [Exporting Datadog Assets](#exporting-datadog-assets)
- [Testing Monitors](#testing-monitors)
- [Verification](#verification)

## Overview

PolicyInsight uses Datadog for comprehensive observability:

- **APM Tracing**: Custom spans across the processing pipeline (upload → extract → classify → risk scan → llm → export)
- **Structured Logging**: JSON logs with correlation IDs, trace/span IDs, and job IDs
- **Custom Metrics**: Job duration, success/failure rates, LLM costs, queue backlog
- **Dashboards**: Real-time operational metrics
- **Monitors**: Alerts for latency, backlog, and cost anomalies
- **SLOs**: Service level objectives for availability and latency

## Local Development Setup

### Prerequisites

1. Datadog account with API key and Application key
2. Datadog Agent running locally (or use Docker Compose)
3. Python 3 with `requests` library (for export scripts)

### Step 1: Start Datadog Agent

Using Docker Compose (recommended):

**PowerShell:**
```powershell
docker-compose -f docker-compose.datadog.yml up -d datadog-agent
```

**Bash:**
```bash
docker-compose -f docker-compose.datadog.yml up -d datadog-agent
```

Or install Datadog Agent locally:
- Follow [Datadog Agent installation guide](https://docs.datadoghq.com/agent/)
- Ensure agent is running on `localhost:8126` (APM) and `localhost:8125` (StatsD)

### Step 2: Configure Environment Variables

**PowerShell:**
```powershell
$env:DD_API_KEY = "your-api-key"
$env:DD_APP_KEY = "your-app-key"
$env:DD_AGENT_HOST = "localhost"
$env:DD_SERVICE = "policy-insight"
$env:DD_ENV = "local"
$env:DD_VERSION = "1.0.0"
$env:DD_LOGS_INJECTION = "true"
$env:DATADOG_ENABLED = "true"
```

**Bash:**
```bash
export DD_API_KEY=your-api-key
export DD_APP_KEY=your-app-key
export DD_AGENT_HOST=localhost
export DD_SERVICE=policy-insight
export DD_ENV=local
export DD_VERSION=1.0.0
export DD_LOGS_INJECTION=true
export DATADOG_ENABLED=true
```

### Step 3: Download dd-java-agent

The application will auto-download the agent via the run scripts, or download manually:

**PowerShell:**
```powershell
New-Item -ItemType Directory -Force -Path .dd-java-agent
Invoke-WebRequest -Uri "https://dtdg.co/latest-java-tracer" -OutFile ".dd-java-agent\dd-java-agent.jar"
```

**Bash:**
```bash
mkdir -p .dd-java-agent
curl -L -o .dd-java-agent/dd-java-agent.jar https://dtdg.co/latest-java-tracer
```

### Step 4: Run Application with Datadog

**Option A: Use wrapper scripts**

**PowerShell:**
```powershell
.\scripts\datadog\run-with-datadog.ps1
```

**Bash:**
```bash
./scripts/datadog/run-with-datadog.sh
```

**Option B: Manual Maven run**

```bash
export DD_SERVICE=policy-insight
export DD_ENV=local
export DD_AGENT_HOST=localhost
export DD_LOGS_INJECTION=true
export DATADOG_ENABLED=true

mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-javaagent:.dd-java-agent/dd-java-agent.jar \
    -Ddd.service=policy-insight \
    -Ddd.env=local \
    -Ddd.agent.host=localhost \
    -Ddd.logs.injection=true"
```

### Step 5: Verify Traces and Logs

1. **Check Datadog APM**: Go to https://app.datadoghq.com/apm/traces
2. **Check Logs**: Go to https://app.datadoghq.com/logs
3. **Check Metrics**: Go to https://app.datadoghq.com/metric/explorer

## Cloud Run Deployment

### Step 1: Update Dockerfile

Ensure your Dockerfile includes dd-java-agent:

```dockerfile
# Download Datadog Java agent
RUN curl -Lo /app/dd-java-agent.jar https://dtdg.co/latest-java-tracer

# Set environment variables
ENV DD_SERVICE=policy-insight
ENV DD_ENV=prod
ENV DD_LOGS_INJECTION=true
ENV DD_TRACE_ENABLED=true

# Run with agent
CMD exec java -javaagent:/app/dd-java-agent.jar \
  -Ddd.service=${DD_SERVICE} \
  -Ddd.env=${DD_ENV} \
  -Ddd.version=${DD_VERSION} \
  -Ddd.agent.host=${DD_AGENT_HOST} \
  -Ddd.logs.injection=true \
  -jar /app/policyinsight.jar
```

### Step 2: Deploy with Environment Variables

```bash
gcloud run deploy policyinsight \
  --image gcr.io/PROJECT_ID/policyinsight:latest \
  --set-env-vars \
    DD_API_KEY=your-api-key,\
    DD_AGENT_HOST=datadog-agent:8126,\
    DD_SERVICE=policy-insight,\
    DD_ENV=prod,\
    DD_VERSION=1.0.0,\
    DD_LOGS_INJECTION=true,\
    DD_TRACE_ENABLED=true,\
    DATADOG_ENABLED=true
```

**Note**: For Cloud Run, you may need to use Datadog's serverless integration or run a sidecar container with the Datadog Agent.

## Exporting Datadog Assets

Export dashboards, monitors, and SLOs to version-controlled JSON files. **Exports contain real IDs from Datadog** and are written to `/datadog/dashboards/`, `/datadog/monitors/`, and `/datadog/slos/`.

### Using Wrapper Scripts (Recommended)

**PowerShell:**
```powershell
# Set environment variables
$env:DD_API_KEY = "your-api-key"
$env:DD_APP_KEY = "your-app-key"
# Optional: for EU or US3 sites
$env:DD_SITE = "datadoghq.eu"  # or "us3.datadoghq.com"

# Export all assets
.\scripts\datadog\export-assets.ps1

# Export only dashboards
.\scripts\datadog\export-assets.ps1 --dashboards-only

# Export to custom directory
.\scripts\datadog\export-assets.ps1 --output-dir .\custom-datadog
```

**Bash:**
```bash
# Set environment variables
export DD_API_KEY=your-api-key
export DD_APP_KEY=your-app-key
# Optional: for EU or US3 sites
export DD_SITE=datadoghq.eu  # or us3.datadoghq.com

# Export all assets
./scripts/datadog/export-assets.sh

# Export only dashboards
./scripts/datadog/export-assets.sh --dashboards-only
```

### Using Python Script Directly

**PowerShell:**
```powershell
$env:DD_API_KEY = "your-api-key"
$env:DD_APP_KEY = "your-app-key"
python scripts\datadog\export-assets.py
```

**Bash:**
```bash
export DD_API_KEY=your-api-key
export DD_APP_KEY=your-app-key
python scripts/datadog/export-assets.py
```

### Verifying Exports Contain Real IDs

After exporting, verify that the JSON files contain real Datadog IDs:

**PowerShell:**
```powershell
# Check for IDs in exported files
Get-Content datadog\dashboards\*.json | Select-String -Pattern '"id"\s*:\s*\d+'
Get-Content datadog\monitors\*.json | Select-String -Pattern '"id"\s*:\s*\d+'
Get-Content datadog\slos\*.json | Select-String -Pattern '"id"\s*:\s*"[^"]+"'
```

**Bash:**
```bash
# Check for IDs in exported files
grep -r '"id"' datadog/dashboards/*.json
grep -r '"id"' datadog/monitors/*.json
grep -r '"id"' datadog/slos/*.json
```

If exports don't contain IDs, the export failed. Check error messages and verify API keys are correct.

## Applying Datadog Assets from Templates

To create or update dashboards, monitors, and SLOs from templates in `/datadog/templates/`:

**PowerShell:**
```powershell
$env:DD_API_KEY = "your-api-key"
$env:DD_APP_KEY = "your-app-key"
.\scripts\datadog\apply-assets.ps1
```

**Bash:**
```bash
export DD_API_KEY=your-api-key
export DD_APP_KEY=your-app-key
./scripts/datadog/apply-assets.sh
```

After applying templates, run `export-assets.py` to export the created assets with real IDs.

### Template Format Notes

- **UTF-8 BOM**: Template files can include a UTF-8 BOM (Byte Order Mark). The script automatically handles this using `utf-8-sig` encoding.
- **Idempotent Operations**: The script performs upsert operations:
  - **Dashboards**: Matched by `title` (exact match). Updates existing or creates new.
  - **Monitors**: Matched by `name` (exact match). Updates existing or creates new.
  - **SLOs**: Uses ID from template if present, otherwise creates new.
- **Response Handling**: The script robustly handles various API response shapes, including cases where responses may be lists instead of dictionaries.

### Directory Structure

```
datadog/
├── dashboards/          # Real exports from Datadog (contain IDs)
│   └── policyinsight-ops.json
├── monitors/            # Real exports from Datadog (contain IDs)
│   ├── api-latency.json
│   ├── queue-backlog.json
│   └── llm-cost-anomaly.json
├── slos/               # Real exports from Datadog (contain IDs)
│   ├── api-availability-slo.json
│   └── api-latency-slo.json
└── templates/          # Templates (no IDs) for creating/updating assets
    ├── dashboards/
    ├── monitors/
    └── slos/
```

## Testing Monitors

Use the traffic generator to simulate incidents and test monitor alerts:

### Latency Spike

```bash
python scripts/datadog/traffic-generator.py \
  --scenario latency \
  --duration 60 \
  --base-url http://localhost:8080
```

### Job Backlog Spike

```bash
python scripts/datadog/traffic-generator.py \
  --scenario backlog \
  --duration 120 \
  --base-url http://localhost:8080
```

### LLM Cost Spike

```bash
python scripts/datadog/traffic-generator.py \
  --scenario llm-cost \
  --duration 90 \
  --base-url http://localhost:8080
```

## Verification

This section provides exact commands to verify that all Datadog observability features are working correctly.

### Prerequisites

Before running verification commands, ensure:
1. Datadog Agent is running: `docker-compose -f docker-compose.datadog.yml up -d datadog-agent`
2. Application is running with Datadog enabled: `./scripts/datadog/run-with-datadog.sh` (or `.ps1` on Windows)
3. Environment variables are set: `DATADOG_ENABLED=true`, `DD_AGENT_HOST=localhost`

### 1. Verify Traces

**PowerShell:**
```powershell
# Upload a document
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$requestId = "verify-trace-$timestamp"
Invoke-WebRequest -Uri "http://localhost:8080/api/documents/upload" `
    -Method POST `
    -InFile "test.pdf" `
    -ContentType "multipart/form-data" `
    -Headers @{"X-Request-ID"=$requestId}

# Wait a few seconds for processing, then check Datadog APM
# Go to: https://app.datadoghq.com/apm/traces
# Filter by: service:policy-insight
```

**Bash:**
```bash
# Upload a document
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@test.pdf" \
  -H "X-Request-ID: verify-trace-$(date +%s)"

# Wait a few seconds for processing, then check Datadog APM
# Go to: https://app.datadoghq.com/apm/traces
# Filter by: service:policy-insight
```

**Expected:**
- Trace for `upload` operation visible in Datadog APM
- Child spans: `extraction`, `classification`, `risk_scan`, `llm`, `export`
- All spans tagged with `job_id`, `document_id`, `stage`
- Parent span `job.process` contains all child spans

**Verify in Datadog UI:**
1. Navigate to APM → Traces
2. Filter by service: `policy-insight`
3. Click on a trace to see the waterfall view
4. Verify span hierarchy: `upload` → `job.process` → `extraction`, `classification`, `risk_scan`, `llm`, `export`

### 2. Verify Logs

**PowerShell:**
```powershell
# Start application with Datadog enabled (logs should be JSON)
# Then make a request
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$requestId = "verify-logs-$timestamp"
Invoke-WebRequest -Uri "http://localhost:8080/api/documents/upload" `
    -Method POST `
    -InFile "test.pdf" `
    -ContentType "multipart/form-data" `
    -Headers @{"X-Request-ID"=$requestId}

# Check console output - should be JSON format
# Or check Datadog Logs: https://app.datadoghq.com/logs
```

**Bash:**
```bash
# Start application with Datadog enabled (logs should be JSON)
# Then make a request
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@test.pdf" \
  -H "X-Request-ID: verify-logs-$(date +%s)"

# Check console output - should be JSON format
# Or check Datadog Logs: https://app.datadoghq.com/logs
```

**Expected JSON structure in logs:**
```json
{
  "timestamp": "2025-01-29T10:00:00Z",
  "level": "INFO",
  "logger": "com.policyinsight.processing.LocalDocumentProcessingWorker",
  "message": "Processing document for job: ...",
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "job_id": "550e8400-e29b-41d4-a716-446655440000",
  "dd.trace_id": "12345678901234567890123456789012",
  "dd.span_id": "9876543210987654",
  "dd.service": "policy-insight",
  "dd.env": "local",
  "dd.version": "1.0.0"
}
```

**Verify in Datadog UI:**
1. Navigate to Logs → Search
2. Filter by: `service:policy-insight`
3. Verify logs are in JSON format with all required fields
4. Click on a log to see trace correlation (should link to APM trace)

### 3. Verify Metrics

**PowerShell:**
```powershell
# Check metrics endpoint (actuator)
Invoke-WebRequest -Uri "http://localhost:8080/actuator/metrics/policyinsight.job.duration"
Invoke-WebRequest -Uri "http://localhost:8080/actuator/metrics/policyinsight.job.success"
Invoke-WebRequest -Uri "http://localhost:8080/actuator/metrics/policyinsight.llm.tokens"
Invoke-WebRequest -Uri "http://localhost:8080/actuator/metrics/policyinsight.job.backlog"

# Check HTTP server metrics (auto-instrumented by dd-java-agent)
Invoke-WebRequest -Uri "http://localhost:8080/actuator/metrics/http.server.requests"
```

**Bash:**
```bash
# Check metrics endpoint (actuator)
curl http://localhost:8080/actuator/metrics/policyinsight.job.duration
curl http://localhost:8080/actuator/metrics/policyinsight.job.success
curl http://localhost:8080/actuator/metrics/policyinsight.llm.tokens
curl http://localhost:8080/actuator/metrics/policyinsight.job.backlog

# Check HTTP server metrics (auto-instrumented by dd-java-agent)
curl http://localhost:8080/actuator/metrics/http.server.requests
```

**Expected:**
- Metrics visible in Datadog Metrics Explorer: https://app.datadoghq.com/metric/explorer
- Custom metrics: `policyinsight.job.duration`, `policyinsight.job.success`, `policyinsight.job.failure`, `policyinsight.llm.tokens`, `policyinsight.llm.cost_estimate_usd`, `policyinsight.job.backlog`
- HTTP server metrics: `http.server.requests` (with status, method, uri tags)

**Verify in Datadog UI:**
1. Navigate to Metrics → Explorer
2. Search for: `policyinsight.job.duration`
3. Verify metric appears with tags: `service:policy-insight`
4. Check other custom metrics: `policyinsight.llm.tokens`, `policyinsight.job.backlog`

### 4. Verify Custom Spans

**Check in Datadog APM:**
1. Go to APM → Traces
2. Filter by service: `policy-insight`
3. Click on a trace to see span details
4. Look for custom spans:
   - `upload` (with tags: `job_id`, `document_id`, `stage`, `file_size_bytes`)
   - `extraction` (with tags: `provider`, `fallback_used`, `chunk_count`)
   - `classification` (with tags: `classification`, `confidence`, `provider`)
   - `risk_scan` (with tags: `risk_count.total`, `risk_count.data_privacy`, etc.)
   - `llm` (with tags: `provider`, `model`, `tokens.input`, `tokens.output`, `cost_estimate_usd`)
   - `export` (with tags: `report_stored`, `report_path`)

**Command to generate test data:**
```bash
# Upload multiple documents to see spans
for i in {1..3}; do
  curl -X POST http://localhost:8080/api/documents/upload \
    -F "file=@test.pdf" \
    -H "X-Request-ID: test-$i"
  sleep 2
done
```

### 5. Verify Correlation IDs

**PowerShell:**
```powershell
# Upload a document with custom request ID
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$requestId = "verify-correlation-$timestamp"
$response = Invoke-WebRequest -Uri "http://localhost:8080/api/documents/upload" `
    -Method POST `
    -InFile "test.pdf" `
    -ContentType "multipart/form-data" `
    -Headers @{"X-Request-ID"=$requestId}

# Response should include:
# X-Request-ID: verify-correlation-...
$response.Headers["X-Request-ID"]
```

**Bash:**
```bash
# Upload a document with custom request ID
REQUEST_ID="verify-correlation-$(date +%s)"
curl -v -X POST http://localhost:8080/api/documents/upload \
  -F "file=@test.pdf" \
  -H "X-Request-ID: $REQUEST_ID"

# Response should include:
# X-Request-ID: verify-correlation-...
```

**Expected:**
- Request ID in response header (`X-Request-ID`)
- Request ID in logs (as `request_id` and `correlation_id`)
- Job ID in logs (as `job_id`) when processing jobs
- Request ID and Job ID in span tags

**Verify in Datadog UI:**
1. Navigate to Logs → Search
2. Filter by: `request_id:verify-correlation-*`
3. Verify all logs for that request have the same `request_id`
4. Check that `job_id` appears in processing logs

### 6. End-to-End Verification

**PowerShell:**
```powershell
# Complete verification of Datadog observability
Write-Host "=== Verifying Datadog Observability ===" -ForegroundColor Green

# 1. Check Datadog Agent
Write-Host "1. Checking Datadog Agent..." -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8126/info" -UseBasicParsing -ErrorAction Stop
    Write-Host "✅ Agent is running" -ForegroundColor Green
} catch {
    Write-Host "❌ Agent not running" -ForegroundColor Red
}

# 2. Upload document
Write-Host "2. Uploading test document..." -ForegroundColor Cyan
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$requestId = "e2e-verify-$timestamp"
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/documents/upload" `
        -Method POST `
        -InFile "test.pdf" `
        -ContentType "multipart/form-data" `
        -Headers @{"X-Request-ID"=$requestId}
    $jobId = ($response.Content | ConvertFrom-Json).jobId
    Write-Host "✅ Uploaded, job ID: $jobId" -ForegroundColor Green
} catch {
    Write-Host "❌ Upload failed: $_" -ForegroundColor Red
}

# 3. Wait for processing
Write-Host "3. Waiting for processing..." -ForegroundColor Cyan
Start-Sleep -Seconds 10

# 4. Check status
Write-Host "4. Checking job status..." -ForegroundColor Cyan
try {
    $statusResponse = Invoke-WebRequest -Uri "http://localhost:8080/api/documents/$jobId/status"
    $status = ($statusResponse.Content | ConvertFrom-Json).status
    Write-Host "✅ Job status: $status" -ForegroundColor Green
} catch {
    Write-Host "❌ Status check failed: $_" -ForegroundColor Red
}

# 5. Check metrics
Write-Host "5. Checking metrics..." -ForegroundColor Cyan
try {
    $metricsResponse = Invoke-WebRequest -Uri "http://localhost:8080/actuator/metrics/policyinsight.job.duration" -UseBasicParsing
    Write-Host "✅ Metrics endpoint accessible" -ForegroundColor Green
} catch {
    Write-Host "❌ Metrics endpoint not accessible" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== Verification Complete ===" -ForegroundColor Green
Write-Host "Next steps:"
Write-Host "1. Check Datadog APM: https://app.datadoghq.com/apm/traces"
Write-Host "2. Check Datadog Logs: https://app.datadoghq.com/logs"
Write-Host "3. Check Datadog Metrics: https://app.datadoghq.com/metric/explorer"
```

**Bash:**
```bash
#!/bin/bash
# Complete verification of Datadog observability

echo "=== Verifying Datadog Observability ==="

# 1. Check Datadog Agent
echo "1. Checking Datadog Agent..."
curl -s http://localhost:8126/info > /dev/null && echo "✅ Agent is running" || echo "❌ Agent not running"

# 2. Upload document
echo "2. Uploading test document..."
RESPONSE=$(curl -s -X POST http://localhost:8080/api/documents/upload \
  -F "file=@test.pdf" \
  -H "X-Request-ID: e2e-verify-$(date +%s)")
JOB_ID=$(echo $RESPONSE | jq -r '.jobId')
echo "✅ Uploaded, job ID: $JOB_ID"

# 3. Wait for processing
echo "3. Waiting for processing..."
sleep 10

# 4. Check status
echo "4. Checking job status..."
STATUS=$(curl -s http://localhost:8080/api/documents/$JOB_ID/status | jq -r '.status')
echo "✅ Job status: $STATUS"

# 5. Check metrics
echo "5. Checking metrics..."
curl -s http://localhost:8080/actuator/metrics/policyinsight.job.duration > /dev/null && echo "✅ Metrics endpoint accessible" || echo "❌ Metrics endpoint not accessible"

echo ""
echo "=== Verification Complete ==="
echo "Next steps:"
echo "1. Check Datadog APM: https://app.datadoghq.com/apm/traces"
echo "2. Check Datadog Logs: https://app.datadoghq.com/logs"
echo "3. Check Datadog Metrics: https://app.datadoghq.com/metric/explorer"
```

## Runbook: API Key Validation

This section provides commands to validate Datadog API keys and troubleshoot permission issues.

### Validate API Key

The API key is used for authentication and must be valid.

**PowerShell:**
```powershell
$headers = @{"DD-API-KEY" = $env:DD_API_KEY}
$response = Invoke-RestMethod -Uri "https://api.datadoghq.com/api/v1/validate" -Headers $headers -Method Get
$response.valid  # Should be $true
```

**Expected Output:**
```powershell
valid
---
True
```

If validation fails (403 or invalid response), check your API key at:
https://app.datadoghq.com/organization-settings/api-keys

### Validate Application Key Permissions

The Application key must have read/write permissions for monitors, dashboards, and SLOs.

**PowerShell:**
```powershell
$headers = @{
    "DD-API-KEY" = $env:DD_API_KEY
    "DD-APPLICATION-KEY" = $env:DD_APP_KEY
}
$response = Invoke-RestMethod -Uri "https://api.datadoghq.com/api/v1/monitor" -Headers $headers -Method Get
# Should return successfully (200 OK), not 403 Forbidden
```

**Expected Output:**
- Success: Returns a list/array of monitors (may be empty `[]`)
- Failure: HTTP 403 Forbidden error

### Troubleshooting 403 Forbidden

If you see `403 Client Error: Forbidden` when running apply-assets.py or export-assets.py:

1. **Check Application Key Scopes**: Navigate to https://app.datadoghq.com/organization-settings/application-keys

2. **Required Scopes**:
   - For **applying assets** (create/update):
     - `monitors_read`, `monitors_write`
     - `dashboards_read`, `dashboards_write`
     - `slo_read`, `slo_write`
   - For **exporting assets** (read only):
     - `monitors_read`
     - `dashboards_read`
     - `slo_read`

3. **Update Key**: Edit your Application key and ensure all required scopes are checked. If creating a new key, select the appropriate scopes during creation.

4. **Verify**: Re-run the validation commands above. The monitor list endpoint should return 200 OK, not 403.

**Common Issues:**
- Application key created without scopes (default is no permissions)
- Key belongs to different Datadog organization than API key
- Key has been revoked or expired
- Key has read scopes but not write scopes (for apply operations)

### Verification Checklist

Run these commands to verify your setup before running apply/export scripts:

```powershell
# 1. Validate API key
$env:DD_API_KEY = "your-api-key"
$headers = @{"DD-API-KEY" = $env:DD_API_KEY}
$result = Invoke-RestMethod -Uri "https://api.datadoghq.com/api/v1/validate" -Headers $headers -Method Get
if ($result.valid) {
    Write-Host "✅ API key is valid" -ForegroundColor Green
} else {
    Write-Host "❌ API key validation failed" -ForegroundColor Red
    exit 1
}

# 2. Validate Application key (test monitor read)
$env:DD_APP_KEY = "your-app-key"
$headers = @{
    "DD-API-KEY" = $env:DD_API_KEY
    "DD-APPLICATION-KEY" = $env:DD_APP_KEY
}
try {
    $monitors = Invoke-RestMethod -Uri "https://api.datadoghq.com/api/v1/monitor" -Headers $headers -Method Get
    Write-Host "✅ Application key has monitor_read permission" -ForegroundColor Green
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 403) {
        Write-Host "❌ Application key lacks required permissions (403 Forbidden)" -ForegroundColor Red
        Write-Host "   Update scopes at: https://app.datadoghq.com/organization-settings/application-keys" -ForegroundColor Yellow
        exit 1
    } else {
        Write-Host "⚠️  Unexpected error: $_" -ForegroundColor Yellow
        throw
    }
}

# 3. Run apply-assets script (should validate keys automatically)
.\scripts\datadog\apply-assets.ps1
# Expected: "✅ API keys validated" and successful asset creation/updates

# 4. Run export-assets script (should validate keys automatically)
.\scripts\datadog\export-assets.ps1
# Expected: "✅ API keys validated" and successful exports to ./datadog/
```

**Expected Success Output:**
```
✅ API key is valid
✅ Application key has monitor_read permission
Validating API keys...
✅ API keys validated

Applying Datadog assets from templates...
...
✅ All assets applied successfully!
```

## Troubleshooting

### Traces Not Appearing

1. Check Datadog Agent is running: `curl http://localhost:8126/info`
2. Verify `DD_AGENT_HOST` is correct
3. Check `DD_TRACE_ENABLED=true`
4. Verify dd-java-agent is loaded: Check application startup logs

### Logs Not in JSON Format

1. Ensure `DATADOG_ENABLED=true`
2. Check Spring profile: `--spring.profiles.active=datadog`
3. Verify `logback-spring.xml` has `datadog` profile configuration

### Metrics Not Appearing

1. Check StatsD connection: `netcat -u localhost 8125`
2. Verify `DATADOG_ENABLED=true`
3. Check `DD_AGENT_HOST` and `DD_DOGSTATSD_PORT` (default: 8125)
4. Verify Micrometer StatsD registry is configured

### Custom Spans Not Visible

1. Ensure OpenTelemetry dependencies are in `pom.xml`
2. Check `TracingService` is autowired (not null)
3. Verify spans are created with `tracingService.spanBuilder()`
4. Check span is ended with `span.end()`

## Additional Resources

- [Datadog Java Agent Documentation](https://docs.datadoghq.com/tracing/setup_overview/setup/java/)
- [OpenTelemetry Java Documentation](https://opentelemetry.io/docs/instrumentation/java/)
- [Micrometer StatsD Documentation](https://micrometer.io/docs/registry/statsd)

---

## Script Implementation Notes

The Datadog apply/export scripts (`scripts/datadog/apply-assets.py` and `export-assets.py`) include:

- **Robust error handling**: All HTTP requests use timeouts (20s), retries with exponential backoff (429/5xx), and validate JSON responses
- **Preflight validation**: API and Application keys are validated before attempting operations
- **Actionable error messages**: 403 errors include required scope information and links to fix permissions
- **Failure tracking**: Apply script tracks created/updated/failed counts and exits non-zero on any failure
- **Diagnostics**: Errors include HTTP method, URL, status code, content-type, and response body preview (first 300 chars)
- **Metrics sanity check**: Optional post-apply check warns if referenced metrics might be missing (best-effort, non-blocking)
- **UTF-8 BOM handling**: Template files are read with `utf-8-sig` encoding to handle BOM gracefully
- **Idempotent operations**: Dashboards and monitors are upserted by name/title, preventing duplicate creation errors
- **Flexible response handling**: Scripts handle various API response shapes (dicts, lists) robustly

PowerShell wrappers properly propagate exit codes to prevent pipelines from continuing after failures.


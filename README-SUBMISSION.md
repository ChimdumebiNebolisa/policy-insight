# PolicyInsight - Submission Guide

This document provides evidence and pointers for judges evaluating the PolicyInsight submission, particularly for the Datadog Challenge track.

## Quick Links

- **Main README**: [README.md](./README.md)
- **Observability Guide**: [docs/OBSERVABILITY.md](./docs/OBSERVABILITY.md)
- **Tasks/Milestones**: [tasks.md](./tasks.md)

## Datadog Observability Evidence

### 1. APM Tracing

**Evidence Location:**
- Datadog APM Dashboard: https://app.datadoghq.com/apm/traces
- Code: Custom spans in `src/main/java/com/policyinsight/observability/TracingService.java`
- Pipeline spans: `src/main/java/com/policyinsight/processing/LocalDocumentProcessingWorker.java`

**What to Look For:**
- Custom spans for each pipeline stage: `upload`, `extraction`, `classification`, `risk_scan`, `llm`, `export`
- Span tags: `job_id`, `document_id`, `stage`, `provider`, `tokens`, `duration_ms`
- Parent-child span relationships showing the full processing pipeline

**Verification Command:**
```bash
# Upload a document and check traces
curl -X POST http://localhost:8080/api/documents/upload -F "file=@test.pdf"
# Then check Datadog APM for the trace
```

### 2. Structured JSON Logging

**Evidence Location:**
- Logback configuration: `src/main/resources/logback-spring.xml`
- Correlation filter: `src/main/java/com/policyinsight/util/CorrelationIdFilter.java`
- Logs: Application console output (when `DATADOG_ENABLED=true`)

**What to Look For:**
- JSON log format with fields: `timestamp`, `level`, `logger`, `message`, `request_id`, `job_id`, `dd.trace_id`, `dd.span_id`, `dd.service`, `dd.env`, `dd.version`
- Correlation IDs propagated through request headers (`X-Request-ID`)
- Job IDs in logs for processing operations

**Verification Command:**
```bash
# Run with Datadog enabled and check logs
export DATADOG_ENABLED=true
./scripts/datadog/run-with-datadog.sh
# Check console output - should be JSON format
```

### 3. Custom Metrics

**Evidence Location:**
- Metrics service: `src/main/java/com/policyinsight/observability/DatadogMetricsService.java`
- Metrics config: `src/main/java/com/policyinsight/config/DatadogMetricsConfig.java`
- Datadog Metrics Explorer: https://app.datadoghq.com/metric/explorer

**Metrics Implemented:**
- `policyinsight.job.duration` - Job processing duration (Timer)
- `policyinsight.job.success` - Successful job count (Counter)
- `policyinsight.job.failure` - Failed job count (Counter)
- `policyinsight.job.backlog` - Pending jobs in queue (Gauge)
- `policyinsight.llm.tokens` - LLM token usage (Counter, with input/output tags)
- `policyinsight.llm.cost_estimate_usd` - Estimated LLM cost (Counter)
- `policyinsight.llm.latency_ms` - LLM API latency (Timer)

**Verification Command:**
```bash
# Check metrics endpoint (if actuator enabled)
curl http://localhost:8080/actuator/metrics/policyinsight.job.duration
curl http://localhost:8080/actuator/metrics/policyinsight.llm.tokens
```

### 4. Dashboards

**Evidence Location:**
- Exported dashboard: `datadog/dashboards/policyinsight-ops.json`
- Datadog Dashboard: https://app.datadoghq.com/dashboard/lists

**Dashboard Widgets:**
- API Latency (p50, p95, p99)
- Error Rate
- Throughput (Requests/sec)
- Queue Backlog (Pending Jobs)
- LLM Cost (USD)
- LLM Latency (ms)

**Export Command:**
```bash
export DD_API_KEY=your-api-key
export DD_APP_KEY=your-app-key
python scripts/datadog/export-assets.py --dashboards-only
```

### 5. Monitors

**Evidence Location:**
- Exported monitors: `datadog/monitors/`
  - `api-latency.json` - API latency monitor
  - `queue-backlog.json` - Queue backlog monitor
  - `llm-cost-anomaly.json` - LLM cost anomaly monitor
- Datadog Monitors: https://app.datadoghq.com/monitors/manage

**Monitor Types:**
- **API Latency**: Alert when p95 latency > 2 seconds
- **Queue Backlog**: Alert when pending jobs > 50
- **LLM Cost Anomaly**: Alert when LLM cost exceeds baseline by 2x

**Export Command:**
```bash
export DD_API_KEY=your-api-key
export DD_APP_KEY=your-app-key
python scripts/datadog/export-assets.py --monitors-only
```

### 6. SLOs

**Evidence Location:**
- Exported SLOs: `datadog/slos/`
  - `api-availability-slo.json` - API availability SLO (99.9%)
  - `api-latency-slo.json` - API latency SLO (p95 < 2s)
- Datadog SLOs: https://app.datadoghq.com/slo

**SLO Definitions:**
- **API Availability**: 99.9% uptime (7-day rolling window)
- **API Latency**: 95% of requests < 2 seconds (7-day rolling window)

**Export Command:**
```bash
export DD_API_KEY=your-api-key
export DD_APP_KEY=your-app-key
python scripts/datadog/export-assets.py --slos-only
```

### 7. Traffic Generator

**Evidence Location:**
- Script: `scripts/datadog/traffic-generator.py`
- Purpose: Generate traffic to trigger monitors for testing

**Usage:**
```bash
# Generate latency spike
python scripts/datadog/traffic-generator.py --scenario latency --duration 60

# Generate backlog spike
python scripts/datadog/traffic-generator.py --scenario backlog --duration 120

# Generate LLM cost spike
python scripts/datadog/traffic-generator.py --scenario llm-cost --duration 90
```

## Code Structure

### Observability Components

```
src/main/java/com/policyinsight/
├── observability/
│   ├── TracingService.java          # OpenTelemetry span creation
│   ├── TracingServiceInterface.java # Interface for tracing
│   ├── TracingServiceStub.java      # Stub when Datadog disabled
│   ├── DatadogMetricsService.java   # Custom metrics emission
│   ├── DatadogMetricsServiceInterface.java
│   └── DatadogMetricsServiceStub.java
├── config/
│   └── DatadogMetricsConfig.java    # StatsD/Micrometer config
└── util/
    └── CorrelationIdFilter.java     # Request correlation IDs
```

### Pipeline Instrumentation

- **Upload**: `src/main/java/com/policyinsight/api/DocumentController.java` (upload span)
- **Processing**: `src/main/java/com/policyinsight/processing/LocalDocumentProcessingWorker.java` (extraction, classification, risk_scan, llm, export spans)
- **LLM Calls**: `src/main/java/com/policyinsight/processing/GeminiService.java` (llm.call span with token/cost tracking)

## Verification Checklist

- [ ] **dd-java-agent**: Configured and loaded (check startup logs for "Datadog tracer started")
- [ ] **Traces**: Visible in Datadog APM with custom spans (upload → extract → classify → risk_scan → llm → export)
- [ ] **Logs**: JSON format with correlation IDs and trace/span IDs (check console output or Datadog Logs)
- [ ] **Metrics**: Custom metrics visible in Datadog Metrics Explorer (`policyinsight.*` namespace)
- [ ] **HTTP Server Metrics**: Auto-instrumented metrics visible (`http.server.requests`)
- [ ] **Dashboards**: PolicyInsight-Ops dashboard shows all metrics (exported to `/datadog/dashboards/`)
- [ ] **Monitors**: 3+ monitors configured and exported (API latency, queue backlog, LLM cost anomaly)
- [ ] **SLOs**: 2+ SLOs defined and exported (API availability, API latency)
- [ ] **Correlation IDs**: Propagated via headers (`X-Request-ID`) and MDC (visible in logs)
- [ ] **Job IDs**: Present in logs and span tags (check processing logs)
- [ ] **Export Scripts**: Successfully export dashboards/monitors/SLOs via `export-assets.py`

## Quick Verification Commands

### Step 1: Start Datadog Agent and Application

```bash
# Start Datadog Agent (if not already running)
docker-compose -f docker-compose.datadog.yml up -d datadog-agent

# Verify agent is running
curl http://localhost:8126/info

# Start application with Datadog enabled
export DD_API_KEY=your-api-key  # Optional for local testing
export DD_APP_KEY=your-app-key  # Optional for local testing
export DATADOG_ENABLED=true
export DD_AGENT_HOST=localhost
export DD_SERVICE=policy-insight
export DD_ENV=local

# Use wrapper script (recommended)
./scripts/datadog/run-with-datadog.sh

# Or manually with Maven
./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-javaagent:.dd-java-agent/dd-java-agent.jar \
    -Ddd.service=policy-insight \
    -Ddd.env=local \
    -Ddd.agent.host=localhost \
    -Ddd.logs.injection=true"
```

### Step 2: Verify Traces

```bash
# Upload a document
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@test.pdf" \
  -H "X-Request-ID: verify-$(date +%s)"

# Wait 10-30 seconds for processing, then check:
# https://app.datadoghq.com/apm/traces?service=policy-insight
```

**Expected in Datadog APM:**
- Trace for `upload` operation
- Parent span `job.process` with child spans: `extraction`, `classification`, `risk_scan`, `llm`, `export`
- All spans tagged with `job_id`, `document_id`, `stage`

### Step 3: Verify Logs

```bash
# Check console output (should be JSON when DATADOG_ENABLED=true)
# Or check Datadog Logs: https://app.datadoghq.com/logs?query=service%3Apolicy-insight
```

**Expected JSON log structure:**
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

### Step 4: Verify Metrics

```bash
# Check custom metrics via actuator
curl http://localhost:8080/actuator/metrics/policyinsight.job.duration
curl http://localhost:8080/actuator/metrics/policyinsight.job.success
curl http://localhost:8080/actuator/metrics/policyinsight.llm.tokens
curl http://localhost:8080/actuator/metrics/policyinsight.job.backlog

# Check HTTP server metrics (auto-instrumented)
curl http://localhost:8080/actuator/metrics/http.server.requests

# Check in Datadog Metrics Explorer:
# https://app.datadoghq.com/metric/explorer?query=policyinsight.job.duration
```

**Expected metrics:**
- `policyinsight.job.duration` (Timer)
- `policyinsight.job.success` (Counter)
- `policyinsight.job.failure` (Counter)
- `policyinsight.job.backlog` (Gauge)
- `policyinsight.llm.tokens` (Counter)
- `policyinsight.llm.cost_estimate_usd` (Counter)
- `http.server.requests` (Timer, auto-instrumented)

### Step 5: Verify Correlation IDs

```bash
# Upload with custom request ID
REQUEST_ID="test-request-$(date +%s)"
curl -v -X POST http://localhost:8080/api/documents/upload \
  -F "file=@test.pdf" \
  -H "X-Request-ID: $REQUEST_ID"

# Response should include: X-Request-ID: test-request-...
# Logs should include: "request_id": "test-request-..."
```

### Step 6: Export Datadog Assets

```bash
# Export dashboards, monitors, and SLOs
export DD_API_KEY=your-api-key
export DD_APP_KEY=your-app-key

# Using Python script
python scripts/datadog/export-assets.py

# Or using wrapper scripts
./scripts/datadog/export-assets.sh  # Bash
./scripts/datadog/export-assets.ps1  # PowerShell
```

**Expected output:**
- `/datadog/dashboards/policyinsight-ops.json`
- `/datadog/monitors/api-latency.json`
- `/datadog/monitors/queue-backlog.json`
- `/datadog/monitors/llm-cost-anomaly.json`
- `/datadog/slos/api-availability-slo.json`
- `/datadog/slos/api-latency-slo.json`

### Step 7: Test Monitors with Traffic Generator

```bash
# Generate latency spike
python scripts/datadog/traffic-generator.py \
  --scenario latency \
  --duration 60 \
  --base-url http://localhost:8080

# Generate backlog spike
python scripts/datadog/traffic-generator.py \
  --scenario backlog \
  --duration 120 \
  --base-url http://localhost:8080

# Generate LLM cost spike
python scripts/datadog/traffic-generator.py \
  --scenario llm-cost \
  --duration 90 \
  --base-url http://localhost:8080
```

**Check monitors in Datadog:**
- https://app.datadoghq.com/monitors/manage
- Verify monitors trigger when thresholds are exceeded

## Docker Validation: Web vs Worker Mode

This section provides exact docker run commands to validate that web instances do NOT poll jobs, while worker instances do.

### Prerequisites

1. Build the Docker image:
```bash
docker build -t policyinsight:local .
```

2. Ensure PostgreSQL and Datadog Agent are running (or use docker network):
```bash
# Create a network for containers to communicate
docker network create policyinsight-network

# Start PostgreSQL (if not already running)
docker run -d --name postgres --network policyinsight-network \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=policyinsight \
  -p 5432:5432 \
  postgres:15

# Start Datadog Agent (if not already running)
docker run -d --name datadog-agent --network policyinsight-network \
  -e DD_API_KEY=${DD_API_KEY} \
  -e DD_SITE=datadoghq.com \
  -p 8126:8126 \
  -p 8125:8125/udp \
  gcr.io/datadoghq/agent:7
```

### Web Mode (No Polling)

Run the web container with `POLICYINSIGHT_WORKER_ENABLED=false` and verify NO polling queries appear:

```bash
docker run --rm --network policyinsight-network \
  -e SPRING_PROFILES_ACTIVE=cloudrun \
  -e POLICYINSIGHT_WORKER_ENABLED=false \
  -e DB_HOST=postgres \
  -e DB_PORT=5432 \
  -e DB_NAME=policyinsight \
  -e DB_USER=postgres \
  -e DB_PASSWORD=postgres \
  -e DD_AGENT_HOST=datadog-agent \
  -e DATADOG_ENABLED=true \
  -e DD_SERVICE=policyinsight-web \
  -e DD_ENV=local \
  -p 8080:8080 \
  policyinsight:local
```

**Verification:**
1. Watch the logs for SQL queries
2. You should **NOT** see repeating queries like: `SELECT * FROM policy_jobs WHERE status='PENDING' ORDER BY created_at ASC LIMIT ? FOR UPDATE SKIP LOCKED`
3. The application should start and serve HTTP requests normally
4. Health endpoint should work: `curl http://localhost:8080/actuator/health`

**Expected Log Output:**
- Application starts successfully
- No scheduled polling messages
- No `LocalDocumentProcessingWorker` bean creation messages
- HTTP endpoints respond normally

### Worker Mode (Polling Enabled)

Run the worker container with `POLICYINSIGHT_WORKER_ENABLED=true` and verify polling queries appear:

```bash
docker run --rm --network policyinsight-network \
  -e SPRING_PROFILES_ACTIVE=cloudrun \
  -e POLICYINSIGHT_WORKER_ENABLED=true \
  -e DB_HOST=postgres \
  -e DB_PORT=5432 \
  -e DB_NAME=policyinsight \
  -e DB_USER=postgres \
  -e DB_PASSWORD=postgres \
  -e DD_AGENT_HOST=datadog-agent \
  -e DATADOG_ENABLED=true \
  -e DD_SERVICE=policyinsight-worker \
  -e DD_ENV=local \
  -p 8081:8080 \
  policyinsight:local
```

**Verification:**
1. Watch the logs for SQL queries
2. You **SHOULD** see repeating queries like: `SELECT * FROM policy_jobs WHERE status='PENDING' ORDER BY created_at ASC LIMIT ? FOR UPDATE SKIP LOCKED`
3. Polling should occur every 2 seconds (configurable via `APP_LOCAL_WORKER_POLL_MS`)
4. The `LocalDocumentProcessingWorker` bean should be created

**Expected Log Output:**
- Application starts successfully
- `LocalDocumentProcessingWorker` bean created
- Periodic polling messages: `Found X pending job(s) to process` (or no jobs if queue is empty)
- SQL queries for `policy_jobs` table with `FOR UPDATE SKIP LOCKED`

### Quick Validation Script

**PowerShell:**
```powershell
# Build image
docker build -t policyinsight:local .

# Test web mode (should NOT poll)
Write-Host "Testing web mode (no polling)..." -ForegroundColor Cyan
docker run --rm --network policyinsight-network `
  -e SPRING_PROFILES_ACTIVE=cloudrun `
  -e POLICYINSIGHT_WORKER_ENABLED=false `
  -e DB_HOST=postgres `
  -e DB_PORT=5432 `
  -e DB_NAME=policyinsight `
  -e DB_USER=postgres `
  -e DB_PASSWORD=postgres `
  -e DD_AGENT_HOST=datadog-agent `
  -e DATADOG_ENABLED=true `
  -e DD_SERVICE=policyinsight-web `
  -e DD_ENV=local `
  -p 8080:8080 `
  policyinsight:local 2>&1 | Select-String -Pattern "FOR UPDATE SKIP LOCKED|LocalDocumentProcessingWorker|pollAndProcessJobs"

# Should show NO matches (or only startup messages, not polling)
```

**Bash:**
```bash
# Build image
docker build -t policyinsight:local .

# Test web mode (should NOT poll)
echo "Testing web mode (no polling)..."
docker run --rm --network policyinsight-network \
  -e SPRING_PROFILES_ACTIVE=cloudrun \
  -e POLICYINSIGHT_WORKER_ENABLED=false \
  -e DB_HOST=postgres \
  -e DB_PORT=5432 \
  -e DB_NAME=policyinsight \
  -e DB_USER=postgres \
  -e DB_PASSWORD=postgres \
  -e DD_AGENT_HOST=datadog-agent \
  -e DATADOG_ENABLED=true \
  -e DD_SERVICE=policyinsight-web \
  -e DD_ENV=local \
  -p 8080:8080 \
  policyinsight:local 2>&1 | grep -E "FOR UPDATE SKIP LOCKED|LocalDocumentProcessingWorker|pollAndProcessJobs"

# Should show NO matches (or only startup messages, not polling)
```

## Environment Variables

See [.env.example](./.env.example) for all required Datadog environment variables.

## Additional Resources

- **Observability Guide**: [docs/OBSERVABILITY.md](./docs/OBSERVABILITY.md)
- **Datadog Agent Setup**: https://docs.datadoghq.com/agent/
- **Java APM**: https://docs.datadoghq.com/tracing/setup_overview/setup/java/


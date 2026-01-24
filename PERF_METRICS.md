# Performance and Extraction Health Metrics

This document describes how to run the performance and extraction health metrics harness for PolicyInsight.

## Prerequisites

1. **PostgreSQL Database**
   - Start PostgreSQL using Docker Compose:
     ```bash
     docker-compose up -d postgres
     ```
   - Or ensure PostgreSQL is running on `localhost:5432` with database `policyinsight`

2. **Spring Boot Application**
   - Start the application:
     ```bash
     # Windows
     .\mvnw.cmd spring-boot:run

     # Linux/Mac
     ./mvnw spring-boot:run
     ```
   - Or run the packaged JAR:
     ```bash
     java -jar target/policy-insight-1.0.0-SNAPSHOT.jar
     ```
   - Default port: `8080` (configurable via `SERVER_PORT` env var)

3. **k6** (for load testing)
   - Install k6: https://k6.io/docs/getting-started/installation/
   - Verify installation: `k6 version`

4. **Python 3** (for summarization and extraction health)
   - Python 3.6+ required (uses standard library only)

## Environment Variables

- `BASE_URL`: API base URL (default: `http://localhost:8080`)
- `POLL_INTERVAL_MS`: Status polling interval in milliseconds (default: `2000`)
- `POLL_TIMEOUT_MS`: Status polling timeout in milliseconds (default: `180000`)

## Milestone 1: k6 Load Tests

Run load tests to measure API performance and end-to-end timing.

### Running the Tests

```bash
# Windows (Git Bash)
cd perf
bash run_k6.sh

# Linux/Mac
cd perf
chmod +x run_k6.sh
./run_k6.sh
```

### Configuration

The script runs k6 for multiple VU (Virtual Users) levels: 1, 5, 20, 50

- Default duration: `60s` (configurable via `DURATION` env var)
- Custom VUS list: set `VUS` env var (e.g., `VUS="1 5" ./run_k6.sh`)

### Outputs

- `perf/out/summary_vus<N>.json` - k6 summary JSON for each VU level
- `perf/out/runlog_vus<N>.txt` - k6 stdout log for each VU level

### Metrics Collected

- **Latency (p50/p95/p99):**
  - `upload_latency_ms` - Upload endpoint latency
  - `status_poll_latency_ms` - Status polling latency
  - `qa_latency_ms` - Q&A endpoint latency
  - `end_to_end_ms` - Upload accepted to SUCCESS

- **Success Rates:**
  - `upload_success` - Upload success rate
  - `status_success` - Status polling success rate
  - `qa_success` - Q&A success rate
  - `job_success` - Job completion success rate

## Milestone 2: Summarize k6 Results

Generate CSV and Markdown summaries from k6 JSON outputs.

### Running the Summarizer

```bash
# Windows (Git Bash)
python3 perf/summarize_k6.py

# Linux/Mac
python3 perf/summarize_k6.py
```

### Outputs

- `perf/out/summary.csv` - CSV table with all metrics
- `perf/out/summary.md` - Markdown report with interpretation

The summary includes:
- RPS (requests per second)
- Error rates
- Latency percentiles (p50/p95/p99) for each endpoint
- Job success rates
- Bottleneck analysis at p95
- Error rate trend analysis

## Milestone 3: Extraction Health Metrics

Measure schema pass rate, null rates, and self-consistency for extraction output.

### Running the Health Metrics

```bash
# Windows (Git Bash)
cd eval
bash run_extraction_health.sh

# Linux/Mac
cd eval
chmod +x run_extraction_health.sh
./run_extraction_health.sh
```

### Process

For each PDF fixture in `eval/fixtures`:
1. Upload PDF and wait for SUCCESS
2. Fetch report JSON via `GET /api/documents/{id}/report-json`
3. Save as `eval/out/<name>_run1.json`
4. Repeat upload for same PDF
5. Save as `eval/out/<name>_run2.json`

### Metrics Computed

1. **Schema Pass Rate**: Percentage of report-json responses that pass minimal schema validation
   - Validates: jobId (UUID), report object, required fields exist, chunksMeta.chunkCount >= 0

2. **Null Rate per Field**: Percentage of reports where each field is null/empty
   - Fields: documentOverview, summaryBullets, obligations, restrictions, terminationTriggers, riskTaxonomy

3. **Self-Consistency Rate**: Percentage of field comparisons that match between run1 and run2 for the same PDF
   - Compares normalized JSON values for each report field

### Outputs

- `eval/out/<pdf_name>_run1.json` - Report JSON for first run of each PDF
- `eval/out/<pdf_name>_run2.json` - Report JSON for second run of each PDF
- `eval/out/health_metrics.json` - Aggregated metrics in JSON format
- `eval/out/health_metrics.md` - Human-readable summary report

## API Endpoint: GET /api/documents/{id}/report-json

A new JSON endpoint for retrieving report data programmatically.

### Authentication

- Requires `X-Job-Token` header (same capability token model as status and Q&A endpoints)

### Response Format

When status is SUCCESS:
```json
{
  "jobId": "uuid-string",
  "generatedAt": "2024-01-01T00:00:00Z",
  "report": {
    "documentOverview": {...},
    "summaryBullets": {...},
    "obligations": {...},
    "restrictions": {...},
    "terminationTriggers": {...},
    "riskTaxonomy": {...}
  },
  "chunksMeta": {
    "chunkCount": 42,
    "avgSpanConfidence": 0.85
  }
}
```

When status is not SUCCESS:
- Returns `409 Conflict` with JSON:
  ```json
  {
    "jobId": "uuid-string",
    "status": "PROCESSING",
    "message": "Report not available. Job status is PROCESSING"
  }
  ```

When token is invalid:
- Returns `403 Forbidden`

### Schema

See `eval/schema_report_min.json` for the minimal JSON schema expected for report-json responses.

## Output Locations

All outputs are stored in:

- `perf/out/` - k6 load test results and summaries
- `eval/out/` - Extraction health metrics and report JSON files

## Troubleshooting

### k6 Script Errors

- Ensure `perf/fixtures/sample.pdf` exists
- Check BASE_URL is correct
- Verify application is running and accessible

### Extraction Health Errors

- Ensure at least one PDF exists in `eval/fixtures/`
- Defaults to copying from `perf/fixtures/sample.pdf` if eval/fixtures is empty
- Check BASE_URL matches running application
- Verify application can process PDFs successfully

### Token Authentication Errors

- Ensure `X-Job-Token` header matches the token returned from upload endpoint
- Verify token has not expired (tokens are valid for job lifetime)
- Check JobTokenInterceptor patterns include new endpoint paths

## Notes

- The harness uses the exact API contract routes:
  - `POST /api/documents/upload`
  - `GET /api/documents/{id}/status`
  - `POST /api/questions`
  - `GET /api/documents/{id}/report-json` (new)

- All scripts are designed to work on Windows Git Bash and macOS/Linux

- Python scripts use only standard library (no external dependencies)

- PDF fixtures should be valid PDF files (sample.pdf is provided)


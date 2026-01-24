# PolicyInsight Performance Metrics

This document contains measured performance metrics from load testing the PolicyInsight API.

> **Note**: Metrics shown here are from controlled load tests, not production traffic. Actual production performance may vary based on workload, infrastructure, and external service dependencies.

---

## How to Reproduce

### Prerequisites

1. **Start the service** (choose one):
   ```bash
   # Option 1: Docker Compose (recommended)
   docker compose up --build

   # Option 2: Local development
   docker compose up -d postgres
   ./mvnw spring-boot:run
   ```

2. **Install Python dependencies**:
   ```bash
   cd tools/metrics
   pip install -r requirements.txt
   ```

3. **Generate sample PDF** (if needed):
   ```bash
   python generate_sample_pdf.py --output sample.pdf
   ```

### Run Load Tests

#### Baseline Test (Sequential)
```bash
cd tools/metrics
python runner.py --n 10 --concurrency 1 --base_url http://localhost:8080 --pdf_file sample.pdf --timeout_s 600
```

#### Stress Test (Concurrent)
```bash
python runner.py --n 25 --concurrency 5 --base_url http://localhost:8080 --pdf_file sample.pdf --timeout_s 600
```

### Aggregate Results

```bash
python aggregator.py --input out/run_*.json --output out/summary.md
```

---

## Test Results

### Status

**✅ Metrics Collected**

Results from load test runs on 2026-01-01T03:38:50Z (UTC).

---

## Expected Metrics

Once runs are completed, this section will include:

### Test Conditions

- **Base URL**: `http://localhost:8080`
- **PDF File Size**: 1473 bytes
- **Test Date/Time**: 2026-01-01T03:38:50Z (UTC)
- **Machine Notes**: Docker Compose on Windows (local test environment)

### Baseline Results (n=10, concurrency=1)

- **Success Rate**: 100%
- **Upload Latency**:
  - p50: 50.44 ms
  - p95: 78.73 ms
  - p99: 83.65 ms
- **End-to-End Time**:
  - p50: 2137.41 ms
  - p95: 2183.78 ms
  - p99: 2186.17 ms
- **Throughput**: 29.38 docs/min

### Stress Test Results (n=25, concurrency=5)

- **Success Rate**: 100%
- **Upload Latency**:
  - p50: 84.06 ms
  - p95: 125.53 ms
  - p99: 131.93 ms
- **End-to-End Time**:
  - p50: 2204.19 ms
  - p95: 4250.90 ms
  - p99: 4257.87 ms
- **Throughput**: 24.42 docs/min

### Combined Results (n=35, baseline + stress)

- **Total Documents**: 35
- **Successful**: 35
- **Failed**: 0
- **Success Rate**: 100.0%
- **Upload Latency**:
  - p50: 76.34 ms
  - p95: 119.17 ms
  - p99: 131.42 ms
  - Mean: 76.9 ms
  - Min: 39.31 ms
  - Max: 133.14 ms
- **End-to-End Time**:
  - p50: 2182.16 ms (2.18 seconds)
  - p95: 4233.94 ms (4.23 seconds)
  - p99: 4257.84 ms (4.26 seconds)
  - Mean: 2472.27 ms (2.47 seconds)
  - Min: 2106.36 ms (2.11 seconds)
  - Max: 4257.93 ms (4.26 seconds)
- **Processing Time (successful jobs only)**:
  - p50: 2047.04 ms (2.05 seconds)
  - p95: 4097.14 ms (4.10 seconds)
  - p99: 4102.62 ms (4.10 seconds)
  - Mean: 2338.31 ms (2.34 seconds)
  - Min: 2025.01 ms (2.03 seconds)
  - Max: 4104.32 ms (4.10 seconds)
- **Throughput**: 25.66 docs/min

---

## Disclaimer

These metrics are measured in controlled load tests with sample data. They do not represent production traffic patterns or real-world performance under variable load conditions. External service dependencies (GCP Document AI, Vertex AI, Pub/Sub) may introduce additional latency and variability.

---

## Metric Definitions

- **Upload Latency**: Time from HTTP request start to receipt of job ID (ms)
- **Time to Complete**: Time from upload completion to final status (SUCCESS/FAILED) (ms)
- **End-to-End Time**: Total time from upload start to completion (ms)
- **Success Rate**: Percentage of documents that completed with SUCCESS status
- **Throughput**: Documents processed per minute (based on average completion time)

---

## Raw Data

Raw test results (JSON and CSV) are stored in `tools/metrics/out/` after each run.


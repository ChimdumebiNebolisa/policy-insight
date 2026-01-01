# PolicyInsight - Submission Guide

This document provides quick pointers for judges evaluating the PolicyInsight submission, particularly for the Datadog Challenge track.

## Quick Links

- **Main README**: [README.md](./README.md)
- **Observability Guide**: [docs/OBSERVABILITY.md](./docs/OBSERVABILITY.md)
- **Deployment Guide**: [DEPLOYMENT.md](./DEPLOYMENT.md)
- **Demo Guide**: [DEMO.md](./DEMO.md)

## Datadog Observability Evidence

### Evidence Locations

**Code:**
- Custom spans: `src/main/java/com/policyinsight/observability/TracingService.java`
- Pipeline instrumentation: `src/main/java/com/policyinsight/processing/LocalDocumentProcessingWorker.java`
- Metrics: `src/main/java/com/policyinsight/observability/DatadogMetricsService.java`
- Correlation IDs: `src/main/java/com/policyinsight/util/CorrelationIdFilter.java`

**Exported Configs:**
- Dashboards: `datadog/dashboards/policyinsight-ops.json`
- Monitors: `datadog/monitors/` (api-latency.json, queue-backlog.json, llm-cost-anomaly.json)
- SLOs: `datadog/slos/` (api-availability-slo.json, api-latency-slo.json)

**Datadog UI:**
- APM Traces: https://app.datadoghq.com/apm/traces
- Metrics Explorer: https://app.datadoghq.com/metric/explorer
- Dashboards: https://app.datadoghq.com/dashboard/lists
- Monitors: https://app.datadoghq.com/monitors/manage
- SLOs: https://app.datadoghq.com/slo

### Key Features

**APM Tracing:**
- Custom spans for each pipeline stage: `upload`, `extraction`, `classification`, `risk_scan`, `llm`, `export`
- Span tags: `job_id`, `document_id`, `stage`, `provider`, `tokens`, `duration_ms`

**Structured Logging:**
- JSON format with correlation IDs (`request_id`, `job_id`)
- Trace/span IDs injected via `dd.trace_id`, `dd.span_id`

**Custom Metrics:**
- `policyinsight.job.duration` - Job processing duration
- `policyinsight.job.success` / `policyinsight.job.failure` - Success/failure counts
- `policyinsight.job.backlog` - Pending jobs in queue
- `policyinsight.llm.tokens` - LLM token usage
- `policyinsight.llm.cost_estimate_usd` - Estimated LLM cost
- `policyinsight.llm.latency_ms` - LLM API latency

**Monitors:**
- API Latency: Alert when p95 latency > 2 seconds
- Queue Backlog: Alert when pending jobs > 50
- LLM Cost Anomaly: Alert when LLM cost exceeds baseline by 2x

**SLOs:**
- API Availability: 99.9% uptime (7-day rolling window)
- API Latency: 95% of requests < 2 seconds (7-day rolling window)

## Verification

For detailed verification procedures, see [docs/OBSERVABILITY.md](./docs/OBSERVABILITY.md).

Quick verification:
1. Upload a document: `curl -X POST http://localhost:8080/api/documents/upload -F "file=@test.pdf"`
2. Check Datadog APM for traces with custom spans
3. Check Datadog Metrics Explorer for `policyinsight.*` metrics
4. Review exported dashboard/monitor/SLO configs in `datadog/` directory

## Code Structure

```
src/main/java/com/policyinsight/
├── observability/
│   ├── TracingService.java
│   ├── DatadogMetricsService.java
│   └── ...
├── config/
│   └── DatadogMetricsConfig.java
└── util/
    └── CorrelationIdFilter.java
```

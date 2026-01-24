# Datadog Observability Configuration

This directory contains **real exports** from Datadog (with IDs) and **templates** for creating/updating assets.

## Directory Structure

```
datadog/
├── dashboards/          # Real exports from Datadog (contain IDs)
│   └── policyinsight-ops.json
├── monitors/            # Real exports from Datadog (contain IDs)
│   ├── api-latency.json
│   ├── queue-backlog.json
│   └── llm-cost-anomaly.json
├── slos/               # Real exports from Datadog (contain IDs)
│   ├── api-latency-slo.json
│   └── api-availability-slo.json
├── templates/          # Templates (no IDs) for creating/updating assets
│   ├── dashboards/
│   ├── monitors/
│   └── slos/
└── README.md           # This file
```

**Important**:
- Files in `dashboards/`, `monitors/`, and `slos/` are **real exports** pulled from Datadog via API and contain real IDs
- Files in `templates/` are **configuration templates** without IDs, used to create/update assets in Datadog

## Exporting Real Assets from Datadog

To export current Datadog configurations from your Datadog account (writes to `dashboards/`, `monitors/`, `slos/`):

### PowerShell (Windows)
```powershell
$env:DD_API_KEY = "your-api-key"
$env:DD_APP_KEY = "your-app-key"
# Optional: for EU or US3 sites
$env:DD_SITE = "datadoghq.eu"  # or "us3.datadoghq.com"
.\scripts\datadog\export-assets.ps1
```

### Bash (Linux/Mac)
```bash
export DD_API_KEY=your-api-key
export DD_APP_KEY=your-app-key
# Optional: for EU or US3 sites
export DD_SITE=datadoghq.eu  # or us3.datadoghq.com
./scripts/datadog/export-assets.sh
```

**Note**: You need both API key and Application key from Datadog:
- API Key: https://app.datadoghq.com/organization-settings/api-keys
- Application Key: https://app.datadoghq.com/organization-settings/application-keys

**Verifying Exports**: Exported files must contain real IDs. Check with:
- PowerShell: `Get-Content datadog\dashboards\*.json | Select-String -Pattern '"id"\s*:\s*\d+'`
- Bash: `grep -r '"id"' datadog/dashboards/*.json`

## Applying Templates to Datadog

To create or update dashboards, monitors, and SLOs from templates:

### PowerShell (Windows)
```powershell
$env:DD_API_KEY = "your-api-key"
$env:DD_APP_KEY = "your-app-key"
.\scripts\datadog\apply-assets.ps1
```

### Bash (Linux/Mac)
```bash
export DD_API_KEY=your-api-key
export DD_APP_KEY=your-app-key
./scripts/datadog/apply-assets.sh
```

After applying templates, run `export-assets.py` to export the created assets with real IDs.

## Configuration Details

### Dashboard: PolicyInsight-Ops

Shows:
- **API Latency**: P50, P95, P99 latency metrics
- **Error Rate**: Count of API errors
- **Throughput**: Requests per second
- **Queue Backlog**: Number of pending jobs
- **LLM Cost**: Cumulative LLM API costs in USD
- **LLM Latency**: Average and P95 latency for LLM calls

### Monitors

1. **API Latency Monitor**
   - Triggers when P95 latency > 2000ms for 5 minutes
   - Creates high-severity incident
   - Tags: `service:policy-insight`, `alert_type:latency`

2. **Queue Backlog Monitor**
   - Triggers when queue backlog > 50 jobs for 5 minutes
   - Creates medium-severity incident
   - Tags: `service:policy-insight`, `alert_type:backlog`

3. **LLM Cost Anomaly Monitor**
   - Triggers when LLM cost exceeds baseline by 2 standard deviations
   - Creates medium-severity incident
   - Tags: `service:policy-insight`, `alert_type:cost`

### SLOs

1. **API Latency SLO**
   - Target: 99% of requests complete within 2000ms (P95)
   - Timeframes: 7 days, 30 days

2. **API Availability SLO**
   - Target: 99.9% of requests return successful responses (2xx, 3xx)
   - Timeframes: 7 days, 30 days

## Incident Automation

All monitors are configured to automatically create Datadog incidents when triggered:
- Incident title includes monitor name
- Severity matches monitor severity
- Notification handles: `@on-call-engineering`
- Escalation messages after 1 hour

## Local Development

For local development without Datadog credentials:
- Set `DATADOG_ENABLED=false` (default)
- Application runs normally without Datadog agent
- Tests pass without Datadog dependencies

To enable Datadog locally:
1. Start Datadog agent: `docker-compose -f docker-compose.datadog.yml up -d datadog-agent`
2. Run app with Datadog: `.\scripts\datadog\run-with-datadog.ps1` (Windows) or `./scripts/datadog/run-with-datadog.sh` (Linux/Mac)

## References

- [Datadog APM Java Documentation](https://docs.datadoghq.com/tracing/setup_overview/setup/java/)
- [Datadog DogStatsD Documentation](https://docs.datadoghq.com/developers/dogstatsd/?tab=java)
- [Datadog Logs Injection](https://docs.datadoghq.com/tracing/other_telemetry/connect_logs_and_traces/java/)
- [Datadog API Documentation](https://docs.datadoghq.com/api/latest/)


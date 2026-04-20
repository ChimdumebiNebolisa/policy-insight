# Oracle Web Deployment Runbook

This runbook defines the Oracle web deployment path for PolicyInsight and the
validation matrix required to declare the Oracle path stable (Chunk 4).

Scope rules for this runbook:

- Reuse existing OCI auth and existing network resources.
- Do not create or recreate VCN/subnet/NSG resources here.
- Keep Postgres as the active datasource.
- Keep Cloud Run path available as fallback.

Default runtime mode for this Oracle path:

- Single-node web+worker process on one host (`POLICYINSIGHT_WORKER_ENABLED=true`).
- Pub/Sub is not required by default (`APP_MESSAGING_MODE=local`, `PUBSUB_ENABLED=false`).
- LLM (Vertex AI) is disabled by default (`VERTEX_AI_ENABLED=false`).
- Datadog is disabled by default (`DATADOG_ENABLED=false`).

## Prerequisites

- Existing Oracle VM host reachable by SSH.
- Docker installed on the Oracle VM.
- Local clone of this repository.
- Runtime env file on Oracle host at `/opt/policyinsight/web.env`.
- Env file seeded from `infra/oracle/web.env.example` with real secrets.

## Deploy (Windows)

```powershell
.\scripts\deploy_oracle_web.ps1 `
  -Host <oracle-vm-ip-or-dns> `
  -SshKeyPath <path-to-private-key> `
  -User opc `
  -ImageRef ghcr.io/chimdumebinebolisa/policy-insight:latest `
  -ContainerName policyinsight-web `
  -EnvFilePath /opt/policyinsight/web.env `
  -AppPort 8080 `
  -VerifyRoutes
```

Dry-run mode:

```powershell
.\scripts\deploy_oracle_web.ps1 -Host <oracle-vm-ip-or-dns> -SshKeyPath <path-to-private-key> -DryRun
```

## Deploy (Linux/macOS)

```bash
./scripts/deploy_oracle_web.sh \
  --host <oracle-vm-ip-or-dns> \
  --ssh-key <path-to-private-key> \
  --user opc \
  --image-ref ghcr.io/chimdumebinebolisa/policy-insight:latest \
  --container-name policyinsight-web \
  --env-file /opt/policyinsight/web.env \
  --app-port 8080 \
  --verify-routes
```

Dry-run mode:

```bash
./scripts/deploy_oracle_web.sh --host <oracle-vm-ip-or-dns> --ssh-key <path-to-private-key> --dry-run
```

## Verify (Oracle web)

From any machine that can reach the service:

```powershell
Invoke-WebRequest http://<oracle-vm-ip-or-dns>:8080/health -UseBasicParsing
Invoke-WebRequest http://<oracle-vm-ip-or-dns>:8080/readiness -UseBasicParsing
Invoke-WebRequest http://<oracle-vm-ip-or-dns>:8080/sample-report -UseBasicParsing
Invoke-WebRequest http://<oracle-vm-ip-or-dns>:8080/sample-pdf -UseBasicParsing
```

Expected: all endpoints return HTTP 200.

Or use the route smoke script:

```powershell
pwsh scripts\oracle_route_smoke.ps1 -BaseUrl http://<oracle-vm-ip-or-dns>:8080
```

## Rollback

If Oracle web verification fails, rollback immediately to Cloud Run for active usage and stop Oracle web container.

```bash
ssh -i <path-to-private-key> opc@<oracle-vm-ip-or-dns> "sudo docker rm -f policyinsight-web"
```

Then continue serving traffic via existing Cloud Run path until issues are fixed.

## Validation Matrix (Chunk 4 — Cutover Readiness)

Run the full matrix twice consecutively before declaring the Oracle path stable.

### 1. Branch and Baseline Safety

```powershell
git branch --show-current   # must be policyinsight-revamp or copilot/policyinsight-revamp-continuation
git status -sb
```

Pass criteria:

- Branch is correct.
- No unexpected uncommitted changes.

### 2. Compile and Test Gate

```bash
# Linux/macOS
JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64 mvn -q -DskipTests compile
JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64 mvn test

# Windows
mvn -q -DskipTests compile
mvn test
```

Pass criteria:

- Compile succeeds.
- All tests pass, including `OracleProfileSmokeTest` (5 tests).

### 3. Oracle Deployment Boots

After deploy, run the route smoke:

```powershell
pwsh scripts\oracle_route_smoke.ps1 -BaseUrl http://<oracle-vm-ip-or-dns>:8080
```

Pass criteria:

- `/health`, `/readiness`, `/sample-report`, `/sample-pdf` all return HTTP 200.
- Flyway completed migration at startup without error (check docker logs).
- Active datasource is Postgres (`jdbc:postgresql://` in startup logs).

### 4. Oracle Path Operable (Single PDF Smoke)

```powershell
pwsh scripts\smoke_test.ps1 <path-to-pdf>
```

Pass criteria:

- Upload succeeds (HTTP 202).
- Status transitions to SUCCESS.
- Report route is renderable with valid token.
- No Pub/Sub dependency required (`APP_MESSAGING_MODE=local`).

### 5. Cost Rule Check

Pass criteria:

- Default Oracle deployment path has no mandatory dependency on Cloud SQL, Pub/Sub, Datadog, or Vertex AI.
- Service boot and minimum route health (/health, /readiness, /sample-report, /sample-pdf) pass without live LLM (`VERTEX_AI_ENABLED=false`).

### 6. Cloud Run Fallback Check

```powershell
pwsh scripts\cloudrun_fallback_smoke.ps1 -BaseUrl https://<cloudrun-service-url>
```

Pass criteria:

- Cloud Run service is still reachable and minimum routes pass HTTP 200.

### 7. Baseline Latency Comparison

Capture Oracle smoke latency:

```powershell
$PdfPath = "<path-to-pdf>"
$OracleDurations = 1..3 | ForEach-Object { (Measure-Command { pwsh scripts\smoke_test.ps1 $PdfPath }).TotalSeconds }
$OracleDurations
```

Pass criteria:

- Oracle median smoke duration is less than or equal to 1.30 times baseline median.
- Rollback trigger: Oracle median exceeds 1.30× baseline in two consecutive rounds.

## Rollback Triggers

See `MUST_READ_FIRST/01_POLICYINSIGHT_REVAMP_BRANCH_PLAN.md` for the full rollback trigger list.
Quick reference:

- Any required route regression (expected 200 becomes non-200).
- Oracle deployment boots check fails.
- Upload→status→report smoke flow fails once in validation.
- Oracle median smoke latency exceeds 1.30× baseline in two consecutive validation rounds.
- Any newly required paid service appears in default Oracle path.

## External Blocker Handling

If Oracle VM creation is blocked by OCI A1 capacity, treat this as an external platform blocker.
Continue progressing local/config/docs substeps and defer only the live Oracle-host verification until capacity is available.

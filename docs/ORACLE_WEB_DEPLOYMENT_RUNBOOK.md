# Oracle Web Deployment Runbook (Chunk 2)

This runbook defines the first working Oracle web deployment path for PolicyInsight.

Scope rules for this runbook:

- Reuse existing OCI auth and existing network resources.
- Do not create or recreate VCN/subnet/NSG resources here.
- Keep Postgres as the active datasource.
- Keep Cloud Run path available as fallback.

Default runtime mode for this first Oracle path:

- Single-node web+worker process on one host (`POLICYINSIGHT_WORKER_ENABLED=true`).
- Pub/Sub is not required by default for this path.

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

## Rollback

If Oracle web verification fails, rollback immediately to Cloud Run for active usage and stop Oracle web container.

```bash
ssh -i <path-to-private-key> opc@<oracle-vm-ip-or-dns> "sudo docker rm -f policyinsight-web"
```

Then continue serving traffic via existing Cloud Run path until issues are fixed.

## External Blocker Handling

If Oracle VM creation is blocked by OCI A1 capacity, treat this as an external platform blocker.
Continue progressing local/config/docs substeps and defer only the live Oracle-host verification until capacity is available.

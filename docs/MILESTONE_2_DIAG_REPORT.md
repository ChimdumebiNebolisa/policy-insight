# Milestone 2 Diagnostic Report

## 1) Cmd window popups — root cause (exact file+line)
Root cause was `scripts/verify-local.ps1:89-93`, which used `Start-Process` to run `.\mvnw.cmd` (PowerShell defaults to launching a new window for `Start-Process`).

Quoted lines that launched a new window (from `scripts/verify-local.ps1` before the fix):
```
$appProcess = Start-Process -FilePath ".\mvnw.cmd" `
    -ArgumentList "spring-boot:run", "-Dspring-boot.run.profiles=local" `
    -RedirectStandardOutput $logOutPath `
    -RedirectStandardError $logErrPath `
    -PassThru
```

## 2) Search results (paths + line numbers)
**Start-Process**
- `scripts/verify-local.ps1:89` (removed in the fix)
- `scripts/start-and-generate-openapi.ps1:536`

**cmd.exe**
- No matches in `*.ps1`, `*.cmd`, `*.bat`

**/c**
- No matches in `*.ps1`, `*.cmd`, `*.bat`

**start**
- `scripts/start-and-generate-openapi.ps1:2,22,32,50,77,118,517,569`
- `scripts/quick-start-app.ps1:1-2`
- `scripts/generate-openapi.ps1:27`
- `mvnw.cmd:44`

**powershell.exe**
- No matches in `*.ps1`, `*.cmd`, `*.bat`

**Invoke-Expression**
- `scripts/db-doctor.ps1:21-22`

## 3) Postgres mismatch — root cause + evidence
The official Postgres image only reads `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_DB` during the **first** initialization of an empty data directory; after that it reuses the existing data files and ignores new env var values. Wiping the volume clears the data directory, so the entrypoint re-initializes the DB and applies the updated credentials.

Evidence: `docker compose config`
```
name: policy-insight
services:
  postgres:
    container_name: policyinsight-postgres
    environment:
      POSTGRES_DB: policyinsight
      POSTGRES_PASSWORD: postgres
      POSTGRES_USER: postgres
    healthcheck:
      test:
        - CMD-SHELL
        - pg_isready -U postgres -d policyinsight
      timeout: 5s
      interval: 10s
      retries: 5
    image: postgres:15-alpine
    networks:
      default: null
    ports:
      - mode: ingress
        target: 5432
        published: "5432"
        protocol: tcp
    volumes:
      - type: volume
        source: postgres_data
        target: /var/lib/postgresql/data
        volume: {}
networks:
  default:
    name: policy-insight_default
volumes:
  postgres_data:
    name: policy-insight_postgres_data
```

Volume reset note:
The Postgres image only reads `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` on the first startup
with an empty data directory. If credentials change later, the volume must be reset so init runs again:
```
docker compose down -v
docker compose up -d
```

Evidence: `docker compose ps`
```
NAME                     IMAGE                COMMAND                  SERVICE    CREATED         STATUS                   PORTS
policyinsight-postgres   postgres:15-alpine   "docker-entrypoint.s…"   postgres   2 minutes ago   Up 2 minutes (healthy)   0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
```

Evidence: `docker compose logs postgres --tail 200`
```
policyinsight-postgres  | The files belonging to this database system will be owned by user "postgres".
policyinsight-postgres  | This user must also own the server process.
policyinsight-postgres  |
policyinsight-postgres  | The database cluster will be initialized with locale "en_US.utf8".
policyinsight-postgres  | The default database encoding has accordingly been set to "UTF8".
policyinsight-postgres  | The default text search configuration will be set to "english".
policyinsight-postgres  |
policyinsight-postgres  | Data page checksums are disabled.
policyinsight-postgres  |
policyinsight-postgres  | fixing permissions on existing directory /var/lib/postgresql/data ... ok
policyinsight-postgres  | creating subdirectories ... ok
policyinsight-postgres  | selecting dynamic shared memory implementation ... posix
policyinsight-postgres  | selecting default max_connections ... 100
policyinsight-postgres  | selecting default shared_buffers ... 128MB
policyinsight-postgres  | selecting default time zone ... UTC
policyinsight-postgres  | creating configuration files ... ok
policyinsight-postgres  | running bootstrap script ... ok
policyinsight-postgres  | sh: locale: not found
policyinsight-postgres  | 2026-01-25 04:31:36.849 UTC [36] WARNING:  no usable system locales were found
policyinsight-postgres  | performing post-bootstrap initialization ... ok
policyinsight-postgres  | syncing data to disk ... ok
policyinsight-postgres  |
policyinsight-postgres  |
policyinsight-postgres  | Success. You can now start the database server using:
policyinsight-postgres  |
policyinsight-postgres  |     pg_ctl -D /var/lib/postgresql/data -l logfile start
policyinsight-postgres  |
policyinsight-postgres  | initdb: warning: enabling "trust" authentication for local connections
policyinsight-postgres  | initdb: hint: You can change this by editing pg_hba.conf or using the option -A, or --auth-local and --auth-host, the next time you run initdb.
policyinsight-postgres  | waiting for server to start....2026-01-25 04:31:38.529 UTC [42] LOG:  starting PostgreSQL 15.15 on x86_64-pc-linux-musl, compiled by gcc (Alpine 15.2.0) 15.2.0, 64-bit
policyinsight-postgres  | 2026-01-25 04:31:38.533 UTC [42] LOG:  listening on Unix socket "/var/run/postgresql/.s.PGSQL.5432"
policyinsight-postgres  | 2026-01-25 04:31:38.546 UTC [45] LOG:  database system was shut down at 2026-01-25 04:31:38 UTC
policyinsight-postgres  | 2026-01-25 04:31:38.562 UTC [42] LOG:  database system is ready to accept connections
policyinsight-postgres  |  done
policyinsight-postgres  | server started
policyinsight-postgres  | CREATE DATABASE
policyinsight-postgres  |
policyinsight-postgres  |
policyinsight-postgres  | /usr/local/bin/docker-entrypoint.sh: ignoring /docker-entrypoint-initdb.d/*
policyinsight-postgres  |
policyinsight-postgres  | waiting for server to shut down...2026-01-25 04:31:38.807 UTC [42] LOG:  received fast shutdown request
policyinsight-postgres  | .2026-01-25 04:31:38.817 UTC [42] LOG:  aborting any active transactions
policyinsight-postgres  | 2026-01-25 04:31:38.823 UTC [42] LOG:  background worker "logical replication launcher" (PID 48) exited with exit code 1
policyinsight-postgres  | 2026-01-25 04:31:38.831 UTC [43] LOG:  shutting down
policyinsight-postgres  | 2026-01-25 04:31:38.835 UTC [43] LOG:  checkpoint starting: shutdown immediate
policyinsight-postgres  | 2026-01-25 04:31:39.015 UTC [43] LOG:  checkpoint complete: wrote 921 buffers (5.6%); 0 WAL file(s) added, 0 removed, 0 recycled; write=0.072 s, sync=0.094 s, total=0.184 s; sync files=301, longest=0.004 s, average=0.001 s; distance=4239 kB, estimate=4239 kB
policyinsight-postgres  | 2026-01-25 04:31:39.032 UTC [42] LOG:  database system is shut down
policyinsight-postgres  |  done
policyinsight-postgres  | server stopped
policyinsight-postgres  |
policyinsight-postgres  | PostgreSQL init process complete; ready for start up.
policyinsight-postgres  |
policyinsight-postgres  | 2026-01-25 04:31:39.179 UTC [1] LOG:  starting PostgreSQL 15.15 on x86_64-pc-linux-musl, compiled by gcc (Alpine 15.2.0) 15.2.0, 64-bit
policyinsight-postgres  | 2026-01-25 04:31:39.179 UTC [1] LOG:  listening on IPv4 address "0.0.0.0", port 5432
policyinsight-postgres  | 2026-01-25 04:31:39.179 UTC [1] LOG:  listening on IPv6 address "::", port 5432
policyinsight-postgres  | 2026-01-25 04:31:39.186 UTC [1] LOG:  listening on Unix socket "/var/run/postgresql/.s.PGSQL.5432"
policyinsight-postgres  | 2026-01-25 04:31:39.196 UTC [58] LOG:  database system was shut down at 2026-01-25 04:31:39 UTC
policyinsight-postgres  | 2026-01-25 04:31:39.210 UTC [1] LOG:  database system is ready to accept connections
```

## 4) Exact patch applied (diff)
```
diff --git a/scripts/verify-local.ps1 b/scripts/verify-local.ps1
@@
-$healthAttempts = 30
-$readinessAttempts = 30
-$migrationAttempts = 20
-$maxPolls = 40
-$pollIntervalSeconds = 3
+$healthAttempts = 30
+$readinessAttempts = 30
+$migrationAttempts = 20
+$maxPolls = 40
+$pollIntervalSeconds = 3
+$maxAppStartAttempts = 2
@@
-$appProcess = Start-Process -FilePath ".\mvnw.cmd" `
-    -ArgumentList "spring-boot:run", "-Dspring-boot.run.profiles=local" `
-    -RedirectStandardOutput $logOutPath `
-    -RedirectStandardError $logErrPath `
-    -PassThru
+$appJob = $null
+$appReady = $false
+$appStartAttempt = 0
+try {
+    while (-not $appReady -and $appStartAttempt -lt $maxAppStartAttempts) {
+        $appStartAttempt++
+        Write-Host "Starting app attempt $appStartAttempt/$maxAppStartAttempts..." -ForegroundColor Yellow
+        if ($appJob -and $appJob.State -eq "Running") {
+            Stop-Job -Id $appJob.Id -Force
+            Remove-Job -Id $appJob.Id -Force -ErrorAction SilentlyContinue
+        }
+        $appJob = Start-Job -ScriptBlock {
+            param($repoRoot, $stdoutPath, $stderrPath)
+            Set-Location $repoRoot
+            & .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local 1> $stdoutPath 2> $stderrPath
+        } -ArgumentList (Get-Location).Path, $logOutPath, $logErrPath
+        Write-Host "Waiting for app health endpoint..." -ForegroundColor Yellow
+        for ($i = 1; $i -le $healthAttempts; $i++) {
+            if ($appJob -and $appJob.State -ne "Running") {
+                throw "App process exited early. Check $logOutPath and $logErrPath."
+            }
+            Write-Host "  Health attempt $i/$healthAttempts"
+            try {
+                $healthResponse = Invoke-RestMethod -Uri "$AppUrl/health" -Method Get -TimeoutSec 3
+                if ($healthResponse.status -eq "UP") {
+                    $appReady = $true
+                    Write-Host "App is healthy after $i attempt(s)." -ForegroundColor Green
+                    break
+                }
+            } catch {
+                Start-Sleep -Seconds 2
+            }
+        }
+        if (-not $appReady) {
+            Write-Host "App did not become healthy on attempt $appStartAttempt." -ForegroundColor Yellow
+            Stop-Job -Id $appJob.Id -Force
+            Remove-Job -Id $appJob.Id -Force -ErrorAction SilentlyContinue
+        }
+    }
+    if (-not $appReady) {
+        throw "App did not become healthy after $maxAppStartAttempts attempt(s)."
+    }
@@
-} finally {
-    if ($appProcess -and -not $appProcess.HasExited) {
-        Write-Host "Stopping app process..." -ForegroundColor Yellow
-        $appProcess.CloseMainWindow() | Out-Null
-        Start-Sleep -Seconds 2
-        if (-not $appProcess.HasExited) {
-            Stop-Process -Id $appProcess.Id -Force
-        }
-    }
-}
+} finally {
+    if ($appJob) {
+        Write-Host "Stopping app job..." -ForegroundColor Yellow
+        if ($appJob.State -eq "Running") {
+            Stop-Job -Id $appJob.Id -Force
+        }
+        Receive-Job -Id $appJob.Id -ErrorAction SilentlyContinue | Out-Null
+        Remove-Job -Id $appJob.Id -Force -ErrorAction SilentlyContinue
+    }
+}
```

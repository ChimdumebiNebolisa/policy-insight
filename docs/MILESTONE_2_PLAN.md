# Milestone 2: Local Runtime Fixes

This document details the implementation plan for fixing two known local runtime issues:
1. **Postgres credentials mismatch** between Docker Compose and Spring
2. **TransactionRequiredException** due to Spring proxy self-invocation in the local worker

---

## 1. Objective and Success Criteria

### Objective
Enable deterministic local development where:
- Postgres container starts with credentials that match Spring's datasource configuration
- The local worker claims and processes jobs without `TransactionRequiredException`
- End-to-end verification confirms upload → poll → completion flow

### Success Criteria

| Criterion | Verification |
|-----------|--------------|
| Postgres accepts connections from Spring | `docker compose logs postgres` shows no auth failures |
| Worker polls without exceptions | `./mvnw spring-boot:run` logs show "Claimed job for processing" without `TransactionRequiredException` |
| Job reaches SUCCESS state | curl status poll returns `"status": "SUCCESS"` |
| Token authentication works | Status endpoint accepts `X-Job-Token` header |

---

## 2. Repository Inventory

### Key Files and Locations

| Component | File Path | Key Elements |
|-----------|-----------|--------------|
| Docker Compose | `docker-compose.yml` | Lines 6-8: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| Spring Datasource | `src/main/resources/application.yml` | Lines 5-8: Uses `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` |
| Local Profile | `src/main/resources/application-local.yml` | Line 24: `policyinsight.worker.enabled: true` |
| Scheduling Config | `src/main/java/com/policyinsight/config/SchedulingConfig.java` | `@EnableScheduling` with `@Profile("!test")` |
| Worker Class | `src/main/java/com/policyinsight/processing/LocalDocumentProcessingWorker.java` | `pollAndProcessJobs()` at line 117, `claimJob()` at line 162 |
| Repository | `src/main/java/com/policyinsight/shared/repository/PolicyJobRepository.java` | `findOldestPendingJobsForUpdate()` at line 47, `updateStatusIfPendingWithLease()` at line 94 |
| Token Interceptor | `src/main/java/com/policyinsight/security/JobTokenInterceptor.java` | Line 33: `TOKEN_HEADER = "X-Job-Token"` |
| Document Controller | `src/main/java/com/policyinsight/api/DocumentController.java` | Upload at line 83, Status at line 255 |
| Env Example | `.env.example` | Contains both `POSTGRES_*` and `DB_*` variable sets |
| Gitignore | `.gitignore` | Line 47: `.env` is ignored |

### Current Transaction Flow (Problematic)

```
pollAndProcessJobs() [NO @Transactional - line 117]
    │
    ├── findOldestPendingJobsForUpdate()  ← FOR UPDATE SKIP LOCKED (requires transaction)
    │
    └── claimJob() [@Transactional - line 162]  ← SELF-INVOCATION: proxy bypassed!
            │
            └── updateStatusIfPendingWithLease()  ← @Modifying → TransactionRequiredException
```

---

## 3. Environment Strategy

### Problem Statement

Docker Compose and Spring use **different environment variable names** for database credentials:

| Layer | Database Name | Username | Password |
|-------|---------------|----------|----------|
| Docker Compose | `POSTGRES_DB` | `POSTGRES_USER` | `POSTGRES_PASSWORD` |
| Spring | `DB_NAME` | `DB_USER` | `DB_PASSWORD` |

### Root Cause: Compose `.env` Interpolation vs. Container Environment

**Critical Distinction:**
- Docker Compose `.env` file provides values for **interpolation** (`${VAR}` syntax in `docker-compose.yml`)
- The `.env` file does **NOT** automatically inject variables into the container's environment
- Spring reads environment variables from its **runtime JVM process**, not from Compose interpolation

**Reference:** [Docker Compose Environment Variables Precedence](https://docs.docker.com/compose/how-tos/environment-variables/envvars-precedence/)

> "The .env file is used for variable substitution in Compose files during build time and is not automatically loaded into container environments."

**Reference:** [Postgres Official Image - Environment Variables](https://hub.docker.com/_/postgres)

> "POSTGRES_PASSWORD, POSTGRES_USER, and POSTGRES_DB are used by the postgres image entrypoint script to initialize the database on first start."

### Chosen Solution: Standardize on `DB_*` Variables

We will use `DB_*` as the **single source of truth** and map them into the Postgres container.

**Rationale:**
- Spring already uses `DB_*` consistently throughout `application.yml`
- Fewer changes required (only `docker-compose.yml`)
- Clearer separation: `DB_*` = application config, `POSTGRES_*` = internal container init

### Implementation: Updated `docker-compose.yml`

```yaml
services:
  postgres:
    image: postgres:15-alpine
    container_name: policyinsight-postgres
    environment:
      # Map DB_* variables to Postgres init variables
      POSTGRES_DB: ${DB_NAME:-policyinsight}
      POSTGRES_USER: ${DB_USER:-postgres}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-postgres}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER:-postgres} -d ${DB_NAME:-policyinsight}"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

### Updated `.env.example`

```bash
# PolicyInsight Environment Variables
# Copy this file to .env and fill in your actual values
# DO NOT commit .env to version control

# ============================================
# DATABASE CONFIGURATION (Single Source of Truth)
# ============================================
# These variables configure both:
# 1. Spring Boot datasource (application.yml)
# 2. Postgres container initialization (docker-compose.yml)
DB_HOST=localhost
DB_PORT=5432
DB_NAME=policyinsight
DB_USER=postgres
DB_PASSWORD=postgres

# ============================================
# IMPORTANT: Volume Reset Required for Credential Changes
# ============================================
# If you change DB_USER, DB_PASSWORD, or DB_NAME after the Postgres
# container has been initialized, you MUST reset the volume:
#
#   docker compose down -v
#   docker compose up -d
#
# This is because Postgres only reads POSTGRES_USER/PASSWORD/DB during
# FIRST initialization. The credentials are stored in the volume.
# Without -v, the container reuses existing credentials from the volume.

# Server Configuration
SERVER_PORT=8080
APP_BASE_URL=http://localhost:8080

# Spring Profiles (use 'local' for development with worker enabled)
SPRING_PROFILES_ACTIVE=local

# Application Configuration
APP_STORAGE_MODE=local
APP_STORAGE_LOCAL_DIR=.local-storage
APP_MESSAGING_MODE=local
APP_PROCESSING_MODE=local
APP_LOCAL_WORKER_POLL_MS=2000
APP_LOCAL_WORKER_BATCH_SIZE=5

# Worker Configuration
# Note: application-local.yml sets policyinsight.worker.enabled=true
# This WORKER_ENABLED is for the GCP Pub/Sub worker, not the local poller
WORKER_ENABLED=false
```

### Volume Reset Procedure

**Why is this required?**

The official Postgres Docker image only reads `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_DB` during the **first container start** when the data directory is empty. Credentials are written to the persistent volume. Subsequent starts reuse the existing data directory, ignoring any new environment variable values.

**Reference:** [Postgres Docker Image - Initialization Scripts](https://hub.docker.com/_/postgres)

> "Initialization scripts are only run if the data directory is empty on container startup."

**Reset Commands (PowerShell):**

```powershell
# Stop containers and DELETE the volume (CAUTION: destroys all data)
docker compose down -v

# Verify volume is removed
docker volume ls | Select-String "policyinsight"

# Restart with fresh volume (will use current .env values)
docker compose up -d
```

---

## 4. Transaction Strategy

### Problem: Spring Proxy Self-Invocation

When a method inside a Spring bean calls another method on the **same bean**, the call bypasses the Spring AOP proxy. This means `@Transactional` annotations on the called method are **ignored**.

**Current Code (Problematic):**

```java
// LocalDocumentProcessingWorker.java

@Scheduled(fixedDelayString = "${app.local-worker.poll-ms:2000}")
public void pollAndProcessJobs() {  // NO @Transactional
    List<PolicyJob> pendingJobs = policyJobRepository.findOldestPendingJobsForUpdate(batchSize);
    // ↑ FOR UPDATE SKIP LOCKED requires active transaction!

    for (PolicyJob job : pendingJobs) {
        if (claimJob(job)) {  // SELF-INVOCATION → proxy bypassed!
            // ...
        }
    }
}

@Transactional  // IGNORED due to self-invocation!
public boolean claimJob(PolicyJob job) {
    int updatedRows = policyJobRepository.updateStatusIfPendingWithLease(...);
    // ↑ @Modifying query → TransactionRequiredException
    return updatedRows > 0;
}
```

**Reference:** [Spring Framework - Understanding AOP Proxies](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)

> "Due to the proxy-based nature of Spring's AOP support, calls within the target object are by definition not intercepted... Self-invocation is not going to result in the advice associated with a method invocation getting a chance to execute."

### Solution: Extract Transaction Logic to a Separate Service

Create a new `@Service` class that encapsulates the "claim job with lease" operation. The worker calls this external service, ensuring Spring's proxy applies `@Transactional`.

**New Class: `JobClaimService.java`**

```java
package com.policyinsight.processing;

import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.repository.PolicyJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service responsible for atomically claiming jobs for processing.
 *
 * This service exists as a SEPARATE bean from LocalDocumentProcessingWorker
 * to ensure Spring's @Transactional proxy is applied correctly.
 *
 * IMPORTANT: Self-invocation within the same bean bypasses @Transactional proxies.
 * By extracting this logic to a separate service, we guarantee that:
 * 1. FOR UPDATE SKIP LOCKED executes within an active transaction
 * 2. The status update (@Modifying) executes within the same transaction
 * 3. Both operations are atomic - if either fails, both roll back
 */
@Service
public class JobClaimService {

    private static final Logger logger = LoggerFactory.getLogger(JobClaimService.class);

    private final PolicyJobRepository policyJobRepository;

    @Value("${app.job.lease-duration-minutes:30}")
    private int leaseDurationMinutes;

    public JobClaimService(PolicyJobRepository policyJobRepository) {
        this.policyJobRepository = policyJobRepository;
    }

    /**
     * Finds and claims pending jobs atomically within a single transaction.
     *
     * Transaction boundary ensures:
     * - FOR UPDATE SKIP LOCKED acquires row locks
     * - Status update happens within same transaction
     * - Locks are held until commit/rollback
     *
     * @param batchSize maximum number of jobs to claim
     * @return list of successfully claimed job UUIDs
     */
    @Transactional
    public List<PolicyJob> findAndClaimPendingJobs(int batchSize) {
        // FOR UPDATE SKIP LOCKED - requires active transaction
        List<PolicyJob> pendingJobs = policyJobRepository.findOldestPendingJobsForUpdate(batchSize);

        if (pendingJobs.isEmpty()) {
            return List.of();
        }

        logger.debug("Found {} pending job(s) to claim", pendingJobs.size());

        // Filter to only jobs we successfully claim
        return pendingJobs.stream()
            .filter(this::claimJobInternal)
            .toList();
    }

    /**
     * Claims a single job by updating status from PENDING to PROCESSING.
     * MUST be called within an active transaction (from findAndClaimPendingJobs).
     */
    private boolean claimJobInternal(PolicyJob job) {
        Instant leaseExpiresAt = Instant.now().plus(leaseDurationMinutes, ChronoUnit.MINUTES);

        // @Modifying query - requires active transaction
        int updatedRows = policyJobRepository.updateStatusIfPendingWithLease(
            job.getJobUuid(),
            leaseExpiresAt
        );

        if (updatedRows == 0) {
            logger.debug("Could not claim job {} (already claimed or not PENDING)",
                job.getJobUuid());
            return false;
        }

        logger.debug("Successfully claimed job: {} with lease expiring at {}",
            job.getJobUuid(), leaseExpiresAt);
        return true;
    }
}
```

**Updated `LocalDocumentProcessingWorker.java`:**

```java
// Key changes only - see full file for context

@Service
@ConditionalOnProperty(prefix = "policyinsight.worker", name = "enabled", havingValue = "true")
public class LocalDocumentProcessingWorker implements DocumentJobProcessor {

    @Autowired
    private JobClaimService jobClaimService;  // NEW: inject external service

    // Remove: private int leaseDurationMinutes (moved to JobClaimService)

    @Scheduled(fixedDelayString = "${app.local-worker.poll-ms:2000}")
    public void pollAndProcessJobs() {
        try {
            // NEW: Call external service (proxy applies @Transactional)
            List<PolicyJob> claimedJobs = jobClaimService.findAndClaimPendingJobs(batchSize);

            if (claimedJobs.isEmpty()) {
                return;
            }

            logger.debug("Claimed {} job(s) for processing", claimedJobs.size());

            // Process each claimed job
            for (PolicyJob job : claimedJobs) {
                logger.info("Processing claimed job: {}", job.getJobUuid());
                try {
                    processDocument(job.getJobUuid());
                } catch (Exception e) {
                    logger.error("Error processing job: {}", job.getJobUuid(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Error during job polling", e);
        }
    }

    // REMOVE: claimJob() method (moved to JobClaimService)

    // KEEP: processDocument() with @Transactional (called externally, proxy works)
}
```

### Why This Solution is Proxy-Safe

| Aspect | Before (Broken) | After (Fixed) |
|--------|-----------------|---------------|
| Call path | `pollAndProcessJobs()` → `this.claimJob()` | `pollAndProcessJobs()` → `jobClaimService.findAndClaimPendingJobs()` |
| Proxy involved | No (self-invocation) | Yes (cross-bean call) |
| Transaction active | No | Yes |
| FOR UPDATE SKIP LOCKED | Fails or no-op | Locks rows correctly |
| @Modifying query | `TransactionRequiredException` | Executes successfully |

---

## 5. Runtime Verification Script

### Prerequisites

1. Docker Desktop running
2. PowerShell terminal
3. `.env` file created from `.env.example`

### Step 1: Reset Environment

```powershell
# Stop any running containers and remove volumes
docker compose down -v

# Verify clean state
docker ps -a | Select-String "policyinsight"
docker volume ls | Select-String "policyinsight"
```

### Step 2: Start Postgres with Healthcheck Wait

```powershell
# Start Postgres container
docker compose up -d

# Wait for Postgres to be healthy (deterministic wait)
Write-Host "Waiting for Postgres to be healthy..."
$maxAttempts = 30
$attempt = 0
do {
    $attempt++
    $health = docker inspect --format='{{.State.Health.Status}}' policyinsight-postgres 2>$null
    if ($health -eq "healthy") {
        Write-Host "Postgres is healthy after $attempt attempts"
        break
    }
    Write-Host "  Attempt $attempt/$maxAttempts - Status: $health"
    Start-Sleep -Seconds 2
} while ($attempt -lt $maxAttempts)

if ($health -ne "healthy") {
    Write-Host "ERROR: Postgres did not become healthy in time"
    docker compose logs postgres
    exit 1
}
```

### Step 3: Run Tests

```powershell
# Run unit tests (excludes integration tests that need full env)
.\mvnw.cmd test
```

### Step 4: Start Application with Local Profile

```powershell
# Start Spring Boot with local profile (enables worker)
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

**Expected log output:**
```
INFO  LocalDocumentProcessingWorker - Claimed job for processing: <uuid>
```

**If you see this error, the transaction fix is NOT applied:**
```
TransactionRequiredException: Executing an update/delete query
```

### Step 5: Upload a Test Document

Open a **new PowerShell terminal** and run:

```powershell
# Upload a PDF and capture the response
$response = Invoke-RestMethod -Uri "http://localhost:8080/api/documents/upload" `
    -Method Post `
    -Form @{ file = Get-Item ".\path\to\test.pdf" }

# Extract jobId and token from response
$jobId = $response.jobId
$token = $response.token

Write-Host "Job ID: $jobId"
Write-Host "Token: $token"
```

**Alternative using curl (if installed):**

```powershell
# Upload and save response to file
curl -X POST "http://localhost:8080/api/documents/upload" `
    -F "file=@.\path\to\test.pdf" `
    -o upload_response.json

# Parse response (requires jq or manual inspection)
type upload_response.json
```

### Step 6: Poll Status Until Terminal State

```powershell
# Poll status with X-Job-Token header
$maxPolls = 60
$pollInterval = 5

for ($i = 1; $i -le $maxPolls; $i++) {
    $status = Invoke-RestMethod -Uri "http://localhost:8080/api/documents/$jobId/status" `
        -Headers @{ "X-Job-Token" = $token }

    Write-Host "Poll $i/$maxPolls - Status: $($status.status)"

    if ($status.status -eq "SUCCESS") {
        Write-Host "SUCCESS: Job completed!"
        Write-Host "Report URL: $($status.reportUrl)"
        break
    }
    elseif ($status.status -eq "FAILED") {
        Write-Host "FAILED: $($status.errorMessage)"
        break
    }

    Start-Sleep -Seconds $pollInterval
}
```

**Alternative using curl:**

```powershell
# Single status check with token header
curl -X GET "http://localhost:8080/api/documents/$jobId/status" `
    -H "X-Job-Token: $token"
```

### Step 7: Verify Report (on SUCCESS)

```powershell
# Fetch report JSON
$report = Invoke-RestMethod -Uri "http://localhost:8080/api/documents/$jobId/report-json" `
    -Headers @{ "X-Job-Token" = $token }

# Display summary
$report | ConvertTo-Json -Depth 5
```

### Complete Verification Script

Save as `scripts/verify-local.ps1`:

```powershell
#!/usr/bin/env pwsh
# Milestone 2 Local Runtime Verification Script

param(
    [Parameter(Mandatory=$true)]
    [string]$PdfPath
)

$ErrorActionPreference = "Stop"

Write-Host "=== Milestone 2 Verification ===" -ForegroundColor Cyan

# Step 1: Reset
Write-Host "`n[1/6] Resetting environment..." -ForegroundColor Yellow
docker compose down -v 2>$null
docker compose up -d

# Step 2: Wait for Postgres
Write-Host "`n[2/6] Waiting for Postgres..." -ForegroundColor Yellow
$maxAttempts = 30
for ($i = 1; $i -le $maxAttempts; $i++) {
    $health = docker inspect --format='{{.State.Health.Status}}' policyinsight-postgres 2>$null
    if ($health -eq "healthy") { break }
    Start-Sleep -Seconds 2
}
if ($health -ne "healthy") { throw "Postgres not healthy" }
Write-Host "  Postgres is healthy" -ForegroundColor Green

# Step 3: Start app in background
Write-Host "`n[3/6] Starting application..." -ForegroundColor Yellow
Write-Host "  Run in separate terminal: .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'"
Write-Host "  Press Enter when app is ready..."
Read-Host

# Step 4: Upload
Write-Host "`n[4/6] Uploading document..." -ForegroundColor Yellow
$response = Invoke-RestMethod -Uri "http://localhost:8080/api/documents/upload" `
    -Method Post -Form @{ file = Get-Item $PdfPath }
$jobId = $response.jobId
$token = $response.token
Write-Host "  Job ID: $jobId" -ForegroundColor Green
Write-Host "  Token: $token" -ForegroundColor Green

# Step 5: Poll
Write-Host "`n[5/6] Polling status..." -ForegroundColor Yellow
$finalStatus = $null
for ($i = 1; $i -le 60; $i++) {
    $status = Invoke-RestMethod -Uri "http://localhost:8080/api/documents/$jobId/status" `
        -Headers @{ "X-Job-Token" = $token }
    Write-Host "  Poll $i - Status: $($status.status)"
    if ($status.status -in @("SUCCESS", "FAILED")) {
        $finalStatus = $status.status
        break
    }
    Start-Sleep -Seconds 5
}

# Step 6: Result
Write-Host "`n[6/6] Result" -ForegroundColor Yellow
if ($finalStatus -eq "SUCCESS") {
    Write-Host "  VERIFICATION PASSED" -ForegroundColor Green
} else {
    Write-Host "  VERIFICATION FAILED: $finalStatus" -ForegroundColor Red
    exit 1
}
```

---

## 6. Risk and Rollback

### Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Volume wipe deletes test data | High | Medium | Document procedure; only affects local dev |
| Profile confusion (missing `local`) | Medium | High | Clear instructions; verify `policyinsight.worker.enabled` in logs |
| New service not injected | Low | High | Spring Boot auto-wiring; verify in startup logs |
| Transaction still fails | Low | High | Unit test for `JobClaimService`; integration test |

### Rollback Procedure

If issues arise after implementation:

```powershell
# 1. Stop the application (Ctrl+C in the running terminal)

# 2. Reset git changes
git checkout -- src/main/java/com/policyinsight/processing/LocalDocumentProcessingWorker.java
git checkout -- docker-compose.yml
git checkout -- .env.example

# 3. Remove new file if created
Remove-Item -Path "src/main/java/com/policyinsight/processing/JobClaimService.java" -ErrorAction SilentlyContinue

# 4. Reset Docker
docker compose down -v
docker compose up -d

# 5. Restart with original code
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

### Testing the Transaction Fix

Add a unit test to verify the new service:

```java
// src/test/java/com/policyinsight/processing/JobClaimServiceTest.java

@SpringBootTest
@Transactional
class JobClaimServiceTest {

    @Autowired
    private JobClaimService jobClaimService;

    @Autowired
    private PolicyJobRepository policyJobRepository;

    @Test
    void findAndClaimPendingJobs_shouldNotThrowTransactionException() {
        // Given: A pending job
        PolicyJob job = new PolicyJob(UUID.randomUUID());
        job.setStatus("PENDING");
        policyJobRepository.save(job);

        // When: Claiming jobs (should NOT throw TransactionRequiredException)
        List<PolicyJob> claimed = jobClaimService.findAndClaimPendingJobs(5);

        // Then: Job is claimed
        assertThat(claimed).hasSize(1);

        PolicyJob updated = policyJobRepository.findByJobUuid(job.getJobUuid()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("PROCESSING");
    }
}
```

---

## 7. Milestone Commit Plan

### Branch Strategy

```bash
git checkout -b milestone-2-local-runtime
```

### Commit Sequence

| Commit # | Description | Files Changed |
|----------|-------------|---------------|
| 1 | docs: add Milestone 2 implementation plan | `docs/MILESTONE_2_PLAN.md` |
| 2 | feat: extract JobClaimService for proxy-safe transactions | `src/main/java/com/policyinsight/processing/JobClaimService.java` |
| 3 | refactor: update worker to use JobClaimService | `src/main/java/com/policyinsight/processing/LocalDocumentProcessingWorker.java` |
| 4 | fix: standardize env vars in docker-compose | `docker-compose.yml`, `.env.example` |
| 5 | test: add JobClaimService transaction test | `src/test/java/com/policyinsight/processing/JobClaimServiceTest.java` |
| 6 | docs: add verification script | `scripts/verify-local.ps1` |

### Merge Criteria

- [ ] All existing tests pass: `.\mvnw.cmd test`
- [ ] New `JobClaimServiceTest` passes
- [ ] Manual verification script completes successfully
- [ ] No `TransactionRequiredException` in logs during processing
- [ ] Job reaches `SUCCESS` status with valid report

---

## References

1. **Docker Compose Environment Variables Precedence**
   https://docs.docker.com/compose/how-tos/environment-variables/envvars-precedence/

2. **Docker Compose Variable Interpolation**
   https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/

3. **Postgres Official Image - Environment Variables**
   https://hub.docker.com/_/postgres (see "Environment Variables" section)

4. **Spring Framework - Understanding AOP Proxies (Self-Invocation)**
   https://docs.spring.io/spring-framework/reference/core/aop/proxying.html

5. **Spring @Transactional and Self-Invocation**
   https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html

---

## Questions for Mitch

1. **Test PDF**: Is there a canonical test PDF in the repo, or should we add one to `src/test/resources/`? The verification script assumes a path is provided.

2. **CI Integration**: Should the verification script (`scripts/verify-local.ps1`) be added to CI, or is it purely for local validation?

3. **Datadog Compose**: Does `docker-compose.datadog.yml` also need the env var mapping update (`DB_*` → `POSTGRES_*`), or is that file only used in deployed environments?

4. **Worker Lease Duration**: The default `app.job.lease-duration-minutes` is 30 minutes. Is this appropriate for local development, or should we override it to something shorter (e.g., 5 minutes) in `application-local.yml`?

5. **Existing `claimJob()` Callers**: Are there any other callers of `LocalDocumentProcessingWorker.claimJob()` besides `pollAndProcessJobs()`? If so, they would also need to switch to `JobClaimService`.

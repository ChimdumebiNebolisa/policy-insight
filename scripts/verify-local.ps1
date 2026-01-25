#!/usr/bin/env pwsh
param(
    [Parameter(Mandatory = $true)]
    [string]$PdfPath,
    [string]$AppUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

Write-Host "=== Milestone 2 Local Runtime Verification ===" -ForegroundColor Cyan

if (Test-Path ".env") {
    Write-Host "Loading .env into process environment..." -ForegroundColor Yellow
    Get-Content ".env" | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $parts = $line.Split("=", 2)
        if ($parts.Length -ne 2) { return }
        $key = $parts[0].Trim()
        $value = $parts[1].Trim().Trim('"').Trim("'")
        if ($key) {
            Set-Item -Path "Env:$key" -Value $value
        }
    }
}

if (-not $env:DB_HOST) { $env:DB_HOST = "localhost" }
if (-not $env:DB_NAME) { $env:DB_NAME = "policyinsight" }
if (-not $env:DB_USER) { $env:DB_USER = "postgres" }
if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = "postgres" }
if (-not $env:DB_PORT) {
    if (Get-NetTCPConnection -LocalPort 5432 -State Listen -ErrorAction SilentlyContinue) {
        $env:DB_PORT = "5433"
        Write-Host "Detected port 5432 in use; using DB_PORT=5433" -ForegroundColor Yellow
    } else {
        $env:DB_PORT = "5432"
    }
}

$dbName = $env:DB_NAME
$dbUser = $env:DB_USER
$serverPort = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8080 }
if (Get-NetTCPConnection -LocalPort $serverPort -State Listen -ErrorAction SilentlyContinue) {
    $serverPort = 8081
    $env:SERVER_PORT = $serverPort.ToString()
}
if ($AppUrl -eq "http://localhost:8080") {
    $AppUrl = "http://localhost:$serverPort"
}
$healthAttempts = 30
$readinessAttempts = 30
$migrationAttempts = 20
$maxPolls = 40
$pollIntervalSeconds = 3
$maxAppStartAttempts = 2

if (-not (Test-Path $PdfPath)) {
    throw "PDF file not found: $PdfPath"
}

Write-Host "Using App URL: $AppUrl" -ForegroundColor Yellow

Write-Host "`n[1/6] Resetting Postgres volume..." -ForegroundColor Yellow
docker compose down -v
docker compose up -d

Write-Host "`n[2/6] Waiting for Postgres healthcheck..." -ForegroundColor Yellow
$maxAttempts = 30
$health = ""
for ($i = 1; $i -le $maxAttempts; $i++) {
    $health = docker inspect --format='{{.State.Health.Status}}' policyinsight-postgres 2>$null
    if ($health -eq "healthy") {
        Write-Host "Postgres is healthy after $i attempt(s)." -ForegroundColor Green
        break
    }
    Start-Sleep -Seconds 2
}
if ($health -ne "healthy") {
    docker compose logs postgres
    throw "Postgres failed to become healthy."
}

Write-Host "`n[3/7] Running Flyway migrations..." -ForegroundColor Yellow
$flywayUrl = "jdbc:postgresql://$($env:DB_HOST):$($env:DB_PORT)/$($env:DB_NAME)"
& .\mvnw.cmd -q flyway:migrate `
    "-Dflyway.url=$flywayUrl" `
    "-Dflyway.user=$($env:DB_USER)" `
    "-Dflyway.password=$($env:DB_PASSWORD)"
if ($LASTEXITCODE -ne 0) {
    throw "Flyway migrations failed."
}

Write-Host "`n[4/7] Starting Spring Boot app (local profile)..." -ForegroundColor Yellow
$dbPortLog = if ($env:DB_PORT) { $env:DB_PORT } else { "5432 (default)" }
Write-Host "Using DB_HOST=$($env:DB_HOST), DB_PORT=$dbPortLog, DB_NAME=$($env:DB_NAME), DB_USER=$($env:DB_USER)" -ForegroundColor Yellow
$logOutPath = Join-Path $PSScriptRoot "verify-local.out.log"
$logErrPath = Join-Path $PSScriptRoot "verify-local.err.log"
Remove-Item $logOutPath -ErrorAction SilentlyContinue
Remove-Item $logErrPath -ErrorAction SilentlyContinue
$appJob = $null
$appReady = $false
$appStartAttempt = 0
$verificationFailed = $false
$jobEnv = @{
    DB_HOST = $env:DB_HOST
    DB_PORT = $env:DB_PORT
    DB_NAME = $env:DB_NAME
    DB_USER = $env:DB_USER
    DB_PASSWORD = $env:DB_PASSWORD
    SERVER_PORT = $env:SERVER_PORT
    APP_BASE_URL = $AppUrl
    SPRING_FLYWAY_ENABLED = "true"
}

function Write-LogTail([string]$path, [string]$label) {
    if (Test-Path $path) {
        Write-Host "`n--- $label ($path) ---" -ForegroundColor Yellow
        Get-Content -Path $path -Tail 200 -ErrorAction SilentlyContinue
    }
}

function Write-FailureLogs {
    Write-Host "`nVerification failed. Collecting logs..." -ForegroundColor Red
    Write-LogTail -path $logOutPath -label "app stdout"
    Write-LogTail -path $logErrPath -label "app stderr"
    docker compose logs postgres
}

try {
    while (-not $appReady -and $appStartAttempt -lt $maxAppStartAttempts) {
        $appStartAttempt++
        Write-Host "Starting app attempt $appStartAttempt/$maxAppStartAttempts..." -ForegroundColor Yellow
        if ($appJob -and $appJob.State -eq "Running") {
            Stop-Job -Id $appJob.Id -Force
            Remove-Job -Id $appJob.Id -Force -ErrorAction SilentlyContinue
        }
        $appJob = Start-Job -ScriptBlock {
            param($repoRoot, $stdoutPath, $stderrPath, $envVars)
            Set-Location $repoRoot
            if ($envVars) {
                foreach ($entry in $envVars.GetEnumerator()) {
                    Set-Item -Path ("Env:" + $entry.Key) -Value $entry.Value
                }
            }
            & .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local 1> $stdoutPath 2> $stderrPath
        } -ArgumentList (Get-Location).Path, $logOutPath, $logErrPath, $jobEnv

        Write-Host "Waiting for app health endpoint..." -ForegroundColor Yellow
        for ($i = 1; $i -le $healthAttempts; $i++) {
            if ($appJob -and $appJob.State -ne "Running") {
                throw "App process exited early. Check $logOutPath and $logErrPath."
            }
            Write-Host "  Health attempt $i/$healthAttempts"
            try {
                $healthResponse = Invoke-RestMethod -Uri "$AppUrl/health" -Method Get -TimeoutSec 3
                if ($healthResponse.status -eq "UP") {
                    $appReady = $true
                    Write-Host "App is healthy after $i attempt(s)." -ForegroundColor Green
                    break
                }
            } catch {
                Start-Sleep -Seconds 2
            }
        }
        if (-not $appReady) {
            Write-Host "App did not become healthy on attempt $appStartAttempt." -ForegroundColor Yellow
            Stop-Job -Id $appJob.Id -Force
            Remove-Job -Id $appJob.Id -Force -ErrorAction SilentlyContinue
        }
    }
    if (-not $appReady) {
        throw "App did not become healthy after $maxAppStartAttempts attempt(s)."
    }

    Write-Host "Waiting for readiness endpoint..." -ForegroundColor Yellow
    $ready = $false
    for ($i = 1; $i -le $readinessAttempts; $i++) {
        Write-Host "  Readiness attempt $i/$readinessAttempts"
        try {
            $readinessResponse = Invoke-RestMethod -Uri "$AppUrl/readiness" -Method Get -TimeoutSec 3
            if ($readinessResponse.status -eq "UP") {
                $ready = $true
                Write-Host "App is ready after $i attempt(s)." -ForegroundColor Green
                break
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    if (-not $ready) {
        throw "App did not become ready in time."
    }

    Write-Host "Waiting for Flyway migrations..." -ForegroundColor Yellow
    $migrationsReady = $false
    for ($i = 1; $i -le $migrationAttempts; $i++) {
        Write-Host "  Migration attempt $i/$migrationAttempts"
        $tableCheck = docker exec -e PGPASSWORD=$env:DB_PASSWORD policyinsight-postgres `
            psql -h 127.0.0.1 -U $dbUser -d $dbName -t -A `
            -c "select to_regclass('public.rate_limit_counters');" 2>$null
        if ($tableCheck -match "rate_limit_counters") {
            $migrationsReady = $true
            Write-Host "Migrations are ready after $i attempt(s)." -ForegroundColor Green
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $migrationsReady) {
        throw "Flyway migrations did not complete in time."
    }

    Write-Host "`n[5/7] Uploading document..." -ForegroundColor Yellow
    $uploadRaw = & curl.exe -s -w "`n%{http_code}" -X POST `
        "$AppUrl/api/documents/upload" `
        -F "file=@$PdfPath"
    $uploadLines = $uploadRaw -split "`n"
    $uploadStatus = $uploadLines[-1].Trim()
    $uploadBody = ($uploadLines[0..($uploadLines.Length - 2)] -join "`n").Trim()
    if ($uploadStatus -notin @("200", "202")) {
        throw "Upload failed: HTTP $uploadStatus - $uploadBody"
    }
    $uploadResponse = $uploadBody | ConvertFrom-Json

    $jobId = $uploadResponse.jobId
    $token = $uploadResponse.token
    if (-not $jobId -or -not $token) {
        throw "Upload response missing jobId or token."
    }
    Write-Host "Job ID: $jobId" -ForegroundColor Green
    Write-Host "Token: $token" -ForegroundColor Green

    Write-Host "`n[6/7] Polling job status..." -ForegroundColor Yellow
    $finalStatus = $null
    for ($i = 1; $i -le $maxPolls; $i++) {
        $statusResponse = Invoke-RestMethod -Uri "$AppUrl/api/documents/$jobId/status" `
            -Headers @{ "X-Job-Token" = $token }

        Write-Host "Poll $i/$maxPolls - Status: $($statusResponse.status)"
        if ($statusResponse.status -in @("SUCCESS", "FAILED")) {
            $finalStatus = $statusResponse
            break
        }
        Start-Sleep -Seconds $pollIntervalSeconds
    }

    Write-Host "`n[7/7] Final status JSON:" -ForegroundColor Yellow
    if ($null -eq $finalStatus) {
        throw "Job did not reach a terminal state."
    }
    $finalStatus | ConvertTo-Json -Depth 10
} catch {
    $verificationFailed = $true
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-FailureLogs
} finally {
    if ($appJob) {
        Write-Host "Stopping app job..." -ForegroundColor Yellow
        if ($appJob.State -eq "Running") {
            Stop-Job -Id $appJob.Id -Force
        }
        Receive-Job -Id $appJob.Id -ErrorAction SilentlyContinue | Out-Null
        Remove-Job -Id $appJob.Id -Force -ErrorAction SilentlyContinue
    }
}

if ($verificationFailed) {
    exit 1
}

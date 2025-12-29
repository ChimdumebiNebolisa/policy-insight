# Database Doctor - Diagnostic tool for PostgreSQL connection issues
# Usage: .\scripts\db-doctor.ps1

$ErrorActionPreference = "Stop"

Write-Host "🔍 PolicyInsight Database Doctor" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# Helper function to get server fingerprint
function Get-ServerFingerprint {
    param(
        [string]$ConnectionMethod,
        [string]$Command
    )

    Write-Host "📊 Server Fingerprint (via $ConnectionMethod):" -ForegroundColor Yellow
    Write-Host "   Command: $Command" -ForegroundColor Gray

    try {
        # Use Invoke-Expression for dynamic command execution
        $result = Invoke-Expression $Command 2>&1
        $exitCode = $LASTEXITCODE
        $output = if ($result -is [array]) { $result -join "`n" } else { $result.ToString() }

        if ($exitCode -eq 0) {
            Write-Host "   Result: $output" -ForegroundColor Green
            return $output.Trim()
        } else {
            Write-Host "   ❌ Failed (exit code: $exitCode)" -ForegroundColor Red
            Write-Host "   Error: $output" -ForegroundColor Red
            return $null
        }
    } catch {
        Write-Host "   ❌ Exception: $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
    Write-Host ""
}

# Step 1: Check Docker container
Write-Host "Step 1: Checking Docker PostgreSQL container..." -ForegroundColor Yellow
$containerExists = docker ps -a --filter "name=policyinsight-postgres" --format "{{.Names}}" 2>&1 | Select-String "policyinsight-postgres"
if ($containerExists) {
    Write-Host "✅ Container 'policyinsight-postgres' exists" -ForegroundColor Green

    $containerRunning = docker ps --filter "name=policyinsight-postgres" --format "{{.Names}}" 2>&1 | Select-String "policyinsight-postgres"
    if ($containerRunning) {
        Write-Host "✅ Container is running" -ForegroundColor Green
    } else {
        Write-Host "⚠️  Container exists but is not running" -ForegroundColor Yellow
        Write-Host "   Start it with: docker-compose up -d postgres" -ForegroundColor Cyan
        exit 1
    }
} else {
    Write-Host "❌ Container 'policyinsight-postgres' not found" -ForegroundColor Red
    Write-Host "   Start it with: docker-compose up -d postgres" -ForegroundColor Cyan
    exit 1
}

Write-Host ""

# Step 2: Detect port mapping using docker port (more reliable)
Write-Host "Step 2: Detecting PostgreSQL port mapping..." -ForegroundColor Yellow
try {
    $portOutput = docker port policyinsight-postgres 5432/tcp 2>&1
    if ($LASTEXITCODE -eq 0) {
        # Parse output: "0.0.0.0:5432" or "[::]:5432" -> extract port number
        if ($portOutput -match ":(\d+)") {
            $detectedPort = $matches[1]
            Write-Host "✅ Detected port: $detectedPort" -ForegroundColor Green
            Write-Host "   Full mapping: $portOutput" -ForegroundColor Gray
        } else {
            Write-Host "⚠️  Could not parse port from: $portOutput" -ForegroundColor Yellow
            $detectedPort = "5432"
        }
    } else {
        Write-Host "⚠️  docker port command failed, using default 5432" -ForegroundColor Yellow
        $detectedPort = "5432"
    }
} catch {
    Write-Host "⚠️  Port detection failed, using default 5432" -ForegroundColor Yellow
    $detectedPort = "5432"
}

Write-Host ""

# Step 3: Check for port conflicts
Write-Host "Step 3: Checking for port conflicts..." -ForegroundColor Yellow
$portInUse = Get-NetTCPConnection -LocalPort $detectedPort -ErrorAction SilentlyContinue
if ($portInUse) {
    $processId = $portInUse.OwningProcess
    $processName = (Get-Process -Id $processId -ErrorAction SilentlyContinue).ProcessName
    Write-Host "⚠️  Port $detectedPort is in use by PID $processId ($processName)" -ForegroundColor Yellow

    # Check if it's the Docker container
    $dockerPids = docker top policyinsight-postgres 2>&1 | Select-Object -Skip 1
    if ($dockerPids -match "\b$processId\b") {
        Write-Host "✅ Port is used by Docker container (expected)" -ForegroundColor Green
    } else {
        Write-Host "⚠️  Port is NOT used by Docker - possible conflict with local Postgres service!" -ForegroundColor Red
        Write-Host "   Recommendation: Stop local Postgres service or change Docker port" -ForegroundColor Cyan
    }
} else {
    Write-Host "⚠️  Port $detectedPort does not appear to be in use (unexpected if container is running)" -ForegroundColor Yellow
}

Write-Host ""

# Step 4: Test connection from inside container
Write-Host "Step 4: Testing connection from INSIDE container (docker exec)..." -ForegroundColor Yellow
$fingerprintQuery = "SELECT inet_server_addr(), inet_server_port(), current_database(), current_user, version();"
# Escape single quotes in SQL for PowerShell
$escapedQuery = $fingerprintQuery -replace "'", "''"
$dockerCmd = "docker exec policyinsight-postgres psql -U postgres -d postgres -tAc `"$escapedQuery`""
$dockerFingerprint = Get-ServerFingerprint -ConnectionMethod "docker exec" -Command $dockerCmd

Write-Host ""

# Step 5: Test connection from host (if psql is available)
Write-Host "Step 5: Testing connection from HOST machine..." -ForegroundColor Yellow
$psqlPath = Get-Command psql -ErrorAction SilentlyContinue
if ($psqlPath) {
    $env:PGPASSWORD = "postgres"
    $escapedQuery = $fingerprintQuery -replace "'", "''"
    $hostCmd = "psql -h localhost -p $detectedPort -U postgres -d postgres -tAc `"$escapedQuery`""
    $hostFingerprint = Get-ServerFingerprint -ConnectionMethod "host psql" -Command $hostCmd
    $env:PGPASSWORD = $null
} else {
    Write-Host "⚠️  psql not found in PATH, skipping host connection test" -ForegroundColor Yellow
    Write-Host "   You can install PostgreSQL client tools to enable this check" -ForegroundColor Cyan
    $hostFingerprint = $null
}

Write-Host ""

# Step 6: Check database existence
Write-Host "Step 6: Checking for 'policyinsight' database..." -ForegroundColor Yellow
$dbCheck = docker exec policyinsight-postgres psql -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='policyinsight';" 2>&1
$dbCheck = $dbCheck.Trim()
if ($dbCheck -eq "1") {
    Write-Host "✅ Database 'policyinsight' exists" -ForegroundColor Green
} else {
    Write-Host "⚠️  Database 'policyinsight' does not exist" -ForegroundColor Yellow
}

Write-Host ""

# Step 7: Summary and recommendations
Write-Host "📋 Summary" -ForegroundColor Cyan
Write-Host "==========" -ForegroundColor Cyan
Write-Host "Container port mapping: $portOutput"
Write-Host "Detected host port: $detectedPort"
Write-Host ""

if ($dockerFingerprint -and $hostFingerprint) {
    Write-Host "🔍 Server Fingerprint Comparison:" -ForegroundColor Yellow
    Write-Host "   Docker exec: $dockerFingerprint" -ForegroundColor Gray
    Write-Host "   Host psql:   $hostFingerprint" -ForegroundColor Gray

    if ($dockerFingerprint -eq $hostFingerprint) {
        Write-Host "✅ Both connections hit the SAME server (good!)" -ForegroundColor Green
    } else {
        Write-Host "❌ Connections hit DIFFERENT servers (this is the problem!)" -ForegroundColor Red
        Write-Host "   Recommendation: Check for multiple Postgres instances" -ForegroundColor Yellow
    }
} else {
    Write-Host "⚠️  Could not complete full comparison" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "💡 Recommendations:" -ForegroundColor Cyan
Write-Host "   1. If port conflicts exist, stop local Postgres service or change Docker port"
Write-Host "   2. Ensure all operations use the same connection method (Docker or host port)"
Write-Host "   3. Use 'docker port policyinsight-postgres 5432/tcp' for reliable port detection"
Write-Host ""


# Start application and generate OpenAPI spec
# Usage: .\scripts\start-and-generate-openapi.ps1

$ErrorActionPreference = "Stop"

Write-Host "ðŸš€ Starting PolicyInsight application for OpenAPI generation..." -ForegroundColor Cyan
Write-Host ""

# Step 1: Check if Docker is running
Write-Host "Step 1: Checking Docker..." -ForegroundColor Yellow
try {
    $dockerCheck = docker ps 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] Docker is running" -ForegroundColor Green
    } else {
        $errorOutput = $dockerCheck -join "`n"
        if ($errorOutput -match "docker.*not.*running" -or $errorOutput -match "Cannot connect" -or $errorOutput -match "dockerDesktopLinuxEngine") {
            Write-Host "âŒ ERROR: Docker Desktop is not running!" -ForegroundColor Red
            Write-Host ""
            Write-Host "Please:" -ForegroundColor Yellow
            Write-Host "  1. Open Docker Desktop application" -ForegroundColor Cyan
            Write-Host "  2. Wait for it to fully start (whale icon in system tray)" -ForegroundColor Cyan
            Write-Host "  3. Run this script again" -ForegroundColor Cyan
            Write-Host ""
            exit 1
        } else {
            Write-Host "âš ï¸  Warning: Docker check returned an error, but continuing..." -ForegroundColor Yellow
            Write-Host "Error: $errorOutput" -ForegroundColor Gray
        }
    }
} catch {
    Write-Host "âŒ ERROR: Docker is not running. Please start Docker Desktop." -ForegroundColor Red
    Write-Host "Exception: $($_.Exception.Message)" -ForegroundColor Gray
    exit 1
}

# Step 2: Start PostgreSQL if not running
Write-Host ""
Write-Host "Step 2: Starting PostgreSQL database..." -ForegroundColor Yellow

# Check if Docker is still accessible
$dockerCheck2 = docker ps 2>&1
if ($LASTEXITCODE -ne 0) {
    $errorOutput = $dockerCheck2 -join "`n"
    if ($errorOutput -match "docker.*not.*running" -or $errorOutput -match "Cannot connect" -or $errorOutput -match "dockerDesktopLinuxEngine") {
        Write-Host "âŒ ERROR: Docker Desktop connection lost!" -ForegroundColor Red
        Write-Host ""
        Write-Host "Please:" -ForegroundColor Yellow
        Write-Host "  1. Open Docker Desktop application" -ForegroundColor Cyan
        Write-Host "  2. Wait for it to fully start (whale icon in system tray)" -ForegroundColor Cyan
        Write-Host "  3. Run this script again" -ForegroundColor Cyan
        Write-Host ""
        exit 1
    }
}

$postgresRunning = docker ps --filter "name=policyinsight-postgres" --format "{{.Names}}" 2>&1 | Select-String "policyinsight-postgres"
if ($postgresRunning) {
    Write-Host "[OK] PostgreSQL is already running" -ForegroundColor Green
} else {
    Write-Host "Starting PostgreSQL container..." -ForegroundColor Cyan
    # Suppress PowerShell error handling for docker-compose (it writes to stderr even on success)
    $ErrorActionPreference = "SilentlyContinue"
    $composeResult = docker-compose up -d postgres 2>&1
    $ErrorActionPreference = "Stop"
    $composeOutput = $composeResult -join "`n"
    $composeExitCode = $LASTEXITCODE

    # Check for actual errors (not informational messages like "Recreate")
    if ($composeExitCode -ne 0) {
        # Only fail on real errors, not informational messages
        if ($composeOutput -match "docker.*not.*running" -or $composeOutput -match "Cannot connect" -or $composeOutput -match "dockerDesktopLinuxEngine" -or ($composeOutput -match "error" -and -not $composeOutput -match "Recreate")) {
            Write-Host "âŒ ERROR: Docker Desktop is not running or docker-compose failed!" -ForegroundColor Red
            Write-Host ""
            Write-Host "Please:" -ForegroundColor Yellow
            Write-Host "  1. Open Docker Desktop application" -ForegroundColor Cyan
            Write-Host "  2. Wait for it to fully start (whale icon in system tray)" -ForegroundColor Cyan
            Write-Host "  3. Run this script again" -ForegroundColor Cyan
            Write-Host ""
            Write-Host "Error details: $composeOutput" -ForegroundColor Gray
            exit 1
        }
    }

    # "Recreate", "Starting", "Started" are all success indicators
    if ($composeOutput -match "Recreate" -or $composeOutput -match "Starting" -or $composeOutput -match "Started" -or $composeOutput -match "Up") {
        Write-Host "[OK] PostgreSQL container started" -ForegroundColor Green
    } else {
        # If no clear success message, verify container is running
        Start-Sleep -Seconds 2
        $verifyRunning = docker ps --filter "name=policyinsight-postgres" --format "{{.Names}}" 2>&1 | Select-String "policyinsight-postgres"
        if ($verifyRunning) {
            Write-Host "[OK] PostgreSQL container is running" -ForegroundColor Green
        } else {
            Write-Host "âš ï¸  Warning: Container may not have started. Output: $composeOutput" -ForegroundColor Yellow
        }
    }

    # Wait for PostgreSQL to be ready
    Write-Host "Waiting for PostgreSQL to be ready..." -ForegroundColor Cyan
    $maxWait = 30
    $elapsed = 0
    while ($elapsed -lt $maxWait) {
        try {
            $result = docker exec policyinsight-postgres pg_isready -U postgres 2>&1
            if ($LASTEXITCODE -eq 0) {
                Write-Host "[OK] PostgreSQL is ready" -ForegroundColor Green
                break
            }
        } catch {
            # Continue waiting
        }
        Start-Sleep -Seconds 1
        $elapsed++
    }

    if ($elapsed -eq $maxWait) {
        Write-Host "âŒ ERROR: PostgreSQL failed to start within ${maxWait} seconds" -ForegroundColor Red
        exit 1
    }
}

# Step 2.5: Detect PostgreSQL port and ensure database exists
Write-Host ""
Write-Host "Step 2.5: Detecting PostgreSQL port and ensuring database exists..." -ForegroundColor Yellow

# Detect the actual port mapping using docker port (more reliable than docker ps format)
Write-Host "Detecting port mapping using 'docker port' command..." -ForegroundColor Cyan
try {
    $portOutput = docker port policyinsight-postgres 5432/tcp 2>&1
    if ($LASTEXITCODE -eq 0) {
        # Parse output: "0.0.0.0:5432" or "[::]:5432" -> extract port number at end of line
        # Use :(\d+)$ to match port at end, and safely check $matches array
        $portMatch = $portOutput -match ":(\d+)$"
        if ($portMatch -and $null -ne $matches -and $matches.Count -gt 1 -and $null -ne $matches[1]) {
            $dbPort = $matches[1]
            Write-Host "[OK] Detected PostgreSQL port: $dbPort" -ForegroundColor Green
            Write-Host "   Full mapping: $portOutput" -ForegroundColor Gray
        } else {
            Write-Host "[WARN]  Could not safely parse port from output: $portOutput" -ForegroundColor Yellow
            Write-Host "   Falling back to default port 5432" -ForegroundColor Yellow
            $dbPort = "5432"
        }
    } else {
        Write-Host "⚠️  docker port command failed, falling back to default 5432" -ForegroundColor Yellow
        $dbPort = "5432"
    }
} catch {
    Write-Host "âš ï¸  Port detection failed, using default 5432" -ForegroundColor Yellow
    Write-Host "   Error: $($_.Exception.Message)" -ForegroundColor Gray
    $dbPort = "5432"
}

# Verify port is accessible from host
Write-Host "Verifying port $dbPort is accessible from host..." -ForegroundColor Cyan
$portTest = Test-NetConnection -ComputerName localhost -Port $dbPort -WarningAction SilentlyContinue
if ($portTest.TcpTestSucceeded) {
    Write-Host "[OK] Port $dbPort is accessible from host" -ForegroundColor Green
} else {
    Write-Host "âŒ ERROR: Port $dbPort is not accessible from host!" -ForegroundColor Red
    Write-Host "   This may indicate a port conflict or container issue." -ForegroundColor Yellow
    Write-Host "   Run .\scripts\db-doctor.ps1 for diagnostics" -ForegroundColor Cyan
    exit 1
}

# Get server fingerprint from inside container
# Force TCP connection using -h 127.0.0.1 -p 5432 so inet_server_addr/port are non-null
Write-Host "Getting server fingerprint from container (via TCP)..." -ForegroundColor Cyan
$fingerprintQuery = "SELECT COALESCE(inet_server_addr()::text, ''), COALESCE(inet_server_port()::text, ''), current_setting('port'), current_database(), current_user, substring(version(), 1, 50);"
$containerFingerprint = docker exec policyinsight-postgres psql -h 127.0.0.1 -p 5432 -U postgres -d postgres -tAc "$fingerprintQuery" 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "Š Container server fingerprint: $($containerFingerprint.Trim())" -ForegroundColor Gray
    Write-Host "   (Connection method: docker exec with TCP)" -ForegroundColor Gray
} else {
    Write-Host "âš ï¸  Could not get container fingerprint" -ForegroundColor Yellow
}

try {
    # Connect to postgres database to check/create policyinsight database
    $dbCheck = docker exec policyinsight-postgres psql -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='policyinsight';" 2>&1
    $dbCheck = $dbCheck.Trim()

    if ($dbCheck -eq "1") {
        Write-Host "[OK] Database 'policyinsight' already exists" -ForegroundColor Green
    } else {
        Write-Host "Creating database 'policyinsight'..." -ForegroundColor Cyan
        $createResult = docker exec policyinsight-postgres psql -U postgres -d postgres -c "CREATE DATABASE policyinsight;" 2>&1
        if ($LASTEXITCODE -eq 0 -and -not ($createResult -match "ERROR")) {
            Write-Host "[OK] Database created successfully" -ForegroundColor Green
        } else {
            Write-Host "âš ï¸  Warning: Database creation may have failed" -ForegroundColor Yellow
            Write-Host "Output: $createResult" -ForegroundColor Gray
        }
    }

    # Verify we can connect to the database from inside container
    $testConnection = docker exec policyinsight-postgres psql -U postgres -d policyinsight -c "SELECT 1;" 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] Verified connection to 'policyinsight' database (inside container)" -ForegroundColor Green
    } else {
        Write-Host "âš ï¸  Warning: Could not verify connection to database" -ForegroundColor Yellow
    }

    # Verify we can connect from HOST to the same server (critical for Flyway)
    Write-Host "Verifying connection from HOST to port $dbPort..." -ForegroundColor Cyan
    $psqlPath = Get-Command psql -ErrorAction SilentlyContinue
    if ($psqlPath) {
        $env:PGPASSWORD = "postgres"
        $hostFingerprint = psql -h localhost -p $dbPort -U postgres -d postgres -tAc "$fingerprintQuery" 2>&1
        $env:PGPASSWORD = $null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Š Host server fingerprint: $($hostFingerprint.Trim())" -ForegroundColor Gray
            Write-Host "   (Connection method: psql -h localhost -p $dbPort)" -ForegroundColor Gray
            if ($containerFingerprint -and $hostFingerprint) {
                $containerFpTrim = $containerFingerprint.Trim()
                $hostFpTrim = $hostFingerprint.Trim()

                # Parse fingerprints: format is "addr|port|config_port|database|user|version"
                # Split by pipe and compare meaningful fields (skip addr/port if NULL/empty)
                $containerParts = $containerFpTrim -split '\|'
                $hostParts = $hostFpTrim -split '\|'

                if ($containerParts.Count -ge 6 -and $hostParts.Count -ge 6) {
                    # Compare: config_port (index 2), database (index 3), user (index 4), version (index 5)
                    # Treat empty/NULL addr (index 0) and port (index 1) as acceptable differences
                    $configPortMatch = ($containerParts[2] -eq $hostParts[2])
                    $dbMatch = ($containerParts[3] -eq $hostParts[3])
                    $userMatch = ($containerParts[4] -eq $hostParts[4])
                    $versionMatch = ($containerParts[5] -eq $hostParts[5])

                    # Check if only difference is NULL/empty server addr/port (socket vs TCP)
                    $addrDiff = ($containerParts[0] -ne $hostParts[0]) -and
                                (([string]::IsNullOrWhiteSpace($containerParts[0])) -or
                                 ([string]::IsNullOrWhiteSpace($hostParts[0])))
                    $portDiff = ($containerParts[1] -ne $hostParts[1]) -and
                                (([string]::IsNullOrWhiteSpace($containerParts[1])) -or
                                 ([string]::IsNullOrWhiteSpace($hostParts[1])))

                    if ($configPortMatch -and $dbMatch -and $userMatch -and $versionMatch) {
                        if ($addrDiff -or $portDiff) {
                            Write-Host "✅ Container and host fingerprints match (same server)!" -ForegroundColor Green
                            Write-Host "   Note: Server addr/port differ (socket vs TCP), but this is expected" -ForegroundColor Gray
                        } else {
                            Write-Host "✅ Container and host fingerprints match - same server!" -ForegroundColor Green
                        }
                    } else {
                        Write-Host "❌ ERROR: Container and host fingerprints DO NOT match!" -ForegroundColor Red
                        Write-Host "   This indicates we're connecting to different PostgreSQL servers." -ForegroundColor Yellow
                        Write-Host "   Container: $containerFpTrim" -ForegroundColor Yellow
                        Write-Host "   Host:      $hostFpTrim" -ForegroundColor Yellow
                        Write-Host "   Config port match: $configPortMatch, DB match: $dbMatch, User match: $userMatch, Version match: $versionMatch" -ForegroundColor Gray
                        Write-Host "   Run .\scripts\db-doctor.ps1 for detailed diagnostics" -ForegroundColor Cyan
                        Write-Host "   Proceeding anyway (Flyway connectivity is the real test)" -ForegroundColor Yellow

                    }
                } elseif ($containerFpTrim -eq $hostFpTrim) {
                    # Fallback: exact match
                    Write-Host "✅ Container and host fingerprints match - same server!" -ForegroundColor Green
                } else {
                    # If parsing fails, check if only difference is NULL addr/port
                    $containerNormalized = $containerFpTrim -replace '\|\|', '|NULL|NULL|'
                    $hostNormalized = $hostFpTrim -replace '^\d+\.\d+\.\d+\.\d+\|\d+\|', 'NULL|NULL|'
                    if ($containerNormalized -eq $hostNormalized) {
                        Write-Host "✅ Container and host fingerprints match (same server)!" -ForegroundColor Green
                        Write-Host "   Note: Server addr/port differ (socket vs TCP), but this is expected" -ForegroundColor Gray
                    } else {
                        Write-Host "⚠️  Warning: Could not parse fingerprints for detailed comparison" -ForegroundColor Yellow
                        Write-Host "   Container: $containerFpTrim" -ForegroundColor Gray
                        Write-Host "   Host:      $hostFpTrim" -ForegroundColor Gray
                        Write-Host "   Proceeding anyway (may be false positive)" -ForegroundColor Yellow
                    }
                }
            }
        } else {
            Write-Host "âš ï¸  Could not connect from host (psql may not be installed)" -ForegroundColor Yellow
            Write-Host "   This is OK if Docker port is correct, but verification is limited" -ForegroundColor Gray
        }
    } else {
        Write-Host "âš ï¸  psql not found on host, skipping host verification" -ForegroundColor Yellow
    }
} catch {
    Write-Host "âš ï¸  Warning: Could not verify/create database: $($_.Exception.Message)" -ForegroundColor Yellow
}

# Step 3: Clean and run Flyway migrations
Write-Host ""
Write-Host "Step 3: Cleaning database and running migrations..." -ForegroundColor Yellow

# Drop and recreate database to ensure clean state
Write-Host "Dropping existing database (if exists)..." -ForegroundColor Cyan

# First, terminate all connections to the database
Write-Host "Terminating active connections to database..." -ForegroundColor Cyan
docker exec policyinsight-postgres psql -U postgres -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'policyinsight' AND pid <> pg_backend_pid();" 2>&1 | Out-Null
Start-Sleep -Seconds 1

# Now drop the database
$dropResult = docker exec policyinsight-postgres psql -U postgres -d postgres -c "DROP DATABASE IF EXISTS policyinsight;" 2>&1
if ($LASTEXITCODE -ne 0) {
    $errorOutput = $dropResult -join "`n"
    if ($errorOutput -notmatch "does not exist") {
        Write-Host "âš ï¸  Warning: Database drop may have failed: $errorOutput" -ForegroundColor Yellow
    }
}
# Wait a moment for drop to complete
Start-Sleep -Seconds 2

Write-Host "Creating fresh database..." -ForegroundColor Cyan
$createResult = docker exec policyinsight-postgres psql -U postgres -d postgres -c "CREATE DATABASE policyinsight;" 2>&1
if ($LASTEXITCODE -ne 0) {
    $errorOutput = $createResult -join "`n"
    if ($errorOutput -match "already exists") {
        Write-Host "Database already exists, continuing..." -ForegroundColor Yellow
    } else {
        Write-Host "âŒ ERROR: Database creation failed: $errorOutput" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "[OK] Database created successfully" -ForegroundColor Green
}

# Verify database exists and is accessible before running Flyway
Write-Host "Verifying database is accessible..." -ForegroundColor Cyan
$verifyAttempts = 0
$maxVerifyAttempts = 5
$dbVerified = $false

while ($verifyAttempts -lt $maxVerifyAttempts) {
    $verifyResult = docker exec policyinsight-postgres psql -U postgres -d policyinsight -c "SELECT 1;" 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] Database is accessible" -ForegroundColor Green
        $dbVerified = $true
        break
    }
    $verifyAttempts++
    Write-Host "Waiting for database to be ready (attempt $verifyAttempts/$maxVerifyAttempts)..." -ForegroundColor Yellow
    Start-Sleep -Seconds 1
}

if (-not $dbVerified) {
    Write-Host "âŒ ERROR: Database is not accessible after creation" -ForegroundColor Red
    exit 1
}

# Double-check database exists before Flyway
Write-Host "Final verification before Flyway..." -ForegroundColor Cyan
$finalCheck = docker exec policyinsight-postgres psql -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='policyinsight';" 2>&1
$finalCheck = $finalCheck.Trim()
if ($finalCheck -ne "1") {
    Write-Host "âŒ ERROR: Database 'policyinsight' does not exist in container!" -ForegroundColor Red
    Write-Host "Attempting to create it again..." -ForegroundColor Yellow
    docker exec policyinsight-postgres psql -U postgres -d postgres -c "CREATE DATABASE policyinsight;" 2>&1 | Out-Null
    Start-Sleep -Seconds 2
    # Verify again
    $finalCheck2 = docker exec policyinsight-postgres psql -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='policyinsight';" 2>&1
    $finalCheck2 = $finalCheck2.Trim()
    if ($finalCheck2 -ne "1") {
        Write-Host "âŒ ERROR: Failed to create database!" -ForegroundColor Red
        exit 1
    }
}

# Verify database exists from HOST perspective (what Flyway will see)
Write-Host "Verifying database from HOST perspective (Flyway will use this)..." -ForegroundColor Cyan
$psqlPath = Get-Command psql -ErrorAction SilentlyContinue
if ($psqlPath) {
    $env:PGPASSWORD = "postgres"
    $hostDbCheck = psql -h localhost -p $dbPort -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='policyinsight';" 2>&1
    $env:PGPASSWORD = $null
    $hostDbCheck = $hostDbCheck.Trim()
    if ($LASTEXITCODE -eq 0 -and $hostDbCheck -eq "1") {
        Write-Host "[OK] Database 'policyinsight' is visible from HOST on port $dbPort" -ForegroundColor Green
    } else {
        Write-Host "âŒ ERROR: Database 'policyinsight' is NOT visible from HOST on port $dbPort!" -ForegroundColor Red
        Write-Host "   This indicates a port/server mismatch. Flyway will fail." -ForegroundColor Yellow
        Write-Host "   Container check result: $finalCheck" -ForegroundColor Gray
        Write-Host "   Host check result: $hostDbCheck" -ForegroundColor Gray
        Write-Host "   Run .\scripts\db-doctor.ps1 for diagnostics" -ForegroundColor Cyan
        exit 1
    }
} else {
    Write-Host "âš ï¸  psql not available for host verification, proceeding with Flyway anyway" -ForegroundColor Yellow
}

Write-Host "Running Flyway migrations on port $dbPort..." -ForegroundColor Cyan
$flywayUrl = "jdbc:postgresql://localhost:$dbPort/policyinsight"
Write-Host "Flyway URL: $flywayUrl" -ForegroundColor Gray
Write-Host "Flyway user: postgres" -ForegroundColor Gray
Write-Host "Flyway password: postgres" -ForegroundColor Gray

try {
    # Explicitly pass all Flyway properties to override pom.xml defaults
    .\mvnw.cmd flyway:migrate "-Dflyway.url=$flywayUrl" "-Dflyway.user=postgres" "-Dflyway.password=postgres"
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] Migrations completed" -ForegroundColor Green
    } else {
        Write-Host "âŒ ERROR: Migrations failed (exit code: $LASTEXITCODE)" -ForegroundColor Red
        Write-Host "Verifying database state..." -ForegroundColor Yellow
        docker exec policyinsight-postgres psql -U postgres -d postgres -c "\l" | Select-String "policyinsight"
        exit 1
    }
} catch {
    Write-Host "âŒ ERROR: Migrations failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Verifying database state..." -ForegroundColor Yellow
    docker exec policyinsight-postgres psql -U postgres -d postgres -c "\l" | Select-String "policyinsight"
    exit 1
}

# Step 4: Build the application
Write-Host ""
Write-Host "Step 4: Building application..." -ForegroundColor Yellow
try {
    .\mvnw.cmd clean package -DskipTests
    Write-Host "[OK] Build completed" -ForegroundColor Green
} catch {
    Write-Host "âŒ ERROR: Build failed" -ForegroundColor Red
    exit 1
}

# Step 5: Start the application in background
Write-Host ""
Write-Host "Step 5: Starting application..." -ForegroundColor Yellow

# Check if port 8080 is already in use
Write-Host "Checking if port 8080 is available..." -ForegroundColor Cyan
$port8080 = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
if ($port8080) {
    Write-Host "Port 8080 is in use. Attempting to stop existing process..." -ForegroundColor Yellow
    $processId = $port8080.OwningProcess
    try {
        Stop-Process -Id $processId -Force -ErrorAction Stop
        Write-Host "[OK] Stopped process $processId" -ForegroundColor Green
        Start-Sleep -Seconds 2
    } catch {
        Write-Host "âš ï¸  Could not stop process $processId. Trying to continue..." -ForegroundColor Yellow
    }
} else {
    Write-Host "[OK] Port 8080 is available" -ForegroundColor Green
}

# Find the JAR file (exclude .original files)
$jarFile = Get-ChildItem -Path "target" -Filter "policy-insight-*.jar" | Where-Object { $_.Name -notlike "*.original" } | Select-Object -First 1
if (-not $jarFile) {
    Write-Host "âŒ ERROR: JAR file not found in target/" -ForegroundColor Red
    Write-Host "Available files in target/:" -ForegroundColor Yellow
    Get-ChildItem -Path "target" -Filter "*.jar" | ForEach-Object { Write-Host "  - $($_.Name)" }
    exit 1
}

Write-Host "Using JAR: $($jarFile.Name)" -ForegroundColor Cyan

# Start the application
Write-Host "Setting environment variables (DB_PORT=$dbPort)..." -ForegroundColor Cyan

# Create environment variable hashtable for Start-Process
$processEnv = @{
    "SPRING_PROFILES_ACTIVE" = "local"
    "DB_HOST" = "localhost"
    "DB_PORT" = $dbPort
    "DB_NAME" = "policyinsight"
    "DB_USER" = "postgres"
    "DB_PASSWORD" = "postgres"
    "SERVER_PORT" = "8080"
}

# Also set in current session for verification
$env:SPRING_PROFILES_ACTIVE = "local"
$env:DB_HOST = "localhost"
$env:DB_PORT = $dbPort
$env:DB_NAME = "policyinsight"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "postgres"
$env:SERVER_PORT = "8080"

Write-Host "Starting Java process with environment variables..." -ForegroundColor Cyan
Write-Host "  DB_PORT=$dbPort" -ForegroundColor Gray
Write-Host "  DB_HOST=localhost" -ForegroundColor Gray
Write-Host "  DB_NAME=policyinsight" -ForegroundColor Gray

# Verify database is accessible on the detected port before starting app
Write-Host "Verifying database connection on port $dbPort..." -ForegroundColor Cyan
try {
    $testConn = Test-NetConnection -ComputerName localhost -Port $dbPort -WarningAction SilentlyContinue
    if ($testConn.TcpTestSucceeded) {
        Write-Host "[OK] Database port $dbPort is accessible" -ForegroundColor Green
    } else {
        Write-Host "âš ï¸  Warning: Cannot connect to database on port $dbPort" -ForegroundColor Yellow
    }
} catch {
    Write-Host "âš ï¸  Warning: Could not verify database port accessibility" -ForegroundColor Yellow
}

# Build Java arguments with system properties
# Pass datasource URL directly to override any system-level environment variables
# Spring Boot property placeholders ${VAR:default} check environment variables FIRST,
# so passing -DDB_PORT won't override a system-level DB_PORT env var
# Solution: Pass the full datasource URL directly as a Spring Boot property
$datasourceUrl = "jdbc:postgresql://localhost:$dbPort/policyinsight"
$javaArgs = @(
    "-Dspring.profiles.active=local",
    "-Dspring.datasource.url=$datasourceUrl",
    "-Dspring.datasource.username=postgres",
    "-Dspring.datasource.password=postgres",
    "-Dserver.port=8080",
    "-jar",
    "`"$($jarFile.FullName)`""
)

# #region agent log
$logPath = ".cursor\debug.log"
$logDir = Split-Path -Parent $logPath
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
$logEntry = @{
    timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
    location = "start-and-generate-openapi.ps1:410"
    message = "Before Start-Process - configuration check"
    data = @{
        detectedDbPort = $dbPort
        datasourceUrl = $datasourceUrl
        envDbPort = $env:DB_PORT
        systemDbPort = [Environment]::GetEnvironmentVariable("DB_PORT", "User")
        machineDbPort = [Environment]::GetEnvironmentVariable("DB_PORT", "Machine")
        javaArgs = ($javaArgs -join " ")
    }
    sessionId = "debug-session"
    runId = "run2"
    hypothesisId = "A"
} | ConvertTo-Json -Depth 10 -Compress
Add-Content -Path $logPath -Value $logEntry
# #endregion agent log

# Use Start-Process - Pass datasource URL directly as Spring Boot property
# This overrides any system-level environment variables
$appProcess = Start-Process -FilePath "java" `
    -ArgumentList $javaArgs `
    -PassThru -NoNewWindow `
    -RedirectStandardOutput "app.log" `
    -RedirectStandardError "app-error.log"

Write-Host "Application starting (PID: $($appProcess.Id))..." -ForegroundColor Cyan

# Wait for application to be ready
Write-Host "Waiting for application to be ready..." -ForegroundColor Cyan
$maxWait = 60
$elapsed = 0
$ready = $false

while ($elapsed -lt $maxWait) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -Method Get -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            Write-Host "[OK] Application is ready!" -ForegroundColor Green
            $ready = $true
            break
        }
    } catch {
        # Continue waiting
    }
    Start-Sleep -Seconds 1
    $elapsed++
    Write-Host "." -NoNewline -ForegroundColor Gray
}

Write-Host ""

if (-not $ready) {
    Write-Host "âŒ ERROR: Application failed to start within ${maxWait} seconds" -ForegroundColor Red
    Write-Host "Application logs:" -ForegroundColor Yellow
    if (Test-Path "app.log") {
        Get-Content "app.log" -Tail 20
    }
    if (Test-Path "app-error.log") {
        Get-Content "app-error.log" -Tail 20
    }
    Stop-Process -Id $appProcess.Id -Force -ErrorAction SilentlyContinue
    exit 1
}

# Give it a moment for OpenAPI to be ready
Start-Sleep -Seconds 2

# Step 6: Generate OpenAPI spec
Write-Host ""
Write-Host "Step 6: Generating OpenAPI spec..." -ForegroundColor Yellow
try {
    .\scripts\generate-openapi.ps1
    Write-Host ""
    Write-Host "[OK] OpenAPI spec generated successfully!" -ForegroundColor Green
} catch {
    Write-Host "âŒ ERROR: Failed to generate OpenAPI spec" -ForegroundColor Red
    Write-Host $_.Exception.Message
}

# Step 7: Stop the application
Write-Host ""
Write-Host "Step 7: Stopping application..." -ForegroundColor Yellow
try {
    Stop-Process -Id $appProcess.Id -Force -ErrorAction SilentlyContinue
    Write-Host "[OK] Application stopped" -ForegroundColor Green
} catch {
    Write-Host "âš ï¸  Warning: Could not stop application process" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "âœ¨ Done! OpenAPI spec is available at: docs/openapi.json" -ForegroundColor Green






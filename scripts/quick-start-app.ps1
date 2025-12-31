# Quick start script - assumes database is already running
# Usage: .\scripts\quick-start-app.ps1

$ErrorActionPreference = "Stop"

Write-Host "🚀 Starting PolicyInsight application..." -ForegroundColor Cyan

# Check if app is already running
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -Method Get -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
    if ($response.StatusCode -eq 200) {
        Write-Host "✅ Application is already running!" -ForegroundColor Green
        exit 0
    }
} catch {
    # App is not running, continue
}

# Build if needed
if (-not (Test-Path "target/policy-insight-*.jar")) {
    Write-Host "Building application..." -ForegroundColor Yellow
    .\mvnw.cmd clean package -DskipTests
}

# Find JAR file
$jarFile = Get-ChildItem -Path "target" -Filter "policy-insight-*.jar" -Exclude "*original*" | Select-Object -First 1
if (-not $jarFile) {
    Write-Host "❌ ERROR: JAR file not found. Run: .\mvnw.cmd clean package" -ForegroundColor Red
    exit 1
}

# Set environment variables (use existing env vars or defaults for local dev)
$env:SPRING_PROFILES_ACTIVE = if ($env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE } else { "local" }
$env:DB_HOST = if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }
$env:DB_PORT = if ($env:DB_PORT) { $env:DB_PORT } else { "5432" }
$env:DB_NAME = if ($env:DB_NAME) { $env:DB_NAME } else { "policyinsight" }
$env:DB_USER = if ($env:DB_USER) { $env:DB_USER } else { "postgres" }
$env:DB_PASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "postgres" }
$env:SERVER_PORT = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { "8080" }

Write-Host "Starting application with JAR: $($jarFile.Name)" -ForegroundColor Cyan
Write-Host "Application will be available at: http://localhost:8080" -ForegroundColor Cyan
Write-Host "Press Ctrl+C to stop the application" -ForegroundColor Yellow
Write-Host ""

# Start the application (foreground)
java -jar $jarFile.FullName


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

# Set environment variables
$env:SPRING_PROFILES_ACTIVE = "local"
$env:DB_HOST = "localhost"
$env:DB_PORT = "5432"
$env:DB_NAME = "policyinsight"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "postgres"
$env:SERVER_PORT = "8080"

Write-Host "Starting application with JAR: $($jarFile.Name)" -ForegroundColor Cyan
Write-Host "Application will be available at: http://localhost:8080" -ForegroundColor Cyan
Write-Host "Press Ctrl+C to stop the application" -ForegroundColor Yellow
Write-Host ""

# Start the application (foreground)
java -jar $jarFile.FullName


# PowerShell script to run PolicyInsight with Datadog agent
# Prerequisites:
#   1. Datadog agent running (docker-compose -f docker-compose.datadog.yml up -d datadog-agent)
#   2. DD_API_KEY environment variable set (optional for local testing)
#   3. Java 21 installed
#   4. Maven wrapper (./mvnw) available

param(
    [string]$DdApiKey = $env:DD_API_KEY,
    [string]$DdService = "policy-insight",
    [string]$DdEnv = "local",
    [string]$DdVersion = "dev",
    [string]$DdAgentHost = "localhost"
)

Write-Host "Starting PolicyInsight with Datadog observability..." -ForegroundColor Green
Write-Host "  DD_SERVICE: $DdService" -ForegroundColor Cyan
Write-Host "  DD_ENV: $DdEnv" -ForegroundColor Cyan
Write-Host "  DD_VERSION: $DdVersion" -ForegroundColor Cyan
Write-Host "  DD_AGENT_HOST: $DdAgentHost" -ForegroundColor Cyan

# Download dd-java-agent if not present
$ddAgentPath = ".\.dd-java-agent\dd-java-agent.jar"
if (-not (Test-Path $ddAgentPath)) {
    Write-Host "Downloading dd-java-agent..." -ForegroundColor Yellow
    $ddAgentDir = Split-Path $ddAgentPath -Parent
    if (-not (Test-Path $ddAgentDir)) {
        New-Item -ItemType Directory -Path $ddAgentDir -Force | Out-Null
    }

    $downloadUrl = "https://dtdg.co/latest-java-tracer"
    try {
        Invoke-WebRequest -Uri $downloadUrl -OutFile $ddAgentPath -UseBasicParsing
        Write-Host "Downloaded dd-java-agent to $ddAgentPath" -ForegroundColor Green
    } catch {
        Write-Host "Failed to download dd-java-agent: $_" -ForegroundColor Red
        Write-Host "Please download manually from: https://dtdg.co/latest-java-tracer" -ForegroundColor Yellow
        exit 1
    }
}

# Set environment variables
$env:DATADOG_ENABLED = "true"
$env:DD_SERVICE = $DdService
$env:DD_ENV = $DdEnv
$env:DD_VERSION = $DdVersion
$env:DD_AGENT_HOST = $DdAgentHost
$env:DD_LOGS_INJECTION = "true"
$env:DD_TRACE_SAMPLE_RATE = "1.0"
$env:DD_PROFILING_ENABLED = "false"
$env:SPRING_PROFILES_ACTIVE = "datadog"

if ($DdApiKey) {
    $env:DD_API_KEY = $DdApiKey
}

# Build Java agent path (absolute)
$absoluteAgentPath = (Resolve-Path $ddAgentPath).Path

# Run Spring Boot with dd-java-agent
Write-Host "`nStarting application with dd-java-agent..." -ForegroundColor Green
Write-Host "  Java agent: $absoluteAgentPath" -ForegroundColor Cyan
Write-Host "  Spring profile: datadog" -ForegroundColor Cyan
& .\mvnw.cmd spring-boot:run `
    -Dspring-boot.run.jvmArguments="-javaagent:$absoluteAgentPath -Ddd.service=$DdService -Ddd.env=$DdEnv -Ddd.version=$DdVersion -Ddd.agent.host=$DdAgentHost -Ddd.logs.injection=true" `
    -Dspring-boot.run.arguments="--spring.profiles.active=datadog"


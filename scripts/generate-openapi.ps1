# Generate OpenAPI spec from running application
# Usage: .\scripts\generate-openapi.ps1 [output-path]
# Default output: docs/openapi.json

param(
    [string]$OutputPath = "docs/openapi.json",
    [string]$AppUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

$HealthUrl = "$AppUrl/actuator/health"
$OpenApiUrl = "$AppUrl/v3/api-docs"

Write-Host "Generating OpenAPI spec..." -ForegroundColor Cyan
Write-Host "App URL: $AppUrl"
Write-Host "Output: $OutputPath"

# Check if app is running
try {
    $response = Invoke-WebRequest -Uri $HealthUrl -Method Get -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
    if ($response.StatusCode -ne 200) {
        throw "Health check returned status $($response.StatusCode)"
    }
} catch {
    Write-Host "ERROR: Application is not running at $AppUrl" -ForegroundColor Red
    Write-Host "Please start the application first:"
    Write-Host "  .\mvnw.cmd spring-boot:run"
    Write-Host "  OR"
    Write-Host "  java -jar target/policy-insight-*.jar"
    exit 1
}

Write-Host "Application is running, fetching OpenAPI spec..." -ForegroundColor Green

# Fetch OpenAPI spec
try {
    $response = Invoke-WebRequest -Uri $OpenApiUrl -Method Get -UseBasicParsing -ErrorAction Stop

    # Ensure output directory exists
    $outputDir = Split-Path -Parent $OutputPath
    if (-not (Test-Path $outputDir)) {
        New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
    }

    # Save to file
    $response.Content | Out-File -FilePath $OutputPath -Encoding UTF8

    # Try to format JSON if possible (requires PowerShell 7+ or ConvertTo-Json)
    try {
        $json = Get-Content $OutputPath -Raw | ConvertFrom-Json
        $json | ConvertTo-Json -Depth 100 | Set-Content $OutputPath -Encoding UTF8
    } catch {
        # If formatting fails, just keep original
        Write-Host "Note: Could not format JSON (requires PowerShell 7+ for proper formatting)" -ForegroundColor Yellow
    }

    Write-Host "✅ OpenAPI spec generated successfully at $OutputPath" -ForegroundColor Green

    # Show first few lines
    Write-Host ""
    Write-Host "First 20 lines of generated spec:"
    Get-Content $OutputPath -Head 20

    exit 0
} catch {
    Write-Host "ERROR: Failed to fetch OpenAPI spec from $OpenApiUrl" -ForegroundColor Red
    Write-Host $_.Exception.Message
    exit 1
}


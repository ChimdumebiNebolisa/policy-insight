# PowerShell script to import Datadog dashboards, monitors, and SLOs from JSON files
# Requires: DD_API_KEY and DD_APP_KEY environment variables
# Usage: .\scripts\datadog\import.ps1

param(
    [string]$DdApiKey = $env:DD_API_KEY,
    [string]$DdAppKey = $env:DD_APP_KEY,
    [string]$DdSite = $env:DD_SITE
)

if (-not $DdApiKey -or -not $DdAppKey) {
    Write-Host "Error: DD_API_KEY and DD_APP_KEY environment variables must be set" -ForegroundColor Red
    exit 1
}

$baseUrl = if ($DdSite) { "https://api.$DdSite" } else { "https://api.datadoghq.com" }
$headers = @{
    "DD-API-KEY" = $DdApiKey
    "DD-APPLICATION-KEY" = $DdAppKey
    "Content-Type" = "application/json"
}

Write-Host "Importing Datadog configurations..." -ForegroundColor Green
Write-Host "  API Base URL: $baseUrl" -ForegroundColor Cyan

# Import dashboards
Write-Host "`nImporting dashboards..." -ForegroundColor Yellow
$dashboardsDir = "datadog\dashboards"
if (Test-Path $dashboardsDir) {
    $dashboardFiles = Get-ChildItem -Path $dashboardsDir -Filter "*.json"
    foreach ($file in $dashboardFiles) {
        try {
            $dashboardJson = Get-Content -Path $file.FullName -Raw | ConvertFrom-Json
            $body = $dashboardJson | ConvertTo-Json -Depth 10
            $response = Invoke-RestMethod -Uri "$baseUrl/api/v1/dashboard" -Method Post -Headers $headers -Body $body
            Write-Host "  Imported: $($file.Name)" -ForegroundColor Green
        } catch {
            Write-Host "  Error importing $($file.Name): $_" -ForegroundColor Red
        }
    }
} else {
    Write-Host "  No dashboards directory found" -ForegroundColor Yellow
}

# Import monitors
Write-Host "`nImporting monitors..." -ForegroundColor Yellow
$monitorsDir = "datadog\monitors"
if (Test-Path $monitorsDir) {
    $monitorFiles = Get-ChildItem -Path $monitorsDir -Filter "*.json"
    foreach ($file in $monitorFiles) {
        try {
            $monitorJson = Get-Content -Path $file.FullName -Raw | ConvertFrom-Json
            $body = $monitorJson | ConvertTo-Json -Depth 10
            $response = Invoke-RestMethod -Uri "$baseUrl/api/v1/monitor" -Method Post -Headers $headers -Body $body
            Write-Host "  Imported: $($file.Name)" -ForegroundColor Green
        } catch {
            Write-Host "  Error importing $($file.Name): $_" -ForegroundColor Red
        }
    }
} else {
    Write-Host "  No monitors directory found" -ForegroundColor Yellow
}

# Import SLOs
Write-Host "`nImporting SLOs..." -ForegroundColor Yellow
$slosDir = "datadog\slos"
if (Test-Path $slosDir) {
    $sloFiles = Get-ChildItem -Path $slosDir -Filter "*.json"
    foreach ($file in $sloFiles) {
        try {
            $sloJson = Get-Content -Path $file.FullName -Raw | ConvertFrom-Json
            $body = $sloJson | ConvertTo-Json -Depth 10
            $response = Invoke-RestMethod -Uri "$baseUrl/api/v1/slo" -Method Post -Headers $headers -Body $body
            Write-Host "  Imported: $($file.Name)" -ForegroundColor Green
        } catch {
            Write-Host "  Error importing $($file.Name): $_" -ForegroundColor Red
        }
    }
} else {
    Write-Host "  No SLOs directory found" -ForegroundColor Yellow
}

Write-Host "`nImport complete!" -ForegroundColor Green


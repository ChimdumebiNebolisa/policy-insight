# PowerShell script to export Datadog dashboards, monitors, and SLOs to JSON files
# Requires: DD_API_KEY and DD_APP_KEY environment variables
# Usage: .\scripts\datadog\export.ps1

param(
    [string]$DdApiKey = $env:DD_API_KEY,
    [string]$DdAppKey = $env:DD_APP_KEY,
    [string]$DdSite = $env:DD_SITE
)

if (-not $DdApiKey -or -not $DdAppKey) {
    Write-Host "Error: DD_API_KEY and DD_APP_KEY environment variables must be set" -ForegroundColor Red
    Write-Host "  Set-DotEnv DD_API_KEY=your-api-key" -ForegroundColor Yellow
    Write-Host "  Set-DotEnv DD_APP_KEY=your-app-key" -ForegroundColor Yellow
    exit 1
}

$baseUrl = if ($DdSite) { "https://api.$DdSite" } else { "https://api.datadoghq.com" }
$headers = @{
    "DD-API-KEY" = $DdApiKey
    "DD-APPLICATION-KEY" = $DdAppKey
    "Content-Type" = "application/json"
}

Write-Host "Exporting Datadog configurations..." -ForegroundColor Green
Write-Host "  API Base URL: $baseUrl" -ForegroundColor Cyan

# Export dashboards
Write-Host "`nExporting dashboards..." -ForegroundColor Yellow
$dashboardsDir = "datadog\dashboards"
if (-not (Test-Path $dashboardsDir)) {
    New-Item -ItemType Directory -Path $dashboardsDir -Force | Out-Null
}

try {
    $dashboardsResponse = Invoke-RestMethod -Uri "$baseUrl/api/v1/dashboard" -Method Get -Headers $headers
    foreach ($dashboard in $dashboardsResponse.dashboards) {
        if ($dashboard.title -like "*PolicyInsight*") {
            $dashboardDetail = Invoke-RestMethod -Uri "$baseUrl/api/v1/dashboard/$($dashboard.id)" -Method Get -Headers $headers
            $filename = "$dashboardsDir\$($dashboard.title -replace '[^\w\s-]', '' -replace '\s', '-').json"
            $dashboardDetail | ConvertTo-Json -Depth 10 | Out-File -FilePath $filename -Encoding UTF8
            Write-Host "  Exported: $filename" -ForegroundColor Green
        }
    }
} catch {
    Write-Host "  Error exporting dashboards: $_" -ForegroundColor Red
}

# Export monitors
Write-Host "`nExporting monitors..." -ForegroundColor Yellow
$monitorsDir = "datadog\monitors"
if (-not (Test-Path $monitorsDir)) {
    New-Item -ItemType Directory -Path $monitorsDir -Force | Out-Null
}

try {
    $monitorsResponse = Invoke-RestMethod -Uri "$baseUrl/api/v1/monitor" -Method Get -Headers $headers
    foreach ($monitor in $monitorsResponse) {
        if ($monitor.name -like "*PolicyInsight*") {
            $filename = "$monitorsDir\$($monitor.name -replace '[^\w\s-]', '' -replace '\s', '-').json"
            $monitor | ConvertTo-Json -Depth 10 | Out-File -FilePath $filename -Encoding UTF8
            Write-Host "  Exported: $filename" -ForegroundColor Green
        }
    }
} catch {
    Write-Host "  Error exporting monitors: $_" -ForegroundColor Red
}

# Export SLOs
Write-Host "`nExporting SLOs..." -ForegroundColor Yellow
$slosDir = "datadog\slos"
if (-not (Test-Path $slosDir)) {
    New-Item -ItemType Directory -Path $slosDir -Force | Out-Null
}

try {
    $slosResponse = Invoke-RestMethod -Uri "$baseUrl/api/v1/slo" -Method Get -Headers $headers
    foreach ($slo in $slosResponse.data) {
        if ($slo.name -like "*PolicyInsight*") {
            $sloDetail = Invoke-RestMethod -Uri "$baseUrl/api/v1/slo/$($slo.id)" -Method Get -Headers $headers
            $filename = "$slosDir\$($slo.name -replace '[^\w\s-]', '' -replace '\s', '-').json"
            $sloDetail | ConvertTo-Json -Depth 10 | Out-File -FilePath $filename -Encoding UTF8
            Write-Host "  Exported: $filename" -ForegroundColor Green
        }
    }
} catch {
    Write-Host "  Error exporting SLOs: $_" -ForegroundColor Red
}

Write-Host "`nExport complete!" -ForegroundColor Green


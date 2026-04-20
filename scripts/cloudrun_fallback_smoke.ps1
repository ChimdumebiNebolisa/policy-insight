param(
    [string]$BaseUrl = "https://policy-insight-828177954618.us-central1.run.app"
)

$ErrorActionPreference = "Stop"

$paths = @(
    "/health",
    "/readiness",
    "/sample-report",
    "/sample-pdf"
)

$results = @()

foreach ($path in $paths) {
    $url = "$BaseUrl$path"
    try {
        $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 30
        $status = [int]$response.StatusCode
    }
    catch {
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        else {
            $status = -1
        }
    }

    $results += [PSCustomObject]@{
        Path = $path
        StatusCode = $status
        Url = $url
    }
}

$results | Format-Table -AutoSize

$failed = $results | Where-Object { $_.StatusCode -ne 200 }
if ($failed.Count -gt 0) {
    Write-Host "Cloud Run fallback smoke failed for one or more routes." -ForegroundColor Red
    exit 1
}

Write-Host "Cloud Run fallback smoke passed." -ForegroundColor Green

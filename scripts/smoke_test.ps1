# Smoke test: Upload a PDF, poll status until SUCCESS/FAILED
# Usage: pwsh scripts\smoke_test.ps1 [path-to-pdf]
#
# Arguments:
#   path-to-pdf    Path to PDF file to upload (required)
#
# Environment variables:
#   WEB_URL        Web service URL (default: https://policyinsight-web-828177954618.us-central1.run.app)
#
# Exit codes:
#   0: Success
#   1: Error (upload failed, job failed, timeout)
#   2: Usage error (missing PDF path)
#
# Example:
#   pwsh scripts\smoke_test.ps1 tiny.pdf

$ErrorActionPreference = "Stop"

if ($args.Count -eq 0) {
    Write-Host "Usage: pwsh scripts\smoke_test.ps1 <path-to-pdf>" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Arguments:"
    Write-Host "  path-to-pdf    Path to PDF file to upload (required)"
    Write-Host ""
    Write-Host "Environment variables:"
    Write-Host "  WEB_URL        Web service URL (default: https://policyinsight-web-828177954618.us-central1.run.app)"
    Write-Host ""
    Write-Host "Example:"
    Write-Host "  pwsh scripts\smoke_test.ps1 tiny.pdf"
    exit 2
}

$PDF_PATH = $args[0]
$WEB_URL = if ($env:WEB_URL) { $env:WEB_URL } else { "https://policyinsight-web-828177954618.us-central1.run.app" }

if (-not (Test-Path $PDF_PATH)) {
    Write-Host "ERROR: PDF file not found: $PDF_PATH" -ForegroundColor Red
    exit 2
}

Write-Host "=== Smoke Test: Upload and Process Document ===" -ForegroundColor Cyan
Write-Host "PDF: $PDF_PATH"
Write-Host "Web URL: $WEB_URL"
Write-Host ""

# Upload document
Write-Host "Step 1: Uploading document..." -ForegroundColor Yellow
$form = @{
    file = Get-Item $PDF_PATH
}
try {
    $UPLOAD_RESPONSE = Invoke-RestMethod -Uri "$WEB_URL/api/documents/upload" `
        -Method Post `
        -Form $form `
        -ContentType "multipart/form-data"
    $UPLOAD_RESPONSE_JSON = $UPLOAD_RESPONSE | ConvertTo-Json -Depth 10
} catch {
    Write-Host "ERROR: Upload failed: $_" -ForegroundColor Red
    exit 1
}

Write-Host "Upload response: $UPLOAD_RESPONSE_JSON"
Write-Host ""

# Extract jobId
$JOB_ID = $UPLOAD_RESPONSE.jobId

if ([string]::IsNullOrWhiteSpace($JOB_ID)) {
    Write-Host "ERROR: Could not extract jobId from upload response" -ForegroundColor Red
    Write-Host "Response: $UPLOAD_RESPONSE_JSON"
    exit 1
}

Write-Host "Job ID: $JOB_ID"
Write-Host ""

# Poll status endpoint
Write-Host "Step 2: Polling status endpoint..." -ForegroundColor Yellow
$MAX_ATTEMPTS = 120  # 10 minutes max (5 second intervals)
$ATTEMPT = 0
$STATUS = ""
$FINAL_RESPONSE = $null

while ($ATTEMPT -lt $MAX_ATTEMPTS) {
    try {
        $STATUS_RESPONSE = Invoke-RestMethod -Uri "$WEB_URL/api/documents/$JOB_ID/status"
        $STATUS = $STATUS_RESPONSE.status
    } catch {
        Write-Host "ERROR: Failed to get status: $_" -ForegroundColor Red
        exit 1
    }

    Write-Host "[Attempt $($ATTEMPT + 1)/$MAX_ATTEMPTS] Status: $STATUS"

    if ($STATUS -eq "SUCCESS" -or $STATUS -eq "FAILED") {
        $FINAL_RESPONSE = $STATUS_RESPONSE
        break
    }

    Start-Sleep -Seconds 5
    $ATTEMPT++
}

Write-Host ""

if (-not $FINAL_RESPONSE) {
    Write-Host "ERROR: Timeout waiting for job to complete" -ForegroundColor Red
    Write-Host "Last status: $STATUS"
    exit 1
}

Write-Host "=== Final Status Response ===" -ForegroundColor Cyan
$FINAL_RESPONSE | ConvertTo-Json -Depth 10
Write-Host ""

if ($STATUS -eq "FAILED") {
    Write-Host "ERROR: Job failed" -ForegroundColor Red
    exit 1
}

Write-Host "✓ Smoke test passed: Job completed successfully" -ForegroundColor Green


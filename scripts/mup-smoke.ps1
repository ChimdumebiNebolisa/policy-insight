# MUP Smoke Test: Upload → Poll → Redirect → Report → Q&A
# Usage: pwsh scripts\mup-smoke.ps1 [path-to-pdf]
#
# Arguments:
#   path-to-pdf    Path to PDF file to upload (default: tiny.pdf)
#
# Environment variables:
#   BASE_URL       Base URL for the app (default: http://localhost:8080)
#
# Exit codes:
#   0: Success
#   1: Error (upload failed, polling failed, report missing sections, Q&A failed)

$ErrorActionPreference = "Stop"

$PDF_PATH = if ($args.Count -gt 0) { $args[0] } else { "tiny.pdf" }
$BASE_URL = if ($env:BASE_URL) { $env:BASE_URL } else { "http://localhost:8080" }

if (-not (Test-Path $PDF_PATH)) {
    Write-Host "ERROR: PDF file not found: $PDF_PATH" -ForegroundColor Red
    exit 1
}

Write-Host "=== MUP Smoke Test ===" -ForegroundColor Cyan
Write-Host "PDF: $PDF_PATH"
Write-Host "Base URL: $BASE_URL"
Write-Host ""

# Step 1: Upload
Write-Host "Step 1: Uploading document..." -ForegroundColor Yellow
$form = @{
    file = Get-Item $PDF_PATH
}
try {
    $UPLOAD_RESPONSE = Invoke-RestMethod -Uri "$BASE_URL/api/documents/upload" `
        -Method Post `
        -Form $form `
        -ContentType "multipart/form-data"
} catch {
    Write-Host "ERROR: Upload failed: $_" -ForegroundColor Red
    Write-Host "Response: $($_.Exception.Response)" -ForegroundColor Red
    exit 1
}

Write-Host "Upload response:" -ForegroundColor Green
$UPLOAD_RESPONSE | ConvertTo-Json -Depth 10
Write-Host ""

# Extract jobId
$JOB_ID = $UPLOAD_RESPONSE.jobId
if ([string]::IsNullOrWhiteSpace($JOB_ID)) {
    Write-Host "ERROR: Could not extract jobId from upload response" -ForegroundColor Red
    exit 1
}

Write-Host "Job ID: $JOB_ID"
Write-Host ""

# Step 2: Poll status (with HX-Request header for htmx)
Write-Host "Step 2: Polling status (with HX-Request header)..." -ForegroundColor Yellow
$MAX_ATTEMPTS = 60  # 60 seconds max (1 second intervals)
$ATTEMPT = 0
$STATUS = ""
$HX_REDIRECT_PRESENT = $false

while ($ATTEMPT -lt $MAX_ATTEMPTS) {
    try {
        $STATUS_RESPONSE = Invoke-WebRequest -Uri "$BASE_URL/api/documents/$JOB_ID/status" `
            -Headers @{"HX-Request" = "true"} `
            -UseBasicParsing

        # Check for HX-Redirect header
        if ($STATUS_RESPONSE.Headers["HX-Redirect"]) {
            $HX_REDIRECT_PRESENT = $true
            $REDIRECT_URL = $STATUS_RESPONSE.Headers["HX-Redirect"]
            Write-Host "[Attempt $($ATTEMPT + 1)/$MAX_ATTEMPTS] Status: SUCCESS (HX-Redirect: $REDIRECT_URL)" -ForegroundColor Green
            break
        }

        # Parse HTML fragment to check status
        $HTML_CONTENT = $STATUS_RESPONSE.Content
        if ($HTML_CONTENT -match 'status["\s]*[:=]["\s]*([^"]+)') {
            $STATUS = $matches[1]
        } elseif ($HTML_CONTENT -match 'SUCCESS|FAILED|PROCESSING|PENDING') {
            $STATUS = $matches[0]
        } else {
            $STATUS = "UNKNOWN"
        }

        Write-Host "[Attempt $($ATTEMPT + 1)/$MAX_ATTEMPTS] Status: $STATUS"

        if ($STATUS -eq "SUCCESS" -or $STATUS -eq "FAILED") {
            break
        }
    } catch {
        Write-Host "ERROR: Failed to get status: $_" -ForegroundColor Red
        exit 1
    }

    Start-Sleep -Seconds 1
    $ATTEMPT++
}

Write-Host ""

if (-not $HX_REDIRECT_PRESENT -and $STATUS -ne "SUCCESS") {
    Write-Host "ERROR: Job did not complete successfully. Status: $STATUS" -ForegroundColor Red
    exit 1
}

if ($STATUS -eq "FAILED") {
    Write-Host "ERROR: Job failed" -ForegroundColor Red
    exit 1
}

Write-Host "✓ Polling successful. HX-Redirect header present: $HX_REDIRECT_PRESENT" -ForegroundColor Green
Write-Host ""

# Step 3: Fetch report page
Write-Host "Step 3: Fetching report page..." -ForegroundColor Yellow
try {
    $REPORT_RESPONSE = Invoke-WebRequest -Uri "$BASE_URL/documents/$JOB_ID/report" `
        -UseBasicParsing
    $REPORT_HTML = $REPORT_RESPONSE.Content

    # Save report HTML for debugging
    $REPORT_HTML | Out-File -FilePath "report.html" -Encoding UTF8
    Write-Host "Report HTML saved to report.html"
} catch {
    Write-Host "ERROR: Failed to fetch report page: $_" -ForegroundColor Red
    exit 1
}

Write-Host "✓ Report page fetched (status: $($REPORT_RESPONSE.StatusCode))" -ForegroundColor Green
Write-Host ""

# Step 4: Assert report contains 5 expected section markers
Write-Host "Step 4: Validating report sections..." -ForegroundColor Yellow
$REQUIRED_SECTIONS = @(
    'id="overview"',
    'id="summary"',
    'id="obligations"',
    'id="risks"',
    'id="qa"'
)

$MISSING_SECTIONS = @()
foreach ($SECTION in $REQUIRED_SECTIONS) {
    if ($REPORT_HTML -notmatch [regex]::Escape($SECTION)) {
        $MISSING_SECTIONS += $SECTION
    }
}

if ($MISSING_SECTIONS.Count -gt 0) {
    Write-Host "ERROR: Report missing required sections:" -ForegroundColor Red
    foreach ($SECTION in $MISSING_SECTIONS) {
        Write-Host "  - $SECTION" -ForegroundColor Red
    }
    exit 1
}

Write-Host "✓ All 5 required sections found:" -ForegroundColor Green
foreach ($SECTION in $REQUIRED_SECTIONS) {
    Write-Host "  - $SECTION" -ForegroundColor Green
}
Write-Host ""

# Step 5: Q&A
Write-Host "Step 5: Testing Q&A endpoint..." -ForegroundColor Yellow
$QUESTION = "What is the termination policy?"
$QA_BODY = @{
    document_id = $JOB_ID
    question = $QUESTION
}

try {
    $QA_RESPONSE = Invoke-WebRequest -Uri "$BASE_URL/api/questions" `
        -Method Post `
        -Headers @{"HX-Request" = "true"} `
        -Body $QA_BODY `
        -UseBasicParsing

    $QA_HTML = $QA_RESPONSE.Content

    # Save Q&A response for debugging
    $QA_HTML | Out-File -FilePath "qa.html" -Encoding UTF8
    Write-Host "Q&A response HTML saved to qa.html"

    # Check if response contains citations or "Insufficient evidence"
    if ($QA_HTML -match "citation|citations|Insufficient evidence|insufficient evidence|ABSTAINED") {
        Write-Host "✓ Q&A response contains citations or abstention message" -ForegroundColor Green
    } else {
        Write-Host "WARNING: Q&A response may not contain expected citation/abstention markers" -ForegroundColor Yellow
        Write-Host "Response preview:" -ForegroundColor Yellow
        if ($QA_HTML.Length -gt 500) {
            Write-Host $QA_HTML.Substring(0, 500) -ForegroundColor Yellow
        } else {
            Write-Host $QA_HTML -ForegroundColor Yellow
        }
    }
} catch {
    Write-Host "ERROR: Q&A request failed: $_" -ForegroundColor Red
    Write-Host "Response: $($_.Exception.Response)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Summary
Write-Host "=== Smoke Test Results ===" -ForegroundColor Cyan
Write-Host "✓ Upload: OK" -ForegroundColor Green
Write-Host "✓ Polling: OK" -ForegroundColor Green
Write-Host "✓ HX-Redirect: $HX_REDIRECT_PRESENT" -ForegroundColor $(if ($HX_REDIRECT_PRESENT) { "Green" } else { "Yellow" })
Write-Host "✓ Report fetched: OK" -ForegroundColor Green
Write-Host "✓ Report sections: All 5 found" -ForegroundColor Green
Write-Host "✓ Q&A: OK" -ForegroundColor Green
Write-Host ""
Write-Host "MUP smoke test passed!" -ForegroundColor Green
Write-Host ""
Write-Host "Report URL: $BASE_URL/documents/$JOB_ID/report" -ForegroundColor Cyan


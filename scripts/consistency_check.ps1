# Consistency check script to prevent Pub/Sub subscription name drift
# Exits non-zero if both subscription names are found in the repo
#
# Usage: pwsh scripts\consistency_check.ps1
#
# This script greps for both subscription name variants and fails if both appear.
# The canonical subscription name is: policyinsight-analysis-sub

$ErrorActionPreference = "Stop"

$CANONICAL_NAME = "policyinsight-analysis-sub"
$OLD_NAME = "policyinsight-analysis-topic-sub"

Write-Host "=== Checking for Pub/Sub Subscription Name Consistency ===" -ForegroundColor Cyan
Write-Host "Canonical name: $CANONICAL_NAME"
Write-Host "Old name (should not appear): $OLD_NAME"
Write-Host ""

# Search for both names in the repo (excluding .git)
$FOUND_CANONICAL = 0
$FOUND_OLD = 0

Get-ChildItem -Recurse -File -Exclude "consistency_check.ps1" | Where-Object {
    $_.FullName -notmatch "\.git"
} | ForEach-Object {
    $content = Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue
    if ($content) {
        $canonicalMatches = ([regex]::Matches($content, [regex]::Escape($CANONICAL_NAME))).Count
        $oldMatches = ([regex]::Matches($content, [regex]::Escape($OLD_NAME))).Count
        if ($canonicalMatches -gt 0) {
            $script:FOUND_CANONICAL += $canonicalMatches
        }
        if ($oldMatches -gt 0) {
            $script:FOUND_OLD += $oldMatches
            Write-Host "Found old name in: $($_.FullName)" -ForegroundColor Yellow
        }
    }
}

Write-Host "Found canonical name ($CANONICAL_NAME): $FOUND_CANONICAL occurrences"
Write-Host "Found old name ($OLD_NAME): $FOUND_OLD occurrences"
Write-Host ""

if ($FOUND_OLD -gt 0) {
    Write-Host "ERROR: Found $FOUND_OLD occurrence(s) of old subscription name: $OLD_NAME" -ForegroundColor Red
    Write-Host "All references should use the canonical name: $CANONICAL_NAME"
    exit 1
}

Write-Host "✓ No old subscription name found. All references use canonical name: $CANONICAL_NAME" -ForegroundColor Green
Write-Host "=== Consistency Check Passed ===" -ForegroundColor Cyan


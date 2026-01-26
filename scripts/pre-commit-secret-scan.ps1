# Pre-commit hook to scan for secrets using gitleaks
# Install gitleaks: https://github.com/gitleaks/gitleaks#installation
# Usage: Copy this to .git/hooks/pre-commit or run manually before commits

$ErrorActionPreference = "Stop"

Write-Host "🔍 Running secret scan with gitleaks..." -ForegroundColor Cyan

# Check if gitleaks is installed
$gitleaksPath = Get-Command gitleaks -ErrorAction SilentlyContinue
if (-not $gitleaksPath) {
    Write-Host "⚠️  Warning: gitleaks is not installed. Skipping secret scan." -ForegroundColor Yellow
    Write-Host "   Install from: https://github.com/gitleaks/gitleaks#installation" -ForegroundColor Yellow
    Write-Host "   Or use: choco install gitleaks (Windows) / brew install gitleaks (macOS)" -ForegroundColor Yellow
    exit 0
}

# Run gitleaks on staged files
try {
    $headExists = git rev-parse --verify HEAD 2>&1
    if ($LASTEXITCODE -eq 0) {
        # Compare against HEAD
        gitleaks detect --source . --no-banner --verbose
    } else {
        # First commit - scan all files
        gitleaks detect --source . --no-banner --verbose
    }

    if ($LASTEXITCODE -eq 1) {
        Write-Host ""
        Write-Host "❌ Secret scan failed! Potential secrets detected." -ForegroundColor Red
        Write-Host "   Please remove any hardcoded secrets and use environment variables instead." -ForegroundColor Yellow
        Write-Host "   See README.md#troubleshooting for guidance." -ForegroundColor Yellow
        exit 1
    }

    Write-Host "✅ Secret scan passed - no secrets detected" -ForegroundColor Green
    exit 0
} catch {
    Write-Host "❌ Error running gitleaks: $_" -ForegroundColor Red
    exit 1
}


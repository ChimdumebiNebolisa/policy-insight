# PowerShell wrapper script for export-assets.py
# Works without grep dependency

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)
$PythonScript = Join-Path $ScriptDir "export-assets.py"

# Check for required environment variables
if (-not $env:DD_API_KEY) {
    Write-Host "❌ Error: DD_API_KEY environment variable is not set" -ForegroundColor Red
    Write-Host "   Set it with: `$env:DD_API_KEY = 'your-api-key'" -ForegroundColor Yellow
    exit 1
}

if (-not $env:DD_APP_KEY) {
    Write-Host "❌ Error: DD_APP_KEY environment variable is not set" -ForegroundColor Red
    Write-Host "   Set it with: `$env:DD_APP_KEY = 'your-app-key'" -ForegroundColor Yellow
    exit 1
}

# DD_SITE is optional (defaults to datadoghq.com in the script)
if ($env:DD_SITE) {
    Write-Host "Using DD_SITE: $env:DD_SITE" -ForegroundColor Cyan
} else {
    Write-Host "Using default DD_SITE: datadoghq.com" -ForegroundColor Cyan
}

# Check if Python 3 is available
$pythonCmd = Get-Command python3 -ErrorAction SilentlyContinue
if (-not $pythonCmd) {
    $pythonCmd = Get-Command python -ErrorAction SilentlyContinue
    if (-not $pythonCmd) {
        Write-Host "❌ Error: python3 or python is not installed" -ForegroundColor Red
        exit 1
    }
}

# Check if requests library is available
$checkRequests = & $pythonCmd.Name -c "import requests" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "⚠️  Warning: requests library not found. Installing..." -ForegroundColor Yellow
    & $pythonCmd.Name -m pip install requests --user
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Error: Failed to install requests library" -ForegroundColor Red
        exit 1
    }
}

# Run the Python script
Set-Location $ProjectRoot
& $pythonCmd.Name $PythonScript $args


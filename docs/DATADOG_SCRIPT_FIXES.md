# Datadog Script Remediation - Implementation Summary

This document summarizes the fixes applied to the Datadog apply/export scripts to address error handling, validation, and misleading success messages.

## Key Corrections Made

### 1. Removed Scope Introspection Claims

**Original Plan Issue**: Claimed ability to "check scopes" via API introspection.

**Corrected Approach**:
- Scripts detect 403 Forbidden responses and print required scopes as guidance
- No attempt to introspect actual scopes (Datadog API doesn't support this reliably)
- Clear error messages list required scopes: `monitors_read/write`, `dashboards_read/write`, `slo_read/write`

### 2. Added Robust HTTP Helper Module

**New File**: `scripts/datadog/dd_http.py`

**Features**:
- `request_json()` function with:
  - Request timeouts (20s default)
  - Retries with exponential backoff for 429 and 5xx errors
  - Content-type validation before JSON parsing
  - Comprehensive error diagnostics (method, URL, status, content-type, body preview)
  - Safe JSON parsing with error messages including response preview

### 3. Preflight Validation Module

**New File**: `scripts/datadog/validate_keys.py`

**Functions**:
- `validate_api_key()`: Calls `/api/v1/validate`, fails fast on invalid keys
- `validate_app_key()`: Tests read endpoint (monitors list), detects 403 and provides actionable error with required scopes

**No scope introspection** - only detects 403 and prints required scopes as guidance.

### 4. Fixed apply-assets.py

**Changes**:
- Uses `request_json()` everywhere (no raw `requests` calls)
- Tracks outcomes: `created`, `updated`, `failed` counts per asset type
- Returns success/failure status from apply functions
- Prints summary with counts per asset type
- Only prints "✅ All assets applied successfully!" when zero failures
- Exits with code 1 if ANY failures occurred
- Preflight validation before applying assets
- Metrics sanity check (best-effort, non-blocking) warns about potentially missing metrics

### 5. Fixed export-assets.py

**Changes**:
- Uses `request_json()` everywhere
- Preflight validation before exporting
- Custom `DatadogPermissionError` exception for 403 errors with actionable messages
- Only writes export files after successful fetch and validation
- Exits non-zero if ANY export step fails

### 6. Fixed PowerShell Wrappers

**Changes**:
- Both `apply-assets.ps1` and `export-assets.ps1` check `$LASTEXITCODE` after Python script execution
- Exit with non-zero code if Python script fails
- Prevents pipelines from continuing after failures

### 7. Updated Documentation

**File**: `docs/OBSERVABILITY.md`

**Added**:
- "Runbook: API Key Validation" section with PowerShell commands using `Invoke-RestMethod`/`Invoke-WebRequest`
- Validation commands for API key and Application key
- Troubleshooting guide for 403 errors with required scopes
- Verification checklist with expected outputs
- Implementation notes section explaining script features

## Files Changed

1. **New**: `scripts/datadog/dd_http.py` - Shared HTTP helper
2. **New**: `scripts/datadog/validate_keys.py` - Preflight validation
3. **Modified**: `scripts/datadog/apply-assets.py` - Complete rewrite with error tracking
4. **Modified**: `scripts/datadog/export-assets.py` - Complete rewrite with proper error handling
5. **Modified**: `scripts/datadog/apply-assets.ps1` - Exit code propagation
6. **Modified**: `scripts/datadog/export-assets.ps1` - Exit code propagation
7. **Modified**: `docs/OBSERVABILITY.md` - Added Runbook section

## Verification

All Python files compile successfully with no syntax errors. The scripts are ready for testing with valid Datadog credentials.

## Key Design Decisions

1. **No scope introspection**: Datadog API doesn't reliably support checking actual scopes, so we detect 403 and provide required scopes as guidance
2. **Best-effort metrics check**: Metrics sanity check is informational only and doesn't block script execution
3. **Comprehensive diagnostics**: All errors include HTTP method, URL, status code, content-type, and body preview (first 300 chars)
4. **Hard stop on failures**: Scripts exit non-zero on any failure, preventing misleading success messages
5. **PowerShell-first**: Documentation uses PowerShell `Invoke-RestMethod`/`Invoke-WebRequest` instead of `curl` for Windows compatibility


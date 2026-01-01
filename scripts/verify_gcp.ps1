# Verify GCP configuration for demo readiness
# Exits non-zero if subscription configuration doesn't match expected values
#
# Usage: pwsh scripts\verify_gcp.ps1
# Environment variables (optional):
#   GCP_PROJECT_ID: GCP project ID (default: policy-insight)
#   GCP_REGION: GCP region (default: us-central1)
#   PUBSUB_SUBSCRIPTION_NAME: Pub/Sub subscription name (default: policyinsight-analysis-sub)
#   WORKER_SERVICE: Cloud Run worker service name (default: policyinsight-worker)
#
# Example:
#   pwsh scripts\verify_gcp.ps1
#   $env:GCP_PROJECT_ID="my-project"; pwsh scripts\verify_gcp.ps1
#   $env:PUBSUB_SUBSCRIPTION_NAME="my-sub"; pwsh scripts\verify_gcp.ps1

$ErrorActionPreference = "Stop"

$PROJECT_ID = if ($env:GCP_PROJECT_ID) { $env:GCP_PROJECT_ID } else { "policy-insight" }
$REGION = if ($env:GCP_REGION) { $env:GCP_REGION } else { "us-central1" }
$SUBSCRIPTION_NAME = if ($env:PUBSUB_SUBSCRIPTION_NAME) { $env:PUBSUB_SUBSCRIPTION_NAME } else { "policyinsight-analysis-sub" }
$WORKER_SERVICE = if ($env:WORKER_SERVICE) { $env:WORKER_SERVICE } else { "policyinsight-worker" }

Write-Host "=== Verifying Pub/Sub Subscription Configuration ===" -ForegroundColor Cyan
Write-Host "Subscription: $SUBSCRIPTION_NAME"
Write-Host ""

# Get worker URL to construct expected push endpoint
try {
    $WORKER_URL_STR = gcloud run services describe $WORKER_SERVICE `
        --region=$REGION `
        --format="value(status.url)" 2>&1 | Out-String
    $WORKER_URL = $WORKER_URL_STR.Trim()
} catch {
    $WORKER_URL = ""
}

$EXPECTED_PUSH_ENDPOINT = "$WORKER_URL/internal/pubsub"
$EXPECTED_OIDC_SA = "policyinsight-worker@${PROJECT_ID}.iam.gserviceaccount.com"
$EXPECTED_OIDC_AUDIENCE = $WORKER_URL

# Get subscription details
try {
    $ACK_DEADLINE = gcloud pubsub subscriptions describe $SUBSCRIPTION_NAME `
        --format="value(ackDeadlineSeconds)" 2>&1 | Out-String
    $ACK_DEADLINE = $ACK_DEADLINE.Trim()
} catch {
    $ACK_DEADLINE = ""
}

try {
    $PUSH_ENDPOINT = gcloud pubsub subscriptions describe $SUBSCRIPTION_NAME `
        --format="value(pushConfig.pushEndpoint)" 2>&1 | Out-String
    $PUSH_ENDPOINT = $PUSH_ENDPOINT.Trim()
} catch {
    $PUSH_ENDPOINT = ""
}

try {
    $OIDC_SA = gcloud pubsub subscriptions describe $SUBSCRIPTION_NAME `
        --format="value(pushConfig.oidcToken.serviceAccountEmail)" 2>&1 | Out-String
    $OIDC_SA = $OIDC_SA.Trim()
} catch {
    $OIDC_SA = ""
}

try {
    $OIDC_AUDIENCE = gcloud pubsub subscriptions describe $SUBSCRIPTION_NAME `
        --format="value(pushConfig.oidcToken.audience)" 2>&1 | Out-String
    $OIDC_AUDIENCE = $OIDC_AUDIENCE.Trim()
} catch {
    $OIDC_AUDIENCE = ""
}

# Verify ackDeadlineSeconds
if ([string]::IsNullOrWhiteSpace($ACK_DEADLINE)) {
    Write-Host "ERROR: Could not read ackDeadlineSeconds for subscription $SUBSCRIPTION_NAME" -ForegroundColor Red
    exit 1
}

Write-Host "Checking ackDeadlineSeconds..."
Write-Host "  Expected: 600"
Write-Host "  Got:      $ACK_DEADLINE"
if ($ACK_DEADLINE -ne "600") {
    Write-Host "ERROR: ackDeadlineSeconds mismatch. Expected 600, got $ACK_DEADLINE" -ForegroundColor Red
    Write-Host "Fix with: gcloud pubsub subscriptions update $SUBSCRIPTION_NAME --ack-deadline=600"
    exit 1
}
Write-Host "✓ ackDeadlineSeconds is correctly set to 600" -ForegroundColor Green
Write-Host ""

# Verify push endpoint
if ([string]::IsNullOrWhiteSpace($PUSH_ENDPOINT)) {
    Write-Host "ERROR: Could not read pushEndpoint for subscription $SUBSCRIPTION_NAME" -ForegroundColor Red
    exit 1
}

Write-Host "Checking pushEndpoint..."
Write-Host "  Expected: $EXPECTED_PUSH_ENDPOINT"
Write-Host "  Got:      $PUSH_ENDPOINT"
if ($PUSH_ENDPOINT -ne $EXPECTED_PUSH_ENDPOINT) {
    Write-Host "ERROR: pushEndpoint mismatch. Expected $EXPECTED_PUSH_ENDPOINT, got $PUSH_ENDPOINT" -ForegroundColor Red
    exit 1
}
if (-not $PUSH_ENDPOINT.EndsWith("/internal/pubsub")) {
    Write-Host "ERROR: pushEndpoint must end with /internal/pubsub. Got: $PUSH_ENDPOINT" -ForegroundColor Red
    exit 1
}
Write-Host "✓ pushEndpoint is correctly set to $EXPECTED_PUSH_ENDPOINT" -ForegroundColor Green
Write-Host ""

# Verify OIDC service account (if present)
if ($OIDC_SA) {
    Write-Host "Checking oidcToken.serviceAccountEmail..."
    Write-Host "  Expected: $EXPECTED_OIDC_SA"
    Write-Host "  Got:      $OIDC_SA"
    if ($OIDC_SA -ne $EXPECTED_OIDC_SA) {
        Write-Host "ERROR: oidcToken.serviceAccountEmail mismatch. Expected $EXPECTED_OIDC_SA, got $OIDC_SA" -ForegroundColor Red
        exit 1
    }
    Write-Host "✓ oidcToken.serviceAccountEmail is correctly set to $EXPECTED_OIDC_SA" -ForegroundColor Green
    Write-Host ""
}

# Verify OIDC audience (if present)
if ($OIDC_AUDIENCE) {
    Write-Host "Checking oidcToken.audience..."
    Write-Host "  Expected: $EXPECTED_OIDC_AUDIENCE"
    Write-Host "  Got:      $OIDC_AUDIENCE"
    if ($OIDC_AUDIENCE -ne $EXPECTED_OIDC_AUDIENCE) {
        Write-Host "ERROR: oidcToken.audience mismatch. Expected $EXPECTED_OIDC_AUDIENCE, got $OIDC_AUDIENCE" -ForegroundColor Red
        exit 1
    }
    Write-Host "✓ oidcToken.audience is correctly set to $EXPECTED_OIDC_AUDIENCE" -ForegroundColor Green
    Write-Host ""
}

Write-Host "=== Verifying Cloud Run Worker Timeout ===" -ForegroundColor Cyan
Write-Host "Service: $WORKER_SERVICE"
Write-Host ""

# Get worker timeout
try {
    $WORKER_TIMEOUT_STR = gcloud run services describe $WORKER_SERVICE `
        --region=$REGION `
        --format="value(spec.template.spec.timeoutSeconds)" 2>&1 | Out-String
    $WORKER_TIMEOUT_STR = $WORKER_TIMEOUT_STR.Trim()
    $WORKER_TIMEOUT = [int]$WORKER_TIMEOUT_STR
} catch {
    $WORKER_TIMEOUT = $null
}

Write-Host "timeoutSeconds: $WORKER_TIMEOUT_STR"
Write-Host ""

if (-not $WORKER_TIMEOUT) {
    Write-Host "ERROR: Could not read timeoutSeconds for service $WORKER_SERVICE" -ForegroundColor Red
    exit 1
}

if ($WORKER_TIMEOUT -lt 600) {
    Write-Host "ERROR: Worker timeoutSeconds is $WORKER_TIMEOUT, expected >= 600" -ForegroundColor Red
    Write-Host "Fix with: gcloud run services update $WORKER_SERVICE --timeout=600 --region=$REGION"
    exit 1
}

Write-Host "✓ Worker timeoutSeconds is >= 600" -ForegroundColor Green
Write-Host ""

Write-Host "=== Verification Complete ===" -ForegroundColor Cyan
Write-Host "All checks passed!" -ForegroundColor Green


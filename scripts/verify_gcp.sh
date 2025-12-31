#!/bin/bash
# Verify GCP configuration for demo readiness
# Exits non-zero if subscription configuration doesn't match expected values
#
# Usage: bash scripts/verify_gcp.sh
# Environment variables (optional):
#   GCP_PROJECT_ID: GCP project ID (default: policy-insight)
#   GCP_REGION: GCP region (default: us-central1)
#   PUBSUB_SUBSCRIPTION_NAME: Pub/Sub subscription name (default: policyinsight-analysis-sub)
#   WORKER_SERVICE: Cloud Run worker service name (default: policyinsight-worker)
#
# Example:
#   bash scripts/verify_gcp.sh
#   GCP_PROJECT_ID=my-project bash scripts/verify_gcp.sh
#   PUBSUB_SUBSCRIPTION_NAME=my-sub bash scripts/verify_gcp.sh

set -e

PROJECT_ID="${GCP_PROJECT_ID:-policy-insight}"
REGION="${GCP_REGION:-us-central1}"
SUBSCRIPTION_NAME="${PUBSUB_SUBSCRIPTION_NAME:-policyinsight-analysis-sub}"
WORKER_SERVICE="${WORKER_SERVICE:-policyinsight-worker}"

echo "=== Verifying Pub/Sub Subscription Configuration ==="
echo "Subscription: $SUBSCRIPTION_NAME"
echo ""

# Get worker URL to construct expected push endpoint
WORKER_URL=$(gcloud run services describe "$WORKER_SERVICE" \
  --region="$REGION" \
  --format="value(status.url)" 2>/dev/null || echo "")
EXPECTED_PUSH_ENDPOINT="${WORKER_URL}/internal/pubsub"
EXPECTED_OIDC_SA="policyinsight-worker@${PROJECT_ID}.iam.gserviceaccount.com"
EXPECTED_OIDC_AUDIENCE="$WORKER_URL"

# Get subscription details
ACK_DEADLINE=$(gcloud pubsub subscriptions describe "$SUBSCRIPTION_NAME" \
  --format="value(ackDeadlineSeconds)" 2>/dev/null || echo "")

PUSH_ENDPOINT=$(gcloud pubsub subscriptions describe "$SUBSCRIPTION_NAME" \
  --format="value(pushConfig.pushEndpoint)" 2>/dev/null || echo "")

OIDC_SA=$(gcloud pubsub subscriptions describe "$SUBSCRIPTION_NAME" \
  --format="value(pushConfig.oidcToken.serviceAccountEmail)" 2>/dev/null || echo "")

OIDC_AUDIENCE=$(gcloud pubsub subscriptions describe "$SUBSCRIPTION_NAME" \
  --format="value(pushConfig.oidcToken.audience)" 2>/dev/null || echo "")

# Verify ackDeadlineSeconds
if [ -z "$ACK_DEADLINE" ]; then
  echo "ERROR: Could not read ackDeadlineSeconds for subscription $SUBSCRIPTION_NAME"
  exit 1
fi

echo "Checking ackDeadlineSeconds..."
echo "  Expected: 600"
echo "  Got:      $ACK_DEADLINE"
if [ "$ACK_DEADLINE" != "600" ]; then
  echo "ERROR: ackDeadlineSeconds mismatch. Expected 600, got $ACK_DEADLINE"
  echo "Fix with: gcloud pubsub subscriptions update $SUBSCRIPTION_NAME --ack-deadline=600"
  exit 1
fi
echo "✓ ackDeadlineSeconds is correctly set to 600"
echo ""

# Verify push endpoint
if [ -z "$PUSH_ENDPOINT" ]; then
  echo "ERROR: Could not read pushEndpoint for subscription $SUBSCRIPTION_NAME"
  exit 1
fi

echo "Checking pushEndpoint..."
echo "  Expected: $EXPECTED_PUSH_ENDPOINT"
echo "  Got:      $PUSH_ENDPOINT"
if [ "$PUSH_ENDPOINT" != "$EXPECTED_PUSH_ENDPOINT" ]; then
  echo "ERROR: pushEndpoint mismatch. Expected $EXPECTED_PUSH_ENDPOINT, got $PUSH_ENDPOINT"
  exit 1
fi
if [[ ! "$PUSH_ENDPOINT" =~ /internal/pubsub$ ]]; then
  echo "ERROR: pushEndpoint must end with /internal/pubsub. Got: $PUSH_ENDPOINT"
  exit 1
fi
echo "✓ pushEndpoint is correctly set to $EXPECTED_PUSH_ENDPOINT"
echo ""

# Verify OIDC service account (if present)
if [ -n "$OIDC_SA" ]; then
  echo "Checking oidcToken.serviceAccountEmail..."
  echo "  Expected: $EXPECTED_OIDC_SA"
  echo "  Got:      $OIDC_SA"
  if [ "$OIDC_SA" != "$EXPECTED_OIDC_SA" ]; then
    echo "ERROR: oidcToken.serviceAccountEmail mismatch. Expected $EXPECTED_OIDC_SA, got $OIDC_SA"
    exit 1
  fi
  echo "✓ oidcToken.serviceAccountEmail is correctly set to $EXPECTED_OIDC_SA"
  echo ""
fi

# Verify OIDC audience (if present)
if [ -n "$OIDC_AUDIENCE" ]; then
  echo "Checking oidcToken.audience..."
  echo "  Expected: $EXPECTED_OIDC_AUDIENCE"
  echo "  Got:      $OIDC_AUDIENCE"
  if [ "$OIDC_AUDIENCE" != "$EXPECTED_OIDC_AUDIENCE" ]; then
    echo "ERROR: oidcToken.audience mismatch. Expected $EXPECTED_OIDC_AUDIENCE, got $OIDC_AUDIENCE"
    exit 1
  fi
  echo "✓ oidcToken.audience is correctly set to $EXPECTED_OIDC_AUDIENCE"
  echo ""
fi

echo "=== Verifying Cloud Run Worker Timeout ==="
echo "Service: $WORKER_SERVICE"
echo ""

# Get worker timeout
WORKER_TIMEOUT=$(gcloud run services describe "$WORKER_SERVICE" \
  --region="$REGION" \
  --format="value(spec.template.spec.timeoutSeconds)" 2>/dev/null || echo "")

echo "timeoutSeconds: $WORKER_TIMEOUT"
echo ""

if [ -z "$WORKER_TIMEOUT" ]; then
  echo "ERROR: Could not read timeoutSeconds for service $WORKER_SERVICE"
  exit 1
fi

# Convert to integer for comparison (bash doesn't support float comparison easily, but timeout is always integer)
if [ "$WORKER_TIMEOUT" -lt 600 ]; then
  echo "ERROR: Worker timeoutSeconds is $WORKER_TIMEOUT, expected >= 600"
  echo "Fix with: gcloud run services update $WORKER_SERVICE --timeout=600 --region=$REGION"
  exit 1
fi

echo "✓ Worker timeoutSeconds is >= 600"
echo ""

echo "=== Verification Complete ==="
echo "All checks passed!"


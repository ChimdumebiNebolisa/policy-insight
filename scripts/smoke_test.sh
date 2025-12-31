#!/bin/bash
# Smoke test: Upload a PDF, poll status until SUCCESS/FAILED
# Usage: bash scripts/smoke_test.sh [path-to-pdf]
#   path-to-pdf: Path to PDF file to upload (required)
#   WEB_URL: Environment variable for web service URL (default: https://policyinsight-web-828177954618.us-central1.run.app)
#
# Example:
#   bash scripts/smoke_test.sh tiny.pdf
#   WEB_URL=https://custom-url.run.app bash scripts/smoke_test.sh test.pdf
#
# Exit codes:
#   0: Success
#   1: Error (upload failed, job failed, timeout)
#   2: Usage error (missing PDF path)

set -e

if [ $# -eq 0 ]; then
  echo "Usage: bash scripts/smoke_test.sh <path-to-pdf>"
  echo ""
  echo "Arguments:"
  echo "  path-to-pdf    Path to PDF file to upload (required)"
  echo ""
  echo "Environment variables:"
  echo "  WEB_URL        Web service URL (default: https://policyinsight-web-828177954618.us-central1.run.app)"
  echo ""
  echo "Example:"
  echo "  bash scripts/smoke_test.sh tiny.pdf"
  exit 2
fi

PDF_PATH="$1"
WEB_URL="${WEB_URL:-https://policyinsight-web-828177954618.us-central1.run.app}"

if [ ! -f "$PDF_PATH" ]; then
  echo "ERROR: PDF file not found: $PDF_PATH"
  exit 2
fi

echo "=== Smoke Test: Upload and Process Document ==="
echo "PDF: $PDF_PATH"
echo "Web URL: $WEB_URL"
echo ""

# Upload document
echo "Step 1: Uploading document..."
UPLOAD_RESPONSE=$(curl -s -X POST "$WEB_URL/api/documents/upload" \
  -F "file=@$PDF_PATH" \
  -H "Content-Type: multipart/form-data")

echo "Upload response: $UPLOAD_RESPONSE"
echo ""

# Extract jobId from response (assuming JSON format: {"jobId":"...",...})
JOB_ID=$(echo "$UPLOAD_RESPONSE" | grep -o '"jobId":"[^"]*"' | cut -d'"' -f4 || echo "")

if [ -z "$JOB_ID" ]; then
  echo "ERROR: Could not extract jobId from upload response"
  echo "Response: $UPLOAD_RESPONSE"
  exit 1
fi

echo "Job ID: $JOB_ID"
echo ""

# Poll status endpoint
echo "Step 2: Polling status endpoint..."
MAX_ATTEMPTS=120  # 10 minutes max (5 second intervals)
ATTEMPT=0
STATUS=""
FINAL_RESPONSE=""

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
  STATUS_RESPONSE=$(curl -s "$WEB_URL/api/documents/$JOB_ID/status")
  STATUS=$(echo "$STATUS_RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || echo "")

  echo "[Attempt $((ATTEMPT + 1))/$MAX_ATTEMPTS] Status: $STATUS"

  if [ "$STATUS" = "SUCCESS" ] || [ "$STATUS" = "FAILED" ]; then
    FINAL_RESPONSE="$STATUS_RESPONSE"
    break
  fi

  sleep 5
  ATTEMPT=$((ATTEMPT + 1))
done

echo ""

if [ -z "$FINAL_RESPONSE" ]; then
  echo "ERROR: Timeout waiting for job to complete"
  echo "Last status: $STATUS"
  exit 1
fi

echo "=== Final Status Response ==="
echo "$FINAL_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$FINAL_RESPONSE"
echo ""

if [ "$STATUS" = "FAILED" ]; then
  echo "ERROR: Job failed"
  exit 1
fi

echo "✓ Smoke test passed: Job completed successfully"


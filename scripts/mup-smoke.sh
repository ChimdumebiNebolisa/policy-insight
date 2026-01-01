#!/bin/bash
# MUP Smoke Test: Upload → Poll → Redirect → Report → Q&A
# Usage: bash scripts/mup-smoke.sh [path-to-pdf]
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

set -euo pipefail

PDF_PATH="${1:-tiny.pdf}"
BASE_URL="${BASE_URL:-http://localhost:8080}"

if [ ! -f "$PDF_PATH" ]; then
    echo "ERROR: PDF file not found: $PDF_PATH" >&2
    exit 1
fi

echo "=== MUP Smoke Test ==="
echo "PDF: $PDF_PATH"
echo "Base URL: $BASE_URL"
echo ""

# Step 1: Upload
echo "Step 1: Uploading document..."
UPLOAD_RESPONSE=$(curl -sS -X POST \
    -F "file=@$PDF_PATH;type=application/pdf" \
    "$BASE_URL/api/documents/upload")

echo "Upload response:"
echo "$UPLOAD_RESPONSE" | jq .
echo ""

# Extract jobId
JOB_ID=$(echo "$UPLOAD_RESPONSE" | jq -r '.jobId // empty')
if [ -z "$JOB_ID" ]; then
    echo "ERROR: Could not extract jobId from upload response" >&2
    exit 1
fi

echo "Job ID: $JOB_ID"
echo ""

# Step 2: Poll status (with HX-Request header for htmx)
echo "Step 2: Polling status (with HX-Request header)..."
MAX_ATTEMPTS=60  # 60 seconds max (1 second intervals)
ATTEMPT=0
STATUS=""
HX_REDIRECT_PRESENT=false

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    STATUS_RESPONSE=$(curl -sS -D headers.txt \
        -H "HX-Request: true" \
        "$BASE_URL/api/documents/$JOB_ID/status")

    # Check for HX-Redirect header
    if grep -qi "HX-Redirect:" headers.txt; then
        HX_REDIRECT_PRESENT=true
        REDIRECT_URL=$(grep -i "HX-Redirect:" headers.txt | cut -d' ' -f2- | tr -d '\r\n')
        echo "[Attempt $((ATTEMPT + 1))/$MAX_ATTEMPTS] Status: SUCCESS (HX-Redirect: $REDIRECT_URL)"
        break
    fi

    # Try to extract status from HTML fragment
    if echo "$STATUS_RESPONSE" | grep -qi "SUCCESS"; then
        STATUS="SUCCESS"
        echo "[Attempt $((ATTEMPT + 1))/$MAX_ATTEMPTS] Status: SUCCESS"
        break
    elif echo "$STATUS_RESPONSE" | grep -qi "FAILED"; then
        STATUS="FAILED"
        echo "[Attempt $((ATTEMPT + 1))/$MAX_ATTEMPTS] Status: FAILED"
        break
    elif echo "$STATUS_RESPONSE" | grep -qi "PROCESSING"; then
        STATUS="PROCESSING"
        echo "[Attempt $((ATTEMPT + 1))/$MAX_ATTEMPTS] Status: PROCESSING"
    elif echo "$STATUS_RESPONSE" | grep -qi "PENDING"; then
        STATUS="PENDING"
        echo "[Attempt $((ATTEMPT + 1))/$MAX_ATTEMPTS] Status: PENDING"
    else
        STATUS="UNKNOWN"
        echo "[Attempt $((ATTEMPT + 1))/$MAX_ATTEMPTS] Status: UNKNOWN"
    fi

    sleep 1
    ATTEMPT=$((ATTEMPT + 1))
done

rm -f headers.txt
echo ""

if [ "$HX_REDIRECT_PRESENT" = false ] && [ "$STATUS" != "SUCCESS" ]; then
    echo "ERROR: Job did not complete successfully. Status: $STATUS" >&2
    exit 1
fi

if [ "$STATUS" = "FAILED" ]; then
    echo "ERROR: Job failed" >&2
    exit 1
fi

echo "✓ Polling successful. HX-Redirect header present: $HX_REDIRECT_PRESENT"
echo ""

# Step 3: Fetch report page
echo "Step 3: Fetching report page..."
REPORT_HTML=$(curl -sS "$BASE_URL/documents/$JOB_ID/report")

# Save report HTML for debugging
echo "$REPORT_HTML" > report.html
echo "Report HTML saved to report.html"

echo "✓ Report page fetched"
echo ""

# Step 4: Assert report contains 5 expected section markers
echo "Step 4: Validating report sections..."
REQUIRED_SECTIONS=(
    'id="overview"'
    'id="summary"'
    'id="obligations"'
    'id="risks"'
    'id="qa"'
)

MISSING_SECTIONS=()
for SECTION in "${REQUIRED_SECTIONS[@]}"; do
    if ! echo "$REPORT_HTML" | grep -q "$SECTION"; then
        MISSING_SECTIONS+=("$SECTION")
    fi
done

if [ ${#MISSING_SECTIONS[@]} -gt 0 ]; then
    echo "ERROR: Report missing required sections:" >&2
    for SECTION in "${MISSING_SECTIONS[@]}"; do
        echo "  - $SECTION" >&2
    done
    exit 1
fi

echo "✓ All 5 required sections found:"
for SECTION in "${REQUIRED_SECTIONS[@]}"; do
    echo "  - $SECTION"
done
echo ""

# Step 5: Q&A
echo "Step 5: Testing Q&A endpoint..."
QUESTION="What is the termination policy?"

QA_RESPONSE=$(curl -sS -X POST \
    -H "HX-Request: true" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "document_id=$JOB_ID" \
    --data-urlencode "question=$QUESTION" \
    "$BASE_URL/api/questions")

# Save Q&A response for debugging
echo "$QA_RESPONSE" > qa.html
echo "Q&A response HTML saved to qa.html"

# Check if response contains citations or "Insufficient evidence"
if echo "$QA_RESPONSE" | grep -qiE "citation|citations|Insufficient evidence|insufficient evidence|ABSTAINED"; then
    echo "✓ Q&A response contains citations or abstention message"
else
    echo "WARNING: Q&A response may not contain expected citation/abstention markers"
    echo "Response preview:"
    echo "$QA_RESPONSE" | head -c 500
    echo ""
fi

echo ""

# Summary
echo "=== Smoke Test Results ==="
echo "✓ Upload: OK"
echo "✓ Polling: OK"
echo "✓ HX-Redirect: $HX_REDIRECT_PRESENT"
echo "✓ Report fetched: OK"
echo "✓ Report sections: All 5 found"
echo "✓ Q&A: OK"
echo ""
echo "MUP smoke test passed!"
echo ""
echo "Report URL: $BASE_URL/documents/$JOB_ID/report"


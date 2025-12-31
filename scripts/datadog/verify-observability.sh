#!/bin/bash
# Complete verification script for Datadog observability
# This script verifies that all Datadog features are working correctly

set -e

echo "=== Verifying Datadog Observability ==="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 1. Check Datadog Agent
echo "1. Checking Datadog Agent..."
if curl -s http://localhost:8126/info > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Agent is running${NC}"
else
    echo -e "${RED}❌ Agent not running${NC}"
    echo "   Start with: docker-compose -f docker-compose.datadog.yml up -d datadog-agent"
    exit 1
fi

# 2. Check if application is running
echo "2. Checking application..."
if curl -s http://localhost:8080/health > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Application is running${NC}"
else
    echo -e "${RED}❌ Application not running${NC}"
    echo "   Start with: ./scripts/datadog/run-with-datadog.sh"
    exit 1
fi

# 3. Check if DATADOG_ENABLED is set
echo "3. Checking Datadog configuration..."
if [ -z "$DATADOG_ENABLED" ] || [ "$DATADOG_ENABLED" != "true" ]; then
    echo -e "${YELLOW}⚠️  DATADOG_ENABLED is not set to 'true'${NC}"
    echo "   Set with: export DATADOG_ENABLED=true"
else
    echo -e "${GREEN}✅ DATADOG_ENABLED=true${NC}"
fi

# 4. Upload document
echo "4. Uploading test document..."
REQUEST_ID="verify-$(date +%s)"
RESPONSE=$(curl -s -X POST http://localhost:8080/api/documents/upload \
  -F "file=@test.pdf" \
  -H "X-Request-ID: $REQUEST_ID" 2>&1)

if echo "$RESPONSE" | grep -q "jobId"; then
    JOB_ID=$(echo "$RESPONSE" | grep -o '"jobId":"[^"]*"' | cut -d'"' -f4)
    echo -e "${GREEN}✅ Uploaded, job ID: $JOB_ID${NC}"
    echo "   Request ID: $REQUEST_ID"
else
    echo -e "${RED}❌ Upload failed${NC}"
    echo "   Response: $RESPONSE"
    exit 1
fi

# 5. Wait for processing
echo "5. Waiting for processing (10 seconds)..."
sleep 10

# 6. Check status
echo "6. Checking job status..."
STATUS_RESPONSE=$(curl -s http://localhost:8080/api/documents/$JOB_ID/status)
STATUS=$(echo "$STATUS_RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || echo "unknown")
echo "   Job status: $STATUS"

if [ "$STATUS" = "SUCCESS" ] || [ "$STATUS" = "PROCESSING" ]; then
    echo -e "${GREEN}✅ Job is processing or completed${NC}"
else
    echo -e "${YELLOW}⚠️  Job status: $STATUS${NC}"
fi

# 7. Check metrics endpoint
echo "7. Checking metrics endpoint..."
if curl -s http://localhost:8080/actuator/metrics/policyinsight.job.duration > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Metrics endpoint accessible${NC}"

    # Check specific metrics
    echo "   Checking custom metrics..."
    METRICS=("policyinsight.job.duration" "policyinsight.job.success" "policyinsight.llm.tokens" "policyinsight.job.backlog")
    for metric in "${METRICS[@]}"; do
        if curl -s "http://localhost:8080/actuator/metrics/$metric" > /dev/null 2>&1; then
            echo -e "   ${GREEN}✅ $metric${NC}"
        else
            echo -e "   ${YELLOW}⚠️  $metric (not found yet, may need more traffic)${NC}"
        fi
    done
else
    echo -e "${RED}❌ Metrics endpoint not accessible${NC}"
fi

# 8. Check HTTP server metrics
echo "8. Checking HTTP server metrics..."
if curl -s http://localhost:8080/actuator/metrics/http.server.requests > /dev/null 2>&1; then
    echo -e "${GREEN}✅ HTTP server metrics accessible${NC}"
else
    echo -e "${YELLOW}⚠️  HTTP server metrics not accessible${NC}"
fi

echo ""
echo "=== Verification Complete ==="
echo ""
echo "Next steps:"
echo "1. Check Datadog APM: https://app.datadoghq.com/apm/traces?service=policy-insight"
echo "2. Check Datadog Logs: https://app.datadoghq.com/logs?query=service%3Apolicy-insight"
echo "3. Check Datadog Metrics: https://app.datadoghq.com/metric/explorer?query=policyinsight.job.duration"
echo ""
echo "Look for:"
echo "- Trace with spans: upload → job.process → extraction, classification, risk_scan, llm, export"
echo "- JSON logs with request_id, job_id, dd.trace_id, dd.span_id"
echo "- Custom metrics: policyinsight.job.duration, policyinsight.llm.tokens, etc."
echo ""
echo "Request ID: $REQUEST_ID"
echo "Job ID: $JOB_ID"


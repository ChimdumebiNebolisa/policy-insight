#!/bin/bash
# Tail logs for web service uploads and worker START/COMPLETE lines
# Usage: bash scripts/logs_tail.sh [N] [job_id]
#   N: Number of log entries to show (default: 20)
#   job_id: Optional job UUID to filter logs (default: show all recent logs)
# Environment variables (optional):
#   GCP_PROJECT_ID: GCP project ID (default: policy-insight)
#   GCP_REGION: GCP region (default: us-central1)
#
# Examples:
#   bash scripts/logs_tail.sh
#   bash scripts/logs_tail.sh 50
#   bash scripts/logs_tail.sh 20 abc123-def456-...

set -e

N="${1:-20}"
JOB_ID="${2:-}"
PROJECT_ID="${GCP_PROJECT_ID:-policy-insight}"
REGION="${GCP_REGION:-us-central1}"

echo "=== Tail Logs (Last $N entries) ==="
echo ""

if [ -n "$JOB_ID" ]; then
  echo "Filtering by job_id: $JOB_ID"
  echo ""

  # Web service logs for this job
  echo "--- Web Service Upload Logs (job_id=$JOB_ID) ---"
  gcloud logging read "resource.labels.service_name=policyinsight-web AND jsonPayload.job_id=\"$JOB_ID\"" \
    --limit=$N \
    --format="table(timestamp,jsonPayload.message,jsonPayload.job_id,jsonPayload.request_id)" \
    --project="$PROJECT_ID" \
    --region="$REGION" 2>/dev/null || echo "No web logs found for job_id=$JOB_ID"
  echo ""

  # Worker logs for this job
  echo "--- Worker Service Logs (job_id=$JOB_ID) ---"
  gcloud logging read "resource.labels.service_name=policyinsight-worker AND (jsonPayload.job_id=\"$JOB_ID\" OR jsonPayload.message=~\"job_id=$JOB_ID\")" \
    --limit=$N \
    --format="table(timestamp,jsonPayload.message,jsonPayload.job_id,jsonPayload.request_id,jsonPayload.pubsub_message_id,jsonPayload.duration_ms,jsonPayload.final_status)" \
    --project="$PROJECT_ID" \
    --region="$REGION" 2>/dev/null || echo "No worker logs found for job_id=$JOB_ID"
  echo ""
else
  echo "No job_id provided, showing recent logs for all jobs"
  echo ""

  # Web service upload logs
  echo "--- Web Service Upload Logs (Recent) ---"
  gcloud logging read "resource.labels.service_name=policyinsight-web AND jsonPayload.message=~\"upload|Job queued\"" \
    --limit=$N \
    --format="table(timestamp,jsonPayload.message,jsonPayload.job_id,jsonPayload.request_id)" \
    --project="$PROJECT_ID" \
    --region="$REGION" 2>/dev/null || echo "No web logs found"
  echo ""

  # Worker START/COMPLETE logs
  echo "--- Worker Service START/COMPLETE Logs (Recent) ---"
  gcloud logging read "resource.labels.service_name=policyinsight-worker AND (jsonPayload.message=~\"START processing|COMPLETE processing\")" \
    --limit=$N \
    --format="table(timestamp,jsonPayload.message,jsonPayload.job_id,jsonPayload.request_id,jsonPayload.pubsub_message_id,jsonPayload.duration_ms,jsonPayload.final_status)" \
    --project="$PROJECT_ID" \
    --region="$REGION" 2>/dev/null || echo "No worker logs found"
  echo ""
fi

echo "=== Log Tail Complete ==="


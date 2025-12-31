# Tail logs for web service uploads and worker START/COMPLETE lines
# Usage: pwsh scripts\logs_tail.ps1 [N] [job_id]

$ErrorActionPreference = "Stop"

$N = if ($args[0]) { [int]$args[0] } else { 20 }
$JOB_ID = if ($args[1]) { $args[1] } else { "" }
$PROJECT_ID = if ($env:GCP_PROJECT_ID) { $env:GCP_PROJECT_ID } else { "policy-insight" }
$REGION = if ($env:GCP_REGION) { $env:GCP_REGION } else { "us-central1" }

Write-Host "=== Tail Logs (Last $N entries) ===" -ForegroundColor Cyan
Write-Host ""

if ($JOB_ID) {
    Write-Host "Filtering by job_id: $JOB_ID"
    Write-Host ""

    # Web service logs for this job
    Write-Host "--- Web Service Upload Logs (job_id=$JOB_ID) ---" -ForegroundColor Yellow
    try {
        gcloud logging read "resource.labels.service_name=policyinsight-web AND jsonPayload.job_id=`"$JOB_ID`"" `
            --limit=$N `
            --format="table(timestamp,jsonPayload.message,jsonPayload.job_id,jsonPayload.request_id)" `
            --project="$PROJECT_ID" `
            --region="$REGION" 2>&1 | Out-String
    } catch {
        Write-Host "No web logs found for job_id=$JOB_ID"
    }
    Write-Host ""

    # Worker logs for this job
    Write-Host "--- Worker Service Logs (job_id=$JOB_ID) ---" -ForegroundColor Yellow
    try {
        gcloud logging read "resource.labels.service_name=policyinsight-worker AND (jsonPayload.job_id=`"$JOB_ID`" OR jsonPayload.message=~`"job_id=$JOB_ID`")" `
            --limit=$N `
            --format="table(timestamp,jsonPayload.message,jsonPayload.job_id,jsonPayload.request_id,jsonPayload.pubsub_message_id,jsonPayload.duration_ms,jsonPayload.final_status)" `
            --project="$PROJECT_ID" `
            --region="$REGION" 2>&1 | Out-String
    } catch {
        Write-Host "No worker logs found for job_id=$JOB_ID"
    }
    Write-Host ""
} else {
    Write-Host "No job_id provided, showing recent logs for all jobs"
    Write-Host ""

    # Web service upload logs
    Write-Host "--- Web Service Upload Logs (Recent) ---" -ForegroundColor Yellow
    try {
        gcloud logging read "resource.labels.service_name=policyinsight-web AND jsonPayload.message=~`"upload|Job queued`"" `
            --limit=$N `
            --format="table(timestamp,jsonPayload.message,jsonPayload.job_id,jsonPayload.request_id)" `
            --project="$PROJECT_ID" `
            --region="$REGION" 2>&1 | Out-String
    } catch {
        Write-Host "No web logs found"
    }
    Write-Host ""

    # Worker START/COMPLETE logs
    Write-Host "--- Worker Service START/COMPLETE Logs (Recent) ---" -ForegroundColor Yellow
    try {
        gcloud logging read "resource.labels.service_name=policyinsight-worker AND (jsonPayload.message=~`"START processing|COMPLETE processing`")" `
            --limit=$N `
            --format="table(timestamp,jsonPayload.message,jsonPayload.job_id,jsonPayload.request_id,jsonPayload.pubsub_message_id,jsonPayload.duration_ms,jsonPayload.final_status)" `
            --project="$PROJECT_ID" `
            --region="$REGION" 2>&1 | Out-String
    } catch {
        Write-Host "No worker logs found"
    }
    Write-Host ""
}

Write-Host "=== Log Tail Complete ===" -ForegroundColor Cyan


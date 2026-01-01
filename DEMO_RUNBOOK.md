# PolicyInsight — Demo Runbook

**Date:** December 31, 2025
**Project:** policy-insight
**Region:** us-central1

## Environment Configuration

### Cloud Run Services
- **Web Service URL:** https://policyinsight-web-828177954618.us-central1.run.app
- **Worker Service URL:** https://policyinsight-worker-828177954618.us-central1.run.app

### Pub/Sub Configuration
- **Topic:** policyinsight-analysis-topic
- **Subscription:** policyinsight-analysis-sub
- **Push Endpoint:** https://policyinsight-worker-828177954618.us-central1.run.app/internal/pubsub
- **Push Auth Service Account:** policyinsight-worker@policy-insight.iam.gserviceaccount.com

### Secrets (Secret Manager)
- `cloudsql-host`
- `cloudsql-port`
- `cloudsql-database`
- `cloudsql-username`
- `cloudsql-password`

## Critical Constraints

### 1. Pub/Sub Push: Endpoint Acks Only on HTTP Success (2xx)
**Non-negotiable constraint:** Pub/Sub push endpoints that return an HTTP success status code (2xx) are considered acknowledged. Any non-success status code (4xx, 5xx) means Pub/Sub will resend the message.

**Implications:**
- Return 204 (No Content) on successful processing
- Return 4xx for invalid payloads (never 5xx) - these should not be retried
- Return 5xx for processing exceptions - these will be retried by Pub/Sub
- The push handler must complete processing synchronously within the ack deadline window

### 2. Pub/Sub Ack Deadline Maximum: 600 Seconds
**Non-negotiable constraint:** The `ackDeadlineSeconds` value has a maximum of 600 seconds. This value sets both:
1. The acknowledgment deadline for message processing
2. **The HTTP request timeout to the push endpoint**

**Critical Setting:** `ackDeadlineSeconds` must be set to 600 (maximum) to provide the longest possible processing window. This value also sets the HTTP request timeout to the push endpoint.

### 3. Cloud Run Request Timeout: Default 300s, Set Worker to 600s
**Non-negotiable constraint:** Cloud Run request timeout defaults to 300 seconds, configurable up to 3600 seconds (less than 60 minutes). However, for Pub/Sub push subscriptions, the Pub/Sub timeout (based on `ackDeadlineSeconds`) still applies and is the limiting factor.

**Critical Setting:** Worker service timeout must be set to 600 seconds to match the Pub/Sub `ackDeadlineSeconds`. When triggered by Pub/Sub push, the upstream Pub/Sub timeout still applies, so Cloud Run timeout should be equal or greater.

**Deployment:** Set via `gcloud run deploy --timeout=600` or in GitHub Actions workflow.

### 4. Cloud Run CPU Allocation: Request-Based Billing
**Non-negotiable constraint:** With request-based billing, CPU is only allocated during request processing. Background threads that continue after returning the response for the primary path can be throttled unless using always-on/instance-based CPU allocation.

**Why Synchronous Processing:** We keep synchronous processing in the push handler for demo reliability to avoid background CPU pitfalls. Processing completes within the request context, ensuring CPU is allocated throughout the operation. Do NOT implement "ack immediately then process in @Async" as the primary fix.

## Pre-Demo Verification

### 1. Verify Pub/Sub Subscription Configuration

```bash
# Check ackDeadlineSeconds is set to 600 (max)
gcloud pubsub subscriptions describe policyinsight-analysis-sub \
  --format="value(ackDeadlineSeconds)"

# Expected output: 600
```

If not 600, update it:
```bash
gcloud pubsub subscriptions update policyinsight-analysis-sub \
  --ack-deadline=600
```

**Why:** The `ackDeadlineSeconds` value sets the HTTP request timeout to the push endpoint. With 600 seconds, the worker has the maximum window to complete processing before Pub/Sub retries.

### 2. Verify Cloud Run Timeout

```bash
# Check worker service timeout
gcloud run services describe policyinsight-worker \
  --region=us-central1 \
  --format="value(spec.template.spec.timeoutSeconds)"

# Expected: >= 600
```

If less than 600, update it:
```bash
gcloud run services update policyinsight-worker \
  --timeout=600 \
  --region=us-central1
```

**Note:** Pub/Sub push still times out based on `ackDeadlineSeconds`, so Cloud Run timeout should be equal or greater.

### 3. Verify Push Endpoint Configuration

```bash
# Check subscription push endpoint
gcloud pubsub subscriptions describe policyinsight-analysis-sub \
  --format="value(pushConfig.pushEndpoint)"

# Expected: https://policyinsight-worker-828177954618.us-central1.run.app/internal/pubsub
```

### 4. Verify Service Account

```bash
# Check push auth service account
gcloud pubsub subscriptions describe policyinsight-analysis-sub \
  --format="value(pushConfig.oidcToken.serviceAccountEmail)"

# Expected: policyinsight-worker@policy-insight.iam.gserviceaccount.com
```

### 5. Automated Verification Script

Use the provided verification script:
```bash
bash scripts/verify_gcp.sh
# or
pwsh scripts\verify_gcp.ps1
```

## Smoke Test Steps

### Step 1: Upload a Document

```bash
# Upload a test PDF
curl -X POST https://policyinsight-web-828177954618.us-central1.run.app/api/documents/upload \
  -F "file=@test-document.pdf" \
  -H "Content-Type: multipart/form-data"

# Expected response (202 Accepted):
# {
#   "jobId": "uuid-here",
#   "status": "PENDING",
#   "statusUrl": "/api/documents/uuid-here/status",
#   "message": "Document uploaded successfully. Processing will begin shortly."
# }
```

**Extract `jobId` from response for next steps.**

### Step 2: Poll Status Endpoint

```bash
# Replace JOB_ID with the UUID from Step 1
JOB_ID="your-job-id-here"

# Poll status
curl https://policyinsight-web-828177954618.us-central1.run.app/api/documents/${JOB_ID}/status

# Expected progression:
# 1. {"jobId":"...","status":"PENDING","message":"Job is queued for processing"}
# 2. {"jobId":"...","status":"PROCESSING","message":"Document is being processed"}
# 3. {"jobId":"...","status":"SUCCESS","reportUrl":"/api/documents/.../report","message":"Analysis completed successfully"}
```

### Step 3: Automated Smoke Test

Use the provided smoke test script:
```bash
bash scripts/smoke_test.sh tiny.pdf
# or
pwsh scripts\smoke_test.ps1 tiny.pdf
```

### Step 4: Check Worker Logs

```bash
# View worker logs for the job
gcloud logging read "resource.labels.service_name=policyinsight-worker AND jsonPayload.job_id=${JOB_ID}" \
  --limit=50 \
  --format=json \
  --region=us-central1

# Look for:
# - START log with job_id, request_id, pubsub_message_id
# - COMPLETE log with duration_ms and final_status
# - Any error logs if processing failed
```

Or use the provided logs script:
```bash
bash scripts/logs_tail.sh 50 ${JOB_ID}
# or
pwsh scripts\logs_tail.ps1 50 ${JOB_ID}
```

### Step 5: Verify Idempotency (Optional)

To test that duplicate pushes don't cause duplicate processing:

1. Manually trigger a Pub/Sub push for an already-processed job
2. Verify in logs that processing is skipped (status not PENDING)
3. Check database that no duplicate chunks or reports were created

## Troubleshooting

### Issue: Job Stuck in PENDING

**Check:**
1. Pub/Sub subscription is active and has messages
2. Worker service is running and healthy
3. Worker logs for errors processing messages
4. Push endpoint is accessible (check Cloud Run service status)

**Commands:**
```bash
# Check subscription message count
gcloud pubsub subscriptions describe policyinsight-analysis-sub \
  --format="value(numUndeliveredMessages)"

# Check worker service status
gcloud run services describe policyinsight-worker \
  --region=us-central1 \
  --format="value(status.conditions[0].status)"
```

### Issue: Processing Times Out

**Check:**
1. `ackDeadlineSeconds` is set to 600
2. Cloud Run timeout is >= 600
3. Worker logs show processing duration
4. No long-running operations blocking completion

**Commands:**
```bash
# Check processing duration in logs
gcloud logging read "resource.labels.service_name=policyinsight-worker AND jsonPayload.duration_ms" \
  --limit=10 \
  --format="table(jsonPayload.job_id,jsonPayload.duration_ms)" \
  --region=us-central1
```

### Issue: Duplicate Processing

**Check:**
1. Atomic status transition is working (repository method `updateStatusIfPending`)
2. Logs show "skip duplicate" messages for non-PENDING jobs
3. Database constraints prevent duplicate chunks/reports

**Commands:**
```bash
# Check for duplicate chunks
gcloud sql connect policyinsight-db --user=policyinsight-app
# Then in SQL:
SELECT job_uuid, COUNT(*) as chunk_count
FROM document_chunks
GROUP BY job_uuid
HAVING COUNT(*) > 100;  -- Adjust threshold as needed
```

## Key Metrics to Monitor

1. **Processing Duration:** Should be < 600 seconds (ackDeadlineSeconds)
2. **Success Rate:** Jobs should transition PENDING -> PROCESSING -> SUCCESS
3. **Error Rate:** Failed jobs should have error messages in database
4. **Queue Depth:** Undelivered messages in subscription should be low

## Demo Flow Summary

1. **Upload:** User uploads PDF via web UI → Job created in PENDING status → Pub/Sub message published
2. **Processing:** Pub/Sub pushes to worker → Worker atomically transitions to PROCESSING → Processes document → Updates to SUCCESS
3. **Status Polling:** Web UI polls status endpoint → Shows progress → Redirects to report when SUCCESS
4. **Report View:** User views analysis report with citations and risk taxonomy

## Contact & Resources

- **Project:** policy-insight
- **Region:** us-central1
- **Web Service:** https://policyinsight-web-828177954618.us-central1.run.app
- **Worker Service:** https://policyinsight-worker-828177954618.us-central1.run.app

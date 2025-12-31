# PolicyInsight — Demo Readiness Audit

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

**Reference:** Google Cloud Pub/Sub documentation on push subscriptions and acknowledgment deadlines.

### 3. Cloud Run Request Timeout: Default 300s, Set Worker to 600s
**Non-negotiable constraint:** Cloud Run request timeout defaults to 300 seconds, configurable up to 3600 seconds (less than 60 minutes). However, for Pub/Sub push subscriptions, the Pub/Sub timeout (based on `ackDeadlineSeconds`) still applies and is the limiting factor.

**Critical Setting:** Worker service timeout must be set to 600 seconds to match the Pub/Sub `ackDeadlineSeconds`. When triggered by Pub/Sub push, the upstream Pub/Sub timeout still applies, so Cloud Run timeout should be equal or greater.

**Deployment:** Set via `gcloud run deploy --timeout=600` or in GitHub Actions workflow.

### 4. Cloud Run CPU Allocation: Request-Based Billing
**Non-negotiable constraint:** With request-based billing, CPU is only allocated during request processing. Background threads that continue after returning the response for the primary path can be throttled unless using always-on/instance-based CPU allocation.

**Why Synchronous Processing:** We keep synchronous processing in the push handler for demo reliability to avoid background CPU pitfalls. Processing completes within the request context, ensuring CPU is allocated throughout the operation. Do NOT implement "ack immediately then process in @Async" as the primary fix.

## Demo-Readiness Checklist

### Idempotency
- [x] Atomic status transition from PENDING to PROCESSING prevents duplicate processing
- [x] Repository method `updateStatusIfPending` ensures only one instance processes a job
- [x] Duplicate Pub/Sub pushes for the same job cannot start processing twice

### Correlation & Observability
- [x] `request_id` generated in upload flow and propagated via Pub/Sub attributes
- [x] Worker extracts `request_id`, `job_id`, and Pub/Sub `messageId` into MDC
- [x] START and COMPLETE log lines include all correlation IDs
- [x] Processing duration (ms) included in COMPLETE log

### Contract Testing
- [x] Contract test validates Pub/Sub push envelope format
- [x] Test asserts 2xx on valid payload, 4xx on invalid
- [x] Test verifies processor invoked exactly once
- [x] OIDC verification can be disabled in test profile

### Error Handling
- [x] Invalid payload returns 4xx (never 5xx)
- [x] Internal failures return 5xx (so Pub/Sub retries)
- [x] Processing failures mark job as FAILED in database

### Synchronous Processing
- [x] Push handler processes synchronously (no @Async background threads as primary path)
- [x] Processing completes within ackDeadlineSeconds window

## Non-Negotiable Demo Settings

### Pub/Sub Subscription
Set `ackDeadlineSeconds` to 600 (maximum) to provide the longest possible processing window:
```bash
gcloud pubsub subscriptions update policyinsight-analysis-sub \
  --ack-deadline=600
```

**Verification:**
```bash
gcloud pubsub subscriptions describe policyinsight-analysis-sub \
  --format="value(ackDeadlineSeconds)"
# Expected: 600
```

### Cloud Run Request Timeout
Ensure worker service timeout is >= 600 seconds:
```bash
gcloud run services update policyinsight-worker \
  --timeout=600 \
  --region=us-central1
```

**Verification:**
```bash
gcloud run services describe policyinsight-worker \
  --region=us-central1 \
  --format="value(spec.template.spec.timeoutSeconds)"
# Expected: 600
```

**Note:** Pub/Sub push still times out based on `ackDeadlineSeconds`, so the Cloud Run timeout should be equal or greater. The `ackDeadlineSeconds` value (max 600s) also sets the HTTP request timeout to the push endpoint.


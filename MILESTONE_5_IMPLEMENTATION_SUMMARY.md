# Milestone 5 Implementation Summary

## Changes Made

### 1. GeminiService.java - Real Vertex AI Integration
- **Added `vertexai.enabled` config flag** to control real vs stub mode
- **Implemented real Vertex AI Gemini API calls** using Google Gen AI Java SDK (com.google.genai:google-genai)
- **Kept stub mode** when `vertexai.enabled=false` for local development
- **Uses ADC (Application Default Credentials)** when enabled=true for Cloud Run compatibility
- Location: `src/main/java/com/policyinsight/processing/GeminiService.java`

### 2. ReportGroundingValidator.java - NEW Service
- **Created cite-or-abstain validation service** that walks entire report JSON
- **Validates all claims** have chunk_id AND page number citations
- **Enforces abstain** by replacing claims without citations with "Not detected / Not stated"
- **Validates chunk_ids exist** in stored document chunks
- **Validates all report sections**: summary bullets, obligations, restrictions, termination triggers, risk taxonomy
- Location: `src/main/java/com/policyinsight/processing/ReportGroundingValidator.java`

### 3. DocumentProcessingWorker.java - Validation Integration
- **Added grounding validation** before job status becomes SUCCESS
- **Validates report** using ReportGroundingValidator
- **Applies abstain statements** when citations are missing (per PRD)
- **Uses validated data** for report persistence and GCS upload
- Location: `src/main/java/com/policyinsight/processing/DocumentProcessingWorker.java`

### 4. Page Number Propagation
- **Already implemented** in ReportGenerationService (lines 107-116, 261-270)
- **Page numbers** are extracted from DocumentChunk entities and included in citations
- **Validator ensures** page numbers are present in all validated claims

### 5. pom.xml - Dependency Update
- **Added google-genai dependency** (Google Gen AI Java SDK, replaces deprecated google-cloud-vertexai)
- Location: `pom.xml` line 124-127

## PRD Compliance Checklist

✅ **Real Vertex AI Gemini call path implemented** (using Google Gen AI Java SDK)
✅ **Local-only path works with NO GCP creds** (stub mode when vertexai.enabled=false)
✅ **Cite-or-abstain enforced** (ReportGroundingValidator replaces uncited claims)
✅ **Page numbers in citations** (propagated from DocumentChunk.pageNumber)
✅ **Report stored in reports table** (JSONB fields)
✅ **Report uploaded to GCS** (when GCS enabled, with deterministic key)
✅ **Job status SUCCESS only after validation** (validation happens before status update)

## Configuration

### Local Development (Stub Mode)
```yaml
# application-local.yml or environment variable
vertexai:
  enabled: false  # Uses stub responses
```

### Production (Real Vertex AI)
```yaml
# application.yml or environment variable
vertexai:
  enabled: true
  project-id: ${GOOGLE_CLOUD_PROJECT}
  location: us-central1
  model: gemini-2.0-flash-exp
```

Uses Application Default Credentials (ADC):
- Cloud Run: Uses service account automatically
- Local: Use `gcloud auth application-default login` or set `GOOGLE_APPLICATION_CREDENTIALS`


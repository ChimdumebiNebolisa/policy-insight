# PolicyInsight MUP Local Demo

This guide walks through the local happy path for PolicyInsight MUP (Minimum Usable Product).

## Prerequisites

- Java 17+
- Maven
- PostgreSQL running locally (default: localhost:5432)
- Database created: `policyinsight`

## Setup

1. **Start PostgreSQL** (if not already running):
   ```bash
   # Using Docker (example)
   docker run --name postgres-policyinsight -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=policyinsight -p 5432:5432 -d postgres:15
   ```

2. **Run database migrations**:
   ```bash
   ./mvnw flyway:migrate
   # Or on Windows:
   .\mvnw.cmd flyway:migrate
   ```

3. **Start the application with local profile**:
   ```bash
   # Windows
   $env:SPRING_PROFILES_ACTIVE="local"; .\mvnw.cmd spring-boot:run

   # Linux/Mac
   SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
   ```

   The local profile enables:
   - Local worker for processing jobs (`policyinsight.worker.enabled=true`)
   - Debug logging

4. **Verify the application is running**:
   - Open http://localhost:8080 in your browser
   - You should see the upload form

## Demo Steps

### Step 1: Open the Landing Page

1. Navigate to http://localhost:8080
2. You should see:
   - "PolicyInsight" header
   - "Upload Document" form
   - File input for PDF selection
   - "Analyze Document" button

### Step 2: Upload a PDF

1. Click "Select PDF" and choose a PDF file (max 50 MB)
2. Click "Analyze Document"
3. You should see:
   - "Uploading..." spinner
   - Then: "Uploaded. Job: {jobId}" message
   - A polling block appears that says "Checking status…"

### Step 3: Watch Status Updates (Live Polling)

1. The status will update automatically every 2 seconds via htmx polling
2. You'll see status changes:
   - "PENDING" → "PROCESSING" → "SUCCESS"
3. Status messages will update in real-time:
   - "Job is queued for processing"
   - "Document is being processed"
   - "Analysis completed successfully"

### Step 4: Redirect to Report

1. When status reaches "SUCCESS", htmx will automatically redirect to `/documents/{id}/report`
2. The report page shows:
   - Document overview
   - Plain-English summary
   - Obligations and restrictions
   - Risk taxonomy
   - Q&A section

### Step 5: Ask a Q&A Question

1. Scroll to the Q&A section on the report page
2. Type a question in the form (e.g., "What are the key obligations?")
3. Submit the question
4. You should see either:
   - A cited answer with references to document sections
   - "Insufficient evidence in document" if the question cannot be answered from the document

## Troubleshooting

### Worker Not Processing Jobs

- Verify `SPRING_PROFILES_ACTIVE=local` is set
- Check logs for: "Worker enabled = true"
- Ensure database is accessible and migrations ran successfully

### Status Stuck on PENDING

- Check application logs for errors
- Verify worker is enabled (see above)
- Check database connection

### Upload Fails

- Verify PDF file is valid and < 50 MB
- Check file input accepts `.pdf` files only
- Review application logs for validation errors

## Notes

- The local worker processes jobs synchronously (polls every 2 seconds)
- Processing time depends on document size and complexity
- For faster testing, use small PDF files (< 10 pages)


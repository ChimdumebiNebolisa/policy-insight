# PolicyInsight

## What this is

PolicyInsight is a Spring Boot web app for reviewing policy, agreement, and contract PDFs. Users upload a PDF, the app extracts text with PDFBox, creates source sections, generates a structured report, validates citations back to source text, and supports shareable report links plus grounded Q&A for uploaded documents. It also includes a bundled deterministic sample report built from a committed fictional PDF so the demo path does not depend on live Gemini availability.

## Problem it solves

Reviewing contracts and policy documents manually is slow when the goal is to identify the operational terms quickly. This app reduces that friction by extracting the text, organizing the document into source sections, producing a readable report with cited evidence, and allowing follow-up questions against the uploaded document instead of forcing users to scan the full PDF by hand.

## Demo

Live demo:

[https://policy-insight.onrender.com](https://policy-insight.onrender.com)

Screenshots:

Add screenshots here.

Video/GIF:

Add a walkthrough video or GIF here.

## Features

- Upload PDF documents, validate them, extract text with PDFBox, and split the text into source sections without storing the original file
- Generate structured reports for uploaded documents, then validate cited sources before saving the final report
- Open a deterministic Gemini-free sample report built from `src/main/resources/samples/fictional_business_agreement.pdf`
- Create shareable report links and ask grounded Q&A questions for uploaded documents

## Tech stack

Frontend:

Thymeleaf and HTMX

Backend:

Java 21 and Spring Boot

Database:

PostgreSQL

AI/API:

Google Gemini

Authentication:

Owner access cookies, expiring share links, and simple in-memory rate limiting

Deployment:

Docker and Render

Other tools:

PDFBox, Flyway, Maven Wrapper, H2 for tests, and Docker Compose for local PostgreSQL

## Setup

### 1. Clone the repo

```bash
git clone https://github.com/ChimdumebiNebolisa/policy-insight.git
cd policy-insight
```

### 2. Install dependencies

This project uses Java 21, Docker for local PostgreSQL, and the Maven Wrapper for builds and tests. You do not need npm for this app.

```powershell
docker compose up -d
.\mvnw.cmd test
```

### 3. Add environment variables

Environment variables used by this app:

```text
DATABASE_URL
APP_TOKEN_SECRET
APP_AI_PROVIDER
GEMINI_API_KEY
GEMINI_MODEL
GEMINI_TIMEOUT_SECONDS
APP_UPLOAD_MAX_BYTES
APP_OWNER_TOKEN_TTL_MINUTES
APP_SHARE_TTL_DAYS
```

Local mock mode for Windows PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:55432/policyinsight"
$env:SPRING_DATASOURCE_USERNAME="policyinsight"
$env:SPRING_DATASOURCE_PASSWORD="policyinsight"
$env:APP_TOKEN_SECRET="replace-with-a-long-random-secret-that-is-not-short"
$env:APP_AI_PROVIDER="mock"
```

Production Gemini mode:

```text
DATABASE_URL=<Render internal Postgres URL>
APP_TOKEN_SECRET=<long random secret>
APP_AI_PROVIDER=gemini
GEMINI_API_KEY=<your Gemini key>
GEMINI_MODEL=gemini-2.5-flash-lite
GEMINI_TIMEOUT_SECONDS=180
APP_UPLOAD_MAX_BYTES=10485760
APP_OWNER_TOKEN_TTL_MINUTES=120
APP_SHARE_TTL_DAYS=7
```

Recommended production values:

- `APP_AI_PROVIDER=gemini`
- `GEMINI_MODEL=gemini-2.5-flash-lite`
- `GEMINI_TIMEOUT_SECONDS=180`

Recommended local value:

- `APP_AI_PROVIDER=mock`

Render note:

Render free instances can be slow or cold-started, so the deployed app worked more reliably with `GEMINI_MODEL=gemini-2.5-flash-lite` and `GEMINI_TIMEOUT_SECONDS=180` instead of a heavier model and shorter timeout.

Important local note:

The Docker Compose PostgreSQL host port is `55432`, not `5432`, because a local Windows PostgreSQL installation may already be using `5432`.

### 4. Run the app locally

Start PostgreSQL:

```powershell
docker compose up -d
```

Run the app:

```powershell
.\mvnw.cmd spring-boot:run
```

Open the local URL shown in the terminal, typically `http://localhost:8080`.

## Testing

Run tests:

```powershell
.\mvnw.cmd test
```

What is tested:

- application context
- repositories
- PDF extraction
- sample report path
- citation validation
- upload flow
- share links
- Q&A behavior
- Gemini error handling where covered

Latest verified local result:

`49` tests passed.

## How it works

For this project:

1. User uploads a PDF.
2. Backend validates and extracts text with PDFBox.
3. Extracted text is split into source sections.
4. Analyzer generates a structured report.
5. Citation validation checks referenced sources.
6. User views, shares, or asks questions against uploaded reports.

The sample report path is separate from uploaded analysis. It loads the committed fictional PDF at `src/main/resources/samples/fictional_business_agreement.pdf`, uses a deterministic report builder, does not use Gemini, and does not show Q&A.

## Architecture

Briefly explain the codebase structure based on the actual folders.

```txt
src/main/java/com/policyinsight/
  ai/: analyzer interface, Gemini integration, DTOs, mock analyzer
  config/: application config, environment processing, health endpoint, secret validation
  controller/: upload, sample, report, share, Q&A, and global exception handlers
  model/: JPA entities and job status enum
  repository/: Spring Data repositories
  security/: token hashing and in-memory rate limiting
  service/: PDF processing, chunking, async report generation, sample builder, fallback builder, sharing, Q&A
  util/: citation validation and supporting utilities
src/main/resources/templates/
src/main/resources/static/
src/main/resources/samples/
src/main/resources/db/migration/
src/test/
```

System overview:

Frontend:

Server-rendered Thymeleaf pages with HTMX for upload submission, status polling, share-link fragments, and Q&A partial updates.

Backend:

Spring Boot handles PDF validation, text extraction, chunking, async report generation, citation validation, report access, share links, and Q&A.

Database:

PostgreSQL stores jobs, extracted source chunks, reports, share links, and saved Q&A interactions.

External services:

Google Gemini is used for uploaded-document analysis and uploaded-document Q&A when `APP_AI_PROVIDER=gemini`.

Deployment:

The app is packaged as a Dockerized Spring Boot service and deployed to Render with a Postgres database and `/health` health check.

## Known limitations

- Render free instances may be slow or cold start.
- Live Gemini analysis depends on valid Gemini credentials and may timeout if model or timeout settings are misconfigured.
- Citation validation is source-reference based, not deep semantic proof.
- Uploaded PDFs are not malware-scanned.
- In-memory rate limiting is MVP-level and resets on restart.
- The sample report is a deterministic demo path for one bundled fictional document, not a live analysis run.

## License

No license specified yet.

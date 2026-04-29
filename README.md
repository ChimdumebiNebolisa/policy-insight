# PolicyInsight

## What this is

PolicyInsight is a Spring Boot web app for reviewing policy and agreement PDFs with AI-generated reports grounded in source text. Users can upload a PDF, extract and split its text into cited evidence sections, generate a structured report, and ask follow-up questions against the uploaded document. It also includes a deterministic built-in sample report for demos that does not depend on live Gemini availability.

## Problem it solves

Policy and contract PDFs are slow to review manually, especially when users need to find obligations, restrictions, termination language, and risk points quickly. PolicyInsight reduces that friction by extracting the text, organizing key findings into a readable report, and linking each claim back to cited source evidence.

## Demo

Live demo:

Add your Render or Railway deployment URL here.

Screenshots:

Add screenshots here.

Video/GIF:

Add a walkthrough video or GIF here.

## Features

- Upload PDF documents up to the configured file size limit and extract text without storing the original file
- Generate structured reports with overview, summary, obligations, restrictions, termination terms, and risks
- Link report claims and Q&A answers back to cited source evidence
- Open a deterministic built-in sample report from the bundled fictional agreement PDF

## Tech stack

Frontend:

Thymeleaf templates, HTMX, and plain CSS

Backend:

Java 21, Spring Boot, Spring MVC, and Spring Data JPA

Database:

PostgreSQL with Flyway migrations

AI/API:

Google Generative Language API through Gemini, plus a deterministic sample report builder and a demo fallback builder

Authentication:

Owner access cookies, signed share tokens, and simple in-memory IP rate limiting

Deployment:

Render, Railway, Dockerfile, and `render.yaml` / `railway.json`

Other tools:

Apache PDFBox, Maven Wrapper, and Docker Compose for local PostgreSQL

## Setup

### 1. Clone the repo

```bash
git clone <repo-url>
cd policy-insight
```

### 2. Install dependencies

This project uses Maven, not npm. The Maven Wrapper downloads the required build tooling automatically.

```bash
./mvnw test
```

On Windows:

```powershell
.\mvnw.cmd test
```

### 3. Add environment variables

Local development with PostgreSQL:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/policyinsight
SPRING_DATASOURCE_USERNAME=policyinsight
SPRING_DATASOURCE_PASSWORD=policyinsight
APP_TOKEN_SECRET=local-development-token-secret-change-before-deploy
APP_AI_PROVIDER=mock
```

Recommended Render or Railway deployment variables:

```env
DATABASE_URL=<platform postgres url>
APP_TOKEN_SECRET=<long random secret>
APP_AI_PROVIDER=gemini
GEMINI_MODEL=gemini-2.5-flash-lite
GEMINI_TIMEOUT_SECONDS=180
GEMINI_API_KEY=<your Gemini key>
```

Environment variables used:

```md
DATABASE_URL: Platform-style Postgres connection string used in Render or Railway.
SPRING_DATASOURCE_URL: Direct JDBC URL for local database development.
SPRING_DATASOURCE_USERNAME: Local PostgreSQL username.
SPRING_DATASOURCE_PASSWORD: Local PostgreSQL password.
APP_TOKEN_SECRET: Secret used to hash owner and share tokens.
APP_AI_PROVIDER: Selects the AI provider. Use `mock` locally or `gemini` for live Gemini calls.
GEMINI_MODEL: Gemini model name for live analysis. `gemini-2.5-flash-lite` is recommended on Render.
GEMINI_TIMEOUT_SECONDS: Timeout for Gemini requests.
GEMINI_API_KEY: API key for Gemini.
APP_UPLOAD_MAX_BYTES: Maximum upload size in bytes.
APP_OWNER_TOKEN_TTL_MINUTES: Owner access token lifetime.
APP_SHARE_TTL_DAYS: Share link lifetime.
PORT: Web service port used by Render.
```

### 4. Run the app locally

Start PostgreSQL:

```powershell
docker compose down -v
docker compose up -d
```

Run the app:

```powershell
.\mvnw.cmd spring-boot:run
```

Open `http://localhost:8080`.

## Testing

Run tests:

```powershell
.\mvnw.cmd test
```

What is tested:

- AI analyzer configuration and Gemini request handling
- PDF validation, text extraction, and chunk processing
- Repository and database integration behavior
- Upload, report, share, sample, and Q&A controller flows
- Citation validation and report grounding behavior
- UI and HTMX status rendering for the main user flow

## How it works

User uploads a PDF -> the backend validates it -> text is extracted with PDFBox -> text is chunked into source sections -> a background job generates a report -> the frontend polls job status and then opens the completed report.

For this project:

1. Step one: The user uploads a PDF from the homepage, or opens the built-in sample report.
2. Step two: Uploaded PDFs are validated, text is extracted, and source sections are stored in PostgreSQL.
3. Step three: A background job calls Gemini for live analysis, or the sample route builds a deterministic report from the bundled fictional sample PDF.
4. Step four: The report is saved with citations that map claims back to extracted source evidence.
5. Step five: The user can review the report, open share links, and ask grounded Q&A questions for uploaded documents.

## Architecture

Briefly explain the structure of the codebase.

```txt
src/main/java/com/policyinsight/controller/: MVC and HTMX endpoints for upload, sample, report, share, and Q&A flows.
src/main/java/com/policyinsight/service/: PDF processing, chunking, report generation orchestration, sample report building, and async job handling.
src/main/java/com/policyinsight/ai/: AI provider interfaces, Gemini integration, DTOs, and mock analyzer support.
src/main/java/com/policyinsight/repository/: Spring Data repositories for jobs, reports, chunks, Q&A, and share links.
src/main/java/com/policyinsight/model/: JPA entities and enums.
src/main/resources/templates/: Thymeleaf pages and fragments.
src/main/resources/static/: CSS assets.
src/main/resources/db/migration/: Flyway migrations.
src/main/resources/samples/: Bundled fictional sample PDF used by `/sample`.
scripts/: Local helper scripts.
```

System overview:

```txt
Frontend: Thymeleaf templates rendered by Spring MVC, with HTMX for upload status polling and partial updates.
Backend: Spring Boot services handle validation, text extraction, chunking, report generation, sharing, and Q&A.
Database: PostgreSQL stores jobs, extracted chunks, reports, Q&A interactions, and share links.
External services: Gemini through the Google Generative Language REST API for uploaded document analysis.
Deployment: Docker-based deployment to Render or Railway with Flyway migrations on startup.
```

## Known limitations

- Live Gemini analysis can still fail if deployment environment variables are wrong or the external API times out.
- Uploaded documents support grounded Q&A, but the deterministic `/sample` report does not expose Q&A.
- No user account system exists yet; access control is based on owner cookies and share tokens rather than full authentication.

## License

No license file is included yet. Add a project license here.

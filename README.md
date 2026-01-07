# PolicyInsight

Production-grade legal document analysis service with Datadog observability.

## Overview

PolicyInsight analyzes legal documents (PDFs) and outputs plain-English risk reports with mandatory source citations. Every claim references extracted text with page numbers, ensuring grounded AI safety.

![PolicyInsight Upload Interface](Screenshot%202026-01-03%20083034.png)

## Tech Stack

- **Backend**: Java 21 + Spring Boot 3.3
- **UI**: Spring MVC + Thymeleaf (server-rendered)
- **Database**: PostgreSQL 15 + Flyway
- **Observability**: Datadog APM + logs + metrics
- **Cloud**: Google Cloud Run + Cloud SQL + GCS + Pub/Sub + Vertex AI

## Local Development

### Prerequisites

- Java 21 JDK
- Maven 3.8+ (or use Maven wrapper)
- Docker Desktop (for PostgreSQL)

### Quick Start

1. **Start PostgreSQL using Docker Compose:**
   ```bash
   docker-compose up -d
   ```

2. **Run the application:**

   On Windows:
   ```bash
   .\mvnw.cmd spring-boot:run
   ```

   On Linux/Mac:
   ```bash
   ./mvnw spring-boot:run
   ```

   Or if you have Maven installed:
   ```bash
   mvn spring-boot:run
   ```

3. **Access the application:**
   - Web UI: http://localhost:8080
   - Health endpoint: http://localhost:8080/health
   - Readiness endpoint: http://localhost:8080/actuator/readiness
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - OpenAPI docs: http://localhost:8080/v3/api-docs

### Database Setup

Flyway migrations run automatically on application startup. The baseline schema includes:

- `policy_jobs` - Job tracking and metadata
- `document_chunks` - Extracted text chunks with citations
- `reports` - Generated risk reports (JSONB)
- `qa_interactions` - Q&A interactions with grounding
- `share_links` - Shareable report links

To manually run migrations:
```bash
# Windows
.\mvnw.cmd flyway:migrate

# Linux/Mac
./mvnw flyway:migrate

# Or with Maven installed
mvn flyway:migrate
```

### Configuration

Application configuration is in `src/main/resources/application.yml`. For local overrides, use `application-local.yml`.

Key configuration:
- Database connection (defaults to localhost:5432)
- Server port (defaults to 8080)
- File upload limits (50 MB max)

## API Endpoints

### Health & Status

- `GET /health` - Basic health check
- `GET /actuator/readiness` - Readiness check (validates DB connection)

### Documents

- `POST /api/documents/upload` - Upload a PDF document
  - Requires: multipart/form-data with PDF file (max 50 MB)
  - Validates: PDF magic bytes (%PDF-), MIME type, file size
  - Returns: Job ID and capability token (JSON) or HTMX fragment
  - Rate limit: 10 uploads/hour per IP
- `GET /api/documents/{id}/status` - Get document processing status
  - Requires: Job capability token (X-Job-Token header or pi_job_token_{id} cookie)
  - Returns: Current status (PENDING, PROCESSING, SUCCESS, FAILED) with progress info
- `GET /documents/{id}/report` - View analysis report (HTML)
  - Requires: Job capability token
  - Returns: Rendered report with citations
- `GET /api/documents/{id}/export/pdf` - Export report as PDF
  - Requires: Job capability token
  - Returns: PDF file with inline citations

### Q&A

- `POST /api/questions` - Submit a grounded question
  - Requires: Job capability token, document_id, question (max 500 chars)
  - Rate limit: 20 Q&A requests/hour per IP, max 3 questions per job
  - Returns: Citation-backed answer or abstention
- `GET /api/questions/{document_id}` - Get Q&A history for a document
  - Requires: Job capability token
  - Returns: List of Q&A interactions

### Sharing

- `POST /api/documents/{id}/share` - Generate shareable link
  - Requires: Job capability token
  - Returns: Share token and expiration (7 days)
- `GET /documents/{id}/share/{token}` - View shared report (public, no token required)

### API Documentation

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## Project Structure

```
policy-insight/
├── src/main/java/com/policyinsight/
│   ├── PolicyInsightApplication.java
│   ├── api/              # REST API controllers
│   ├── web/              # Web controllers (Thymeleaf)
│   ├── config/           # Configuration classes
│   └── util/             # Utility classes
├── src/main/resources/
│   ├── application.yml   # Application configuration
│   ├── db/migration/     # Flyway migrations
│   ├── templates/        # Thymeleaf templates
│   └── static/           # Static assets (CSS, JS)
├── docker-compose.yml    # Local PostgreSQL setup
└── pom.xml               # Maven dependencies
```

## Development

### Running Tests

```bash
# Windows
.\mvnw.cmd test

# Linux/Mac
./mvnw test

# Or with Maven installed
mvn test
```

### Building

```bash
# Windows
.\mvnw.cmd clean package

# Linux/Mac
./mvnw clean package

# Or with Maven installed
mvn clean package
```

### Code Quality

The project follows Spring Boot best practices:
- Structured logging with correlation IDs
- Global exception handling
- Input validation
- OpenAPI documentation

## CI/CD

GitHub Actions workflow (`.github/workflows/ci.yml`) runs on:
- Pull requests to `main` or `develop`
- Pushes to `main` or `develop`

The CI workflow:
1. Sets up PostgreSQL service
2. Runs Flyway migrations
3. Builds the application
4. Runs tests
5. Packages the JAR

## Security Model

PolicyInsight uses **capability tokens** for access control:

- **Token Generation**: Each uploaded document receives a unique capability token (32 random bytes, base64url encoded)
- **Token Storage**: Only HMAC-SHA256 hash is stored in database (never raw tokens)
- **Token Delivery**:
  - JSON API clients: Token returned in response body (one-time)
  - HTMX/browser clients: Token set as HttpOnly cookie (`pi_job_token_{jobId}`)
- **Token Validation**: Required for all protected endpoints (status, report, export, Q&A, share generation)
- **CSRF Protection**: State-changing endpoints (POST/PUT/PATCH/DELETE) validate Origin/Referer headers

### Public Endpoints (No Token Required)

- `GET /` - Landing page
- `POST /api/documents/upload` - Upload endpoint
- `GET /documents/{id}/share/{token}` - Share link viewing
- `POST /internal/pubsub` - Internal Pub/Sub webhook
- `GET /health` - Health check
- Swagger/OpenAPI endpoints

## Rate Limiting

- **Upload**: 10 requests/hour per IP (configurable)
- **Q&A**: 20 requests/hour per IP, max 3 questions per job (configurable)
- **Implementation**: DB-backed counters with atomic upserts (Cloud Run compatible)

## Job Processing

- **Lease Model**: Jobs use database-backed leases to prevent concurrent processing
- **Stuck Job Recovery**: Scheduled reaper resets expired PROCESSING jobs to PENDING (if attempts < max) or marks as FAILED
- **Idempotent Chunks**: Chunk writes are idempotent (UNIQUE constraint + delete-before-insert)
- **Gemini Retries**: Automatic retries for transient errors (timeouts, 429, 5xx) with exponential backoff + jitter

## File Handling

- **PDF Validation**: Magic bytes (%PDF-) validation in addition to MIME type check
- **Storage Path**: Deterministic path `jobs/{jobId}/document.pdf` (ignores user filename)
- **Text Limits**: Hard cap on extracted text length (default 1M characters, configurable)
- **Processing Timeouts**: Stage-level timeouts enforced (default 300 seconds, configurable)

## Milestones

See [tasks.md](./markdown/tasks.md) for the complete implementation roadmap.

**Current Status**:
- ✅ M1: Capability-token security
- ✅ M2: Rate limiting + quotas
- ✅ M3: Lease + stuck PROCESSING recovery
- ✅ M4: Idempotent chunk writes
- ✅ M5: Gemini-call retries
- ✅ M6: Safer file handling + limits + tests/docs

## License

Apache 2.0

## Documentation

Additional documentation is available in the `/markdown` directory.

## Contributing

For questions or issues, please refer to the documentation in the `/markdown` directory.

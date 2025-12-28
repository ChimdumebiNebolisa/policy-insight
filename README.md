# PolicyInsight

Production-grade legal document analysis service with Datadog observability. Built for the AI Partner Catalyst Devpost hackathon (Datadog Challenge track).

## Overview

PolicyInsight analyzes legal documents (PDFs) and outputs plain-English risk reports with mandatory source citations. Every claim references extracted text with page numbers, ensuring grounded AI safety.

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
- File upload limits (20 MB max)

## API Endpoints

### Health & Status

- `GET /health` - Basic health check
- `GET /actuator/readiness` - Readiness check (validates DB connection)

### Documents

- `POST /api/documents/upload` - Upload a PDF document (stubbed in Milestone 1)
- `GET /api/documents/{id}/status` - Get document processing status (stubbed in Milestone 1)

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

## Milestones

See [tasks.md](./tasks.md) for the complete implementation roadmap.

**Current Status**: Milestone 1 (Repo scaffold + local run + CI) ✅

## License

Apache 2.0

## Contributing

This is a hackathon submission. For questions or issues, please refer to the PRD documents.


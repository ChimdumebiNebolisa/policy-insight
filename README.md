# PolicyInsight

PolicyInsight is a lightweight Spring Boot app for grounded PDF policy analysis.

Flow:

`Upload PDF -> extract text -> chunk text -> generate Gemini or mock risk report -> validate chunk IDs -> save report -> view/share report -> grounded Q&A`

## Stack

- Java 21 and Spring Boot
- PostgreSQL with Flyway migrations
- Spring Data JPA
- PDFBox
- Gemini API through the Google Generative Language REST API
- Thymeleaf and HTMX
- Railway deployment
- Docker Compose only for local PostgreSQL

## Local Setup

Start PostgreSQL:

```powershell
docker compose up -d
```

Run tests:

```powershell
.\mvnw.cmd test
```

Run the app with mock AI:

```powershell
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/policyinsight"
$env:SPRING_DATASOURCE_USERNAME="policyinsight"
$env:SPRING_DATASOURCE_PASSWORD="policyinsight"
$env:APP_TOKEN_SECRET="replace-with-a-long-random-secret"
$env:APP_AI_PROVIDER="mock"
.\mvnw.cmd spring-boot:run
```

Open `http://localhost:8080`.

## Environment Variables

Required:

- `DATABASE_URL`: Railway-style `postgres://user:password@host:port/db` or JDBC `jdbc:postgresql://...`
- `GEMINI_API_KEY`: Gemini API key, required only when `APP_AI_PROVIDER=gemini`
- `APP_TOKEN_SECRET`: long random secret used to hash owner/share tokens

Optional:

- `APP_AI_PROVIDER`: `mock` by default, set to `gemini` for live Gemini calls
- `GEMINI_MODEL`: defaults to `gemini-1.5-flash`
- `GEMINI_TIMEOUT_SECONDS`: defaults to `30`
- `APP_UPLOAD_MAX_BYTES`: defaults to `10485760`
- `APP_OWNER_TOKEN_TTL_MINUTES`: defaults to `120`
- `APP_SHARE_TTL_DAYS`: defaults to `7`

## Railway Deployment

1. Create a Railway project.
2. Add a Railway PostgreSQL database.
3. Deploy this GitHub repository and branch.
4. Configure environment variables:
   - `DATABASE_URL`
   - `APP_TOKEN_SECRET`
   - `APP_AI_PROVIDER=gemini`
   - `GEMINI_API_KEY`
5. Deploy. Flyway runs migrations on application startup.

The app does not use GCP, Cloud Run, Cloud SQL, GCS, Pub/Sub, Vertex AI, Datadog, Kubernetes, uploaded-file storage, or a worker service.

## Security Notes

- Uploaded PDFs are read in memory for text extraction and are not stored.
- Direct report pages require the owner cookie created during upload.
- Public report access is only through `/shared/{token}`.
- Share tokens are generated with `SecureRandom`; only HMAC hashes are stored.
- AI and user-generated text is rendered through escaped Thymeleaf expressions.
- Upload and Q&A are protected with simple in-memory per-IP rate limiting.
- Railway and live Gemini mode reject the default or short `APP_TOKEN_SECRET`.

## Useful Commands

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
docker compose up -d
docker compose down
```

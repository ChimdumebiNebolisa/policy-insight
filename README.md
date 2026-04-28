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
- Render deployment
- Docker Compose only for local PostgreSQL

## Local Setup

Reset and start PostgreSQL from a clean local volume:

```powershell
docker compose down -v
docker compose up -d
```

Run tests:

```powershell
.\mvnw.cmd test
```

Run the app with mock AI:

```powershell
$env:DATABASE_URL=$null
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:55432/policyinsight"
$env:SPRING_DATASOURCE_USERNAME="policyinsight"
$env:SPRING_DATASOURCE_PASSWORD="policyinsight"
$env:APP_TOKEN_SECRET="local-development-token-secret-change-before-deploy"
$env:APP_AI_PROVIDER="mock"
.\mvnw.cmd spring-boot:run
```

Open `http://localhost:8080`.

Shortcut:

```powershell
.\scripts\run-local.ps1
```

The local database is exposed on host port `55432` to avoid collisions with a Windows-installed PostgreSQL service on `5432`. If PostgreSQL reports `password authentication failed for user "policyinsight"`, verify `SPRING_DATASOURCE_URL` uses port `55432`, then reset the local Docker volume with `docker compose down -v` and start again from the same repository directory. PostgreSQL only reads `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_DB` when the database volume is first initialized.

## Environment Variables

Required for Render or Railway:

- `DATABASE_URL`: Render/Railway-style `postgresql://user:password@host:port/db`, `postgres://...`, or JDBC `jdbc:postgresql://...`
- `GEMINI_API_KEY`: Gemini API key, required only when `APP_AI_PROVIDER=gemini`
- `APP_TOKEN_SECRET`: long random secret used to hash owner/share tokens

Required for local development:

- `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/policyinsight`
- `SPRING_DATASOURCE_USERNAME=policyinsight`
- `SPRING_DATASOURCE_PASSWORD=policyinsight`
- `APP_TOKEN_SECRET=local-development-token-secret-change-before-deploy`
- `APP_AI_PROVIDER=mock`

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

Railway can use `railway.json` and the included `Dockerfile`. The PostgreSQL plugin supplies `DATABASE_URL`; do not set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, or `SPRING_DATASOURCE_PASSWORD` in Railway unless you intentionally want to override `DATABASE_URL`.

The app does not use GCP, Cloud Run, Cloud SQL, GCS, Pub/Sub, Vertex AI, Datadog, Kubernetes, uploaded-file storage, or a worker service.

## Render Deployment

Render can deploy the included `Dockerfile` directly. The included `render.yaml` is a Render Blueprint that creates one Docker web service and one Render Postgres database, then wires the database's internal connection string into `DATABASE_URL`.

Blueprint setup:

1. Push this branch to GitHub.
2. In Render, choose **New +** -> **Blueprint**.
3. Connect `ChimdumebiNebolisa/policy-insight`.
4. Select the `rebuild/simple-gemini-railway` branch.
5. Review the `policy-insight` web service and `policy-insight-db` Postgres database.
6. Add the required secret values when Render prompts for unsynced env vars:
   - `APP_TOKEN_SECRET`
   - `GEMINI_API_KEY`
7. Apply the Blueprint. Flyway runs migrations on application startup.

Manual web service setup:

1. Create a Render Postgres database in the same region as the web service.
2. Create a Render Web Service from this repository and branch.
3. Set Runtime to `Docker`.
4. Set Health Check Path to `/health`.
5. Add environment variables:
   - `DATABASE_URL=<Render Postgres internal database URL>`
   - `APP_TOKEN_SECRET=<long random production secret>`
   - `APP_AI_PROVIDER=gemini`
   - `GEMINI_API_KEY=<your Gemini API key>`
   - `PORT=10000`
6. Deploy. Do not set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, or `SPRING_DATASOURCE_PASSWORD` on Render unless you intentionally want to override `DATABASE_URL`.

Render's internal Postgres URL has the form `postgresql://USER:PASSWORD@HOST:PORT/DATABASE`. PolicyInsight converts that value into Spring's JDBC datasource settings at startup.
Render expects web services to bind to `$PORT`; PolicyInsight maps `server.port` to `${PORT:8080}` so local runs still use `8080`.

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

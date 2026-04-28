$ErrorActionPreference = "Stop"

Set-Location -Path (Resolve-Path "$PSScriptRoot\..")

docker compose down -v
docker compose up -d

$env:DATABASE_URL = $null
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:55432/policyinsight"
$env:SPRING_DATASOURCE_USERNAME = "policyinsight"
$env:SPRING_DATASOURCE_PASSWORD = "policyinsight"
$env:APP_TOKEN_SECRET = "local-development-token-secret-change-before-deploy"
$env:APP_AI_PROVIDER = "mock"

.\mvnw.cmd spring-boot:run

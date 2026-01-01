# Security Guide

This document outlines security best practices for the PolicyInsight project, including how to handle secrets, environment variables, and prevent accidental secret exposure.

## Table of Contents

- [Environment Variables](#environment-variables)
- [Secret Management](#secret-management)
- [Git Configuration](#git-configuration)
- [Secret Scanning](#secret-scanning)
- [What is Ignored](#what-is-ignored)
- [Manual Follow-up for Historical Secrets](#manual-follow-up-for-historical-secrets)

## Environment Variables

### Setting Environment Variables

All sensitive configuration values should be provided via environment variables, never hardcoded in source files.

#### Local Development (PowerShell)

```powershell
# Set individual variables
$env:DB_PASSWORD = "your-secure-password"
$env:DD_API_KEY = "your-datadog-api-key"
$env:DD_APP_KEY = "your-datadog-app-key"

# Or use .env file (recommended)
# Copy .env.example to .env and fill in values
Copy-Item .env.example .env
# Edit .env with your actual values
```

#### Local Development (Bash/Shell)

```bash
# Set individual variables
export DB_PASSWORD="your-secure-password"
export DD_API_KEY="your-datadog-api-key"
export DD_APP_KEY="your-datadog-app-key"

# Or use .env file (recommended)
# Copy .env.example to .env and fill in values
cp .env.example .env
# Edit .env with your actual values
```

#### Using .env Files

1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env  # Linux/macOS
   Copy-Item .env.example .env  # Windows PowerShell
   ```

2. Edit `.env` with your actual values (this file is gitignored)

3. Load environment variables:
   - **PowerShell**: Use a module like `dotenv` or manually source
   - **Bash**: Use `source .env` or `export $(cat .env | xargs)`
   - **Docker Compose**: Automatically loads `.env` file

### Required Environment Variables

See `.env.example` for a complete list of all environment variables. Key categories:

- **Database**: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- **Datadog**: `DD_API_KEY`, `DD_APP_KEY`, `DD_SITE`
- **GCP**: `GOOGLE_CLOUD_PROJECT`, `GOOGLE_APPLICATION_CREDENTIALS`
- **Application**: `SERVER_PORT`, `APP_BASE_URL`, etc.

## Secret Management

### What Counts as a Secret?

The following should **never** be hardcoded:

- API keys (Datadog, Google Cloud, etc.)
- Application keys
- Passwords (database, service accounts)
- Tokens (OAuth, JWT secrets)
- Private keys (PEM, PKCS files)
- Service account JSON files
- Database credentials
- Cloud credentials
- Authorization headers with real tokens

### Best Practices

1. **Use Environment Variables**: Always use `${VAR}` or `$env:VAR` instead of hardcoded values
2. **Use .env Files for Local Dev**: Keep `.env` in `.gitignore`, commit `.env.example` with placeholders
3. **Use Secret Managers in Production**:
   - Google Cloud Secret Manager
   - AWS Secrets Manager
   - Azure Key Vault
   - GitHub Secrets (for CI/CD)
4. **Rotate Secrets Regularly**: If a secret is exposed, rotate it immediately
5. **Never Commit Secrets**: Even if "it's just for local dev" - use environment variables

## Git Configuration

### Files Ignored by .gitignore

The following patterns are automatically excluded from git:

- `.env`, `.env.local`, `.env.*.local` (but `.env.example` is tracked)
- `**/*service-account*.json`
- `**/*credentials*.json`
- `**/*.pem`, `**/*.key`, `**/*.p12`, `**/*.pfx`
- `**/*.jks`, `**/*.keystore`
- `**/*secret*.json`, `**/*secret*.yml`, `**/*secret*.yaml`, `**/*secret*.properties`
- `dd-*.json` (local Datadog export artifacts)

### Verifying .gitignore

Before committing, verify sensitive files are ignored:

```bash
# Check if a file would be committed
git check-ignore -v .env
git check-ignore -v path/to/service-account.json
```

## Secret Scanning

### Automated Scanning

We use **gitleaks** to automatically scan for secrets in CI/CD and locally.

#### GitHub Actions CI

Every push and pull request automatically runs gitleaks. The build will fail if secrets are detected.

#### Local Pre-commit Hook

Install the pre-commit hook to scan before each commit:

**Bash (Linux/macOS):**
```bash
# Make script executable
chmod +x scripts/pre-commit-secret-scan.sh

# Install as git hook
cp scripts/pre-commit-secret-scan.sh .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

**PowerShell (Windows):**
```powershell
# Install as git hook
Copy-Item scripts/pre-commit-secret-scan.ps1 .git/hooks/pre-commit
```

#### Manual Scanning

Run gitleaks manually:

```bash
# Install gitleaks (if not installed)
# macOS: brew install gitleaks
# Windows: choco install gitleaks
# Linux: See https://github.com/gitleaks/gitleaks#installation

# Scan repository
gitleaks detect --source . --verbose
```

### What Gets Scanned

gitleaks detects:
- API keys (various formats)
- Passwords in connection strings
- Private keys
- Tokens
- Database credentials
- Cloud service credentials
- And many more patterns

## What is Ignored

### Datadog Exported Assets

The following are **NOT** considered secrets and are tracked in git:
- `datadog/dashboards/*.json` - Dashboard configurations (no secrets)
- `datadog/monitors/*.json` - Monitor configurations (no secrets)
- `datadog/slos/*.json` - SLO configurations (no secrets)

These files contain only configuration metadata, not API keys or credentials.

### Example Values in Documentation

Documentation files may contain example values like:
- `"your-api-key"` - These are placeholders, not real secrets
- `"postgres"` - Default local dev password (acceptable in docs)

## Manual Follow-up for Historical Secrets

### If Secrets Were Previously Committed

If secrets were committed to git history (even if removed from current HEAD):

1. **Immediately Rotate the Secret**:
   - Generate new API keys
   - Change passwords
   - Revoke old credentials

2. **Consider History Rewrite** (if repository is private or team is small):
   ```bash
   # WARNING: This rewrites history - coordinate with team
   git filter-branch --force --index-filter \
     "git rm --cached --ignore-unmatch path/to/secret-file" \
     --prune-empty --tag-name-filter cat -- --all
   ```

3. **For Public Repositories**:
   - Assume secrets are compromised
   - Rotate all exposed secrets
   - Do NOT rewrite history (it's already public)
   - Consider using GitHub's secret scanning alerts

4. **Update Documentation**: Ensure team knows which secrets were rotated

### Current Status

As of the security audit, all hardcoded secrets have been removed from the current codebase. All sensitive values now use environment variables with safe defaults for local development.

## Configuration Files

### Spring Boot Configuration

- `src/main/resources/application.yml` - Uses `${VAR:default}` placeholders ✅
- `src/main/resources/application-local.yml` - Uses environment variables ✅
- `src/test/resources/application-test.yml` - Uses environment variables ✅

### Docker Compose

- `docker-compose.yml` - Uses `${VAR:-default}` syntax ✅
- `docker-compose.datadog.yml` - Uses environment variables ✅

### Scripts

All scripts now read from environment variables:
- `scripts/start-and-generate-openapi.ps1` ✅
- `scripts/quick-start-app.ps1` ✅
- `scripts/db-doctor.ps1` ✅
- `scripts/datadog/*.ps1` and `*.sh` ✅

## Reporting Security Issues

If you discover a security vulnerability:

1. **DO NOT** create a public issue
2. Contact the maintainers privately
3. Include steps to reproduce
4. Allow time for fix before public disclosure

## Additional Resources

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [GitHub Security Best Practices](https://docs.github.com/en/code-security)
- [gitleaks Documentation](https://github.com/gitleaks/gitleaks)
- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)


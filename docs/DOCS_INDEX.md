# Docs Index

## 0) How to use this index
Start with `README.md` for a project overview and local run steps, then read `markdown/PolicyInsight_PRD.md` for product scope and architecture, and `markdown/DEPLOYMENT.md` (after resolving its conflict markers) or `docs/MILESTONE_3_PLAN.md` for Cloud Run deployment steps; use this index to jump into audits, metrics, and runbooks as needed.

## 1) Docs map (table)

| Doc | Location | Purpose | When to read | Last updated (git log) | Depends on |
| --- | --- | --- | --- | --- | --- |
| Codebase audit | `CODEBASE_AUDIT.md` | Full repo audit and architecture map | First deep-dive into system internals | `4d4d11d 2026-01-25 chore: remove Document AI path (Gemini-only pipeline)` | `README.md`, `pom.xml`, `src/main/**`, `src/main/resources/**` |
| Perf + extraction metrics harness | `PERF_METRICS.md` | How to run k6 and extraction health metrics | When running perf/health harness | `d13a064 2026-01-24 test: align suite with job-token protection and disable scheduling in tests` | `docker-compose.yml`, `eval/**`, `scripts/**` |
| Main README | `README.md` | Project overview, local dev, API, CI/CD | First read for new contributors | `32cb323 2026-01-24 docs: fix README commands and local run instructions` | `pom.xml`, `docker-compose.yml`, `src/main/resources/**` |
| Repo audit report | `docs/AUDIT_REPORT.md` | Deployability + functionality audit | When validating run readiness | `4d4d11d 2026-01-25 chore: remove Document AI path (Gemini-only pipeline)` | `README.md`, `docker-compose.yml`, `src/main/**` |
| Public demo blocker report | `docs/DEMO_PUBLIC_CLOUDRUN_BLOCKER_REPORT.md` | Public demo readiness + blocker | Before public demo rollout | `3609dcf 2026-01-25 docs: add public demo evidence (cloud run + pubsub)` | `infra/cloudrun/**`, `docs/MILESTONE_3_EXECUTION_REPORT.md` |
| Metrics spec | `docs/METRICS_SPEC.md` | API metrics spec for harness | When building metrics harness | `4d4d11d 2026-01-25 chore: remove Document AI path (Gemini-only pipeline)` | `application.yml`, `src/main/java/**` |
| Milestone 2 diagnostic report | `docs/MILESTONE_2_DIAG_REPORT.md` | Root-cause analysis for local runtime issues | When diagnosing local runtime | `f6feedf 2026-01-24 docs: add local runtime verification script and milestone 2 evidence` | `scripts/**`, `docker-compose.yml` |
| Milestone 2 plan | `docs/MILESTONE_2_PLAN.md` | Plan for local runtime fixes | When executing milestone 2 | `f6feedf 2026-01-24 docs: add local runtime verification script and milestone 2 evidence` | `docker-compose.yml`, `application.yml`, `scripts/**` |
| Milestone 3 execution | `docs/MILESTONE_3_EXECUTION_REPORT.md` | Cloud Run deploy evidence | When auditing deploy run | `f26ce6c 2026-01-25 docs: add Milestone 3 Cloud Run execution evidence` | `docs/MILESTONE_3_PLAN.md`, `infra/cloudrun/**` |
| Milestone 3 plan | `docs/MILESTONE_3_PLAN.md` | Cloud Run runbook | Before running Cloud Run deploy | `4d4d11d 2026-01-25 chore: remove Document AI path (Gemini-only pipeline)` | `pom.xml`, `application.yml`, `infra/cloudrun/**` |
| HTTP headers sample | `headers.txt` | Static headers or curl notes | Rare; when debugging HTTP headers | `e49b325 2025-12-29 fix(api): standardize JSON error responses for export/share` | None |
| Demo guide | `markdown/DEMO.md` | Local + production demo script | When demoing the app | `11780eb 2026-01-06 Clean up repository: remove hackathon references and organize documentation` | `README.md`, `scripts/smoke_test.*` |
| Deployment guide | `markdown/DEPLOYMENT.md` | GCP deployment steps | When setting up GCP infra | `4d4d11d 2026-01-25 chore: remove Document AI path (Gemini-only pipeline)` | `infra/**`, `.github/workflows/**` |
| Metrics results | `markdown/METRICS.md` | Recorded perf results | When citing metrics results | `4d4d11d 2026-01-25 chore: remove Document AI path (Gemini-only pipeline)` | `PERF_METRICS.md` |
| IDE notes | `markdown/NOTES-IDE.md` | IDE false positives and fixes | When IDE shows errors | `d13a064 2026-01-24 test: align suite with job-token protection and disable scheduling in tests` | `pom.xml` |
| Datadog PRD | `markdown/PolicyInsight_Datadog_PRD.md` | Product + Datadog observability PRD | When focusing on observability scope | `0c17926 2026-01-25 docs: finalize Document AI removal (env example + datadog prd)` | `datadog/**`, `src/main/java/**` |
| Product PRD | `markdown/PolicyInsight_PRD.md` | Full product requirements | When defining product scope | `5ed3545 2026-01-25 docs: remove Document AI remnants from PRD and monitors` | `docs/openapi.json`, `src/main/java/**` |
| Evaluation guide | `markdown/README-EVALUATION.md` | Evidence pointers for reviewers | Before evaluation | `11780eb 2026-01-06 Clean up repository: remove hackathon references and organize documentation` | `datadog/**`, `docs/OBSERVABILITY.md` |
| Submission guide | `markdown/README-SUBMISSION.md` | Submission pointers | Before submission | `11780eb 2026-01-06 Clean up repository: remove hackathon references and organize documentation` | `datadog/**`, `docs/OBSERVABILITY.md` |
| Security findings | `markdown/SECURITY_FINDINGS.md` | Security audit evidence | When verifying secret hygiene | `11780eb 2026-01-06 Clean up repository: remove hackathon references and organize documentation` | `.gitignore`, `scripts/**` |
| Pub/Sub verification | `markdown/VERIFICATION_SUMMARY.md` | Pub/Sub push verification | When validating Pub/Sub behavior | `d13a064 2026-01-24 test: align suite with job-token protection and disable scheduling in tests` | `.github/workflows/cd.yml`, `scripts/**` |
| Datadog assets README | `markdown/datadog/README.md` | Datadog export/apply guidance | When exporting/applying Datadog assets | `d13a064 2026-01-24 test: align suite with job-token protection and disable scheduling in tests` | `datadog/**`, `scripts/datadog/**` |
| Datadog templates README | `markdown/datadog/templates/README.md` | Template usage for Datadog assets | When using templates | `d13a064 2026-01-24 test: align suite with job-token protection and disable scheduling in tests` | `datadog/templates/**`, `scripts/datadog/**` |
| Datadog script fixes | `markdown/docs/DATADOG_SCRIPT_FIXES.md` | Changes to Datadog scripts | When auditing datadog tooling | `11780eb 2026-01-06 Clean up repository: remove hackathon references and organize documentation` | `scripts/datadog/**` |
| Observability guide | `markdown/docs/OBSERVABILITY.md` | How to run Datadog observability | When configuring observability | `11780eb 2026-01-06 Clean up repository: remove hackathon references and organize documentation` | `docker-compose.datadog.yml`, `Dockerfile`, `scripts/datadog/**` |
| Security guide | `markdown/docs/SECURITY.md` | Security best practices | When managing secrets | `d13a064 2026-01-24 test: align suite with job-token protection and disable scheduling in tests` | `.env.example`, `.gitignore`, `scripts/pre-commit-secret-scan.*` |
| Implementation status | `markdown/tasks.md` | Milestone completion status | When confirming project completeness | `d13a064 2026-01-24 test: align suite with job-token protection and disable scheduling in tests` | None |
| PDF fixture | `src/test/resources/valid.pdf` | Test PDF used in scripts/tests | When running smoke/integration tests | `20a03f9 2026-01-24 test: add valid pdf fixture for local verification` | `scripts/verify-local.*`, `src/test/**` |

## 2) Detailed breakdown (one subsection per doc)

### `CODEBASE_AUDIT.md`
- Path: `CODEBASE_AUDIT.md`
- Type: audit report
- Audience: engineers, reviewers
- Purpose: comprehensive audit of architecture, data flow, reliability, and security
- Key contents (by heading): repository map, architecture/data flow, APIs/UI behavior, persistence model, background processing, LLM integration, tests, observability/deployment, security review, prioritized fixes
- How it connects to the system: maps controllers, workers, storage, and DB models in `src/main/java/**` and `src/main/resources/**`
- Known gaps or conflicts: none noted
- Related repo references: `src/main/java/**`, `src/main/resources/**`, `pom.xml`, `docker-compose.yml`

### `PERF_METRICS.md`
- Path: `PERF_METRICS.md`
- Type: runbook/spec
- Audience: engineers running perf or extraction health checks
- Purpose: run k6 load tests and extraction health metrics
- Key contents (by heading): prerequisites, env vars, k6 load tests, k6 summarizer, extraction health metrics, report JSON endpoint, troubleshooting
- How it connects to the system: depends on running API and report JSON endpoint
- Known gaps or conflicts: none noted
- Related repo references: `eval/**`, `scripts/**`, `src/main/java/**`

### `README.md`
- Path: `README.md`
- Type: README
- Audience: new contributors, evaluators
- Purpose: project overview and local development instructions
- Key contents (by heading): overview, tech stack, local dev steps, API endpoints, CI/CD, security model, rate limiting, job processing, milestones
- How it connects to the system: references local run, API routes, and config in `application.yml`
- Known gaps or conflicts: none noted
- Related repo references: `docker-compose.yml`, `pom.xml`, `src/main/resources/application.yml`

### `docs/AUDIT_REPORT.md`
- Path: `docs/AUDIT_REPORT.md`
- Type: audit report
- Audience: engineering and reviewers
- Purpose: deployability and functionality audit evidence
- Key contents (by heading): repo map, run instructions, failure points, system architecture, DB, API surface, integrations, observability, security, deployability
- How it connects to the system: maps to controllers, DB, and infra configs
- Known gaps or conflicts: none noted
- Related repo references: `src/main/java/**`, `src/main/resources/**`, `.github/workflows/**`

### `docs/DEMO_PUBLIC_CLOUDRUN_BLOCKER_REPORT.md`
- Path: `docs/DEMO_PUBLIC_CLOUDRUN_BLOCKER_REPORT.md`
- Type: blocker report
- Audience: release owners, demo operators
- Purpose: identify public demo blocker and resolution steps
- Key contents (by heading): definition of done, current deployed state, main blocker, demo architecture, make-public steps, verification, evidence
- How it connects to the system: references Cloud Run services and job-token flow
- Known gaps or conflicts: none noted
- Related repo references: `infra/cloudrun/**`, `.github/workflows/cd.yml`, `src/main/java/**`

### `docs/METRICS_SPEC.md`
- Path: `docs/METRICS_SPEC.md`
- Type: spec
- Audience: engineers building metrics harness
- Purpose: define endpoints, payloads, and metrics for k6 harness
- Key contents (by heading): runtime entry points, upload/extract/search endpoints, size handling, extraction output contract, test hooks, measurement checklist
- How it connects to the system: maps API contracts to code and DB schema
- Known gaps or conflicts: none noted
- Related repo references: `src/main/java/**`, `src/main/resources/application.yml`

### `docs/MILESTONE_2_DIAG_REPORT.md`
- Path: `docs/MILESTONE_2_DIAG_REPORT.md`
- Type: diagnostic report
- Audience: engineers troubleshooting local runtime
- Purpose: capture root causes and evidence for milestone 2 issues
- Key contents (by heading): cmd popup root cause, search results, Postgres mismatch evidence, patch diff
- How it connects to the system: references scripts and docker-compose behavior
- Known gaps or conflicts: none noted
- Related repo references: `scripts/verify-local.ps1`, `docker-compose.yml`

### `docs/MILESTONE_2_PLAN.md`
- Path: `docs/MILESTONE_2_PLAN.md`
- Type: plan/runbook
- Audience: engineers executing milestone 2
- Purpose: plan for local runtime fixes and verification script
- Key contents (by heading): objective/success criteria, repo inventory, env strategy, transaction strategy, verification script, rollback, commit plan
- How it connects to the system: ties DB config and worker transaction flow to code
- Known gaps or conflicts: none noted
- Related repo references: `docker-compose.yml`, `src/main/java/**`, `scripts/**`

### `docs/MILESTONE_3_EXECUTION_REPORT.md`
- Path: `docs/MILESTONE_3_EXECUTION_REPORT.md`
- Type: execution report
- Audience: engineers and reviewers
- Purpose: Cloud Run deploy evidence and command outputs
- Key contents (by heading): gcloud setup, APIs, Cloud SQL, secrets, deploy attempts, smoke tests, command failure log
- How it connects to the system: validates deployment and runtime behavior
- Known gaps or conflicts: none noted
- Related repo references: `docs/MILESTONE_3_PLAN.md`, `infra/cloudrun/**`, `.github/workflows/**`

### `docs/MILESTONE_3_PLAN.md`
- Path: `docs/MILESTONE_3_PLAN.md`
- Type: runbook
- Audience: deployment operators
- Purpose: step-by-step Cloud Run deployment plan
- Key contents (by heading): preflight, commit hygiene, deploy checklist, env vars, rollback
- How it connects to the system: drives deployment and runtime config
- Known gaps or conflicts: none noted
- Related repo references: `infra/cloudrun/**`, `application.yml`, `pom.xml`

### `headers.txt`
- Path: `headers.txt`
- Type: reference snippet
- Audience: engineers debugging HTTP requests
- Purpose: store headers or curl notes
- Key contents (by heading): none (plain text)
- How it connects to the system: likely used for API testing
- Known gaps or conflicts: none noted
- Related repo references: none

### `markdown/DEMO.md`
- Path: `markdown/DEMO.md`
- Type: demo guide
- Audience: demo operators
- Purpose: local and production demo steps
- Key contents (by heading): local demo steps, automated smoke tests, production demo, troubleshooting
- How it connects to the system: uses upload, status, and report endpoints
- Known gaps or conflicts: overlaps with public demo blocker report
- Related repo references: `scripts/smoke_test.*`, `src/main/java/**`

### `markdown/DEPLOYMENT.md`
- Path: `markdown/DEPLOYMENT.md`
- Type: deployment guide
- Audience: infra and ops
- Purpose: GCP setup and deploy steps
- Key contents (by heading): required APIs, WIF setup, artifact registry, Cloud SQL, GCS, Pub/Sub, GitHub secrets, verification commands, troubleshooting
- How it connects to the system: maps to Cloud Run deployment and CI/CD
- Known gaps or conflicts: file contains merge conflict markers at top (`<<<<<<< HEAD`)
- Related repo references: `.github/workflows/**`, `infra/**`, `Dockerfile`

### `markdown/METRICS.md`
- Path: `markdown/METRICS.md`
- Type: results report
- Audience: reviewers
- Purpose: recorded performance metrics and how to reproduce
- Key contents (by heading): prerequisites, load test runs, results, metric definitions
- How it connects to the system: references load test tooling and API endpoints
- Known gaps or conflicts: overlaps with `PERF_METRICS.md`
- Related repo references: `PERF_METRICS.md`, `scripts/**`

### `markdown/NOTES-IDE.md`
- Path: `markdown/NOTES-IDE.md`
- Type: troubleshooting notes
- Audience: contributors
- Purpose: explain IDE false positives and workflow warnings
- Key contents (by heading): stale JDT errors, GitHub Actions context warnings
- How it connects to the system: mitigates local dev confusion
- Known gaps or conflicts: none noted
- Related repo references: `.github/workflows/**`

### `markdown/PolicyInsight_Datadog_PRD.md`
- Path: `markdown/PolicyInsight_Datadog_PRD.md`
- Type: PRD
- Audience: product and platform engineers
- Purpose: full PRD with Datadog-first scope
- Key contents (by heading): executive summary, scope, architecture, data model, API contract, pipeline stages, UI flows, grounding rules, observability plan, CI/CD plan, deployment checklist, build plan
- How it connects to the system: maps features to code, infra, and Datadog assets
- Known gaps or conflicts: overlaps with `markdown/PolicyInsight_PRD.md`
- Related repo references: `datadog/**`, `src/main/java/**`, `.github/workflows/**`

### `markdown/PolicyInsight_PRD.md`
- Path: `markdown/PolicyInsight_PRD.md`
- Type: PRD
- Audience: product and engineering
- Purpose: product requirements and system design
- Key contents (by heading): executive summary, MVP scope, architecture, data model, API contract, pipeline, UI flows, grounding rules, observability, deployment guide, build plan
- How it connects to the system: ties to API endpoints and UI routes
- Known gaps or conflicts: overlaps with `markdown/PolicyInsight_Datadog_PRD.md`
- Related repo references: `docs/openapi.json`, `src/main/java/**`

### `markdown/README-EVALUATION.md`
- Path: `markdown/README-EVALUATION.md`
- Type: evaluation guide
- Audience: reviewers
- Purpose: evidence links for evaluation
- Key contents (by heading): quick links, evidence locations, verification, code structure
- How it connects to the system: points to observability and code locations
- Known gaps or conflicts: overlaps with `markdown/README-SUBMISSION.md`
- Related repo references: `datadog/**`, `markdown/docs/OBSERVABILITY.md`

### `markdown/README-SUBMISSION.md`
- Path: `markdown/README-SUBMISSION.md`
- Type: submission guide
- Audience: judges/reviewers
- Purpose: submission evidence pointers
- Key contents (by heading): quick links, evidence locations, verification, code structure
- How it connects to the system: points to observability and code locations
- Known gaps or conflicts: overlaps with `markdown/README-EVALUATION.md`
- Related repo references: `datadog/**`, `markdown/docs/OBSERVABILITY.md`

### `markdown/SECURITY_FINDINGS.md`
- Path: `markdown/SECURITY_FINDINGS.md`
- Type: audit report
- Audience: security-minded reviewers
- Purpose: summarize secret scanning and remediation
- Key contents (by heading): findings by area, remediation actions, verification steps
- How it connects to the system: documents config and script hardening
- Known gaps or conflicts: none noted
- Related repo references: `.gitignore`, `scripts/**`

### `markdown/VERIFICATION_SUMMARY.md`
- Path: `markdown/VERIFICATION_SUMMARY.md`
- Type: verification summary
- Audience: engineers validating Pub/Sub push
- Purpose: document Pub/Sub push implementation status
- Key contents (by heading): idempotency, error semantics, correlation IDs, contract tests, config, verification scripts
- How it connects to the system: ties to Pub/Sub push handler and CI/CD config
- Known gaps or conflicts: none noted
- Related repo references: `.github/workflows/cd.yml`, `src/main/java/**`, `scripts/**`

### `markdown/datadog/README.md`
- Path: `markdown/datadog/README.md`
- Type: runbook
- Audience: engineers managing Datadog assets
- Purpose: export/apply Datadog dashboards, monitors, SLOs
- Key contents (by heading): directory structure, export commands, apply templates, configuration details
- How it connects to the system: uses Datadog API scripts and assets in `datadog/**`
- Known gaps or conflicts: none noted
- Related repo references: `datadog/**`, `scripts/datadog/**`

### `markdown/datadog/templates/README.md`
- Path: `markdown/datadog/templates/README.md`
- Type: runbook
- Audience: engineers managing templates
- Purpose: use templates to create/update Datadog assets
- Key contents (by heading): usage for apply/export, important notes
- How it connects to the system: ties to `scripts/datadog/*.py`
- Known gaps or conflicts: none noted
- Related repo references: `datadog/templates/**`, `scripts/datadog/**`

### `markdown/docs/DATADOG_SCRIPT_FIXES.md`
- Path: `markdown/docs/DATADOG_SCRIPT_FIXES.md`
- Type: change summary
- Audience: engineers reviewing tooling changes
- Purpose: summarize Datadog script remediation
- Key contents (by heading): correction list, files changed, verification, design decisions
- How it connects to the system: documents changes to Datadog tooling
- Known gaps or conflicts: none noted
- Related repo references: `scripts/datadog/**`

### `markdown/docs/OBSERVABILITY.md`
- Path: `markdown/docs/OBSERVABILITY.md`
- Type: runbook
- Audience: engineers enabling observability
- Purpose: Datadog setup for local and Cloud Run
- Key contents (by heading): local setup, Cloud Run deployment, export/apply assets, monitor testing, verification, troubleshooting
- How it connects to the system: depends on Datadog agent, Dockerfile, and scripts
- Known gaps or conflicts: none noted
- Related repo references: `Dockerfile`, `docker-compose.datadog.yml`, `scripts/datadog/**`

### `markdown/docs/SECURITY.md`
- Path: `markdown/docs/SECURITY.md`
- Type: security guide
- Audience: contributors
- Purpose: handling secrets and scanning
- Key contents (by heading): env vars, secret management, .gitignore, scanning, historical follow-up
- How it connects to the system: aligns with config and pre-commit scripts
- Known gaps or conflicts: none noted
- Related repo references: `.env.example`, `.gitignore`, `scripts/pre-commit-secret-scan.*`

### `markdown/tasks.md`
- Path: `markdown/tasks.md`
- Type: status summary
- Audience: reviewers
- Purpose: milestone completion checklist
- Key contents (by heading): completed milestones
- How it connects to the system: reflects repo state and commit history
- Known gaps or conflicts: none noted
- Related repo references: none

### `src/test/resources/valid.pdf`
- Path: `src/test/resources/valid.pdf`
- Type: test artifact (PDF)
- Audience: testers and automation
- Purpose: fixture for smoke/integration tests
- Key contents (by heading): not applicable
- How it connects to the system: used in scripts and tests for upload flow
- Known gaps or conflicts: none noted
- Related repo references: `scripts/verify-local.*`, `src/test/java/**`

## 3) Conflicts and duplicates
- Two PRDs overlap in scope: `markdown/PolicyInsight_PRD.md` and `markdown/PolicyInsight_Datadog_PRD.md`. Treat `markdown/PolicyInsight_PRD.md` as the primary product source of truth, and use the Datadog PRD for observability details.
- Two evaluation guides overlap: `markdown/README-EVALUATION.md` and `markdown/README-SUBMISSION.md`. Choose one canonical reviewer entrypoint and link to the other as a mirror.
- Metrics documents overlap: `docs/METRICS_SPEC.md`, `PERF_METRICS.md`, and `markdown/METRICS.md`. Treat `docs/METRICS_SPEC.md` as the spec, `PERF_METRICS.md` as the runbook, and `markdown/METRICS.md` as results evidence.
- Deployment guidance overlaps: `markdown/DEPLOYMENT.md` and `docs/MILESTONE_3_PLAN.md`. Use the milestone plan for exact runbook steps; fix `markdown/DEPLOYMENT.md` conflict markers before relying on it.
- Demo guidance overlaps: `markdown/DEMO.md` and `docs/DEMO_PUBLIC_CLOUDRUN_BLOCKER_REPORT.md`. Use the blocker report for public demo readiness; use the demo guide for local/production demo flow.

## 4) Missing docs
No missing docs block demoability, deployment, or onboarding, but `markdown/DEPLOYMENT.md` must be fixed (merge conflict markers) before it can serve as a reliable deployment guide.

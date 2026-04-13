# PolicyInsight Revamp Branch Plan

This document is the execution plan for the `policyinsight-revamp` branch only.

## Objective

- Build a production-credible Oracle-targeted deployment path.
- Make storage, messaging, LLM, and metrics integrations provider-agnostic.
- Preserve current route behavior and keep the existing GCP path functional during migration.

## Non-Negotiables

- No endpoint regressions for upload, status, report, share, sample-report, and sample-pdf flows.
- Existing Cloud Run deployment path remains working until Oracle path is validated.
- Changes are additive and reversible first; removals only after dual-path validation.
- Work is delivered in 5 scoped commit/push chunks.

## Branch Hygiene Protocol (Mandatory)

Run these before each coding session:

```powershell
git branch --show-current
git status -sb
git worktree list
```

Rules:

- Do all feature work in `policyinsight-revamp` worktree.
- Use separate worktree for `main` review only.
- Never push from `main` (local pre-push hook blocks this).

## Five Delivery Chunks

| Chunk | Scope | Primary Files | Validation Gate | Rollback Trigger |
|---|---|---|---|---|
| 1 | Plan and branch safety setup (completed) | `docs/POLICYINSIGHT_REVAMP_BRANCH_PLAN.md` | Plan committed and pushed | N/A |
| 2 | Config decoupling and profile contract | `src/main/resources/application*.yml`, `src/main/java/com/policyinsight/config/` | Local compile + local smoke pass | Default behavior changes under local/cloudrun |
| 3 | Storage/DB neutrality + migration scaffolding | `src/main/java/com/policyinsight/shared/model/PolicyJob.java`, `src/main/resources/db/migration/`, storage services | Flyway up + route parity pass | Data shape mismatch or route regressions |
| 4 | Messaging/LLM/metrics provider seams | messaging, processing, observability packages | Provider toggle tests pass | Retry/idempotency regressions |
| 5 | Oracle deployment assets, CI matrix, docs and runbook | `infra/`, `.github/workflows/`, `scripts/`, `README.md`, `docs/` | Dual-path deploy smoke pass | Cloud Run breaks or Oracle smoke fails |

## Phase 1: Baseline and Guardrails

### Tasks

1. Capture baseline branch state, compile/test status, and smoke endpoints.
2. Record architectural constraints in this plan and keep them stable.

### Target Files

- `docs/POLICYINSIGHT_REVAMP_BRANCH_PLAN.md`
- `README.md`

### Exit Criteria

- Baseline commands and outcomes are recorded.
- Known-good local startup path is confirmed.
- Required routes return expected HTTP statuses.

### Risk and Rollback

- Risk: stale baseline produces false regression alarms.
- Rollback: recapture baseline immediately and re-run smoke checks.

## Phase 2: Configuration Decoupling First

### Tasks

1. Add explicit provider selectors for db, storage, messaging, llm, and metrics.
2. Preserve current defaults for local and cloudrun profiles.
3. Centralize provider wiring in config package, avoiding scattered conditionals.

### Target Files

- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/main/resources/application-cloudrun.yml`
- `src/main/resources/application-demSleep.yml`
- `src/main/java/com/policyinsight/config/`

### Exit Criteria

- App boots with current local profile behavior unchanged.
- App boots with current cloudrun profile behavior unchanged.
- Provider switches are visible and documented.

### Risk and Rollback

- Risk: implicit defaults break existing environment behavior.
- Rollback: revert profile/property changes and restore previous defaults.

## Phase 3: Data, Storage, and Messaging Abstractions

### Tasks

1. Introduce provider-neutral artifact metadata for persisted paths.
2. Add transitional Flyway migration with safe backfill.
3. Add Oracle-ready db profile wiring while keeping Postgres default.
4. Add non-Pub/Sub messaging seam without removing Pub/Sub path.

### Target Files

- `src/main/java/com/policyinsight/shared/model/PolicyJob.java`
- `src/main/resources/db/migration/`
- `src/main/java/com/policyinsight/api/storage/`
- `src/main/java/com/policyinsight/api/messaging/`
- `src/main/resources/application*.yml`

### Exit Criteria

- Existing documents continue processing with no route contract changes.
- Migration runs successfully on local Postgres baseline snapshot.
- Oracle profile can start configuration path without compile/runtime config errors.
- Pub/Sub mode still works in current path.

### Risk and Rollback

- Risk: schema transitions break existing reads/writes.
- Rollback: stop rollout, revert migration commit, restore from pre-migration backup.

## Phase 4: LLM and Observability Provider Strategy

### Tasks

1. Keep Vertex provider and add a low-cost alternate provider behind common interface.
2. Add per-provider telemetry (latency, errors, token usage where available).
3. Add Prometheus-first profile while preserving Datadog path.

### Target Files

- `src/main/java/com/policyinsight/processing/`
- `src/main/java/com/policyinsight/observability/`
- `src/main/resources/application*.yml`
- `src/test/java/`

### Exit Criteria

- LLM provider can be switched by configuration only.
- Retry/failure behavior remains equivalent across providers.
- Metrics endpoint and configured backend are active for each target profile.

### Risk and Rollback

- Risk: provider mismatch causes output or retry regressions.
- Rollback: return to single-provider path and keep instrumentation changes isolated.

## Phase 5: Deployment, CI, and Cutover Readiness

### Tasks

1. Add Oracle-target deployment assets and scripts in parallel with Cloud Run assets.
2. Add CI validation matrix for local-postgres and oracle profile boot/tests.
3. Add migration and rollback runbook and finalize acceptance checks.

### Target Files

- `.github/workflows/cd.yml`
- `infra/cloudrun/web.yaml`
- `infra/cloudrun/worker.yaml`
- `infra/`
- `scripts/`
- `README.md`
- `docs/`

### Exit Criteria

- Existing Cloud Run path remains green.
- Oracle-target smoke deployment passes.
- CI gates cover both baseline and Oracle-ready profiles.
- Cutover and rollback steps are documented and reviewed.

### Risk and Rollback

- Risk: deployment path divergence creates release instability.
- Rollback: keep Cloud Run as primary and disable Oracle path until fixes land.

## Executable Verification Matrix

Run from repo root in PowerShell.

### Baseline and Local Behavior

```powershell
git branch --show-current
git status -sb
docker compose up -d
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd test
```

Expected:

- Current branch is `policyinsight-revamp`.
- Working tree is clean before new edits.
- Compile and tests succeed.

### Local Smoke Endpoints

Start app locally in a separate terminal, then run:

```powershell
Invoke-WebRequest http://localhost:8080/health -UseBasicParsing
Invoke-WebRequest http://localhost:8080/readiness -UseBasicParsing
Invoke-WebRequest http://localhost:8080/sample-report -UseBasicParsing
Invoke-WebRequest http://localhost:8080/sample-pdf -UseBasicParsing
```

Expected:

- All four endpoints return HTTP 200.

### Migration Safety

```powershell
.\mvnw.cmd test "-Dtest=*Migration*"
```

Expected:

- Migration-related tests pass with no schema validation failures.

### Provider Switching

Run profile/provider toggles used by this branch and verify same route contracts.

Expected:

- No HTTP contract changes for core routes.
- No increase in failed job rate for baseline smoke runs.

## Cutover Strategy

1. Keep Cloud Run path as primary while Oracle path is validated.
2. Run Oracle-target smoke deploy with branch-tagged artifacts.
3. Start with small canary exposure for internal validation.
4. Promote only after route parity, migration parity, and telemetry stability are confirmed.
5. If errors spike, revert traffic to Cloud Run baseline and halt cutover.

## Definition of Done

- Five chunk commits are complete and pushed.
- No endpoint regressions in baseline route set.
- Oracle-target path can be deployed and smoke-validated.
- Provider switches are config-only and tested.
- CI coverage includes baseline and Oracle-ready checks.
- Rollback runbook exists and is actionable.

## Scope Decisions

Included:

- Architecture decoupling.
- Oracle-path enablement.
- Provider abstraction.
- CI, verification, and deployment documentation for `policyinsight-revamp`.

Excluded:

- Full frontend redesign.
- New product features unrelated to portability, cost, or reliability.
- Removal of current GCP path before Oracle path is proven.

## Further Considerations

1. Start Oracle with profile-based compatibility mode before deep vendor tuning.
2. Keep Pub/Sub default until alternate queue path reaches retry/idempotency parity.
3. Prefer deterministic logic for chart-ready data; reserve LLM for extraction and explanation where it adds clear value.

# PolicyInsight Revamp Branch Plan

This document is the execution plan for the policyinsight-revamp branch only.

## Assumptions

- Oracle Cloud is the hosting target for PolicyInsight.
- Postgres remains the default database unless repository evidence clearly requires otherwise.
- Cloud Run path stays intact until Oracle path is proven.
- LLM provider may stay Vertex in phase 1 and can be swapped later behind a narrow seam if needed.
- Claros remains the GCP proof point.

## Objective

Move PolicyInsight to Oracle Cloud free tier quickly and credibly with lower run cost, while preserving current route behavior during transition.

This is a deployment and platform simplification effort, not a broad multi-provider architecture program.

## Non-Negotiables

- No endpoint regressions for upload, status, report, share, sample-report, and sample-pdf flows.
- Existing Cloud Run deployment path remains functional until Oracle path is validated.
- Changes are additive and reversible first; removals happen only after Oracle path passes validation.
- Branch hygiene is mandatory.
- Run before each coding session:

```powershell
git branch --show-current
git status -sb
git worktree list
```

- Do all feature work in policyinsight-revamp worktree.
- Never push from main.
- Local smoke checks are mandatory for backend-affecting changes.
- Every chunk has falsifiable exit criteria, explicit rollback triggers, and a rollback action.

## Operational Definitions

- Oracle deployment boots:
  - App process starts and serves HTTP on the configured port.
  - Flyway completes startup migration without migration error.
  - Active datasource is Postgres (for example, jdbc:postgresql URL or org.postgresql.Driver).
  - /health, /readiness, /sample-report, and /sample-pdf return HTTP 200.
  - One smoke PDF run completes upload -> status SUCCESS -> report render.

- Oracle runtime configuration for hosting differences only:
  - Changes are limited to deployment manifests, profiles, env vars, secret wiring, ports, and runtime flags.
  - No Oracle DB adoption work is introduced.
  - No broad new provider-abstraction track is introduced.

- Oracle path operable:
  - Oracle-hosted app processes at least one PDF end-to-end without Pub/Sub as a default dependency.
  - Default Oracle path does not require app.messaging.mode=gcp to complete smoke flow.

- Cost improved:
  - Default Oracle path has no required paid managed infra except optional LLM usage.
  - Default Oracle path has no mandatory dependency on Cloud SQL, Pub/Sub, Datadog, or other paid managed services.

- Status-flow parity:
  - Upload, polling, success/failure states, and report availability remain client-contract compatible with current behavior.
  - Internal execution model may change, but client-visible state transitions and endpoint contracts do not regress.

- Deterministic output sections:
  - Report fields or chart-ready data derived from deterministic parsing and business rules rather than LLM generation.
  - Examples include extracted metadata, document/job ids, normalized timestamps (only where normalization rules already exist), clause counts, category counts, table/chart JSON payloads, and other rule-derived aggregates.
  - Deterministic output sections must be identical across repeated runs of the same input after excluding documented volatile fields (for example generated ids, raw timestamps, signed URLs, and other explicitly documented non-deterministic fields).

- LLM dependency boundary:
  - App boot, /health, /readiness, /sample-report, and /sample-pdf must not require live LLM availability.
  - Default Oracle deployment path may allow optional LLM-backed enrichment for extraction, summary, or explanation.
  - Repository evidence currently shows a non-live fallback path (Gemini stub mode when vertexai.enabled=false), so default Oracle smoke flow should pass without live LLM dependency.
  - If upload->status->report smoke flow is configured to require live LLM inference, that must be explicitly documented as a scoped exception; in that case, the "not a hard dependency" rule applies to service boot and minimum route health only.

- Baseline regression threshold:
  - Any required route returns non-200 where baseline expects 200.
  - Upload->status->report smoke flow fails once in validation.
  - Repeatable latency degradation is greater than 30 percent on smoke path compared to captured baseline.
  - Any newly required paid service appears in default Oracle path.

## Corrected Scope Decisions

- Oracle Cloud means hosting target change first. It does not imply Oracle DB adoption.
- Postgres remains the default database in this revamp.
- Do not add broad provider-agnostic abstractions across db, storage, messaging, llm, and metrics in phase 1.
- Do not preserve Pub/Sub as a default runtime dependency for Oracle path.
- Oracle path should prefer synchronous or in-process background handling unless evidence forces a queue.
- Do not require provider-neutral metrics in phase 1.
- Keep GCP path functional only as rollback safety and regression guard, not as a parallel architecture program.
- Prefer deterministic logic for chart-ready data. Use LLM only where it clearly adds value.

## What Is Intentionally Not Being Generalized Yet

- No Oracle DB support track.
- No cross-cloud database abstraction layer.
- No universal storage abstraction beyond what already exists.
- No universal messaging abstraction redesign.
- No provider-neutral observability framework in phase 1.
- No enterprise canary/process ceremony beyond practical smoke and rollback checks.

## Delivery Chunks (4 Max)

| Chunk | Scope | Primary Files | Validation Gate | Rollback Trigger |
|---|---|---|---|---|
| 1 | Baseline lock and scope freeze | MUST_READ_FIRST/01_POLICYINSIGHT_REVAMP_BRANCH_PLAN.md, MUST_READ_FIRST/02_EXECUTION_GUARDRAILS.md, README.md | Baseline compile/test/smoke captured, including latency baseline and route contract | Baseline unstable, missing baseline artifact, or baseline checks fail |
| 2 | First working Oracle deployment path | infra/, scripts/, src/main/resources/application*.yml, docs/ | Oracle deployment boots definition fully passes; hosting-differences-only config rule passes | Any Oracle deployment boots check fails |
| 3 | Runtime simplification on Oracle path | src/main/java/com/policyinsight/api/messaging/, src/main/java/com/policyinsight/processing/, src/main/resources/application*.yml | Oracle path operable plus status-flow parity passes with Pub/Sub not required by default | Any status-flow parity breach or smoke flow failure |
| 4 | Cutover readiness and stabilization | infra/, scripts/, README.md, docs/ | Validation matrix passes twice; no baseline regression threshold breach; cost improved definition passes; Cloud Run fallback verified | Any baseline regression threshold breach or cost rule breach |

## Phased Delivery Plan

### Chunk 1: Baseline Lock and Scope Freeze

Tasks:

1. Capture baseline branch state and local behavior.
2. Confirm current route contracts and smoke endpoints.
3. Capture smoke-path latency baseline using the same PDF across three runs and record median.
4. Freeze migration scope to Oracle hosting-first.

Target files:

- MUST_READ_FIRST/01_POLICYINSIGHT_REVAMP_BRANCH_PLAN.md
- MUST_READ_FIRST/02_EXECUTION_GUARDRAILS.md
- README.md

Exit criteria:

- Baseline artifact exists with:
  - required route expectations,
  - one passing upload->status->report smoke run,
  - median latency from three smoke runs using one fixed PDF.
- Scope boundaries are explicit and stable.

Rollback action:

- Re-run baseline capture and pause implementation until stable.

### Chunk 2: First Working Oracle Deployment Path

Tasks:

1. Add minimal Oracle deployment assets and scripts focused on one working path.
2. Add Oracle runtime configuration for hosting differences only.
3. Keep Postgres and Flyway behavior unchanged.
4. Keep Cloud Run assets untouched except for compatibility fixes.

Target files:

- infra/
- scripts/
- src/main/resources/application*.yml
- docs/

Exit criteria:

- Oracle deployment boots definition fully passes.
- Runtime configuration changes satisfy hosting-differences-only rule.
- Cloud Run minimum smoke still passes (fallback preserved).

Rollback action:

- Disable Oracle deployment path and continue Cloud Run as primary.

### Chunk 3: Oracle Runtime Simplification

Tasks:

1. Make Oracle path run without Pub/Sub dependency by default.
2. Use in-process background handling for default Oracle path unless evidence forces queue retention.
3. Preserve status-flow parity even if internal execution model changes.
4. Keep deterministic report/chart-ready logic deterministic.
5. Keep LLM scope limited to extraction/summary/explanation tasks where it adds clear value.

Target files:

- src/main/java/com/policyinsight/api/messaging/
- src/main/java/com/policyinsight/processing/
- src/main/resources/application*.yml

Exit criteria:

- Oracle path operable definition passes.
- Status-flow parity definition passes.
- Required routes remain at baseline contract behavior.
- Deterministic output sections definition passes after excluding documented volatile fields.

Rollback action:

- Re-enable prior runtime mode and revert simplification changes that regress flow parity.

### Chunk 4: Cutover Readiness and Stabilization

Tasks:

1. Run full validation matrix twice on Oracle path.
2. Compare Oracle smoke latency to captured baseline using the same PDF and script.
3. Verify cost improved rule by checking default Oracle dependencies.
4. Keep Cloud Run ready as immediate fallback.
5. Finalize concise runbook: deploy, verify, rollback.

Target files:

- infra/
- scripts/
- README.md
- docs/

Exit criteria:

- Oracle validation matrix passes twice consecutively.
- No baseline regression threshold breach.
- Cost improved definition passes.
- Cloud Run fallback path remains validated.
- Rollback steps are tested and documented.

Rollback action:

- Return traffic to Cloud Run immediately if any rollback trigger is breached.

## Validation Matrix

Run from repo root in PowerShell.

### Branch and Baseline Safety

```powershell
git branch --show-current
git status -sb
git worktree list
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd test
```

Pass criteria:

- Current branch is policyinsight-revamp.
- Compile and tests pass for changed scope.

### Route Contract Smoke (Local and Oracle)

```powershell
Invoke-WebRequest http://localhost:8080/health -UseBasicParsing
Invoke-WebRequest http://localhost:8080/readiness -UseBasicParsing
Invoke-WebRequest http://localhost:8080/sample-report -UseBasicParsing
Invoke-WebRequest http://localhost:8080/sample-pdf -UseBasicParsing
```

Pass criteria:

- All return HTTP 200.

### Core Flow Smoke

Use existing smoke scripts for upload and processing path:

```powershell
pwsh scripts\smoke_test.ps1 <path-to-pdf>
pwsh scripts\mup-smoke.ps1 <path-to-pdf>
```

Pass criteria:

- Upload succeeds.
- Status transitions to SUCCESS.
- Report route is renderable with valid token.
- Repeated runs of the same PDF produce identical deterministic output sections after excluding documented volatile fields.

### Baseline Latency Capture and Comparison

Baseline capture (Chunk 1):

```powershell
$PdfPath = "<path-to-pdf>"
$Durations = 1..3 | ForEach-Object { (Measure-Command { pwsh scripts\smoke_test.ps1 $PdfPath }).TotalSeconds }
$Durations
```

Oracle comparison (Chunk 4):

```powershell
$PdfPath = "<path-to-pdf>"
$OracleDurations = 1..3 | ForEach-Object { (Measure-Command { pwsh scripts\smoke_test.ps1 $PdfPath }).TotalSeconds }
$OracleDurations
```

Pass criteria:

- Oracle median smoke duration is less than or equal to 1.30 times baseline median.
- If Oracle median exceeds 1.30 times baseline median in two consecutive validation rounds, rollback trigger is met.

### Oracle Path Operability Check

Pass criteria:

- Oracle deployment meets Oracle deployment boots definition.
- Oracle-hosted app processes at least one PDF end-to-end without Pub/Sub as default dependency.
- Postgres remains active datasource unless explicit repo evidence changes this decision.

### Cloud Run Fallback Check

Pass criteria:

- Existing Cloud Run path can still boot and pass minimum smoke checks.

### Cost Rule Check

Pass criteria:

- Default Oracle deployment path has no mandatory dependency on Cloud SQL, Pub/Sub, Datadog, or other paid managed services.
- Service boot and minimum route health (/health, /readiness, /sample-report, /sample-pdf) pass without live LLM dependency.
- Smoke-flow dependency follows repo evidence: current repo includes a non-live fallback path (Gemini stub mode when vertexai.enabled=false), so default Oracle upload->status->report smoke flow should pass without live LLM; if configured as live-LLM-only, that requirement must be explicitly documented as a scoped exception.

## Rollback Triggers

- Any required route regression in upload, status, report, share, sample-report, or sample-pdf (expected 200 becomes non-200).
- Any Oracle deployment boots check fails.
- Upload->status->report smoke flow fails once in validation.
- Status-flow parity fails (client-visible contract/state behavior changes incompatibly).
- Oracle median smoke latency exceeds 1.30 times baseline median in two consecutive validation rounds.
- Any newly required paid service appears in default Oracle path.

## Rollback Actions

1. Switch active traffic back to Cloud Run path immediately.
2. Revert the most recent Oracle-specific chunk commit.
3. Re-run baseline compile/test and route smoke checks.
4. Keep Oracle path disabled until failed matrix items are fixed.

## Definition of Done

- PolicyInsight has a working Oracle Cloud free-tier deployment path that satisfies Oracle deployment boots definition.
- Postgres remains default and operational unless repo evidence requires change.
- Core route behavior preserves status-flow parity with no contract regressions.
- Oracle path is operable without Pub/Sub as default dependency.
- Cloud Run path remains functional as fallback until Oracle path is proven stable.
- Validation matrix passes twice on Oracle with no baseline regression threshold breach.
- Cost improved definition is met for default Oracle path.
- LLM dependency boundary definition is met.
- Rollback runbook is actionable and verified.
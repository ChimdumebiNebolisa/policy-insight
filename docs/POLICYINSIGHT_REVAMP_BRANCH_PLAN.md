# PolicyInsight Revamp Branch Plan

This plan is exclusively for the `policyinsight-revamp` branch.

Objective:
- Build a production-credible Oracle-targeted deployment path.
- Make key integrations provider-agnostic.
- Preserve current behavior and deployability during transition.

Delivery model:
- Incremental, reversible changes.
- Five scoped commit/push chunks.

## Phase 1: Baseline and Guardrails

1. Confirm branch baseline and invariants before refactor.
- Verify current branch, compile state, local startup path, and smoke endpoints.
- Record a known-good baseline for regression comparison.

2. Create branch ADR/non-negotiables.
- Keep current routes and UX behavior.
- Keep GCP deploy path working.
- Add Oracle path in parallel.
- Make provider swaps config-driven.

## Phase 2: Configuration Decoupling First

3. Add provider selectors and environment contract.
- Introduce explicit selectors for db, storage, messaging, llm, and metrics providers.
- Preserve current defaults to avoid behavior changes.

4. Normalize profile model and naming.
- Keep current `cloudrun` and `local` behavior intact.
- Add explicit Oracle profile.
- Document profile matrix for revamp targets.

5. Centralize runtime wiring.
- Move provider selection to conditional bean wiring.
- Avoid scattered implicit assumptions across the codebase.

## Phase 3: Data, Storage, and Messaging Abstractions

6. Make persisted artifact paths provider-neutral.
- Replace GCS-specific semantics with neutral storage metadata.
- Keep backward-compatible fields during transition.

7. Add transitional schema migration.
- Add Flyway migration for neutral columns and safe backfill.
- Keep migration non-breaking for existing deployments.

8. Add Oracle-ready DB path.
- Introduce Oracle profile config and driver/dialect wiring.
- Keep Postgres as the default path until validation is complete.

9. Add non-Pub/Sub messaging seam.
- Preserve Pub/Sub mode.
- Add second mode for Oracle-hosted deployment strategy.

## Phase 4: LLM and Observability Provider Strategy

10. Refactor LLM wiring to provider strategy.
- Keep current Vertex provider.
- Add one low-cost provider adapter behind common interface and retries.

11. Add provider-tagged telemetry.
- Emit per-provider latency/error/token counters for tradeoff visibility.

12. Add Prometheus-first observability profile.
- Keep Datadog path intact.
- Add self-host/Oracle-compatible metrics profile.

## Phase 5: Deployment, Verification, and Exit Criteria

13. Add Oracle deployment assets in parallel.
- Introduce Oracle-targeted infra and scripts.
- Do not remove existing Cloud Run workflow yet.

14. Update CI for matrix validation.
- Add checks for local-postgres and oracle profile boot/test paths.

15. Execute staged verification and branch acceptance gate.
- Route-level regression checks.
- Migration safety checks.
- Provider-switching checks.
- Deployment smoke checks.

16. Deliver five scoped commit/push chunks.
- Chunk by config, storage/db abstraction, messaging, llm/observability, infra/docs.

## Relevant Files

- `README.md`
- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/main/resources/application-cloudrun.yml`
- `src/main/resources/application-demSleep.yml`
- `src/main/java/com/policyinsight/config/`
- `src/main/java/com/policyinsight/api/storage/`
- `src/main/java/com/policyinsight/api/messaging/`
- `src/main/java/com/policyinsight/processing/`
- `src/main/java/com/policyinsight/observability/`
- `src/main/java/com/policyinsight/shared/model/PolicyJob.java`
- `src/main/resources/db/migration/`
- `.github/workflows/cd.yml`
- `infra/cloudrun/web.yaml`
- `infra/cloudrun/worker.yaml`
- `scripts/`
- `src/test/java/`

## Verification Checklist

1. Baseline capture: compile/tests and route smokes before refactor.
2. Config regression: upload/status/report/share/sample routes unchanged.
3. Migration safety: Flyway forward migration on local Postgres snapshot.
4. Oracle readiness: oracle profile boot path and repository-level tests.
5. Provider switching: same flow across supported provider combinations.
6. LLM fallback behavior: timeout/retry/failure semantics unchanged.
7. Observability parity: health/readiness/metrics verified per profile.
8. Deployment dual-path: existing Cloud Run workflow green and Oracle target smoke passes.
9. Branch acceptance: no endpoint regressions, no migration breakage, no provider lock-in regressions, documentation complete.

## Scope Decisions

Included:
- Architecture decoupling.
- Oracle-path enablement.
- Provider abstraction.
- CI/verification and deployment docs for `policyinsight-revamp`.

Excluded:
- Full frontend redesign.
- New product features unrelated to portability/cost/reliability.
- Removal of existing GCP path before Oracle path is proven.

Risk posture:
- Additive and reversible changes first.
- Destructive removals deferred until dual-path validation succeeds.

## Further Considerations

1. Start Oracle with profile-based compatibility mode before deeper vendor optimizations.
2. Keep Pub/Sub default until alternative queue path reaches parity for retry and idempotency.
3. Use deterministic logic for chart data; reserve LLM for extraction/explanation where model value is clear.

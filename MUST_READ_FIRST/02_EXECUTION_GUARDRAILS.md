# Execution Guardrails (Mandatory)

This document defines required operating rules for work on this repository.

## Always-Read Protocol

Before any planning, coding, or review work:

1. Read `MUST_READ_FIRST/01_POLICYINSIGHT_REVAMP_BRANCH_PLAN.md`.
2. Read `MUST_READ_FIRST/02_EXECUTION_GUARDRAILS.md`.
3. Confirm branch and worktree state:

```powershell
git branch --show-current
git status -sb
git worktree list
```

If branch is not `policyinsight-revamp`, stop and switch before editing.

## Branch Separation Rules

1. Feature work happens only on `policyinsight-revamp`.
2. `main` is review and merge target only.
3. Never push directly from `main`.
4. Use separate worktree paths for `main` and `policyinsight-revamp`.

## Commit and Push Rules

1. Keep changes scoped and small.
2. Prefer five chunked push increments where feasible:
   - Chunk 1: Plan and constraints.
   - Chunk 2: Config/profile groundwork.
   - Chunk 3: Storage/db migration scaffolding.
   - Chunk 4: Messaging/LLM/observability seams.
   - Chunk 5: Infra, CI matrix, and docs.
3. Before each commit:

```powershell
git status --short
git diff --name-only
git diff
```

## Safety Gates Before Push

```powershell
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd test
```

Expected:

1. Compile succeeds.
2. Test suite passes for changed scope.
3. No unexpected files staged.

## Endpoint Regression Minimum Set

When backend behavior or profile wiring changes, verify:

```powershell
Invoke-WebRequest http://localhost:8080/health -UseBasicParsing
Invoke-WebRequest http://localhost:8080/readiness -UseBasicParsing
Invoke-WebRequest http://localhost:8080/sample-report -UseBasicParsing
Invoke-WebRequest http://localhost:8080/sample-pdf -UseBasicParsing
```

Expected: all return HTTP 200.

## Failure Handling

1. If branch/worktree mismatch is detected, stop and fix context first.
2. If tests fail, do not push; fix or isolate failing changes.
3. If unexpected files are modified, separate commits by concern.

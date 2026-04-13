# Project Guidelines

## Mandatory Startup

Before any work in this repository, read both required docs:

1. `MUST_READ_FIRST/01_POLICYINSIGHT_REVAMP_BRANCH_PLAN.md`
2. `MUST_READ_FIRST/02_EXECUTION_GUARDRAILS.md`

Do not edit code until both documents have been read in the current session.

## Branch and Worktree Safety

1. Confirm branch and worktree status before edits:
   - `git branch --show-current`
   - `git status -sb`
   - `git worktree list`
2. Do feature work on `policyinsight-revamp` only.
3. Keep `main` clean and protected.

## Build and Test

Use these default checks for backend-affecting changes:

- `./mvnw -q -DskipTests compile` (Linux/macOS)
- `.\mvnw.cmd -q -DskipTests compile` (Windows)
- `./mvnw test` or `.\mvnw.cmd test`

## Delivery Conventions

1. Keep commits scoped and reviewable.
2. Prefer five chunked pushes for larger workstreams.
3. Document behavior, rollout, and rollback for infrastructure-impacting changes.

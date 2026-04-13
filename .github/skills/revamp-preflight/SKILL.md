---
name: revamp-preflight
description: 'Use when working in this repository to run mandatory preflight checks, read required must-read docs, verify branch/worktree safety, and enforce chunked delivery discipline before implementation.'
argument-hint: 'Task summary for this session'
user-invocable: true
disable-model-invocation: false
---

# Revamp Preflight

## When to Use

- Any task in this repository: planning, coding, reviewing, or release prep.
- Any branch-sensitive work where `main` and `policyinsight-revamp` must stay isolated.

## Procedure

1. Read required docs in order:
   - `MUST_READ_FIRST/01_POLICYINSIGHT_REVAMP_BRANCH_PLAN.md`
   - `MUST_READ_FIRST/02_EXECUTION_GUARDRAILS.md`
2. Verify branch/worktree state:

```powershell
git branch --show-current
git status -sb
git worktree list
```

3. Confirm current task scope and target chunk (1 to 5).
4. Execute changes only after preflight checks pass.
5. Validate compile/tests and route smokes for affected paths.

## Required Outputs

- Branch name and worktree confirmation.
- Which chunk the task belongs to.
- Validation commands executed and outcomes.

## Guardrails

- Do not push from `main`.
- Keep commits scoped to a single concern.
- If unexpected changed files appear, stop and separate work by concern.

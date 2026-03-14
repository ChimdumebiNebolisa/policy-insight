# Evaluation outputs

This directory holds evaluation outputs for auditability. The repo is the source of truth; do not commit fake or synthetic outputs.

**Currently present:** `failure_cases.md` (curated failure cases) and this README.

**Expected artifacts** (may be added when available):

- **raw_reports/** – Per-document, per-run report JSON (or representative subset) for each evaluated document. Filenames should be stable and machine-readable (e.g. by doc_id and run).
- **extraction_health_results.csv** – Schema pass rate, null rates, self-consistency (or equivalent) from extraction health runs.
- **grounding_results.csv** – Claim-level grounding labels (supported/partial/unsupported/contradicted) and optionally span-level precision.
- **extraction_accuracy_results.csv** – Precision, recall, date exact-match, spurious extraction vs gold.

**Guidelines:**

- Store raw outputs per evaluated document/run where feasible for auditability.
- Do not commit fabricated outputs; if real outputs are too large, commit a representative subset and document how to reproduce or what was omitted.
- Keep filenames stable and machine-readable.

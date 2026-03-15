# Evaluation outputs

This directory holds evaluation outputs for auditability. The repo is the source of truth; do not commit fake or synthetic outputs.

**Present in this directory:**

- **failure_cases.md** – Curated failure cases (overgeneralization, date misbinding, spurious restriction, OCR numeric error, partial grounding).
- **extraction_health_results.csv** – Aggregate metrics: schema pass rate, self-consistency (definitions and values from the committed technical report).
- **grounding_results.csv** – Aggregate metrics: strict/relaxed supported rate, citation precision, contradicted rate (definitions and values from the committed technical report).
- **extraction_accuracy_results.csv** – Aggregate metrics: extraction precision, recall, date exact-match rate, spurious extraction rate (definitions and values from the committed technical report).

Each CSV includes a `source` column indicating the committed evaluation document from which the aggregate results are derived (e.g. eval/docs/TECHNICAL_REPORT.md). Values are numeric; interpret percentage metrics as whole numbers (e.g. 96 = 96%).

**Guidelines:**

- Do not commit fabricated outputs; if real per-run outputs are added later, document how to reproduce or what was omitted.
- Keep filenames stable and machine-readable.

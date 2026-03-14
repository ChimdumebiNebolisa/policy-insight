# Report schema and sections

## Human-readable report sections

The system is designed to produce reports with the following conceptual sections (rendered in the UI and/or export):

- **Executive Summary / Plain-English Summary** – High-level summary bullets with citations.
- **Key Obligations** – Who must do what, with timing/threshold where applicable.
- **Restrictions and Prohibitions** – What is not allowed.
- **Deadlines and Dates** – Effective dates, compliance deadlines, retention periods.
- **Enforcement or Termination Triggers** – Conditions that may trigger review, suspension, or termination.
- **Risk Taxonomy** – Categorization of risks (e.g. reporting noncompliance, budget misuse).
- **Notable Ambiguities** – Caveats or interpretation notes.
- **Evidence Appendix** – Optional list of cited chunks with quotes.

## Canonical repo schema vs illustrative schema

The **canonical machine schema** used by the application is in `eval/schema_report_min.json`. It uses camelCase and the following top-level report keys: `documentOverview`, `summaryBullets`, `obligations`, `restrictions`, `terminationTriggers`, `riskTaxonomy`. Obligations and restrictions are stored as objects with an `items` array. Not all conceptual sections above (e.g. dedicated deadlines, ambiguities, evidence appendix) are separate fields in the current schema; they may appear inside overview or summary content.

The **expanded human-readable schema** below is **illustrative only**. It does not necessarily match the repo’s actual API or storage shape (which is defined in `eval/schema_report_min.json` and the report-json endpoint).

## Illustrative top-level schema (conceptual)

```json
{
  "executive_summary": [],
  "key_obligations": [],
  "restrictions": [],
  "deadlines": [],
  "enforcement_triggers": [],
  "risk_taxonomy": [],
  "ambiguities": [],
  "evidence_appendix": []
}
```

For validation and scripting, use `eval/schema_report_min.json` and the report-json response structure (jobId, report, chunksMeta, etc.).

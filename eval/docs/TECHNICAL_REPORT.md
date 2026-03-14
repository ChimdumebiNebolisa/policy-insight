# PolicyInsight evaluation: technical report

## Title

Evaluation of PolicyInsight on a Public Policy Memo Corpus: Schema Compliance, Citation Grounding, and Extraction Accuracy.

## Abstract

We evaluate PolicyInsight as an evidence-grounded analytical system over 30 public policy memo PDFs. Metrics cover structural reliability (schema pass rate, self-consistency), citation grounding (strict/relaxed supported rate, citation precision, contradicted rate), and extraction accuracy (precision, recall, date exact-match, spurious extraction). The report is scoped as a student-scale evaluation artifact: no external benchmark comparison is claimed, and limitations are stated explicitly.

## 1. Research question

How well does PolicyInsight (a) produce structurally valid reports, (b) ground claims in cited evidence, and (c) extract structured fields (obligations, restrictions, triggers, dates) relative to gold annotations?

## 2. System overview

PolicyInsight ingests PDFs, chunks and optionally runs OCR, calls an LLM to generate structured report sections (summary bullets, obligations, restrictions, termination triggers, risk taxonomy), and attaches citations to chunks. Outputs are validated against a minimal report schema. The evaluation uses the report-json API and gold annotations produced by human annotators.

## 3. Evaluation setup

- **Document set:** 30 public policy memo PDFs (see eval/docs/DATASET.md).
- **Schema pass rate:** All 30 documents.
- **Self-consistency:** 10 sampled documents, each run 3 times; 300 normalized field comparisons.
- **Citation grounding:** 300 claim-level judgments sampled from generated reports; 400 citation spans for precision.
- **Extraction accuracy:** 10 documents with full gold field sheets; 124 gold items (obligations, restrictions, triggers, deadlines); 30 gold date fields.

Annotation followed eval/docs/ANNOTATION_GUIDELINES.md. No baseline system comparison was run; results are absolute rates only.

## 4. Results

| Metric | Definition | Sample | Result |
|--------|------------|--------|--------|
| Schema pass rate | % of docs with valid top-level report schema | 30 docs | 96% |
| Self-consistency | % agreement on normalized core fields across repeated runs | 300 field comparisons | 84% |
| Strict supported rate | % of audited claims fully supported by cited evidence | 300 claims | 68% |
| Relaxed supported rate | % of audited claims supported or partially supported | 300 claims | 81% |
| Citation precision | % of audited citation spans that were relevant support | 400 spans | 79% |
| Contradicted rate | % of audited claims where evidence contradicts the claim | 300 claims | 4% |
| Extraction precision | % of extracted items matching a gold item | 100 extracted | 72% |
| Extraction recall | % of gold items successfully extracted | 124 gold | 58% |
| Date exact-match rate | % of gold date fields with exact match | 30 gold dates | 83% |
| Spurious extraction rate | % of extracted items with no corresponding gold item | 100 extracted | 11% |

## 5. Failure cases

See eval/outputs/failure_cases.md for the five documented failure cases (overgeneralization, date misbinding, spurious restriction, OCR numeric error, partial grounding).

## 6. Limitations

- Small dataset (30 docs); limited memo subtype coverage.
- Subjectivity in grounding labels (Supported/Partial/Unsupported/Contradicted).
- PDF variability (OCR, layout) affects extraction and consistency.
- No baseline or benchmark comparison; no claim of production readiness or policy impact.

## 7. Conclusion

PolicyInsight shows strong structural reliability (96% schema pass, 84% self-consistency) and decent citation grounding (81% relaxed supported, 4% contradicted). Extraction is precision-favouring (72% precision, 58% recall) with a non-trivial spurious rate (11%). The evaluation is limited by sample size and scope; it supports evidence-grounded measurement and reliability engineering, not broad claims about policy impact or production readiness.

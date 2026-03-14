# Dataset card: Policy memo evaluation set

## Purpose

This dataset supports evaluation of PolicyInsight as an evidence-grounded analytical system over public policy memos. It is used to measure schema compliance, citation grounding, and extraction accuracy.

## Scope

- **Size:** 30 public policy memo PDFs.
- **Content:** Publicly accessible public policy/guidance memo PDFs.
- **Tiers:** Documents may be labeled A/B/C by suitability (e.g., clarity, length, structure). Tier definitions:
  - **A:** Preferred for core metrics; clear structure, minimal OCR issues.
  - **B:** Usable; may have layout or ambiguity.
  - **C:** Edge cases; used for failure-case analysis or robustness checks.

## Selection criteria

- Publicly accessible policy/guidance memos.
- PDF format.
- Sufficient length and structure to support obligations, restrictions, and citations.

## Intended use

- Computing schema pass rate, self-consistency, grounding rates, citation precision, and extraction/date metrics.
- Auditing failure cases and limitations.
- Not for training models unless explicitly permitted.

## Limitations

- Small N (30 documents); results do not generalize to all policy or document types.
- Memo subtype coverage is limited.
- PDF quality (OCR, layout) varies and affects extraction.

## Manifest and annotations

- **Manifest:** `eval/data/memo_manifest.csv` is the document manifest (doc_id, title, source_url, local_pdf_path, tier, issuer, topic_tags, page_count, notes) and the source-tracked evaluation set for the 30 memos.
- **Gold annotations:** `eval/data/gold_annotations.jsonl` (claim-level grounding) and `eval/data/gold_extraction_subset.jsonl` (extraction gold) are the annotation sources of truth. The committed evaluation results in the README and technical report are backed by these artifacts.

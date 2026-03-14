# Annotation guidelines

## Unit of evaluation

The unit for grounding audit is a **claim**: a single asserted statement in the generated report that is tied to one or more cited evidence spans.

## Labels and meanings

For each audited claim, assign exactly one of:

| Label | Meaning |
|-------|--------|
| **Supported** | Cited evidence fully supports the claim. |
| **Partial** | Cited evidence partially supports the claim (e.g., missing nuance or a key clause). |
| **Unsupported** | Cited evidence does not support the claim (e.g., irrelevant or insufficient). |
| **Contradicted** | Cited evidence directly conflicts with the claim. |

Strict supported rate = % Supported. Relaxed supported rate = % Supported or Partial.

## Annotation procedure

1. Take one claim from the generated report and its cited spans.
2. Open the source memo at the cited page/chunk and read the evidence.
3. Compare claim wording to the evidence (no overgeneralization, no conflation of effective date vs deadline, etc.).
4. Assign one of the four labels.
5. For citation precision: at the span level, mark whether the span is genuinely relevant support for the attached claim.

## Evidence format

- Use page number and chunk id (e.g. `page: 3`, `chunk_id: chunk_017`) as in the report.
- Quote the exact span text when recording gold evidence.

## Examples

**Example 1 – Supported**
- Claim: "Recipients must submit quarterly progress reports."
- Evidence: "Recipients are required to submit quarterly progress and expenditure reports."
- Verdict: Supported.

**Example 2 – Partial**
- Claim: "Failure to file within 15 days terminates funding."
- Evidence: Only states that filing is due within 15 days; consequence is "may trigger review" elsewhere.
- Verdict: Partial (consequence overstated).

**Example 3 – Unsupported**
- Claim: "Agencies may not subcontract implementation tasks."
- Evidence: "Agencies are encouraged to retain direct oversight of implementation tasks."
- Verdict: Unsupported (recommendation was interpreted as prohibition; evidence does not directly state the opposite, so not Contradicted).

## Notes

- Prefer **atomic claims** (one assertion per claim) for clearer judgment.
- When in doubt between Partial and Unsupported, use the stricter label that matches the evidence.

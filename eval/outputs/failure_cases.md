# Failure cases

## Failure Case 1: Exception clause flattened into a universal rule

- **Generated claim:** "All recipients must submit quarterly budget revisions."
- **Source memo:** Only recipients exceeding a funding threshold must do so.
- **Error type:** overgeneralization
- **Impact:** claim labeled Unsupported
- **Likely cause:** qualifier in subordinate clause was dropped during summarization

## Failure Case 2: Effective date confused with compliance deadline

- **Generated field:** deadline = "2025-07-01"
- **Gold field:** deadline = "2025-09-01"
- **Source memo:** July 1 is the effective date, September 1 is the compliance deadline
- **Error type:** date misbinding
- **Impact:** exact-match failure
- **Likely cause:** multiple date mentions in close proximity

## Failure Case 3: Restriction hallucinated from advisory language

- **Generated restriction:** "Agencies may not subcontract implementation tasks"
- **Source memo:** "Agencies are encouraged to retain direct oversight of implementation tasks"
- **Error type:** spurious extraction
- **Impact:** false restriction
- **Likely cause:** recommendation language interpreted as prohibition

## Failure Case 4: OCR corruption breaks threshold extraction

- **Generated claim:** "Projects above $50,000 require review"
- **Gold claim:** projects above $500,000 require review
- **Error type:** numeric extraction failure
- **Impact:** materially incorrect threshold
- **Likely cause:** OCR loss of one zero in scan-like PDF

## Failure Case 5: Citation span is related but not sufficient

- **Generated claim:** "Failure to file within 15 days terminates funding"
- **Cited span:** text about filing within 15 days
- **Missing evidence:** separate clause stating noncompliance may trigger review, not automatic termination
- **Error type:** partial grounding
- **Impact:** claim labeled Partial
- **Likely cause:** citation points to a nearby requirement but not the consequence clause

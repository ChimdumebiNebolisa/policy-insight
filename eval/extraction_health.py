#!/usr/bin/env python3
"""
Extraction health metrics script.
Computes schema pass rate, null rates, and self-consistency for report data.
"""

import json
import os
import sys
import time
import uuid
from pathlib import Path
from typing import Dict, List, Optional, Any
import urllib.request
import urllib.parse
import urllib.error

BASE_URL = os.environ.get("BASE_URL", "http://localhost:8080")
POLL_INTERVAL_MS = int(os.environ.get("POLL_INTERVAL_MS", "2000"))
POLL_TIMEOUT_MS = int(os.environ.get("POLL_TIMEOUT_MS", "180000"))

def upload_pdf(pdf_path: Path):
    """Upload a PDF and return (jobId, token)."""
    url = f"{BASE_URL}/api/documents/upload"

    with open(pdf_path, 'rb') as f:
        pdf_data = f.read()

    # Create multipart form data
    boundary = '----WebKitFormBoundary7MA4YWxkTrZu0gW'
    body_parts = []
    body_parts.append(f'--{boundary}'.encode())
    body_parts.append(b'Content-Disposition: form-data; name="file"; filename="sample.pdf"')
    body_parts.append(b'Content-Type: application/pdf')
    body_parts.append(b'')
    body_parts.append(pdf_data)
    body_parts.append(f'--{boundary}--'.encode())

    body = b'\r\n'.join(body_parts)

    req = urllib.request.Request(url, data=body, method='POST')
    req.add_header('Content-Type', f'multipart/form-data; boundary={boundary}')

    try:
        with urllib.request.urlopen(req) as response:
            if response.status != 202:
                print(f"Error: Upload returned status {response.status}", file=sys.stderr)
                return None, None
            data = json.loads(response.read().decode('utf-8'))
            return data.get('jobId'), data.get('token')
    except Exception as e:
        print(f"Error uploading PDF: {e}", file=sys.stderr)
        return None, None

def poll_status(job_id: str, token: str):
    """Poll status until SUCCESS or FAILED. Returns (status, success)."""
    start_time = time.time() * 1000
    timeout_time = start_time + POLL_TIMEOUT_MS

    while True:
        if time.time() * 1000 > timeout_time:
            print(f"Error: Status poll timeout for jobId={job_id}", file=sys.stderr)
            return None, False

        url = f"{BASE_URL}/api/documents/{job_id}/status"
        req = urllib.request.Request(url)
        req.add_header('X-Job-Token', token)

        try:
            with urllib.request.urlopen(req) as response:
                if response.status != 200:
                    print(f"Error: Status returned {response.status}", file=sys.stderr)
                    return None, False
                data = json.loads(response.read().decode('utf-8'))
                status = data.get('status')

                if status == 'SUCCESS':
                    return status, True
                elif status == 'FAILED':
                    print(f"Error: Job failed: {data.get('errorMessage', 'Unknown error')}", file=sys.stderr)
                    return status, False

                # Still processing, wait and retry
                time.sleep(POLL_INTERVAL_MS / 1000.0)
        except Exception as e:
            print(f"Error polling status: {e}", file=sys.stderr)
            return None, False

def get_report_json(job_id: str, token: str) -> Optional[Dict[str, Any]]:
    """Fetch report JSON for a job."""
    url = f"{BASE_URL}/api/documents/{job_id}/report-json"
    req = urllib.request.Request(url)
    req.add_header('X-Job-Token', token)

    try:
        with urllib.request.urlopen(req) as response:
            if response.status != 200:
                print(f"Error: Report-json returned {response.status}", file=sys.stderr)
                return None
            return json.loads(response.read().decode('utf-8'))
    except Exception as e:
        print(f"Error fetching report JSON: {e}", file=sys.stderr)
        return None

def validate_schema_minimal(report_data: Dict[str, Any]) -> bool:
    """Minimal schema validation without external libraries."""
    # Check jobId exists and looks like UUID
    job_id = report_data.get('jobId')
    if not job_id:
        return False
    try:
        uuid.UUID(job_id)
    except (ValueError, AttributeError):
        return False

    # Check report object exists
    report = report_data.get('report')
    if not isinstance(report, dict):
        return False

    # Check report fields exist (can be null, but keys must exist)
    required_fields = ['documentOverview', 'summaryBullets', 'obligations',
                       'restrictions', 'terminationTriggers', 'riskTaxonomy']
    for field in required_fields:
        if field not in report:
            return False

    # Check chunksMeta
    chunks_meta = report_data.get('chunksMeta')
    if not isinstance(chunks_meta, dict):
        return False

    # Check chunkCount is integer >= 0
    chunk_count = chunks_meta.get('chunkCount')
    if not isinstance(chunk_count, int) or chunk_count < 0:
        return False

    # generatedAt is optional (only when SUCCESS)
    generated_at = report_data.get('generatedAt')
    # If it exists, should be a string (we don't validate format strictly)
    if generated_at is not None and not isinstance(generated_at, str):
        return False

    return True

def is_null_or_empty(value: Any) -> bool:
    """Check if a value is null or empty."""
    if value is None:
        return True
    if isinstance(value, str) and value.strip() == '':
        return True
    if isinstance(value, (list, dict)) and len(value) == 0:
        return True
    return False

def compute_null_rates(reports: List[Dict[str, Any]]) -> Dict[str, float]:
    """Compute null rate per field across all reports."""
    field_names = ['documentOverview', 'summaryBullets', 'obligations',
                   'restrictions', 'terminationTriggers', 'riskTaxonomy']

    null_counts = {field: 0 for field in field_names}
    total_reports = len(reports)

    if total_reports == 0:
        return {field: 0.0 for field in field_names}

    for report_data in reports:
        report = report_data.get('report', {})
        for field in field_names:
            value = report.get(field)
            if is_null_or_empty(value):
                null_counts[field] += 1

    return {field: null_counts[field] / total_reports for field in field_names}

def normalize_value(value: Any) -> str:
    """Normalize a value for comparison (JSON dump with sorted keys)."""
    if value is None:
        return "null"
    if isinstance(value, (dict, list)):
        return json.dumps(value, sort_keys=True)
    if isinstance(value, str):
        return value.strip()
    return str(value)

def compute_self_consistency(run1_reports: Dict[str, Dict], run2_reports: Dict[str, Dict]) -> float:
    """Compute self-consistency rate by comparing run1 vs run2 for same PDFs."""
    field_names = ['documentOverview', 'summaryBullets', 'obligations',
                   'restrictions', 'terminationTriggers', 'riskTaxonomy']

    matches = 0
    comparisons = 0

    for pdf_name in run1_reports.keys():
        if pdf_name not in run2_reports:
            continue

        report1 = run1_reports[pdf_name].get('report', {})
        report2 = run2_reports[pdf_name].get('report', {})

        for field in field_names:
            comparisons += 1
            value1 = report1.get(field)
            value2 = report2.get(field)

            norm1 = normalize_value(value1)
            norm2 = normalize_value(value2)

            if norm1 == norm2:
                matches += 1

    if comparisons == 0:
        return 0.0

    return matches / comparisons

def process_pdf(pdf_path: Path, run_num: int) -> Optional[Dict[str, Any]]:
    """Upload PDF, poll to SUCCESS, and fetch report JSON."""
    pdf_name = pdf_path.stem

    print(f"Processing {pdf_name} (run {run_num})...", file=sys.stderr)

    # Upload
    job_id, token = upload_pdf(pdf_path)
    if not job_id or not token:
        print(f"Error: Failed to upload {pdf_name}", file=sys.stderr)
        return None

    # Poll status
    status, success = poll_status(job_id, token)
    if not success:
        print(f"Error: Job did not succeed for {pdf_name}", file=sys.stderr)
        return None

    # Fetch report JSON
    report_data = get_report_json(job_id, token)
    if not report_data:
        print(f"Error: Failed to fetch report for {pdf_name}", file=sys.stderr)
        return None

    return report_data

def main():
    fixtures_dir = Path("eval/fixtures")
    out_dir = Path("eval/out")

    out_dir.mkdir(parents=True, exist_ok=True)

    # Find PDF fixtures
    pdf_files = list(fixtures_dir.glob("*.pdf"))
    if not pdf_files:
        # Copy from perf/fixtures if eval/fixtures is empty
        perf_fixtures = Path("perf/fixtures/sample.pdf")
        if perf_fixtures.exists():
            import shutil
            shutil.copy(perf_fixtures, fixtures_dir / "sample.pdf")
            pdf_files = [fixtures_dir / "sample.pdf"]

    if len(pdf_files) < 5:
        print(f"Warning: Only {len(pdf_files)} PDF fixtures found. Need at least 5 for meaningful metrics.", file=sys.stderr)
        print(f"Using {len(pdf_files)} PDF(s).", file=sys.stderr)

    # Process each PDF twice (run1 and run2)
    run1_reports = {}
    run2_reports = {}
    all_reports = []

    for pdf_path in pdf_files:
        pdf_name = pdf_path.stem

        # Run 1
        report1 = process_pdf(pdf_path, 1)
        if report1:
            run1_reports[pdf_name] = report1
            all_reports.append(report1)
            output_file = out_dir / f"{pdf_name}_run1.json"
            with open(output_file, 'w') as f:
                json.dump(report1, f, indent=2)
            print(f"Saved: {output_file}", file=sys.stderr)

        # Run 2
        report2 = process_pdf(pdf_path, 2)
        if report2:
            run2_reports[pdf_name] = report2
            all_reports.append(report2)
            output_file = out_dir / f"{pdf_name}_run2.json"
            with open(output_file, 'w') as f:
                json.dump(report2, f, indent=2)
            print(f"Saved: {output_file}", file=sys.stderr)

    if not all_reports:
        print("Error: No reports were successfully processed", file=sys.stderr)
        sys.exit(1)

    # Compute metrics
    # 1. Schema pass rate
    schema_pass_count = sum(1 for r in all_reports if validate_schema_minimal(r))
    schema_pass_rate = schema_pass_count / len(all_reports) if all_reports else 0.0

    # 2. Null rates per field
    null_rates = compute_null_rates(all_reports)

    # 3. Self-consistency rate
    self_consistency_rate = compute_self_consistency(run1_reports, run2_reports)

    # Build metrics JSON
    metrics = {
        "docs_count": len(pdf_files),
        "schema_pass_rate": schema_pass_rate,
        "null_rates": null_rates,
        "self_consistency_rate": self_consistency_rate,
        "top_missing_fields": sorted(null_rates.items(), key=lambda x: x[1], reverse=True)[:3]
    }

    # Write JSON output
    json_file = out_dir / "health_metrics.json"
    with open(json_file, 'w') as f:
        json.dump(metrics, f, indent=2)

    # Write Markdown output
    md_file = out_dir / "health_metrics.md"
    with open(md_file, 'w') as f:
        f.write("# Extraction Health Metrics\n\n")
        f.write(f"**Documents processed:** {metrics['docs_count']}\n\n")
        f.write(f"**Schema pass rate:** {metrics['schema_pass_rate']:.1%}\n\n")
        f.write("## Null Rates by Field\n\n")
        f.write("| Field | Null Rate |\n")
        f.write("|-------|----------|\n")
        for field, rate in sorted(null_rates.items(), key=lambda x: x[1], reverse=True):
            f.write(f"| {field} | {rate:.1%} |\n")
        f.write(f"\n**Self-consistency rate:** {metrics['self_consistency_rate']:.1%}\n\n")
        f.write("## Top Missing Fields\n\n")
        for field, rate in metrics['top_missing_fields']:
            f.write(f"- {field}: {rate:.1%}\n")

    print(f"Generated: {json_file}")
    print(f"Generated: {md_file}")

if __name__ == "__main__":
    main()


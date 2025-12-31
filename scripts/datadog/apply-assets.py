#!/usr/bin/env python3
"""
Create or update Datadog dashboards, monitors, and SLOs from template files.
Requires DD_API_KEY and DD_APP_KEY environment variables.

Supports DD_SITE environment variable (default: datadoghq.com).

Usage:
    python apply-assets.py [--templates-dir ./datadog/templates]
"""

import os
import sys
import json
import argparse
import re
from pathlib import Path
from typing import Dict, Optional, Tuple, List, Any
from collections import defaultdict

# Import shared modules (assume they're in the same directory)
sys.path.insert(0, str(Path(__file__).parent))
from dd_http import request_json, DatadogAPIError
from validate_keys import validate_api_key, validate_app_key

# Get Datadog site from environment (default: datadoghq.com)
DD_SITE = os.getenv("DD_SITE", "datadoghq.com")
DD_API_BASE = f"https://api.{DD_SITE}/api/v1"


def load_json_template(template_path: Path) -> Dict[str, Any]:
    """
    Load JSON template file, handling UTF-8 BOM if present.

    Uses utf-8-sig encoding to automatically strip BOM if present,
    while remaining compatible with files without BOM.

    Args:
        template_path: Path to JSON template file

    Returns:
        Parsed JSON as dictionary

    Raises:
        json.JSONDecodeError: If file is not valid JSON
        FileNotFoundError: If file doesn't exist
    """
    with open(template_path, 'r', encoding='utf-8-sig') as f:
        return json.load(f)


def get_headers() -> Dict[str, str]:
    """Get API headers with authentication."""
    api_key = os.getenv("DD_API_KEY")
    app_key = os.getenv("DD_APP_KEY")

    if not api_key:
        raise ValueError("DD_API_KEY environment variable is required")
    if not app_key:
        raise ValueError("DD_APP_KEY environment variable is required")

    return {
        "DD-API-KEY": api_key,
        "DD-APPLICATION-KEY": app_key,
        "Content-Type": "application/json"
    }


def apply_dashboard(template_path: Path, headers: Dict[str, str]) -> Tuple[bool, str]:
    """
    Create or update a dashboard from template.

    Returns:
        (success: bool, operation: str) where operation is "created", "updated", or "failed"
    """
    try:
        dashboard_def = load_json_template(template_path)
    except json.JSONDecodeError as e:
        print(f"    ❌ Invalid JSON in template: {e}")
        return False, "failed"

    # Remove any ID if present (templates shouldn't have real IDs)
    dashboard_def.pop("id", None)
    dashboard_def.pop("author_handle", None)
    dashboard_def.pop("created_at", None)
    dashboard_def.pop("modified_at", None)

    dashboard_title = dashboard_def.get("title", "Unknown")
    print(f"  Applying dashboard: {dashboard_title}")

    try:
        # Try to find existing dashboard by title
        existing_dashboards = request_json(
            "GET", f"{DD_API_BASE}/dashboard", headers, timeout=20, retries=3
        ).get("dashboards", [])

        existing_id = None
        for dash in existing_dashboards:
            if dash.get("title") == dashboard_title:
                existing_id = dash.get("id")
                break

        if existing_id:
            # Update existing dashboard
            print(f"    Updating existing dashboard (ID: {existing_id})")
            result = request_json(
                "PUT", f"{DD_API_BASE}/dashboard/{existing_id}",
                headers, payload=dashboard_def, timeout=20, retries=3
            )
            print(f"    ✅ Updated dashboard: {dashboard_title}")
            return True, "updated"
        else:
            # Create new dashboard
            print(f"    Creating new dashboard")
            result = request_json(
                "POST", f"{DD_API_BASE}/dashboard",
                headers, payload=dashboard_def, timeout=20, retries=3
            )
            new_id = result.get("dashboard", {}).get("id")
            print(f"    ✅ Created dashboard: {dashboard_title} (ID: {new_id})")
            return True, "created"
    except DatadogAPIError as e:
        print(f"    ❌ Error: {e}")
        return False, "failed"
    except Exception as e:
        print(f"    ❌ Unexpected error: {e}")
        import traceback
        traceback.print_exc()
        return False, "failed"


def apply_monitor(template_path: Path, headers: Dict[str, str]) -> Tuple[bool, str]:
    """
    Create or update a monitor from template.
    Idempotent: checks for existing monitor by name before creating.

    Returns:
        (success: bool, operation: str) where operation is "created", "updated", or "failed"
    """
    try:
        monitor_def = load_json_template(template_path)
    except json.JSONDecodeError as e:
        print(f"    ❌ Invalid JSON in template: {e}")
        return False, "failed"

    # Remove any ID if present (we'll find by name instead)
    monitor_id = monitor_def.pop("id", None)
    monitor_name = monitor_def.get("name", "Unknown")
    print(f"  Applying monitor: {monitor_name}")

    try:
        # First, try to find existing monitor by name (exact match) for idempotent upsert
        existing_monitors = request_json(
            "GET", f"{DD_API_BASE}/monitor", headers, timeout=20, retries=3
        )

        # Monitors endpoint returns a list directly
        if not isinstance(existing_monitors, list):
            raise ValueError(f"Unexpected response format from monitors endpoint: {type(existing_monitors)}")

        existing_id = None
        for monitor in existing_monitors:
            if isinstance(monitor, dict) and monitor.get("name") == monitor_name:
                existing_id = monitor.get("id")
                break

        if existing_id:
            # Update existing monitor found by name
            print(f"    Updating existing monitor (ID: {existing_id})")
            result = request_json(
                "PUT", f"{DD_API_BASE}/monitor/{existing_id}",
                headers, payload=monitor_def, timeout=20, retries=3
            )
            print(f"    ✅ Updated monitor: {monitor_name} (ID: {existing_id})")
            return True, "updated"
        else:
            # Create new monitor (not found by name)
            print(f"    Creating new monitor")
            result = request_json(
                "POST", f"{DD_API_BASE}/monitor",
                headers, payload=monitor_def, timeout=20, retries=3
            )
            new_id = result.get("id") if isinstance(result, dict) else None
            print(f"    ✅ Created monitor: {monitor_name} (ID: {new_id})")
            return True, "created"
    except DatadogAPIError as e:
        print(f"    ❌ Error: {e}")
        return False, "failed"
    except Exception as e:
        print(f"    ❌ Unexpected error: {e}")
        import traceback
        traceback.print_exc()
        return False, "failed"


def apply_slo(template_path: Path, headers: Dict[str, str]) -> Tuple[bool, str]:
    """
    Create or update an SLO from template.
    Handles various response shapes (dict with data.id, dict with id, or list).

    Returns:
        (success: bool, operation: str) where operation is "created", "updated", or "failed"
    """
    try:
        slo_def = load_json_template(template_path)
    except json.JSONDecodeError as e:
        print(f"    ❌ Invalid JSON in template: {e}")
        return False, "failed"

    # Remove any ID if present
    slo_id = slo_def.pop("id", None)
    slo_name = slo_def.get("name", "Unknown")
    print(f"  Applying SLO: {slo_name}")

    def extract_slo_id(response: Any) -> Optional[str]:
        """Extract SLO ID from various response shapes."""
        if isinstance(response, dict):
            # Try data.id first (common format)
            if "data" in response and isinstance(response["data"], dict):
                id_from_data = response["data"].get("id")
                if id_from_data:
                    return str(id_from_data)
            # Try direct id
            if "id" in response:
                return str(response["id"])
        elif isinstance(response, list):
            # If response is a list, try to extract ID from first element
            if len(response) > 0 and isinstance(response[0], dict):
                id_from_item = response[0].get("id")
                if id_from_item:
                    return str(id_from_item)
        return None

    try:
        if slo_id:
            # Try to update existing SLO
            try:
                result = request_json(
                    "PUT", f"{DD_API_BASE}/slo/{slo_id}",
                    headers, payload=slo_def, timeout=20, retries=3
                )
                print(f"    ✅ Updated SLO: {slo_name} (ID: {slo_id})")
                return True, "updated"
            except DatadogAPIError as e:
                if e.status_code == 404:
                    print(f"    SLO ID {slo_id} not found, creating new SLO")
                else:
                    raise

        # Create new SLO
        print(f"    Creating new SLO")
        result = request_json(
            "POST", f"{DD_API_BASE}/slo",
            headers, payload=slo_def, timeout=20, retries=3
        )

        # Handle various response shapes
        new_id = extract_slo_id(result)

        if not new_id:
            # If we couldn't extract ID from response, try to find it by name
            print(f"    Warning: Could not extract ID from response, searching by name...")
            try:
                slos_result = request_json(
                    "GET", f"{DD_API_BASE}/slo", headers, timeout=20, retries=3
                )
                slos_list = slos_result.get("data", []) if isinstance(slos_result, dict) else slos_result
                if isinstance(slos_list, list):
                    for slo in slos_list:
                        if isinstance(slo, dict) and slo.get("name") == slo_name:
                            new_id = slo.get("id")
                            if new_id:
                                break
            except Exception:
                pass  # Best effort, don't fail if we can't find it

        if new_id:
            print(f"    ✅ Created SLO: {slo_name} (ID: {new_id})")
        else:
            # Diagnostic output for unexpected response shape
            result_type = type(result).__name__
            result_preview = str(result)[:200] if result else "None"
            print(f"    ⚠️  Created SLO: {slo_name} (could not extract ID from response)")
            print(f"       Response type: {result_type}, preview: {result_preview}...")
        return True, "created"
    except DatadogAPIError as e:
        # Add diagnostic info for unexpected response shapes
        if hasattr(e, 'response') and e.response:
            print(f"    ❌ Error: {e}")
            print(f"       Status: {e.status_code}, Content-Type: {e.content_type}")
            print(f"       Body preview: {e.body_preview}")
        else:
            print(f"    ❌ Error: {e}")
        return False, "failed"
    except AttributeError as e:
        # Handle cases where response doesn't have expected attributes
        print(f"    ❌ Unexpected response shape: {e}")
        if 'result' in locals():
            print(f"       Response type: {type(result).__name__}")
            print(f"       Response preview: {str(result)[:200]}...")
        return False, "failed"
    except Exception as e:
        print(f"    ❌ Unexpected error: {e}")
        import traceback
        traceback.print_exc()
        return False, "failed"


def extract_metric_names(query: str) -> set[str]:
    """
    Extract metric names from a Datadog query string.

    Examples:
        - "p95:trace.http.request.duration{service:policy-insight}" -> {"trace.http.request.duration"}
        - "sum:policyinsight.job.backlog{*}" -> {"policyinsight.job.backlog"}
        - "avg(last_5m):avg:policyinsight.job.backlog{service:policy-insight} > 50" -> {"policyinsight.job.backlog"}
        - "avg:trace.http.request.duration{service:policy-insight}.as_count()" -> {"trace.http.request.duration"}

    Args:
        query: Datadog query string (e.g., from monitor "query" field or dashboard widget "q" field)

    Returns:
        Set of metric names found in the query (empty set if none found)
    """
    if not query or not isinstance(query, str):
        return set()

    metrics = set()

    # Remove time windows like "avg(last_5m):", "sum(last_1h):", etc.
    query = re.sub(r'\w+\([^)]+\):', '', query)

    # Remove aggregation functions: avg:, sum:, p95:, p99:, pct_95:, max:, min:, etc.
    # These can appear at the start or after time windows
    # Handle both lowercase and mixed case (e.g., p95, p99, pct_95)
    query = re.sub(r'^(?:avg|sum|p\d+|pct_\d+|max|min|count|rate):', '', query, flags=re.IGNORECASE)

    # Remove comparators and everything after them (>, <, >=, <=, ==, !=)
    query = re.sub(r'\s*[><=!]+.*$', '', query)

    # Extract metric name before tag set {...} or before method calls like .as_count(), .as_rate()
    # Metric names are typically dot-separated (e.g., trace.http.request.duration, policyinsight.job.backlog)
    # Pattern: capture alphanumeric/dot/underscore/hyphen sequence before { or .as_ or end of string
    # Use non-greedy match to stop at first { or .as_
    metric_pattern = r'([a-zA-Z0-9][a-zA-Z0-9._-]+?)(?:\{|\.as_|$)'

    matches = re.findall(metric_pattern, query)
    for match in matches:
        # Filter out non-metric tokens:
        # - Must contain at least one dot (metric names are dot-separated)
        # - Not just numbers
        # - Not aggregation functions
        if (match and
            '.' in match and
            not match.isdigit() and
            not re.match(r'^(avg|sum|p\d+|pct_\d+|max|min|count|rate)$', match, re.IGNORECASE)):
            metrics.add(match)

    return metrics


def extract_metric_names_from_template(template_path: Path) -> List[str]:
    """
    Extract metric names referenced in a template file.
    This is a best-effort extraction for metrics sanity checking.
    """
    try:
        template_data = load_json_template(template_path)
        all_metrics = set()

        # Extract from monitor "query" field
        if isinstance(template_data, dict):
            if "query" in template_data:
                query = template_data["query"]
                if isinstance(query, str):
                    all_metrics.update(extract_metric_names(query))

            # Extract from composite monitor queries
            if "composite_conditions" in template_data:
                def extract_from_composite(conditions):
                    if isinstance(conditions, list):
                        for condition in conditions:
                            if isinstance(condition, dict):
                                if "query" in condition:
                                    query = condition["query"]
                                    if isinstance(query, str):
                                        all_metrics.update(extract_metric_names(query))
                                if "operands" in condition:
                                    extract_from_composite(condition["operands"])

                extract_from_composite(template_data["composite_conditions"])

            # Extract from dashboard widget "q" fields
            if "widgets" in template_data:
                widgets = template_data["widgets"]
                if isinstance(widgets, list):
                    for widget in widgets:
                        if isinstance(widget, dict):
                            definition = widget.get("definition", {})
                            if isinstance(definition, dict):
                                # Check "requests" array for "q" fields
                                requests = definition.get("requests", [])
                                if isinstance(requests, list):
                                    for request in requests:
                                        if isinstance(request, dict) and "q" in request:
                                            query = request["q"]
                                            if isinstance(query, str):
                                                all_metrics.update(extract_metric_names(query))

                                # Also check direct "q" field in definition
                                if "q" in definition:
                                    query = definition["q"]
                                    if isinstance(query, str):
                                        all_metrics.update(extract_metric_names(query))

            # Extract from SLO queries
            if "query" in template_data:
                query = template_data["query"]
                if isinstance(query, dict):
                    # SLO queries can be nested
                    def extract_from_dict(obj):
                        if isinstance(obj, dict):
                            for key, value in obj.items():
                                if key == "query" and isinstance(value, str):
                                    all_metrics.update(extract_metric_names(value))
                                elif isinstance(value, (dict, list)):
                                    extract_from_dict(value)
                        elif isinstance(obj, list):
                            for item in obj:
                                extract_from_dict(item)

                    extract_from_dict(query)

        return sorted(list(all_metrics))
    except Exception:
        return []


def check_metrics_sanity(headers: Dict[str, str], templates_dir: Path) -> None:
    """
    Best-effort check to warn if referenced metrics might not exist.
    This is informational only and won't fail the script.
    """
    print()
    print("Metrics sanity check (best-effort)...")

    # Collect all metric names from templates
    all_metrics = set()
    for asset_dir in ["dashboards", "monitors", "slos"]:
        asset_path = templates_dir / asset_dir
        if asset_path.exists():
            for template_file in asset_path.glob("*.json"):
                metrics = extract_metric_names_from_template(template_file)
                all_metrics.update(metrics)

    if not all_metrics:
        print("  ⚠️  No metrics detected in templates (skipping check)")
        return

    # Try to query metrics API (best-effort, may fail due to permissions)
    try:
        # Use the metrics list endpoint to check if any metrics exist
        # This is a lightweight check
        metrics_url = f"https://api.{DD_SITE}/api/v1/metrics"
        # Query for metrics from the last hour
        import time
        end_time = int(time.time())
        start_time = end_time - 3600  # 1 hour ago

        params = f"?from={start_time}&to={end_time}"
        try:
            request_json("GET", metrics_url + params, headers, timeout=10, retries=1)
            print("  ✅ Metrics API accessible (some metrics may still be missing)")
        except DatadogAPIError as e:
            if e.status_code == 403:
                print("  ⚠️  SKIPPED: Insufficient permissions to verify metrics")
                print(f"     (Requires 'metrics_read' scope)")
            else:
                print(f"  ⚠️  Could not verify metrics: {e.status_code}")
    except Exception as e:
        print(f"  ⚠️  Metrics check skipped: {e}")

    if all_metrics:
        print(f"  ℹ️  Referenced metrics in templates: {', '.join(sorted(all_metrics)[:10])}")
        if len(all_metrics) > 10:
            print(f"     ... and {len(all_metrics) - 10} more")
    print()


def apply_assets(templates_dir: Path) -> Dict[str, Dict[str, int]]:
    """
    Apply all templates from the templates directory.

    Returns:
        Dictionary with asset type -> {created, updated, failed} counts
    """
    headers = get_headers()

    dashboards_dir = templates_dir / "dashboards"
    monitors_dir = templates_dir / "monitors"
    slos_dir = templates_dir / "slos"

    # Track results per asset type
    results = {
        "dashboards": defaultdict(int),
        "monitors": defaultdict(int),
        "slos": defaultdict(int)
    }

    print("Applying Datadog assets from templates...")
    print(f"DD_SITE: {DD_SITE}")
    print()

    # Apply dashboards
    if dashboards_dir.exists():
        print("Dashboards:")
        for template_file in sorted(dashboards_dir.glob("*.json")):
            success, operation = apply_dashboard(template_file, headers)
            if success:
                results["dashboards"][operation] += 1
            else:
                results["dashboards"]["failed"] += 1
        print()

    # Apply monitors
    if monitors_dir.exists():
        print("Monitors:")
        for template_file in sorted(monitors_dir.glob("*.json")):
            success, operation = apply_monitor(template_file, headers)
            if success:
                results["monitors"][operation] += 1
            else:
                results["monitors"]["failed"] += 1
        print()

    # Apply SLOs
    if slos_dir.exists():
        print("SLOs:")
        for template_file in sorted(slos_dir.glob("*.json")):
            success, operation = apply_slo(template_file, headers)
            if success:
                results["slos"][operation] += 1
            else:
                results["slos"]["failed"] += 1
        print()

    return results


def print_summary(results: Dict[str, Dict[str, int]]) -> bool:
    """
    Print summary of results and return True if all succeeded.
    """
    print("Summary:")
    total_failed = 0

    for asset_type, counts in sorted(results.items()):
        created = counts.get("created", 0)
        updated = counts.get("updated", 0)
        failed = counts.get("failed", 0)
        total_failed += failed

        if created + updated + failed == 0:
            print(f"  {asset_type.capitalize()}: (none found)")
        else:
            parts = []
            if created > 0:
                parts.append(f"{created} created")
            if updated > 0:
                parts.append(f"{updated} updated")
            if failed > 0:
                parts.append(f"{failed} failed")
            print(f"  {asset_type.capitalize()}: {', '.join(parts)}")

    print()

    if total_failed == 0:
        print("✅ All assets applied successfully!")
        print()
        print("Next step: Run export-assets.py to export the created assets with real IDs")
        return True
    else:
        print(f"❌ {total_failed} asset(s) failed to apply")
        return False


def main():
    parser = argparse.ArgumentParser(description="Apply Datadog assets from templates")
    parser.add_argument(
        "--templates-dir",
        type=str,
        default="./datadog/templates",
        help="Templates directory (default: ./datadog/templates)"
    )
    parser.add_argument(
        "--skip-metrics-check",
        action="store_true",
        help="Skip metrics sanity check"
    )

    args = parser.parse_args()
    templates_dir = Path(args.templates_dir).resolve()

    if not templates_dir.exists():
        print(f"❌ Error: Templates directory does not exist: {templates_dir}")
        sys.exit(1)

    print(f"Templates directory: {templates_dir}")
    print(f"DD_API_KEY: {'*' * 10 if os.getenv('DD_API_KEY') else 'NOT SET'}")
    print(f"DD_APP_KEY: {'*' * 10 if os.getenv('DD_APP_KEY') else 'NOT SET'}")
    print()

    try:
        # Preflight validation
        api_key = os.getenv("DD_API_KEY")
        app_key = os.getenv("DD_APP_KEY")

        if not api_key or not app_key:
            print("❌ Error: DD_API_KEY and DD_APP_KEY environment variables are required")
            sys.exit(1)

        print("Validating API keys...")
        validate_api_key(DD_SITE, api_key)
        validate_app_key(DD_SITE, api_key, app_key)
        print("✅ API keys validated")
        print()

        # Apply assets
        results = apply_assets(templates_dir)

        # Print summary
        all_succeeded = print_summary(results)

        # Metrics sanity check (best-effort, doesn't fail the script)
        if not args.skip_metrics_check:
            try:
                headers = get_headers()
                check_metrics_sanity(headers, templates_dir)
            except Exception as e:
                print(f"⚠️  Metrics check failed (non-fatal): {e}")
                print()

        # Exit with error code if any failures
        if not all_succeeded:
            sys.exit(1)

    except ValueError as e:
        print(f"❌ Validation Error: {e}")
        sys.exit(1)
    except DatadogAPIError as e:
        print(f"❌ Datadog API Error: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"❌ Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()

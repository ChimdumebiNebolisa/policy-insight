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
from typing import Dict, Optional, Tuple, List
from collections import defaultdict

# Import shared modules (assume they're in the same directory)
sys.path.insert(0, str(Path(__file__).parent))
from dd_http import request_json, DatadogAPIError
from validate_keys import validate_api_key, validate_app_key

# Get Datadog site from environment (default: datadoghq.com)
DD_SITE = os.getenv("DD_SITE", "datadoghq.com")
DD_API_BASE = f"https://api.{DD_SITE}/api/v1"


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
        with open(template_path, 'r', encoding='utf-8') as f:
            dashboard_def = json.load(f)
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

    Returns:
        (success: bool, operation: str) where operation is "created", "updated", or "failed"
    """
    try:
        with open(template_path, 'r', encoding='utf-8') as f:
            monitor_def = json.load(f)
    except json.JSONDecodeError as e:
        print(f"    ❌ Invalid JSON in template: {e}")
        return False, "failed"

    # Remove any ID if present
    monitor_id = monitor_def.pop("id", None)
    monitor_name = monitor_def.get("name", "Unknown")
    print(f"  Applying monitor: {monitor_name}")

    try:
        if monitor_id:
            # Try to update existing monitor
            try:
                result = request_json(
                    "PUT", f"{DD_API_BASE}/monitor/{monitor_id}",
                    headers, payload=monitor_def, timeout=20, retries=3
                )
                print(f"    ✅ Updated monitor: {monitor_name} (ID: {monitor_id})")
                return True, "updated"
            except DatadogAPIError as e:
                if e.status_code == 404:
                    print(f"    Monitor ID {monitor_id} not found, creating new monitor")
                else:
                    raise

        # Create new monitor
        print(f"    Creating new monitor")
        result = request_json(
            "POST", f"{DD_API_BASE}/monitor",
            headers, payload=monitor_def, timeout=20, retries=3
        )
        new_id = result.get("id")
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

    Returns:
        (success: bool, operation: str) where operation is "created", "updated", or "failed"
    """
    try:
        with open(template_path, 'r', encoding='utf-8') as f:
            slo_def = json.load(f)
    except json.JSONDecodeError as e:
        print(f"    ❌ Invalid JSON in template: {e}")
        return False, "failed"

    # Remove any ID if present
    slo_id = slo_def.pop("id", None)
    slo_name = slo_def.get("name", "Unknown")
    print(f"  Applying SLO: {slo_name}")

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
        # SLO response structure may vary
        new_id = result.get("data", {}).get("id") or result.get("id")
        print(f"    ✅ Created SLO: {slo_name} (ID: {new_id})")
        return True, "created"
    except DatadogAPIError as e:
        print(f"    ❌ Error: {e}")
        return False, "failed"
    except Exception as e:
        print(f"    ❌ Unexpected error: {e}")
        import traceback
        traceback.print_exc()
        return False, "failed"


def extract_metric_names_from_template(template_path: Path) -> List[str]:
    """
    Extract metric names referenced in a template file.
    This is a best-effort extraction for metrics sanity checking.
    """
    try:
        with open(template_path, 'r', encoding='utf-8') as f:
            content = f.read()

        # Look for metric patterns like "avg:policyinsight.job.backlog" or "policyinsight.llm.cost"
        # This regex looks for common metric patterns in Datadog queries
        patterns = [
            r'(?:avg|sum|p95|p99|max|min):([a-zA-Z0-9._-]+)',
            r'"q"\s*:\s*"[^"]*:([a-zA-Z0-9._-]+)',
            r'query["\']?\s*:\s*["\'][^"\']*:([a-zA-Z0-9._-]+)',
        ]

        metrics = set()
        for pattern in patterns:
            matches = re.findall(pattern, content)
            metrics.update(matches)

        return sorted(list(metrics))
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

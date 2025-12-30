#!/usr/bin/env python3
"""
Export Datadog dashboards, monitors, and SLOs to JSON files.
Requires DD_API_KEY and DD_APP_KEY environment variables.

Supports DD_SITE environment variable (default: datadoghq.com).
For EU: DD_SITE=datadoghq.eu
For US3: DD_SITE=us3.datadoghq.com

Usage:
    python export-assets.py [--output-dir ./datadog]
"""

import os
import sys
import json
import argparse
import requests
from pathlib import Path
from typing import Dict, List, Optional

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


def export_dashboards(output_dir: Path) -> None:
    """Export all dashboards to JSON files."""
    headers = get_headers()
    dashboards_dir = output_dir / "dashboards"
    dashboards_dir.mkdir(parents=True, exist_ok=True)

    print("Fetching dashboards...")
    response = requests.get(f"{DD_API_BASE}/dashboard", headers=headers)
    response.raise_for_status()

    dashboards = response.json().get("dashboards", [])
    print(f"Found {len(dashboards)} dashboard(s)")

    for dashboard in dashboards:
        dashboard_id = dashboard.get("id")
        dashboard_title = dashboard.get("title", "unknown")

        # Fail fast if ID is missing
        if not dashboard_id:
            raise ValueError(f"Dashboard '{dashboard_title}' missing 'id' field. This is not a real export.")

        # Get full dashboard definition
        detail_response = requests.get(
            f"{DD_API_BASE}/dashboard/{dashboard_id}",
            headers=headers
        )
        detail_response.raise_for_status()
        dashboard_def = detail_response.json()

        # Validate that the export contains the ID
        if "id" not in dashboard_def:
            raise ValueError(f"Dashboard export for '{dashboard_title}' missing 'id' field. Invalid export.")

        # Sanitize filename
        safe_title = "".join(c for c in dashboard_title if c.isalnum() or c in (' ', '-', '_')).strip()
        safe_title = safe_title.replace(' ', '-').lower()
        filename = f"{safe_title}.json"
        filepath = dashboards_dir / filename

        # Write to file
        with open(filepath, 'w') as f:
            json.dump(dashboard_def, f, indent=2)

        print(f"  Exported: {filepath} (ID: {dashboard_id})")


def export_monitors(output_dir: Path) -> None:
    """Export all monitors to JSON files."""
    headers = get_headers()
    monitors_dir = output_dir / "monitors"
    monitors_dir.mkdir(parents=True, exist_ok=True)

    print("Fetching monitors...")
    response = requests.get(f"{DD_API_BASE}/monitor", headers=headers)
    response.raise_for_status()

    monitors = response.json()
    print(f"Found {len(monitors)} monitor(s)")

    for monitor in monitors:
        monitor_id = monitor.get("id")
        monitor_name = monitor.get("name", "unknown")

        # Fail fast if ID is missing
        if not monitor_id:
            raise ValueError(f"Monitor '{monitor_name}' missing 'id' field. This is not a real export.")

        # Get full monitor definition
        detail_response = requests.get(
            f"{DD_API_BASE}/monitor/{monitor_id}",
            headers=headers
        )
        detail_response.raise_for_status()
        monitor_def = detail_response.json()

        # Validate that the export contains the ID
        if "id" not in monitor_def:
            raise ValueError(f"Monitor export for '{monitor_name}' missing 'id' field. Invalid export.")

        # Sanitize filename
        safe_name = "".join(c for c in monitor_name if c.isalnum() or c in (' ', '-', '_')).strip()
        safe_name = safe_name.replace(' ', '-').lower()
        filename = f"{safe_name}.json"
        filepath = monitors_dir / filename

        # Write to file
        with open(filepath, 'w') as f:
            json.dump(monitor_def, f, indent=2)

        print(f"  Exported: {filepath} (ID: {monitor_id})")


def export_slos(output_dir: Path) -> None:
    """Export all SLOs to JSON files."""
    headers = get_headers()
    slos_dir = output_dir / "slos"
    slos_dir.mkdir(parents=True, exist_ok=True)

    print("Fetching SLOs...")
    response = requests.get(f"{DD_API_BASE}/slo", headers=headers)
    response.raise_for_status()

    slos = response.json().get("data", [])
    print(f"Found {len(slos)} SLO(s)")

    for slo in slos:
        slo_id = slo.get("id")
        slo_name = slo.get("name", "unknown")

        # Fail fast if ID is missing
        if not slo_id:
            raise ValueError(f"SLO '{slo_name}' missing 'id' field. This is not a real export.")

        # Get full SLO definition
        detail_response = requests.get(
            f"{DD_API_BASE}/slo/{slo_id}",
            headers=headers
        )
        detail_response.raise_for_status()
        slo_def = detail_response.json()

        # Validate that the export contains the ID
        # SLOs may have id in data.id or at root
        if "id" not in slo_def and ("data" not in slo_def or "id" not in slo_def.get("data", {})):
            raise ValueError(f"SLO export for '{slo_name}' missing 'id' field. Invalid export.")

        # Sanitize filename
        safe_name = "".join(c for c in slo_name if c.isalnum() or c in (' ', '-', '_')).strip()
        safe_name = safe_name.replace(' ', '-').lower()
        filename = f"{safe_name}.json"
        filepath = slos_dir / filename

        # Write to file
        with open(filepath, 'w') as f:
            json.dump(slo_def, f, indent=2)

        print(f"  Exported: {filepath} (ID: {slo_id})")


def main():
    parser = argparse.ArgumentParser(description="Export Datadog assets to JSON files")
    parser.add_argument(
        "--output-dir",
        type=str,
        default="./datadog",
        help="Output directory (default: ./datadog)"
    )
    parser.add_argument(
        "--dashboards-only",
        action="store_true",
        help="Export only dashboards"
    )
    parser.add_argument(
        "--monitors-only",
        action="store_true",
        help="Export only monitors"
    )
    parser.add_argument(
        "--slos-only",
        action="store_true",
        help="Export only SLOs"
    )

    args = parser.parse_args()
    output_dir = Path(args.output_dir).resolve()

    print(f"Exporting Datadog assets to: {output_dir}")
    print(f"DD_SITE: {DD_SITE}")
    print(f"DD_API_KEY: {'*' * 10 if os.getenv('DD_API_KEY') else 'NOT SET'}")
    print(f"DD_APP_KEY: {'*' * 10 if os.getenv('DD_APP_KEY') else 'NOT SET'}")
    print()

    try:
        if args.dashboards_only:
            export_dashboards(output_dir)
        elif args.monitors_only:
            export_monitors(output_dir)
        elif args.slos_only:
            export_slos(output_dir)
        else:
            export_dashboards(output_dir)
            print()
            export_monitors(output_dir)
            print()
            export_slos(output_dir)

        print()
        print("✅ Export completed successfully!")

    except requests.exceptions.HTTPError as e:
        print(f"❌ HTTP Error: {e}")
        if e.response is not None:
            print(f"   Response: {e.response.text}")
        sys.exit(1)
    except Exception as e:
        print(f"❌ Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()


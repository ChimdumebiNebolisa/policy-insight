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
from pathlib import Path
from typing import Dict

# Import shared modules (assume they're in the same directory)
sys.path.insert(0, str(Path(__file__).parent))
from dd_http import request_json, DatadogAPIError
from validate_keys import validate_api_key, validate_app_key

# Get Datadog site from environment (default: datadoghq.com)
DD_SITE = os.getenv("DD_SITE", "datadoghq.com")
DD_API_BASE = f"https://api.{DD_SITE}/api/v1"


class DatadogPermissionError(Exception):
    """Exception raised when 403 Forbidden indicates missing permissions."""
    def __init__(self, endpoint: str):
        self.endpoint = endpoint
        super().__init__(
            f"403 Forbidden accessing {endpoint}.\n"
            f"Application key lacks required READ scopes:\n"
            f"  - monitors_read\n"
            f"  - dashboards_read\n"
            f"  - slo_read\n"
            f"\n"
            f"Update your Application key at:\n"
            f"https://app.datadoghq.com/organization-settings/application-keys"
        )


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
    try:
        result = request_json(
            "GET", f"{DD_API_BASE}/dashboard", headers, timeout=20, retries=3
        )
        dashboards = result.get("dashboards", [])
    except DatadogAPIError as e:
        if e.status_code == 403:
            raise DatadogPermissionError(f"{DD_API_BASE}/dashboard") from e
        raise

    print(f"Found {len(dashboards)} dashboard(s)")

    for dashboard in dashboards:
        dashboard_id = dashboard.get("id")
        dashboard_title = dashboard.get("title", "unknown")

        # Fail fast if ID is missing
        if not dashboard_id:
            raise ValueError(
                f"Dashboard '{dashboard_title}' missing 'id' field. "
                f"This is not a real export."
            )

        # Get full dashboard definition
        try:
            dashboard_def = request_json(
                "GET", f"{DD_API_BASE}/dashboard/{dashboard_id}",
                headers, timeout=20, retries=3
            )
        except DatadogAPIError as e:
            if e.status_code == 403:
                raise DatadogPermissionError(
                    f"{DD_API_BASE}/dashboard/{dashboard_id}"
                ) from e
            raise

        # Validate that the export contains the ID
        if "id" not in dashboard_def:
            raise ValueError(
                f"Dashboard export for '{dashboard_title}' missing 'id' field. "
                f"Invalid export."
            )

        # Sanitize filename
        safe_title = "".join(
            c for c in dashboard_title if c.isalnum() or c in (' ', '-', '_')
        ).strip()
        safe_title = safe_title.replace(' ', '-').lower()
        filename = f"{safe_title}.json"
        filepath = dashboards_dir / filename

        # Write to file (only after successful fetch)
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(dashboard_def, f, indent=2)

        print(f"  Exported: {filepath} (ID: {dashboard_id})")


def export_monitors(output_dir: Path) -> None:
    """Export all monitors to JSON files."""
    headers = get_headers()
    monitors_dir = output_dir / "monitors"
    monitors_dir.mkdir(parents=True, exist_ok=True)

    print("Fetching monitors...")
    try:
        monitors = request_json(
            "GET", f"{DD_API_BASE}/monitor", headers, timeout=20, retries=3
        )
    except DatadogAPIError as e:
        if e.status_code == 403:
            raise DatadogPermissionError(f"{DD_API_BASE}/monitor") from e
        raise

    # Monitors endpoint returns a list, not a dict with "monitors" key
    if not isinstance(monitors, list):
        raise ValueError(f"Unexpected response format from monitors endpoint: {type(monitors)}")

    print(f"Found {len(monitors)} monitor(s)")

    for monitor in monitors:
        monitor_id = monitor.get("id")
        monitor_name = monitor.get("name", "unknown")

        # Fail fast if ID is missing
        if not monitor_id:
            raise ValueError(
                f"Monitor '{monitor_name}' missing 'id' field. "
                f"This is not a real export."
            )

        # Get full monitor definition
        try:
            monitor_def = request_json(
                "GET", f"{DD_API_BASE}/monitor/{monitor_id}",
                headers, timeout=20, retries=3
            )
        except DatadogAPIError as e:
            if e.status_code == 403:
                raise DatadogPermissionError(
                    f"{DD_API_BASE}/monitor/{monitor_id}"
                ) from e
            raise

        # Validate that the export contains the ID
        if "id" not in monitor_def:
            raise ValueError(
                f"Monitor export for '{monitor_name}' missing 'id' field. "
                f"Invalid export."
            )

        # Sanitize filename
        safe_name = "".join(
            c for c in monitor_name if c.isalnum() or c in (' ', '-', '_')
        ).strip()
        safe_name = safe_name.replace(' ', '-').lower()
        filename = f"{safe_name}.json"
        filepath = monitors_dir / filename

        # Write to file (only after successful fetch)
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(monitor_def, f, indent=2)

        print(f"  Exported: {filepath} (ID: {monitor_id})")


def export_slos(output_dir: Path) -> None:
    """Export all SLOs to JSON files."""
    headers = get_headers()
    slos_dir = output_dir / "slos"
    slos_dir.mkdir(parents=True, exist_ok=True)

    print("Fetching SLOs...")
    try:
        result = request_json(
            "GET", f"{DD_API_BASE}/slo", headers, timeout=20, retries=3
        )
        slos = result.get("data", [])
    except DatadogAPIError as e:
        if e.status_code == 403:
            raise DatadogPermissionError(f"{DD_API_BASE}/slo") from e
        raise

    print(f"Found {len(slos)} SLO(s)")

    for slo in slos:
        slo_id = slo.get("id")
        slo_name = slo.get("name", "unknown")

        # Fail fast if ID is missing
        if not slo_id:
            raise ValueError(
                f"SLO '{slo_name}' missing 'id' field. "
                f"This is not a real export."
            )

        # Get full SLO definition
        try:
            slo_def = request_json(
                "GET", f"{DD_API_BASE}/slo/{slo_id}",
                headers, timeout=20, retries=3
            )
        except DatadogAPIError as e:
            if e.status_code == 403:
                raise DatadogPermissionError(
                    f"{DD_API_BASE}/slo/{slo_id}"
                ) from e
            raise

        # Validate that the export contains the ID
        # SLOs may have id in data.id or at root
        if "id" not in slo_def and (
            "data" not in slo_def or "id" not in slo_def.get("data", {})
        ):
            raise ValueError(
                f"SLO export for '{slo_name}' missing 'id' field. "
                f"Invalid export."
            )

        # Sanitize filename
        safe_name = "".join(
            c for c in slo_name if c.isalnum() or c in (' ', '-', '_')
        ).strip()
        safe_name = safe_name.replace(' ', '-').lower()
        filename = f"{safe_name}.json"
        filepath = slos_dir / filename

        # Write to file (only after successful fetch)
        with open(filepath, 'w', encoding='utf-8') as f:
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

        # Export assets
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

    except DatadogPermissionError as e:
        print(f"❌ Permission Error: {e}")
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

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
import requests
from pathlib import Path
from typing import Dict, Optional

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


def apply_dashboard(template_path: Path, headers: Dict[str, str]) -> None:
    """Create or update a dashboard from template."""
    with open(template_path, 'r') as f:
        dashboard_def = json.load(f)

    # Remove any ID if present (templates shouldn't have real IDs)
    dashboard_def.pop("id", None)
    dashboard_def.pop("author_handle", None)
    dashboard_def.pop("created_at", None)
    dashboard_def.pop("modified_at", None)

    dashboard_title = dashboard_def.get("title", "Unknown")
    print(f"  Applying dashboard: {dashboard_title}")

    # Try to find existing dashboard by title
    response = requests.get(f"{DD_API_BASE}/dashboard", headers=headers)
    response.raise_for_status()
    existing_dashboards = response.json().get("dashboards", [])

    existing_id = None
    for dash in existing_dashboards:
        if dash.get("title") == dashboard_title:
            existing_id = dash.get("id")
            break

    if existing_id:
        # Update existing dashboard
        print(f"    Updating existing dashboard (ID: {existing_id})")
        response = requests.put(
            f"{DD_API_BASE}/dashboard/{existing_id}",
            headers=headers,
            json=dashboard_def
        )
        response.raise_for_status()
        print(f"    ✅ Updated dashboard: {dashboard_title}")
    else:
        # Create new dashboard
        print(f"    Creating new dashboard")
        response = requests.post(
            f"{DD_API_BASE}/dashboard",
            headers=headers,
            json=dashboard_def
        )
        response.raise_for_status()
        result = response.json()
        new_id = result.get("dashboard", {}).get("id")
        print(f"    ✅ Created dashboard: {dashboard_title} (ID: {new_id})")


def apply_monitor(template_path: Path, headers: Dict[str, str]) -> None:
    """Create or update a monitor from template."""
    with open(template_path, 'r') as f:
        monitor_def = json.load(f)

    # Remove any ID if present
    monitor_id = monitor_def.pop("id", None)
    monitor_name = monitor_def.get("name", "Unknown")
    print(f"  Applying monitor: {monitor_name}")

    if monitor_id:
        # Try to update existing monitor
        try:
            response = requests.put(
                f"{DD_API_BASE}/monitor/{monitor_id}",
                headers=headers,
                json=monitor_def
            )
            response.raise_for_status()
            print(f"    ✅ Updated monitor: {monitor_name} (ID: {monitor_id})")
            return
        except requests.exceptions.HTTPError as e:
            if e.response.status_code == 404:
                print(f"    Monitor ID {monitor_id} not found, creating new monitor")
            else:
                raise

    # Create new monitor
    print(f"    Creating new monitor")
    response = requests.post(
        f"{DD_API_BASE}/monitor",
        headers=headers,
        json=monitor_def
    )
    response.raise_for_status()
    result = response.json()
    new_id = result.get("id")
    print(f"    ✅ Created monitor: {monitor_name} (ID: {new_id})")


def apply_slo(template_path: Path, headers: Dict[str, str]) -> None:
    """Create or update an SLO from template."""
    with open(template_path, 'r') as f:
        slo_def = json.load(f)

    # Remove any ID if present
    slo_id = slo_def.pop("id", None)
    slo_name = slo_def.get("name", "Unknown")
    print(f"  Applying SLO: {slo_name}")

    if slo_id:
        # Try to update existing SLO
        try:
            response = requests.put(
                f"{DD_API_BASE}/slo/{slo_id}",
                headers=headers,
                json=slo_def
            )
            response.raise_for_status()
            print(f"    ✅ Updated SLO: {slo_name} (ID: {slo_id})")
            return
        except requests.exceptions.HTTPError as e:
            if e.response.status_code == 404:
                print(f"    SLO ID {slo_id} not found, creating new SLO")
            else:
                raise

    # Create new SLO
    print(f"    Creating new SLO")
    response = requests.post(
        f"{DD_API_BASE}/slo",
        headers=headers,
        json=slo_def
    )
    response.raise_for_status()
    result = response.json()
    # SLO response structure may vary
    new_id = result.get("data", {}).get("id") or result.get("id")
    print(f"    ✅ Created SLO: {slo_name} (ID: {new_id})")


def apply_assets(templates_dir: Path) -> None:
    """Apply all templates from the templates directory."""
    headers = get_headers()

    dashboards_dir = templates_dir / "dashboards"
    monitors_dir = templates_dir / "monitors"
    slos_dir = templates_dir / "slos"

    print("Applying Datadog assets from templates...")
    print(f"DD_SITE: {DD_SITE}")
    print()

    # Apply dashboards
    if dashboards_dir.exists():
        print("Dashboards:")
        for template_file in dashboards_dir.glob("*.json"):
            try:
                apply_dashboard(template_file, headers)
            except Exception as e:
                print(f"    ❌ Error applying {template_file.name}: {e}")
        print()

    # Apply monitors
    if monitors_dir.exists():
        print("Monitors:")
        for template_file in monitors_dir.glob("*.json"):
            try:
                apply_monitor(template_file, headers)
            except Exception as e:
                print(f"    ❌ Error applying {template_file.name}: {e}")
        print()

    # Apply SLOs
    if slos_dir.exists():
        print("SLOs:")
        for template_file in slos_dir.glob("*.json"):
            try:
                apply_slo(template_file, headers)
            except Exception as e:
                print(f"    ❌ Error applying {template_file.name}: {e}")
        print()


def main():
    parser = argparse.ArgumentParser(description="Apply Datadog assets from templates")
    parser.add_argument(
        "--templates-dir",
        type=str,
        default="./datadog/templates",
        help="Templates directory (default: ./datadog/templates)"
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
        apply_assets(templates_dir)
        print("✅ All assets applied successfully!")
        print()
        print("Next step: Run export-assets.py to export the created assets with real IDs")

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


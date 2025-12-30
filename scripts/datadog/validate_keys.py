#!/usr/bin/env python3
"""
Preflight validation for Datadog API and Application keys.
"""

import os
import sys
from typing import Dict
from dd_http import request_json, DatadogAPIError


def validate_api_key(site: str, api_key: str) -> None:
    """
    Validate API key by calling /api/v1/validate endpoint.

    Args:
        site: Datadog site (e.g., 'datadoghq.com')
        api_key: Datadog API key

    Raises:
        ValueError: If API key is invalid
        DatadogAPIError: If validation request fails
    """
    url = f"https://api.{site}/api/v1/validate"
    headers = {"DD-API-KEY": api_key}

    try:
        result = request_json("GET", url, headers, timeout=10, retries=1)
        if not result.get("valid", False):
            raise ValueError(f"API key validation failed: {result}")
    except DatadogAPIError as e:
        if e.status_code == 403:
            raise ValueError(
                f"API key is invalid or unauthorized (403). "
                f"Check your API key at: https://app.datadoghq.com/organization-settings/api-keys"
            ) from e
        raise ValueError(f"API key validation failed: {e}") from e


def validate_app_key(site: str, api_key: str, app_key: str) -> None:
    """
    Validate Application key by testing a read endpoint.
    Detects 403 and provides actionable error message with required scopes.

    Args:
        site: Datadog site (e.g., 'datadoghq.com')
        api_key: Datadog API key
        app_key: Datadog Application key

    Raises:
        ValueError: If Application key lacks required permissions
        DatadogAPIError: If validation request fails for other reasons
    """
    url = f"https://api.{site}/api/v1/monitor"
    headers = {
        "DD-API-KEY": api_key,
        "DD-APPLICATION-KEY": app_key,
        "Content-Type": "application/json"
    }

    try:
        # Test with a lightweight read endpoint (list monitors)
        request_json("GET", url, headers, timeout=10, retries=1)
    except DatadogAPIError as e:
        if e.status_code == 403:
            raise ValueError(
                f"Application key lacks required permissions (403 Forbidden).\n"
                f"Required scopes:\n"
                f"  - monitors_read, monitors_write\n"
                f"  - dashboards_read, dashboards_write\n"
                f"  - slo_read, slo_write\n"
                f"\n"
                f"Update your Application key at:\n"
                f"https://app.datadoghq.com/organization-settings/application-keys\n"
                f"\n"
                f"Response details: {e.body_preview}"
            ) from e
        raise ValueError(f"Application key validation failed: {e}") from e


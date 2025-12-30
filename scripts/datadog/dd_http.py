#!/usr/bin/env python3
"""
Shared HTTP helper for Datadog API requests with retries, timeouts, and diagnostics.
"""

import json
import time
import requests
from typing import Dict, Optional, Any


def body_preview(text: str, n: int = 300) -> str:
    """Get first n characters of text, truncated with ellipsis if longer."""
    if len(text) <= n:
        return text
    return text[:n] + "..."


class DatadogAPIError(Exception):
    """Exception raised for Datadog API errors with diagnostics."""
    def __init__(self, method: str, url: str, status_code: Optional[int],
                 content_type: Optional[str], body_preview: str):
        self.method = method
        self.url = url
        self.status_code = status_code
        self.content_type = content_type
        self.body_preview = body_preview

        msg = f"{method} {url}"
        if status_code:
            msg += f" -> {status_code}"
        if content_type:
            msg += f" (Content-Type: {content_type})"
        if body_preview:
            msg += f"\nResponse body preview: {body_preview}"
        super().__init__(msg)


def request_json(method: str, url: str, headers: Dict[str, str],
                 payload: Optional[Any] = None, timeout: int = 20,
                 retries: int = 3) -> Any:
    """
    Send HTTP request with retries, timeout, and JSON validation.

    Args:
        method: HTTP method (GET, POST, PUT, etc.)
        url: Full URL
        headers: Request headers
        payload: Optional JSON-serializable payload (sent as JSON body)
        timeout: Request timeout in seconds
        retries: Number of retries for 429 and 5xx errors

    Returns:
        Parsed JSON response data

    Raises:
        DatadogAPIError: For non-2xx responses or non-JSON content
        requests.exceptions.RequestException: For network errors
    """
    for attempt in range(retries + 1):
        try:
            # Prepare request
            kwargs = {
                "headers": headers,
                "timeout": timeout,
            }
            if payload is not None:
                kwargs["json"] = payload

            # Send request
            response = requests.request(method, url, **kwargs)

            # Check for retryable errors
            if response.status_code in (429, 500, 502, 503, 504) and attempt < retries:
                wait_time = (2 ** attempt) + (time.time() % 1)  # Exponential backoff with jitter
                time.sleep(wait_time)
                continue

            # Check status code
            if not (200 <= response.status_code < 300):
                content_type = response.headers.get("Content-Type", "")
                body = body_preview(response.text)
                raise DatadogAPIError(
                    method, url, response.status_code, content_type, body
                )

            # Validate content-type
            content_type = response.headers.get("Content-Type", "")
            if "application/json" not in content_type.lower():
                body = body_preview(response.text)
                raise DatadogAPIError(
                    method, url, response.status_code, content_type,
                    f"Expected JSON response, got {content_type}. Body: {body}"
                )

            # Parse JSON
            try:
                return response.json()
            except json.JSONDecodeError as e:
                body = body_preview(response.text)
                raise DatadogAPIError(
                    method, url, response.status_code, content_type,
                    f"Invalid JSON response: {str(e)}. Body preview: {body}"
                ) from e

        except requests.exceptions.Timeout:
            if attempt < retries:
                wait_time = (2 ** attempt) + (time.time() % 1)
                time.sleep(wait_time)
                continue
            raise
        except requests.exceptions.RequestException as e:
            if attempt < retries and isinstance(e, requests.exceptions.HTTPError):
                # Check if it's a retryable status code
                if hasattr(e, 'response') and e.response:
                    status = e.response.status_code
                    if status in (429, 500, 502, 503, 504):
                        wait_time = (2 ** attempt) + (time.time() % 1)
                        time.sleep(wait_time)
                        continue
            raise

    # Should not reach here, but handle edge case
    raise RuntimeError(f"Exhausted {retries} retries for {method} {url}")


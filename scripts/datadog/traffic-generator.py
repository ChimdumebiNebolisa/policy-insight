#!/usr/bin/env python3
"""
Traffic generator for PolicyInsight to trigger Datadog monitors.
Simulates various scenarios: latency spikes, job backlog, LLM cost spikes.

Usage:
    python traffic-generator.py --scenario latency --duration 60
    python traffic-generator.py --scenario backlog --duration 120
    python traffic-generator.py --scenario llm-cost --duration 90
"""

import os
import sys
import time
import argparse
import requests
import threading
from typing import Optional
from pathlib import Path

# Default base URL
DEFAULT_BASE_URL = "http://localhost:8080"


def generate_latency_spike(base_url: str, duration_seconds: int) -> None:
    """Generate traffic that causes latency spikes by sending many concurrent requests."""
    print(f"🚀 Generating latency spike scenario for {duration_seconds} seconds...")
    print(f"   Sending concurrent requests to {base_url}/api/documents/upload")

    end_time = time.time() + duration_seconds
    request_count = 0

    def make_request():
        nonlocal request_count
        while time.time() < end_time:
            try:
                # Create a small PDF file in memory
                files = {
                    'file': ('test.pdf', b'%PDF-1.4\n1 0 obj\n<<\n/Type /Catalog\n>>\nendobj\nxref\n0 1\ntrailer\n<<\n/Size 1\n>>\nstartxref\n9\n%%EOF', 'application/pdf')
                }
                response = requests.post(
                    f"{base_url}/api/documents/upload",
                    files=files,
                    timeout=5
                )
                request_count += 1
                if request_count % 10 == 0:
                    print(f"   Sent {request_count} requests...")
            except Exception as e:
                print(f"   Request failed: {e}")
            time.sleep(0.1)  # Small delay between requests

    # Start multiple threads to create concurrent load
    threads = []
    for i in range(10):  # 10 concurrent threads
        t = threading.Thread(target=make_request)
        t.start()
        threads.append(t)

    # Wait for all threads
    for t in threads:
        t.join()

    print(f"✅ Completed: {request_count} requests sent")


def generate_backlog_spike(base_url: str, duration_seconds: int) -> None:
    """Generate many job uploads to create a backlog."""
    print(f"🚀 Generating job backlog scenario for {duration_seconds} seconds...")
    print(f"   Uploading documents to create backlog at {base_url}/api/documents/upload")

    end_time = time.time() + duration_seconds
    upload_count = 0

    while time.time() < end_time:
        try:
            # Create a small PDF file in memory
            files = {
                'file': ('test.pdf', b'%PDF-1.4\n1 0 obj\n<<\n/Type /Catalog\n>>\nendobj\nxref\n0 1\ntrailer\n<<\n/Size 1\n>>\nstartxref\n9\n%%EOF', 'application/pdf')
            }
            response = requests.post(
                f"{base_url}/api/documents/upload",
                files=files,
                timeout=10
            )
            if response.status_code == 202:
                upload_count += 1
                if upload_count % 5 == 0:
                    print(f"   Uploaded {upload_count} documents...")
        except Exception as e:
            print(f"   Upload failed: {e}")

        time.sleep(0.5)  # Upload every 0.5 seconds

    print(f"✅ Completed: {upload_count} documents uploaded (should create backlog)")


def generate_llm_cost_spike(base_url: str, duration_seconds: int) -> None:
    """Generate traffic that triggers many LLM calls (via Q&A or processing)."""
    print(f"🚀 Generating LLM cost spike scenario for {duration_seconds} seconds...")
    print(f"   This requires existing jobs. Checking for jobs...")

    # First, upload a document to get a job ID
    try:
        files = {
            'file': ('test.pdf', b'%PDF-1.4\n1 0 obj\n<<\n/Type /Catalog\n>>\nendobj\nxref\n0 1\ntrailer\n<<\n/Size 1\n>>\nstartxref\n9\n%%EOF', 'application/pdf')
        }
        upload_response = requests.post(
            f"{base_url}/api/documents/upload",
            files=files,
            timeout=10
        )

        if upload_response.status_code != 202:
            print(f"   ⚠️  Failed to upload document: {upload_response.status_code}")
            return

        job_data = upload_response.json()
        job_id = job_data.get("jobId")
        print(f"   Created job: {job_id}")

        # Wait a bit for processing to start
        time.sleep(2)

        # Send Q&A requests (these trigger LLM calls)
        end_time = time.time() + duration_seconds
        qa_count = 0

        while time.time() < end_time:
            try:
                qa_response = requests.post(
                    f"{base_url}/api/documents/{job_id}/qa",
                    json={"question": "What are the key terms in this document?"},
                    headers={"Content-Type": "application/json"},
                    timeout=10
                )
                qa_count += 1
                if qa_count % 5 == 0:
                    print(f"   Sent {qa_count} Q&A requests...")
            except Exception as e:
                print(f"   Q&A request failed: {e}")

            time.sleep(1)  # One Q&A per second

        print(f"✅ Completed: {qa_count} Q&A requests sent (should trigger LLM calls)")

    except Exception as e:
        print(f"   ❌ Error: {e}")
        print(f"   Note: LLM cost spike requires a working job. Try uploading a document first.")


def main():
    parser = argparse.ArgumentParser(description="Generate traffic to trigger Datadog monitors")
    parser.add_argument(
        "--scenario",
        choices=["latency", "backlog", "llm-cost"],
        required=True,
        help="Scenario to run"
    )
    parser.add_argument(
        "--duration",
        type=int,
        default=60,
        help="Duration in seconds (default: 60)"
    )
    parser.add_argument(
        "--base-url",
        type=str,
        default=DEFAULT_BASE_URL,
        help=f"Base URL of the API (default: {DEFAULT_BASE_URL})"
    )

    args = parser.parse_args()

    print(f"Traffic Generator - PolicyInsight")
    print(f"Scenario: {args.scenario}")
    print(f"Duration: {args.duration} seconds")
    print(f"Base URL: {args.base_url}")
    print()

    try:
        if args.scenario == "latency":
            generate_latency_spike(args.base_url, args.duration)
        elif args.scenario == "backlog":
            generate_backlog_spike(args.base_url, args.duration)
        elif args.scenario == "llm-cost":
            generate_llm_cost_spike(args.base_url, args.duration)

        print()
        print("✅ Traffic generation completed!")
        print("   Check Datadog monitors for alerts.")

    except KeyboardInterrupt:
        print()
        print("⚠️  Interrupted by user")
        sys.exit(0)
    except Exception as e:
        print(f"❌ Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()


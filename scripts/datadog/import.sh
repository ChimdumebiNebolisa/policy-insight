#!/bin/bash
# Bash script to import Datadog dashboards, monitors, and SLOs from JSON files
# Requires: DD_API_KEY and DD_APP_KEY environment variables
# Usage: ./scripts/datadog/import.sh

set -e

DD_API_KEY=${DD_API_KEY:-}
DD_APP_KEY=${DD_APP_KEY:-}
DD_SITE=${DD_SITE:-datadoghq.com}

if [ -z "$DD_API_KEY" ] || [ -z "$DD_APP_KEY" ]; then
    echo "Error: DD_API_KEY and DD_APP_KEY environment variables must be set"
    exit 1
fi

BASE_URL="https://api.${DD_SITE}"
AUTH_HEADER="DD-API-KEY: ${DD_API_KEY}"
APP_HEADER="DD-APPLICATION-KEY: ${DD_APP_KEY}"

echo "Importing Datadog configurations..."
echo "  API Base URL: $BASE_URL"

# Import dashboards
echo ""
echo "Importing dashboards..."
if [ -d "datadog/dashboards" ]; then
    for file in datadog/dashboards/*.json; do
        if [ -f "$file" ]; then
            curl -s -X POST "$BASE_URL/api/v1/dashboard" \
                -H "$AUTH_HEADER" \
                -H "$APP_HEADER" \
                -H "Content-Type: application/json" \
                -d @"$file" > /dev/null
            echo "  Imported: $(basename $file)"
        fi
    done
else
    echo "  No dashboards directory found"
fi

# Import monitors
echo ""
echo "Importing monitors..."
if [ -d "datadog/monitors" ]; then
    for file in datadog/monitors/*.json; do
        if [ -f "$file" ]; then
            curl -s -X POST "$BASE_URL/api/v1/monitor" \
                -H "$AUTH_HEADER" \
                -H "$APP_HEADER" \
                -H "Content-Type: application/json" \
                -d @"$file" > /dev/null
            echo "  Imported: $(basename $file)"
        fi
    done
else
    echo "  No monitors directory found"
fi

# Import SLOs
echo ""
echo "Importing SLOs..."
if [ -d "datadog/slos" ]; then
    for file in datadog/slos/*.json; do
        if [ -f "$file" ]; then
            curl -s -X POST "$BASE_URL/api/v1/slo" \
                -H "$AUTH_HEADER" \
                -H "$APP_HEADER" \
                -H "Content-Type: application/json" \
                -d @"$file" > /dev/null
            echo "  Imported: $(basename $file)"
        fi
    done
else
    echo "  No SLOs directory found"
fi

echo ""
echo "Import complete!"


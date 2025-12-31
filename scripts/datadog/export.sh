#!/bin/bash
# Bash script to export Datadog dashboards, monitors, and SLOs to JSON files
# Requires: DD_API_KEY and DD_APP_KEY environment variables
# Usage: ./scripts/datadog/export.sh

set -e

DD_API_KEY=${DD_API_KEY:-}
DD_APP_KEY=${DD_APP_KEY:-}
DD_SITE=${DD_SITE:-datadoghq.com}

if [ -z "$DD_API_KEY" ] || [ -z "$DD_APP_KEY" ]; then
    echo "Error: DD_API_KEY and DD_APP_KEY environment variables must be set"
    echo "  export DD_API_KEY=your-api-key"
    echo "  export DD_APP_KEY=your-app-key"
    exit 1
fi

BASE_URL="https://api.${DD_SITE}"
AUTH_HEADER="DD-API-KEY: ${DD_API_KEY}"
APP_HEADER="DD-APPLICATION-KEY: ${DD_APP_KEY}"

echo "Exporting Datadog configurations..."
echo "  API Base URL: $BASE_URL"

# Export dashboards
echo ""
echo "Exporting dashboards..."
mkdir -p datadog/dashboards

DASHBOARDS=$(curl -s -X GET "$BASE_URL/api/v1/dashboard" \
    -H "$AUTH_HEADER" \
    -H "$APP_HEADER" | jq -r '.dashboards[] | select(.title | contains("PolicyInsight")) | .id')

for DASHBOARD_ID in $DASHBOARDS; do
    DASHBOARD_JSON=$(curl -s -X GET "$BASE_URL/api/v1/dashboard/$DASHBOARD_ID" \
        -H "$AUTH_HEADER" \
        -H "$APP_HEADER")
    TITLE=$(echo "$DASHBOARD_JSON" | jq -r '.title' | tr ' ' '-' | tr -cd '[:alnum:]-')
    FILENAME="datadog/dashboards/${TITLE}.json"
    echo "$DASHBOARD_JSON" | jq '.' > "$FILENAME"
    echo "  Exported: $FILENAME"
done

# Export monitors
echo ""
echo "Exporting monitors..."
mkdir -p datadog/monitors

MONITORS=$(curl -s -X GET "$BASE_URL/api/v1/monitor" \
    -H "$AUTH_HEADER" \
    -H "$APP_HEADER" | jq -r '.[] | select(.name | contains("PolicyInsight")) | .id')

for MONITOR_ID in $MONITORS; do
    MONITOR_JSON=$(curl -s -X GET "$BASE_URL/api/v1/monitor/$MONITOR_ID" \
        -H "$AUTH_HEADER" \
        -H "$APP_HEADER")
    NAME=$(echo "$MONITOR_JSON" | jq -r '.name' | tr ' ' '-' | tr -cd '[:alnum:]-')
    FILENAME="datadog/monitors/${NAME}.json"
    echo "$MONITOR_JSON" | jq '.' > "$FILENAME"
    echo "  Exported: $FILENAME"
done

# Export SLOs
echo ""
echo "Exporting SLOs..."
mkdir -p datadog/slos

SLOS=$(curl -s -X GET "$BASE_URL/api/v1/slo" \
    -H "$AUTH_HEADER" \
    -H "$APP_HEADER" | jq -r '.data[] | select(.name | contains("PolicyInsight")) | .id')

for SLO_ID in $SLOS; do
    SLO_JSON=$(curl -s -X GET "$BASE_URL/api/v1/slo/$SLO_ID" \
        -H "$AUTH_HEADER" \
        -H "$APP_HEADER")
    NAME=$(echo "$SLO_JSON" | jq -r '.name' | tr ' ' '-' | tr -cd '[:alnum:]-')
    FILENAME="datadog/slos/${NAME}.json"
    echo "$SLO_JSON" | jq '.' > "$FILENAME"
    echo "  Exported: $FILENAME"
done

echo ""
echo "Export complete!"


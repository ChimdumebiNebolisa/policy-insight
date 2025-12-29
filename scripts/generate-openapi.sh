#!/bin/bash
# Generate OpenAPI spec from running application
# Usage: ./scripts/generate-openapi.sh [output-path]
# Default output: docs/openapi.json

set -e

OUTPUT_PATH="${1:-docs/openapi.json}"
APP_URL="${APP_URL:-http://localhost:8080}"
HEALTH_URL="${APP_URL}/actuator/health"
OPENAPI_URL="${APP_URL}/v3/api-docs"
MAX_WAIT=60

echo "Generating OpenAPI spec..."
echo "App URL: ${APP_URL}"
echo "Output: ${OUTPUT_PATH}"

# Check if app is running
if ! curl -sf "${HEALTH_URL}" > /dev/null 2>&1; then
    echo "ERROR: Application is not running at ${APP_URL}"
    echo "Please start the application first:"
    echo "  ./mvnw spring-boot:run"
    echo "  OR"
    echo "  java -jar target/policy-insight-*.jar"
    exit 1
fi

echo "Application is running, fetching OpenAPI spec..."

# Fetch OpenAPI spec
if curl -sf "${OPENAPI_URL}" -o "${OUTPUT_PATH}"; then
    echo "✅ OpenAPI spec generated successfully at ${OUTPUT_PATH}"

    # Pretty print JSON if jq is available
    if command -v jq &> /dev/null; then
        echo "Formatting JSON with jq..."
        jq . "${OUTPUT_PATH}" > "${OUTPUT_PATH}.tmp" && mv "${OUTPUT_PATH}.tmp" "${OUTPUT_PATH}"
    fi

    # Show first few lines
    echo ""
    echo "First 20 lines of generated spec:"
    head -20 "${OUTPUT_PATH}"
    exit 0
else
    echo "ERROR: Failed to fetch OpenAPI spec from ${OPENAPI_URL}"
    exit 1
fi


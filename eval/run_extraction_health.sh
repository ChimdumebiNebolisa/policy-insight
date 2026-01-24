#!/bin/bash
# Runner script for extraction health metrics

set -e

# Create output directory
mkdir -p eval/out

echo "Running extraction health metrics..."
echo "BASE_URL=${BASE_URL:-http://localhost:8080}"
echo ""

python3 eval/extraction_health.py

if [ $? -ne 0 ]; then
    echo "Error: extraction_health.py failed" >&2
    exit 1
fi

echo ""
echo "Extraction health metrics completed successfully."
echo "Outputs: eval/out/health_metrics.json, eval/out/health_metrics.md"


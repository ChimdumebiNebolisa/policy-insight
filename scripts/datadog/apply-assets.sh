#!/bin/bash
# Wrapper script for apply-assets.py (bash)
# Works without grep dependency

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PYTHON_SCRIPT="$SCRIPT_DIR/apply-assets.py"

# Check for required environment variables
if [ -z "$DD_API_KEY" ]; then
    echo "❌ Error: DD_API_KEY environment variable is not set"
    echo "   Set it with: export DD_API_KEY=your-api-key"
    exit 1
fi

if [ -z "$DD_APP_KEY" ]; then
    echo "❌ Error: DD_APP_KEY environment variable is not set"
    echo "   Set it with: export DD_APP_KEY=your-app-key"
    exit 1
fi

# DD_SITE is optional (defaults to datadoghq.com in the script)
if [ -n "$DD_SITE" ]; then
    echo "Using DD_SITE: $DD_SITE"
else
    echo "Using default DD_SITE: datadoghq.com"
fi

# Check if Python 3 is available
if ! command -v python3 &> /dev/null; then
    echo "❌ Error: python3 is not installed"
    exit 1
fi

# Check if requests library is available
if ! python3 -c "import requests" 2>/dev/null; then
    echo "⚠️  Warning: requests library not found. Installing..."
    pip3 install requests --user || pip install requests --user
fi

# Run the Python script
cd "$PROJECT_ROOT"
python3 "$PYTHON_SCRIPT" "$@"


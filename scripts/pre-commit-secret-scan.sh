#!/bin/bash
# Pre-commit hook to scan for secrets using gitleaks
# Install gitleaks: https://github.com/gitleaks/gitleaks#installation
# Usage: Copy this to .git/hooks/pre-commit or run manually before commits

set -e

echo "🔍 Running secret scan with gitleaks..."

# Check if gitleaks is installed
if ! command -v gitleaks &> /dev/null; then
    echo "⚠️  Warning: gitleaks is not installed. Skipping secret scan."
    echo "   Install from: https://github.com/gitleaks/gitleaks#installation"
    echo "   Or use: brew install gitleaks (macOS) / choco install gitleaks (Windows)"
    exit 0
fi

# Run gitleaks on staged files
if git rev-parse --verify HEAD >/dev/null 2>&1; then
    # Compare against HEAD
    gitleaks detect --source . --no-banner --verbose
else
    # First commit - scan all files
    gitleaks detect --source . --no-banner --verbose
fi

EXIT_CODE=$?

if [ $EXIT_CODE -eq 1 ]; then
    echo ""
    echo "❌ Secret scan failed! Potential secrets detected."
    echo "   Please remove any hardcoded secrets and use environment variables instead."
    echo "   See README.md#troubleshooting for guidance."
    exit 1
fi

echo "✅ Secret scan passed - no secrets detected"
exit 0


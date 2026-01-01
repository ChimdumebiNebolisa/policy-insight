#!/bin/bash
# Consistency check script to prevent Pub/Sub subscription name drift
# Exits non-zero if both subscription names are found in the repo
#
# Usage: bash scripts/consistency_check.sh
#
# This script greps for both subscription name variants and fails if both appear.
# The canonical subscription name is: policyinsight-analysis-sub

set -e

CANONICAL_NAME="policyinsight-analysis-sub"
OLD_NAME="policyinsight-analysis-topic-sub"

echo "=== Checking for Pub/Sub Subscription Name Consistency ==="
echo "Canonical name: $CANONICAL_NAME"
echo "Old name (should not appear): $OLD_NAME"
echo ""

# Search for both names in the repo (excluding this script and .git)
FOUND_CANONICAL=$(grep -r "$CANONICAL_NAME" --exclude-dir=.git --exclude="consistency_check.sh" . 2>/dev/null | wc -l || echo "0")
FOUND_OLD=$(grep -r "$OLD_NAME" --exclude-dir=.git --exclude="consistency_check.sh" . 2>/dev/null | wc -l || echo "0")

echo "Found canonical name ($CANONICAL_NAME): $FOUND_CANONICAL occurrences"
echo "Found old name ($OLD_NAME): $FOUND_OLD occurrences"
echo ""

if [ "$FOUND_OLD" -gt 0 ]; then
  echo "ERROR: Found $FOUND_OLD occurrence(s) of old subscription name: $OLD_NAME"
  echo "All references should use the canonical name: $CANONICAL_NAME"
  echo ""
  echo "Files containing the old name:"
  grep -rn "$OLD_NAME" --exclude-dir=.git --exclude="consistency_check.sh" . 2>/dev/null || true
  exit 1
fi

echo "✓ No old subscription name found. All references use canonical name: $CANONICAL_NAME"
echo "=== Consistency Check Passed ==="


#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
total=$(grep -c '^| [0-9]' ../docs/error-matrix.md || true)
done_count=$(grep -c '| done |' ../docs/error-matrix.md || true)
echo "rows=$total done=$done_count"
if [ "$total" -eq 0 ]; then
  echo "ERROR: error-matrix.md is empty" >&2
  exit 1
fi
echo "OK: $done_count/$total rows fully covered"

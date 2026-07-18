#!/usr/bin/env bash
# Documentation validator for roadtrip-android (docs/spec/08-testing.md).
#  1. Requirement IDs unique across docs/spec/*.md
#  2. Every `auto` requirement referenced by at least one test source file
#  3. Relative markdown links in docs resolve
set -euo pipefail
cd "$(dirname "$0")/.."

fail=0

# Collect requirement rows: | AND-001 | ... | auto |
mapfile -t rows < <(grep -hoE '^\|\s*[A-Z]+-[0-9]{3}\s*\|.*\|\s*(auto|manual|planned)\s*\|\s*$' docs/spec/*.md || true)
declare -A seen verify
for row in "${rows[@]}"; do
  id=$(echo "$row" | grep -oE '[A-Z]+-[0-9]{3}' | head -1)
  v=$(echo "$row" | grep -oE '(auto|manual|planned)\s*\|\s*$' | grep -oE 'auto|manual|planned')
  if [[ -n "${seen[$id]:-}" ]]; then
    echo "✗ Duplicate requirement ID: $id"; fail=1
  fi
  seen[$id]=1; verify[$id]=$v
done

if [[ ${#seen[@]} -eq 0 ]]; then
  echo "✗ No requirement tables found under docs/spec/"; fail=1
fi

# Test corpus: all test sources in both modules
corpus=$(find core/src/test app/src/test -name '*.kt' -exec cat {} + 2>/dev/null || true)
auto_count=0
for id in "${!seen[@]}"; do
  [[ "${verify[$id]}" == "auto" ]] || continue
  auto_count=$((auto_count + 1))
  if ! grep -qF "$id" <<<"$corpus"; then
    echo "✗ Requirement $id is marked auto but no test references it"; fail=1
  fi
done

# Relative link check
while IFS= read -r -d '' f; do
  while IFS= read -r target; do
    [[ "$target" =~ ^(https?:|mailto:|#) ]] && continue
    resolved="$(dirname "$f")/${target%%#*}"
    if [[ ! -e "$resolved" ]]; then
      echo "✗ Broken link in $f: $target"; fail=1
    fi
  done < <(grep -oE '\]\(([^)]+)\)' "$f" | sed -E 's/^\]\(//; s/\)$//' || true)
done < <(find docs README.md CLAUDE.md -name '*.md' -print0 2>/dev/null)

if [[ $fail -ne 0 ]]; then
  echo "Spec validation FAILED"
  exit 1
fi
echo "Spec validation OK: ${#seen[@]} requirements ($auto_count auto, all test-covered), links resolve."

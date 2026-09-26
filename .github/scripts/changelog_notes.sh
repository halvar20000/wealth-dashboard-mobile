#!/usr/bin/env bash
# Prints the notes CHANGELOG.md holds for one version, one "• " line
# each, and nothing when the file has no section for it.
#
#     changelog_notes.sh 1.2.7
set -euo pipefail
awk -v v="$1" '
  /^## / { on = ($2 == v); next }
  on && /[^[:space:]]/ { sub(/^[[:space:]]*-[[:space:]]*/, ""); print "• " $0 }
' "$(dirname "$0")/../../CHANGELOG.md"

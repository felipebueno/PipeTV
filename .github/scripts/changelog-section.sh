#!/usr/bin/env bash
# Prints a version's section of CHANGELOG.md, without its heading or the blank lines around it.
#
# Usage: changelog-section.sh <version>
set -euo pipefail

awk -v version="$1" '
    index($0, "## [" version "]") == 1 { found = 1; next }
    found && /^## \[/ { exit }
    found && !started && /^[[:space:]]*$/ { next }
    found { started = 1; print }
' CHANGELOG.md | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}'

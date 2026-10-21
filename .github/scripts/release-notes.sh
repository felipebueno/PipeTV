#!/usr/bin/env bash
# Writes the GitHub release notes of a PipeTV build to stdout: what's new in its version (its
# section of CHANGELOG.md), the commits since the previous build, how to install it and the
# APKs' checksums.
#
# Usage: release-notes.sh <version> <build> <previous build tag or ""> <dist dir>
set -euo pipefail

version=$1
build=$2
previous=$3
dist=$4
repo=${GITHUB_REPOSITORY:-felipebueno/PipeTV}

echo "PipeTV $version, build $build, from commit ${GITHUB_SHA:-$(git rev-parse HEAD)}."
echo

echo "## What's new in $version"
echo
section=$(awk -v version="$version" '
    index($0, "## [" version "]") == 1 { found = 1; next }
    found && /^## \[/ { exit }
    found { print }
' CHANGELOG.md)
if [ -n "$(echo "$section" | tr -d '[:space:]')" ]; then
    echo "$section"
else
    echo "CHANGELOG.md has no section for $version yet."
fi
echo

echo "## Changes in this build"
echo
if [ -n "$previous" ]; then
    changes=$(git log --no-merges --pretty='- %s' "$previous"..HEAD | grep -v "^- Squashed '" || true)
    echo "${changes:-No code changes since $previous.}"
else
    echo "The first build published by CI. CHANGELOG.md tells the story so far."
fi
echo

cat <<EOF
## Install

Pick the APK for your TV's processor:

- \`PipeTV-armeabi-v7a.apk\`: nearly every TV, stick and box, even those with 64-bit chips that run a 32-bit Android, such as the Mi TV Stick and the Chromecast with Google TV. Take this one when unsure.
- \`PipeTV-arm64-v8a.apk\`: TVs and boxes with a 64-bit Android, such as the NVIDIA Shield TV
- \`PipeTV-x86_64.apk\` and \`PipeTV-x86.apk\`: emulators and x86 boxes

The latest build is always at the same address, handy for downloader apps on the TV:
https://github.com/$repo/releases/latest/download/PipeTV-armeabi-v7a.apk

Every build is signed with the same key, so each one installs over the one before.

## Checksums (SHA-256)

\`\`\`
EOF
(cd "$dist" && sha256sum -- *.apk)
echo '```'

#!/usr/bin/env bash
#
# Checks the runner's Xcode against the pin in gradle/libs.versions.toml. Every macOS job that runs
# xcodebuild calls it, so the check has one definition (D-230, ADR-0231).
#
# docs/versions-matrix.md pins Xcode and says it is pinned on the CI runner too. A different major
# version is a real toolchain drift and fails; a different patch is reported, because runner images
# move faster than a pin can be reviewed.

set -euo pipefail

PINNED=$(grep -E '^xcode = ' gradle/libs.versions.toml | sed -E 's/.*"([^"]+)".*/\1/')
# awk reads the whole output: `head -1` closes the pipe early, and xcodebuild then aborts on the
# broken pipe (exit 134 on the runner), which pipefail turns into a failed check.
ACTUAL=$(xcodebuild -version | awk 'NR == 1 {print $2}')
echo "pinned=$PINNED actual=$ACTUAL"
if [ "${PINNED%%.*}" != "${ACTUAL%%.*}" ]; then
  echo "::error::Xcode major version drift: pinned $PINNED, runner has $ACTUAL. Update docs/versions-matrix.md and gradle/libs.versions.toml in a reviewed change."
  exit 1
fi
if [ "$PINNED" != "$ACTUAL" ]; then
  echo "::warning::Xcode patch drift: pinned $PINNED, runner has $ACTUAL."
fi

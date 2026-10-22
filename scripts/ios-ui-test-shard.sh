#!/usr/bin/env bash
#
# Runs one shard of the carAppUITests target on the first available iPhone simulator (D-230,
# ADR-0231). The shards are defined in scripts/ios-ui-test-shards.txt; `remainder` is the implicit
# shard that runs every test no manifest line names.
#
# Usage: scripts/ios-ui-test-shard.sh <shard> [--print-selection]
#
# --print-selection prints the xcodebuild test-selection arguments, one per line, and runs nothing.
# Written for the macOS system bash 3.2: no associative arrays, no mapfile.

set -euo pipefail

TARGET=carAppUITests
REMAINDER=remainder
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="$ROOT/scripts/ios-ui-test-shards.txt"

SHARD="${1:-}"
MODE="${2:-run}"
if [[ -z "$SHARD" ]]; then
  echo "usage: $0 <shard> [--print-selection]" >&2
  exit 2
fi
if [[ "$MODE" != "run" && "$MODE" != "--print-selection" ]]; then
  echo "::error::Unknown option $MODE; the only option is --print-selection." >&2
  exit 2
fi

NAMES=()
IDENTIFIERS=()
while read -r name identifier extra || [[ -n "${name:-}" ]]; do
  if [[ -z "${name:-}" || "$name" == \#* ]]; then
    continue
  fi
  if [[ -z "${identifier:-}" || -n "${extra:-}" ]]; then
    echo "::error::Malformed line in $MANIFEST: '$name ${identifier:-} ${extra:-}'." >&2
    exit 1
  fi
  NAMES+=("$name")
  IDENTIFIERS+=("$identifier")
done < "$MANIFEST"

SELECTION=()
if [[ "$SHARD" == "$REMAINDER" ]]; then
  SELECTION+=("-only-testing:$TARGET")
  for identifier in "${IDENTIFIERS[@]}"; do
    SELECTION+=("-skip-testing:$TARGET/$identifier")
  done
else
  for index in "${!NAMES[@]}"; do
    if [[ "${NAMES[$index]}" != "$SHARD" ]]; then
      continue
    fi
    own="${IDENTIFIERS[$index]}"
    SELECTION+=("-only-testing:$TARGET/$own")
    # A class entry yields the methods another shard names inside that class.
    if [[ "$own" != */* ]]; then
      for other in "${!NAMES[@]}"; do
        if [[ "${NAMES[$other]}" != "$SHARD" && "${IDENTIFIERS[$other]}" == "$own/"* ]]; then
          SELECTION+=("-skip-testing:$TARGET/${IDENTIFIERS[$other]}")
        fi
      done
    fi
  done
  if [[ ${#SELECTION[@]} -eq 0 ]]; then
    echo "::error::Shard '$SHARD' is not in $MANIFEST and is not '$REMAINDER'." >&2
    exit 1
  fi
fi

if [[ "$MODE" == "--print-selection" ]]; then
  printf '%s\n' "${SELECTION[@]}"
  exit 0
fi

DEVICE_ID="$(
  xcrun simctl list devices available iPhone \
    | grep -E -o '[0-9A-F]{8}-([0-9A-F]{4}-){3}[0-9A-F]{12}' \
    | head -n 1
)"
if [[ -z "$DEVICE_ID" ]]; then
  echo "::error::No available iPhone simulator." >&2
  exit 1
fi

echo "Shard $SHARD on simulator $DEVICE_ID:"
printf '  %s\n' "${SELECTION[@]}"

cd "$ROOT/iosApp"
xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator \
  -destination "id=$DEVICE_ID" ARCHS=arm64 ONLY_ACTIVE_ARCH=NO test \
  "${SELECTION[@]}"

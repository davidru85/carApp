#!/usr/bin/env bash

set -euo pipefail

EXPECTED_RUNTIME="$({
  awk -F '|' '$2 ~ /Cloud Functions runtime/ {gsub(/[[:space:]]/, "", $4); print $4}' \
    docs/versions-matrix.md
})"
PACKAGE_RUNTIME="$(node -p "require('./functions/package.json').engines.node")"
FIREBASE_RUNTIME="$(node -p "require('./firebase.json').functions.runtime.replace('nodejs', '')")"

if [[ -z "$EXPECTED_RUNTIME" ]]; then
  echo "::error::Cloud Functions runtime is absent from docs/versions-matrix.md."
  exit 1
fi

if [[ "$PACKAGE_RUNTIME" != "$EXPECTED_RUNTIME" ]]; then
  echo "::error::functions/package.json runtime $PACKAGE_RUNTIME differs from normative $EXPECTED_RUNTIME."
  exit 1
fi

if [[ "$FIREBASE_RUNTIME" != "$EXPECTED_RUNTIME" ]]; then
  echo "::error::firebase.json runtime $FIREBASE_RUNTIME differs from normative $EXPECTED_RUNTIME."
  exit 1
fi

DEPLOYED_RUNTIME="$(
  gcloud functions describe stopBilling \
    --gen2 \
    --project=davidruiz-carapp-dev \
    --region=europe-west1 \
    --format='value(buildConfig.runtime)'
)"
EXPECTED_DEPLOYED_RUNTIME="nodejs${EXPECTED_RUNTIME}"

if [[ "$DEPLOYED_RUNTIME" != "$EXPECTED_DEPLOYED_RUNTIME" ]]; then
  echo "::error::Deployed runtime $DEPLOYED_RUNTIME differs from normative $EXPECTED_DEPLOYED_RUNTIME."
  exit 1
fi

echo "Deployed Cloud Functions runtime matches docs/versions-matrix.md: $DEPLOYED_RUNTIME"

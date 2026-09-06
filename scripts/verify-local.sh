#!/usr/bin/env bash
set -euo pipefail

GRADLE="${GRADLE:-./gradlew}"
if [[ ! -x "$GRADLE" ]]; then
  echo "Gradle wrapper missing. Run once: gradle wrapper --gradle-version 9.6.0"
  exit 2
fi
if [[ ! -f app/google-services.json ]]; then
  echo "Missing app/google-services.json (expected locally; intentionally gitignored)."
  exit 3
fi
if [[ ! -f app/src/main/assets/models/glintr100.onnx ]]; then
  echo "Missing AuraFace model at app/src/main/assets/models/glintr100.onnx"
  exit 4
fi
EXPECTED=a7933ea5330113b01c9b60351d8f4c33003f145d8470ac5f0e52ee2effe25c60
ACTUAL=$(shasum -a 256 app/src/main/assets/models/glintr100.onnx | awk '{print $1}')
[[ "$ACTUAL" == "$EXPECTED" ]] || { echo "AuraFace SHA mismatch"; exit 5; }

$GRADLE clean
$GRADLE test
$GRADLE lint
$GRADLE assembleDebug
$GRADLE assembleRelease
$GRADLE signingReport

echo "Local build gates passed. Run connected tests with: $GRADLE connectedDebugAndroidTest"

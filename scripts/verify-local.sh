#!/usr/bin/env bash
set -euo pipefail

if [[ -x ./gradlew ]]; then
  GRADLE="${GRADLE:-./gradlew}"
else
  GRADLE="${GRADLE:-gradle}"
fi

command -v "${GRADLE%% *}" >/dev/null 2>&1 || {
  echo "Gradle 9.6.0 is required. Install it or generate a wrapper with: gradle wrapper --gradle-version 9.6.0"
  exit 2
}

[[ -f app/google-services.json ]] || {
  echo "Missing app/google-services.json (expected locally; intentionally gitignored)."
  exit 3
}

python3 - <<'PY'
import json
from pathlib import Path

path = Path("app/google-services.json")
data = json.loads(path.read_text())
packages = {
    client.get("client_info", {}).get("android_client_info", {}).get("package_name")
    for client in data.get("client", [])
}
if "com.snaploop.app" not in packages:
    raise SystemExit("google-services.json does not contain Android package com.snaploop.app")
print("Firebase package identity: OK (com.snaploop.app)")
PY

MODEL=app/src/main/assets/models/glintr100.onnx
[[ -f "$MODEL" ]] || {
  echo "Missing AuraFace model at $MODEL"
  exit 4
}

EXPECTED=a7933ea5330113b01c9b60351d8f4c33003f145d8470ac5f0e52ee2effe25c60
ACTUAL=$(shasum -a 256 "$MODEL" | awk '{print $1}')
[[ "$ACTUAL" == "$EXPECTED" ]] || {
  echo "AuraFace SHA mismatch"
  echo "Expected: $EXPECTED"
  echo "Actual:   $ACTUAL"
  exit 5
}
echo "AuraFace SHA-256: OK"

$GRADLE clean
$GRADLE :app:processDebugGoogleServices
$GRADLE test lint assembleDebug assembleRelease signingReport

echo
echo "Local build gates passed."
echo "Device ONNX smoke test:"
echo "  $GRADLE :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.snaploop.app.face.AuraFaceRuntimeInstrumentedTest"
echo "Install + launch:"
echo "  $GRADLE :app:installDebug"
echo "  adb shell am start -W -n com.snaploop.app/.MainActivity"

#!/bin/bash
# Builds all embedded extensions and executes actual Vision adapter tests.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# Xcode local-package paths must resolve from the iOS project, not the caller.
cd "$ROOT"
command -v xcodebuild >/dev/null || { echo 'A Mac with Xcode is required.' >&2; exit 1; }
command -v xcodegen >/dev/null || { echo 'Install XcodeGen, then rerun this script.' >&2; exit 1; }
xcodebuild -version
swift --version
xcodegen --version
python3 "$ROOT/scripts/test_project.py"
swift test --package-path "$ROOT"
xcodegen generate --spec "$ROOT/project.json" --project "$ROOT"
DESTINATION_ID="$(xcrun simctl list devices available -j | python3 -c '
import json, sys
for runtime, devices in sorted(json.load(sys.stdin)["devices"].items(), reverse=True):
    if "iOS" in runtime:
        for device in devices:
            if device.get("isAvailable") and "iPhone" in device["name"]:
                print(device["udid"]); sys.exit(0)
sys.exit("Install an iOS Simulator runtime in Xcode first.")
')"
xcodebuild -project "$ROOT/NoScroll.xcodeproj" -scheme NoScrollIOS \
  -configuration Debug -destination "platform=iOS Simulator,id=$DESTINATION_ID" \
  -derivedDataPath "$ROOT/DerivedData" CODE_SIGNING_ALLOWED=NO \
  -parallel-testing-enabled NO test

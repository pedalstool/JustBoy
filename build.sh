#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./gradlew testDebugUnitTest assembleRelease --no-daemon
printf '%s\n' 'APK: app/build/outputs/apk/release/app-release.apk'

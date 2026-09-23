#!/usr/bin/env bash
# Runs inside the emulator: installs the signed release APK, runs the Maestro smoke flow and
# fails on any crash of the app, including ones Maestro does not notice.
set -uo pipefail

APK=$(ls apk/*-signed.apk 2>/dev/null | head -1)
[ -n "$APK" ] || APK=$(ls apk/*.apk | head -1)
echo "Installing $APK"
adb install -r "$APK"
adb logcat -c

mkdir -p smoke-output
maestro test .maestro/smoke.yaml --format junit --output smoke-output/report.xml --test-output-dir smoke-output
status=$?

adb logcat -d > smoke-output/logcat.txt
if grep -A30 "FATAL EXCEPTION" smoke-output/logcat.txt | grep -q "com.metrolist.music"; then
  echo "::error::The app crashed during the smoke test, see logcat.txt"
  grep -A30 "FATAL EXCEPTION" smoke-output/logcat.txt | head -80
  status=1
fi
exit $status

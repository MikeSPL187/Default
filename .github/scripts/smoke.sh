#!/usr/bin/env bash
# Runs inside the emulator: installs the signed release APK, runs the Maestro smoke flow and
# fails on any crash of the app, including ones Maestro does not notice.
set -uo pipefail

APK=$(ls apk/*-signed.apk 2>/dev/null | head -1)
[ -n "$APK" ] || APK=$(ls apk/*.apk | head -1)
echo "Installing $APK"
adb install -r "$APK"
# Grant up front so the Android 13+ notification prompt cannot cover the app mid-flow.
adb shell pm grant com.metrolist.music android.permission.POST_NOTIFICATIONS || true
# A slow emulator can show "Pixel Launcher isn't responding" over the app. Crashes of the app are
# still caught from logcat below, so hiding system error dialogs loses nothing.
adb shell settings put global hide_error_dialogs 1 || true
adb logcat -c

mkdir -p smoke-output
maestro test .maestro/smoke.yaml --format junit --output smoke-output/report.xml --test-output-dir smoke-output
status=$?

adb logcat -d > smoke-output/logcat.txt
if [ $status -ne 0 ]; then
  # Artifacts need a login to download, so put what was on screen straight into the job log.
  echo "::group::Screen at failure (visible texts)"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 && adb pull /sdcard/window.xml smoke-output/window.xml >/dev/null 2>&1
  grep -o 'text="[^"]\+"\|content-desc="[^"]\+"' smoke-output/window.xml 2>/dev/null | head -60
  adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp" | head -5
  echo "::endgroup::"
  echo "::group::ANRs and app errors"
  grep -E "ANR in|am_anr|Application Not Responding" smoke-output/logcat.txt | head -20
  grep -E " E [A-Za-z]|AndroidRuntime" smoke-output/logcat.txt | grep -iE "metrolist|AndroidRuntime" | tail -40
  echo "::endgroup::"
fi
if grep -A30 "FATAL EXCEPTION" smoke-output/logcat.txt | grep -q "com.metrolist.music"; then
  echo "::error::The app crashed during the smoke test, see logcat.txt"
  grep -A30 "FATAL EXCEPTION" smoke-output/logcat.txt | head -80
  status=1
fi
exit $status

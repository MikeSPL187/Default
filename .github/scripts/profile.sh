#!/usr/bin/env bash
# Runs inside the emulator: records which code the app runs on a tour of its screens, which becomes
# the startup profile (baseline-prof.txt), and measures cold start and scrolling before and after it.
set -uo pipefail

PKG=com.metrolist.music
OUT=profile-output
mkdir -p "$OUT"

APK=$(ls app/build/outputs/apk/foss/profiling/*.apk | head -1)
echo "Installing $APK"
adb install -r "$APK"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell settings put global hide_error_dialogs 1 || true
adb shell input keyevent KEYCODE_HOME
sleep 20

# Cold starts: the process is killed and the launcher intent timed until the first frame.
cold_starts() {
  local label=$1 times=()
  for i in 1 2 3 4 5 6; do
    adb shell am force-stop $PKG
    sleep 2
    t=$(adb shell am start-activity -W -n $PKG/.MainActivity | sed -n 's/^TotalTime: *//p' | tr -d '\r')
    [ "$i" -gt 1 ] && times+=("$t")
    sleep 4
  done
  local sorted median
  sorted=$(printf '%s\n' "${times[@]}" | sort -n)
  median=$(echo "$sorted" | sed -n '3p')
  echo "$label: median ${median} ms (runs: $(echo $sorted | tr '\n' ' '))" | tee -a "$OUT/metrics.txt"
}

# Without any profile, as right after installing an APK from GitHub.
adb shell cmd package compile -f -m verify $PKG
cold_starts "Cold start, no profile"

# The tour itself: every main screen, scrolling, search, player, menus.
adb shell dumpsys gfxinfo $PKG reset > /dev/null
maestro test .maestro/tour.yaml -e P=profile- --test-output-dir "$OUT/tour" > "$OUT/tour.log" 2>&1 || true
{
  echo "Frames during the tour, no profile:"
  adb shell dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|99th percentile" | sed 's/^ */  /'
} | tee -a "$OUT/metrics.txt"

# Ask the app to write what it ran to disk, then read it as text.
adb shell am broadcast -a androidx.profileinstaller.action.SAVE_PROFILE $PKG/androidx.profileinstaller.ProfileInstallReceiver
sleep 10
adb shell cmd package dump-profiles --dump-classes-and-methods $PKG
sleep 5
adb pull /data/misc/profman/$PKG-primary.prof.txt "$OUT/raw-prof.txt"

if [ ! -s "$OUT/raw-prof.txt" ]; then
  echo "::error::No profile was recorded"
  exit 1
fi
# Keep what the app and its libraries run; Android's own classes are compiled by the system anyway.
grep -vE '^[HSP]*L(android|java|javax|dalvik|libcore|sun)/' "$OUT/raw-prof.txt" > "$OUT/baseline-prof.txt"
echo "Profile: $(wc -l < "$OUT/baseline-prof.txt") rules ($(grep -c '^[HSP]*Lcom/metrolist' "$OUT/baseline-prof.txt") of them for the app itself)" | tee -a "$OUT/metrics.txt"

# The same starts again with the recorded profile applied, which is what the profile will give users.
adb shell cmd package compile -f -m speed-profile $PKG
cold_starts "Cold start, with the profile"
adb shell dumpsys gfxinfo $PKG reset > /dev/null
maestro test .maestro/tour.yaml -e P=profiled- --test-output-dir "$OUT/tour2" > "$OUT/tour2.log" 2>&1 || true
{
  echo "Frames during the tour, with the profile:"
  adb shell dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|99th percentile" | sed 's/^ */  /'
} | tee -a "$OUT/metrics.txt"

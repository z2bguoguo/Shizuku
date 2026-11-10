#!/usr/bin/env bash
set -euo pipefail
APK=$(find release -name '*.apk' -print -quit)
PROBE_APK=connection-probe/build/outputs/apk/debug/connection-probe-debug.apk
PROBE_ID=app.shizuku.connectionprobe
mkdir -p device-test
cleanup() {
  adb logcat -d -v threadtime > device-test/logcat.txt || true
  adb shell ps -A > device-test/processes.txt || true
  tail -n 250 device-test/logcat.txt | grep -iE 'shizuku|probe|AndroidRuntime|exception' || true
}
trap cleanup EXIT
adb install "$APK"
adb install "$PROBE_APK"
adb shell pm grant "$PROBE_ID" moe.shizuku.manager.permission.API_V23
python3 - "$APK" "$RUNNER_TEMP/shizuku-probe-starter" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as apk:
    data = apk.read('lib/x86_64/libshizuku.so')
with open(sys.argv[2], 'wb') as f:
    f.write(data)
PY
adb push "$RUNNER_TEMP/shizuku-probe-starter" /data/local/tmp/shizuku-probe-starter
adb shell chmod 755 /data/local/tmp/shizuku-probe-starter
adb shell /data/local/tmp/shizuku-probe-starter | tee device-test/startup.txt
adb shell am start -W -n "$PROBE_ID/.ProbeActivity"
for attempt in $(seq 1 25); do
  result=$(adb shell run-as "$PROBE_ID" cat files/result.txt 2>/dev/null | tr -d '\r' || true)
  if [[ -n "$result" ]]; then
    printf '%s\n' "$result" | tee device-test/result.txt
    [[ "$result" == PASS* ]]
    exit $?
  fi
  sleep 1
done
echo 'FAIL no result from client' | tee device-test/result.txt
exit 1

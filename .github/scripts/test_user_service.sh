#!/usr/bin/env bash
set -euo pipefail
APK=$(find release -name '*.apk' -print -quit)
PROBE_APK=$(find probe-apks -name connection-probe-debug.apk -print -quit)
PEER_APK=$(find probe-apks -name connection-peer-debug.apk -print -quit)
PROBE_ID=app.shizuku.connectionprobe
mkdir -p device-test
cleanup() {
  adb logcat -d -v threadtime > device-test/logcat.txt || true
  adb shell ps -A > device-test/processes.txt || true
  if [[ -f release/package-name.txt ]]; then
    local manager_id
    manager_id=$(tr -d '\r\n' < release/package-name.txt)
    adb shell "cat /data/local/tmp/$manager_id-2000-user-service-logs/*.log" > device-test/final-service-startup.txt 2>/dev/null || true
    python3 - <<'PYLOG'
from pathlib import Path
import re
for line in Path('device-test/final-service-startup.txt').read_text(errors='replace').splitlines():
    if any(word in line for word in ('shared-automation', 'Concurrency', 'Exception', 'Caused by:')):
        print(re.sub(r'[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}(?:-[0-9]+)?', '<token>', line))
PYLOG
  fi
  tail -n 250 device-test/logcat.txt | grep -iE 'shizuku|probe|AndroidRuntime|exception' || true
}
trap cleanup EXIT
adb install "$APK"
adb install -t "$PROBE_APK"
adb install -t "$PEER_APK"
adb shell pm grant "$PROBE_ID" moe.shizuku.manager.permission.API_V23
adb shell pm grant app.shizuku.connectionpeer moe.shizuku.manager.permission.API_V23
python3 - "$APK" "$RUNNER_TEMP/shizuku-probe-starter" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as apk:
    data = apk.read('lib/x86_64/libshizuku.so')
with open(sys.argv[2], 'wb') as f:
    f.write(data)
PY
adb push "$RUNNER_TEMP/shizuku-probe-starter" /data/local/tmp/shizuku-probe-starter
adb shell chmod 755 /data/local/tmp/shizuku-probe-starter
LEGACY_NAME=app.p000000000000000000000000.m000000000000:service
probe_path=$(adb shell pm path "$PROBE_ID" | tr -d '\r' | sed -n 's/^package://p' | head -n 1)
test -n "$probe_path"
adb shell "CLASSPATH='$probe_path' /system/bin/app_process /system/bin --nice-name='$LEGACY_NAME' app.shizuku.connectionprobe.LegacyServerFixture > /data/local/tmp/shizuku-legacy-fixture.log 2>&1 < /dev/null &"
legacy_pid=""
for attempt in $(seq 1 10); do
  legacy_pid=$(adb shell cat /data/local/tmp/shizuku-legacy-fixture.log 2>/dev/null | tr -d '\r' | sed -n 's/^legacy-fixture-ready pid=//p' || true)
  [[ "$legacy_pid" =~ ^[0-9]+$ ]] && break
  sleep 1
done
[[ "$legacy_pid" =~ ^[0-9]+$ ]]
adb shell /data/local/tmp/shizuku-probe-starter | tee device-test/startup.txt
grep -F "info: killed $legacy_pid ($LEGACY_NAME)" device-test/startup.txt
echo 'PASS previous randomized main-server process was replaced' | tee device-test/legacy-replacement.txt
# Reproduce the reported failure deterministically: the provider cannot resolve,
# and the manager starts cold. Broadcast bootstrap must carry every handoff.
manager_id=$(tr -d '\r\n' < release/package-name.txt)
adb shell am force-stop "$manager_id"
adb shell pm disable --user 0 "$manager_id/moe.shizuku.manager.ShizukuManagerProvider"
adb shell am start -W -n "$PROBE_ID/.ProbeActivity"
for attempt in $(seq 1 75); do
  result=$(adb shell run-as "$PROBE_ID" cat files/result.txt 2>/dev/null | tr -d '\r' || true)
  if [[ -n "$result" ]]; then
    printf '%s\n' "$result" | tee device-test/result.txt
    [[ "$result" == PASS* ]] || exit 1
    manager_id=$(tr -d '\r\n' < release/package-name.txt)
    captured=false
    for capture_attempt in $(seq 1 15); do
      adb shell "cat /data/local/tmp/$manager_id-2000-user-service-logs/*.log" > device-test/service-startup.txt 2>/dev/null || true
      if grep -q 'ClassNotFoundException' device-test/service-startup.txt && grep -q 'process-exit=1' device-test/service-startup.txt && grep -q 'stage=user-service-call failed code=3' device-test/service-startup.txt && grep -q 'probe expected transaction failure' device-test/service-startup.txt; then
        echo 'PASS startup stderr, failed-process exit and service-call exceptions captured'
        captured=true
        break
      fi
      sleep 1
    done
    if [[ "$captured" != true ]]; then
      cat device-test/service-startup.txt
      echo 'FAIL bootstrap error was not captured'
      exit 1
    fi
    grep -q 'stage=manager-handoff broadcast relay ok' device-test/service-startup.txt
    echo 'PASS cold manager bootstrap and service handoff with provider disabled'
    bash .github/scripts/test_concurrent_clients.sh "$PEER_APK"
    exit 0
  fi
  sleep 1
done
echo 'FAIL no result from client' | tee device-test/result.txt
exit 1

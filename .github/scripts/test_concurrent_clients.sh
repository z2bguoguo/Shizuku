#!/usr/bin/env bash
set -euo pipefail
PEER_APK=$1
PRIMARY=app.shizuku.connectionprobe
PEER=app.shizuku.connectionpeer
for app in "$PEER" "$PRIMARY"; do
  adb shell am start -W -n "$app/app.shizuku.connectionprobe.ConcurrentProbeActivity"
  ready=""
  for attempt in $(seq 1 25); do
    ready=$(adb shell run-as "$app" cat files/concurrent-ready.txt 2>/dev/null | tr -d '\r' || true)
    [[ -n "$ready" ]] && break
    sleep 1
  done
  printf '%s: %s\n' "$app" "$ready" | tee -a device-test/concurrent-startup.txt
  [[ "$ready" == PASS* ]]
done
# Both UIDs now hold two live UiAutomation connections at the same time.
for app in "$PEER" "$PRIMARY"; do
  adb shell am start -W -n "$app/app.shizuku.connectionprobe.ConcurrentProbeActivity" --ez run true
done
for app in "$PEER" "$PRIMARY"; do
  result=""
  for attempt in $(seq 1 60); do
    result=$(adb shell run-as "$app" cat files/concurrent-result.txt 2>/dev/null | tr -d '\r' || true)
    [[ -n "$result" ]] && break
    sleep 1
  done
  printf '%s: %s\n' "$app" "$result" | tee -a device-test/concurrent-result.txt
  [[ "$result" == PASS* ]]
done
# Disconnect the second application while the primary retains one live client.
adb shell am force-stop "$PEER"
echo 'PASS two independent app UIDs, four persistent clients, concurrent reads and queued input mutations'

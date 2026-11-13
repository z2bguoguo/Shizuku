import os
p = os.environ['PACKAGE_ID']
print(f'''Personal fork build. Package: `{p}`

- Concurrency: share UiAutomation across compatible same-user clients, preserve independent callbacks, remap gesture IDs, and coordinate known input/window/activity/package mutations with bounded round-robin queues. User-service startup uses four workers. Reads remain parallel. See FORK.md for platform/entry-point coverage and limitations; arbitrary native/shell workflows are not automatically made atomic.
- Startup: replace main-server processes left by previous randomized-package builds and the original manager, using exact main-role name matching. This prevents multiple old servers from continuing SDK Binder delivery after switching builds.
- Diagnostics: include service liveness, connection callback counts, bounded lifecycle events and forwarded user-service call exceptions. No request/response payloads are logged. CI exercises 12 bind/reconnect rounds. This improves diagnosis; the intermittent physical-device failure is not yet confirmed fixed.
- Compatibility: preserve the official v13.6.0 user-service runtime state. OEM null-state recovery now uses temporary bind state and restores it before client service construction. Android 14 CI verifies active-window reads and screenshots; affected physical-device behavior still needs verification.
- SDK blacklist: Settings → SDK blacklist → select packages → Save. Start Shizuku first.
- Newly blocked apps (and shared-UID peers) are stopped in all Android users to remove cached Binder handles.
- A blocked UID receives no SDK Binder/attach callback. Cached API transactions are rejected immediately; calls are not deliberately hung.
- Hide launcher icon is available in Settings. This does **not** hide installed packages, declared permissions, APK contents, or processes from system queries.
- Random package names and the generated process name only reduce fixed-name matching. They do not guarantee undetectability.
- Package changes on every run, including reruns: each APK is a separate installation, not an in-place upgrade. The API permission names remain compatible with existing SDK clients; uninstall official/previous managers before installing if Android reports a duplicate permission owner.
- Signing: `{os.environ['SIGNING_MODE']}`. Without KEYSTORE secrets, this build uses a fresh temporary signing key. Do not rely on cross-build signature identity.

Open after hiding the icon:
```sh
adb shell am start -n {p}/moe.shizuku.manager.MainActivity
```
Restore the launcher alias:
```sh
adb shell pm enable {p}/moe.shizuku.manager.Launcher
```
The blacklist is stored by the server in its existing shell configuration, independently of the random manager package. Android/SELinux restrictions still apply. Device verification steps are in `FORK.md`.
''')

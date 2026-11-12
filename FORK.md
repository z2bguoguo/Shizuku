# Personal fork

All release workflow writes target `z2bguoguo/Shizuku`. There is no PR creation, upstream synchronization, or upstream publication. The API git submodule is an unchanged, pinned build dependency; it is read during checkout.

## Build and releases

Push to `master` (code changes) or run **Build and release fork** manually. Every run creates a cryptographically random application ID, shared by the manager APK, Java service, native starter, user-service starter, and rish loader. Original SDK Binder keys and API permissions remain unchanged for client compatibility. The server process name follows the random application ID. This is name variation, not system-level process hiding.

Release contains a signed release APK, actual package ID, APK metadata, signing certificate and SHA-256. Optional repository secrets: `KEYSTORE` (base64 JKS), `KEYSTORE_PASSWORD`, `KEYSTORE_ALIAS`, `KEYSTORE_ALIAS_PASSWORD`. Without them the workflow generates a temporary build key; each random package is an independent app. API permission ownership can prevent coexistence with the official or another fork manager; uninstall the old manager if installation reports duplicate permissions. Random package names do not provide ordinary APK updates or automatic manager data migration.

The build pins released NDK r29 (`29.0.14206865`) instead of the original preview revision; see [Android NDK release history](https://github.com/android/ndk/wiki/unsupported-downloads).

Local build: initialize the existing API submodule, install JDK 21 and the Android SDK/NDK versions from the workflow, then run `./gradlew :manager:assembleRelease -PmanagerApplicationId=app.example.manager`. Omitting this property keeps the original ID for development.

## SDK blacklist

Start Shizuku, open **Settings → SDK blacklist**, search or select apps, then **Save blacklist**. Apps from all users are listed. Rules use package names and apply to every Android user; shared-UID peers are blocked together. Removed apps with saved rules remain listed so the rules can be removed.

The server filters startup delivery, UID/process observer delivery, direct attach, and Binder transactions. Only the manager may read or write rules. Newly blocked apps and their shared-UID peers are force-stopped and their user services removed so cached handles are cleared. Unblocking schedules Binder redistribution. Rules persist in Shizuku's existing shell-side JSON file; changing the random manager ID does not change that path.

“No response” means no SDK Binder delivery/attach callback. Rejected direct transactions return immediately, rather than tying up Binder threads. A client that already possessed a handle may still infer that it existed; the kernel's Binder liveness operations cannot be made into an absent service by an ordinary Java override.

## Hiding limits and recovery

**Hide launcher icon** disables a launcher alias, keeping the real activity available. Reopen via App info → Additional settings in the app where supported, or:

```sh
adb shell am start -n ACTUAL_PACKAGE/moe.shizuku.manager.MainActivity
adb shell pm enable ACTUAL_PACKAGE/moe.shizuku.manager.Launcher
```

`ACTUAL_PACKAGE` is in the Release's `package-name.txt`. App lists, permission ownership, `/proc`, privileged scanners, and independent system queries are outside this app's control. This fork cannot guarantee invisibility. True per-app filtering of Android's package/process services needs a separate system-level mechanism and is not implemented here.

## Device validation

1. Install the APK, start with wireless ADB/root, check the manager status and test an ordinary SDK client plus rish.
2. Select that client in the blacklist and save; verify it stops. Reopen it and verify no SDK Binder callback/availability, permission dialog, or privileged action. Confirm a second unblocked client still works.
3. Restart the server and reboot, then confirm rules persist. Remove the client from the blacklist and confirm reconnection. Test shared-UID packages and secondary/work profiles if used.
4. Hide the launcher icon, recover with the exact Release package ID, then switch hiding off. Confirm the app remains visible in system Settings as documented.
5. Install a second build after removing conflicting permission owners and confirm its actual random package matches `package-name.txt` and startup/rish/user services work.

User services first use the official v13.6.0 application-creation path. If an OEM framework throws the known null-state failure, a temporary `ActivityThread.mBoundApplication` record supplies the process/package state only during application creation. The exact previous record is restored before the client service constructor runs, including when creation fails. This avoids changing framework package/process attribution for subsequent client API calls.

CI validates source boundaries, compiles the release, verifies its APK signature and checks its real manifest application ID. An Android 14 emulator additionally checks shell UID 2000, restored unbound framework state, a real active-window read, a non-empty UiAutomation screenshot, and startup exception capture. Passing these checks does not establish compatibility with every OEM ROM or reproduce a particular client's failure.

## Intermittent user-service diagnostics

After reproducing a failure, immediately copy **Settings → Connection diagnostics**. The manager-only report includes the active server-side records (Binder liveness, callback count, whether a Binder is pending), the last 64 service lifecycle events, and private user-service startup/call logs. The user-service Binder forwards transactions locally without changing caller identity, flags, interface descriptor, request or response data. It records the first call of up to 32 transaction codes and up to 64 thrown exceptions per process; request/response payloads are not logged. Exceptions already stripped by a client cannot be reconstructed by this report.

CI now runs 12 rounds of service reuse, detach/rebind, and destroy/restart. Each round checks shell UID, incoming caller UID, active-window reads and screenshots. It also checks that a deliberate service exception reaches the client and is captured in the private service log. This is a diagnostic improvement; passing the emulator test does not establish that an intermittent physical-device problem is fixed.

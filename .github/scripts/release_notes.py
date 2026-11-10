import os
p = os.environ['PACKAGE_ID']
print(f'''Personal fork build. Package: `{p}`

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

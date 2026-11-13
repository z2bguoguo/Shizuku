# Shizuku — z2bguoguo Fork

[![Build and release fork](https://github.com/z2bguoguo/Shizuku/actions/workflows/app.yml/badge.svg)](https://github.com/z2bguoguo/Shizuku/actions/workflows/app.yml)
[![Latest release](https://img.shields.io/github/v/release/z2bguoguo/Shizuku?include_prereleases&label=pre-release)](https://github.com/z2bguoguo/Shizuku/releases)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
[![Upstream](https://img.shields.io/badge/Upstream-RikkaApps%2FShizuku-6f42c1)](https://github.com/RikkaApps/Shizuku)

> [!IMPORTANT]
> **这是基于 [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) 的第三方实验性 Fork，并非 Shizuku 官方发行版。** 本仓库以原版 **v13.6.0** 为基础，重点研究 SDK 访问控制、管理器构建身份、设备兼容性及连接诊断。部分行为依赖 Android 版本和厂商系统实现，不能保证适用于所有设备。
>
> **This is an independent, experimental fork of Shizuku, not an official Shizuku release.** See the [English documentation](#english-documentation) below.

**[下载预发布版本](https://github.com/z2bguoguo/Shizuku/releases)** · **[Actions 构建](https://github.com/z2bguoguo/Shizuku/actions/workflows/app.yml)** · **[Fork 技术说明](FORK.md)** · **[上游官方文档](https://shizuku.rikka.app/)** · **[查看修改提交](https://github.com/z2bguoguo/Shizuku/compare/b844bc491f1790c72328e1a8e5b2349f8978f0ea...master)**

## 项目简介

Shizuku 允许普通 Android 应用通过由 **ADB（shell）或 root** 启动的独立服务进程，使用权限更高的 Android 系统 API。与为每次操作启动一条 `su`/shell 命令不同，Shizuku 通过 Binder IPC 提供可复用的服务连接。**ADB 模式不等于 root**：最终可执行的操作仍受启动服务的 UID、Android 版本及系统权限限制。

本 Fork **保留 Shizuku 原有的 SDK 协议与主要工作方式**，并增加对 SDK 客户端的访问限制、管理器入口控制、连接故障分析及发布构建的身份隔离机制。它不是 Magisk/Xposed/LSPosed 模块，也不会对整个 Android 系统进行全局隐藏或过滤。

## 与官方版的主要区别

| 功能 | 本 Fork 的实现 | 边界与注意事项 |
| --- | --- | --- |
| **SDK 黑名单** | 在管理器中选择包名，阻止选中应用获取或重新连接 Shizuku SDK Binder；对直接连接、启动分发及服务调用进行拦截 | 不是系统级安装包/进程隐藏；同 UID 的应用会一起受到限制 |
| **随机构建身份** | 每次 GitHub Actions 发布构建生成独立的随机管理器 application ID，并使管理器、服务、启动器和 rish 相关标识保持一致 | 这是构建身份变化，**不是进程隐身或反检测保证**；各次发布通常不能相互覆盖安装 |
| **隐藏桌面图标** | 可在设置中关闭 Launcher alias，同时保留真实 Activity 以便恢复 | 不隐藏设置中的应用信息、权限、进程或其他系统查询结果 |
| **OEM UserService 兼容处理** | 优先使用上游应用创建流程；仅在特定空状态异常时临时构造并恢复 Android 框架状态 | 针对已知故障的兼容回退，未宣称覆盖全部 ROM |
| **连接诊断** | 在管理器中查看连接记录、近期 UserService 生命周期、启动异常与部分服务日志，并复制报告 | 报告仅在用户主动查看/复制时提供；分享前仍应检查敏感信息 |
| **旧服务替换** | Native starter 可识别并替换当前主服务、原始 `shizuku_server` 和符合此 Fork 随机命名格式的旧主服务 | 不匹配任意第三方自定义进程名；不会把客户端 UserService 当作主服务清理 |
| **自动构建和测试** | `master` 推送或手动触发后构建并验证 APK；Android 14 模拟器运行 UserService/IPC 相关测试；通过后发布 Pre-release | CI 检测通过不等于所有实体设备或 OEM 系统都兼容 |

以上功能对应本仓库现有实现；代码细节与限制可参见 [FORK.md](FORK.md)。

## 下载与安装

1. 打开 [Releases](https://github.com/z2bguoguo/Shizuku/releases)，选择需要的 **Pre-release**，下载其中的 `*-release.apk`。
2. 同时查看该 Release 的 `package-name.txt`（实际 Android 包名）、`SHA256SUMS`（文件哈希）和 `signing-certificate.txt`（签名信息）。
3. 安装 APK，并根据 [上游官方启动指南](https://shizuku.rikka.app/guide/setup/) 使用 root、无线调试（设备支持时）或电脑 ADB 启动 Shizuku 服务。
4. 打开管理器确认运行状态，使用兼容 Shizuku API 的客户端测试连接。

**校验下载文件：**

```bash
# Linux / macOS：在 Release 文件所在目录执行
sha256sum -c SHA256SUMS

# Windows PowerShell：查看单个 APK 的 SHA-256，再与 SHA256SUMS 比较
Get-FileHash .\shizuku-xxxx-release.apk -Algorithm SHA256
```

> [!WARNING]
> - 自动发布版本的包名形如 `app.p<24位十六进制>.m<12位十六进制>`，**请以每个 Release 的 `package-name.txt` 为准**；不要根据 APK 文件名猜测包名。
> - 每次 CI 构建会生成新的包名，默认不是普通意义上的“升级同一个 App”；旧版数据也不会自动迁移。安装新版本前请保存必要的配置。
> - 如果仓库没有配置自定义签名密钥，CI 会使用**仅供该次构建使用的临时签名密钥**。检查 Release 中的签名指纹，不要假设不同构建使用同一签名。
> - 为兼容既有 SDK 客户端，部分 Shizuku API 权限名保持不变；与官方版或其他 Fork 共存时可能遇到重复权限定义冲突。发生冲突时，应先确认所安装包及其数据，再决定是否卸载旧管理器。
> - 非 root 的 ADB 启动通常会在设备重启后失效，需按官方方式重新启动。

## 功能使用

### 1. SDK 黑名单

路径：**Shizuku 管理器 → 设置 → SDK 黑名单**。

1. 先启动 Shizuku 服务。
2. 搜索应用名称或 Android 包名，勾选需要限制的应用。
3. 点击 **保存黑名单**；勾选但尚未保存的内容不会成为服务端规则。
4. 重新打开目标应用，验证其是否不再收到 Shizuku Binder/连接回调；同时确认未被限制的客户端仍可连接。
5. 如需恢复，取消勾选并再次保存，必要时重启目标应用或 Shizuku 服务。

**具体行为**：

- 按 **包名** 保存规则，并覆盖 Android 的多个用户/工作资料中的同名包；管理器会尝试列出所有用户的已安装应用。
- 同一 UID 下的多个包共享进程身份，因此可能一起被阻止。
- 新增黑名单时会停止受影响应用及相关 UserService，尽量清理已有连接；移除限制后调度重新分发 Binder。
- 已卸载、不可见但此前保存过规则的包仍可在列表中出现，以便删除遗留规则。
- 规则写入 Shizuku shell 侧的配置文件（当前实现为 `/data/user_de/0/com.android.shell/shizuku.json`），不随每次随机管理器包名改变。
- SDK 黑名单不同于授权列表：它管理的是**能否通过此 Shizuku 服务获取/连接 Binder**，并不改变系统授予应用的其他 Android 权限。

**限制**：已经取得 Binder 的进程可能仍能判断服务曾经存在；Binder 内核级存活行为不会因为 Java 层过滤而变成“服务从不存在”。**本功能不会隐藏应用列表、进程列表、系统权限或 ADB/root 痕迹，也不是全局反检测方案。**

### 2. 隐藏与恢复桌面图标

路径：**设置 → 隐私 → 隐藏桌面图标**。

该开关只会禁用启动器入口（Launcher alias），不卸载管理器，也不停止已经运行的 Shizuku 服务。部分系统可通过 **应用信息 → 应用内的其他设置** 打开管理器；也可通过 ADB 恢复：

```bash
# 将 ACTUAL_PACKAGE 替换为当前 Release 的 package-name.txt 内容
adb shell am start -n ACTUAL_PACKAGE/moe.shizuku.manager.MainActivity
adb shell pm enable ACTUAL_PACKAGE/moe.shizuku.manager.Launcher
```

若更换了随机包名，务必替换为新安装包的**真实包名**。系统设置、设备管理员或其他具备相应权限的应用仍可能发现该软件。

### 3. 连接诊断与故障排查

路径：**设置 → 隐私 → 连接诊断**。

建议在客户端连接失败、UserService 不启动或调用异常发生后**立即**打开并复制报告。实现中包含：

- 当前服务与 UserService 的状态，例如 Binder 是否仍存活、是否等待绑定、回调记录等；
- 有界的生命周期事件记录，以及 UserService 启动阶段的输出与异常信息；
- 部分相关 `logcat` 信息（有限长度），用于分析启动、Binder 与 UiAutomation 故障；
- 服务调用中的异常统计与有限的事务编号记录；不以记录请求/响应 Parcel 内容为目的。

诊断在本地收集，**不会因打开该功能而自动上传到本仓库**。尽管代码对部分令牌进行了脱敏，报告仍可能包含设备环境、已安装包名或应用日志。提交公开报告前请自行检查并删去个人信息、令牌和其他机密内容。

若提示 `Server does not support diagnostics`，请先确保已通过当前构建重新启动 Shizuku **服务进程**，而不是只更新管理器 APK。

### 4. OEM UserService 兼容与服务恢复

此 Fork 针对部分 OEM Android Framework 在 `LoadedApk.makeApplication(...)` 中出现的空状态异常添加了受限的回退流程：正常情况下仍走上游实现；确认属于预期的空状态失败时，临时初始化 `ActivityThread.mBoundApplication` 所需字段，并在退出回退流程时恢复原始对象。这样可减少将临时进程信息遗留给后续客户端调用的风险。

此外，当 CI 使用新的随机包名时，启动器会尝试替换此前符合此 Fork 命名模式的**主 Shizuku 服务**，避免已卸载的旧管理器留下仍在分发 Binder 的主进程。它不会自动识别任意第三方命名形式。

## 构建与开发

### 环境要求

- Git（包含子模块支持）；
- **JDK 21**；
- Android SDK **Platform 36**、Build Tools **36.0.0**；
- Android NDK **29.0.14206865**；
- CMake **3.31.6**（CI 安装版本；项目原生构建使用 3.31 系列）；
- 能访问 Gradle、Google Maven、Maven Central 与项目依赖的网络环境。

项目 `minSdk=24`、`targetSdk=36`。这只是代码中的构建配置，**不代表 Android 7–16 的所有设备/功能都经过测试**。CI 设备测试目前主要覆盖 Android 14（API 34）模拟器。

### 拉取和本地编译

```bash
git clone --recurse-submodules https://github.com/z2bguoguo/Shizuku.git
cd Shizuku

# 已经克隆但忘记子模块时：
git submodule update --init --recursive

# Debug（默认使用上游管理器 application ID，开发时注意与已安装版本的冲突）
./gradlew :manager:assembleDebug

# 显式使用自定义包名构建 Release
./gradlew :manager:assembleRelease -PmanagerApplicationId=app.example.manager

# 单元测试
./gradlew :manager:testReleaseUnitTest
```

Windows 可将 `./gradlew` 替换为 `gradlew.bat`。本地构建不会自动使用 CI 的随机包名生成逻辑；省略 `-PmanagerApplicationId` 会回退到上游默认 ID `moe.shizuku.privileged.api`。本地 release 签名还取决于项目中的签名配置，不能假定在任意开发机器上开箱即用。

### GitHub Actions / 发布机制

工作流：[`.github/workflows/app.yml`](.github/workflows/app.yml)，名称为 **Build and release fork**。

流程为：

1. 向 `master` 推送提交，或在 Actions 中手动运行工作流。
2. 为本次构建生成随机 application ID，并统一传给管理器、Java 服务、native starter 与相关加载组件。
3. 初始化 Shizuku API 子模块，设置 JDK/Android 工具链并执行源码约束检查和构建/单元测试。
4. 使用 `apksigner` 校验 APK 签名、核对实际 Manifest 包名、生成 SHA-256 与发布说明。
5. 在 Android 14 模拟器上进行包括服务启动、shell UID、UiAutomation 窗口/截图访问、UserService 多轮连接与异常采集等测试。
6. 所需任务成功后，仅在 **`z2bguoguo/Shizuku`** 创建 GitHub **Pre-release**，不向上游仓库发布。

**发布附件**一般包括 APK、`package-name.txt`、`SHA256SUMS`、`signing-certificate.txt`、`apk-badging.txt`、`AndroidManifest.xml` 和 `release-notes.md`。CI 构建/测试日志及测试客户端在 Actions Artifacts 中；不是产品 APK 的组成部分。

自定义签名需要在仓库 Actions Secrets 中配置：`KEYSTORE`（Base64 编码的 JKS）、`KEYSTORE_PASSWORD`、`KEYSTORE_ALIAS`、`KEYSTORE_ALIAS_PASSWORD`。不要将密钥、口令、`signing.properties` 或真实令牌提交进代码仓库。

### 目录结构

```text
.github/workflows/app.yml    # Fork 构建、设备测试与预发布
.github/scripts/             # Release notes 与 Android 模拟器测试
manager/                     # Android 管理器、设置、SDK 黑名单、诊断 UI
server/                      # Shizuku 服务、客户端分发、黑名单配置与诊断
starter/                     # app_process 启动、UserService 创建兼容处理
shell/                       # rish 相关集成
common/                      # 公共工具
connection-probe/            # CI/开发用测试客户端（不打包进正式管理器）
api/                         # 上游 RikkaApps/Shizuku-API Git 子模块
FORK.md                      # Fork 技术边界和设计记录
tests/                        # 源码与原生进程名校验
```

Shizuku 应用开发者仍应查阅上游 [Shizuku-API](https://github.com/RikkaApps/Shizuku-API)，包括 SDK 集成方式、兼容性与迁移指南；**本 Fork 没有宣称改变公开 SDK 的用法**。对于 Android 9+ 隐藏 API 限制、不同 Android 版本的 Binder 事务差异以及 ADB 权限限制，也请参考上游文档。

## 常见问题

<details>
<summary><b>为什么安装不同 Release 不能直接覆盖升级？</b></summary>

工作流每次生成新的 application ID；如果未配置固定签名，还可能每次使用不同临时证书。它们并非同一个 Android 安装身份。需要升级迁移能力时，应自行制定稳定包名、稳定签名与数据迁移策略，不能依赖此实验性发布流程。
</details>

<details>
<summary><b>与官方 Shizuku 共存时报权限冲突怎么办？</b></summary>

部分 SDK 权限名为了客户端兼容而保留。Android 不允许不同签名的软件任意占有相同的权限声明；具体表现取决于系统和安装顺序。核对当前服务与管理器来源、备份需要的数据后再卸载冲突应用，避免盲目覆盖。
</details>

<details>
<summary><b>黑名单能让某个应用完全发现不了 Shizuku 吗？</b></summary>

不能。它限制本服务的 SDK Binder 分发和相关调用，不会篡改 Android 的全局包管理、进程信息、权限状态或 `/proc`，也不能抹除客户端此前已经获得的全部信息。
</details>

<details>
<summary><b>管理器已更新，连接诊断或黑名单却不可用？</b></summary>

可能仍在运行较早的 Shizuku server 进程。请按正确方式重新启动当前版本的服务，确认管理器连接的是对应版本；如仍失败，再收集诊断日志。
</details>

<details>
<summary><b>为什么 CI 通过但实际手机上的 UserService 仍偶发连接失败？</b></summary>

Android 模拟器不等于特定 OEM ROM。当前测试能发现部分启动、权限、事务或生命周期错误，但厂商框架、进程回收、权限策略等差异仍需实体设备复现与定位。
</details>

## 安全、兼容性与项目状态

- 此项目是**独立维护的实验性 Fork**；请勿将本仓库 APK 误认为官方版，也不要把随机包名理解为安全隔离、匿名性或无法检测。
- Shizuku 能提供高权限系统 API 访问；仅向可信客户端授权，并谨慎考虑自定义 Fork 的安全风险。
- SDK 黑名单可能影响正常依赖 Shizuku 的应用；配置错误时可从管理器恢复，必要时重启服务。
- 功能边界和测试记录以当前源码、[`FORK.md`](FORK.md) 及 GitHub Actions 实际结果为准。对不同 Android/OEM 平台的行为不作普遍保证。
- 本仓库在上游基础上维护，改进代码与设计并不代表得到上游团队的认可或支持。

## 致谢与许可证

本项目 Fork 自 **[RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)**，核心架构、SDK 以及大量代码归功于原项目及其贡献者。感谢 [Shizuku 官方网站与文档](https://shizuku.rikka.app/) 和 [Shizuku-API](https://github.com/RikkaApps/Shizuku-API)。

代码仓库标示为 **[Apache License 2.0](LICENSE)**。请保留原始版权、许可和必要的 NOTICE；并阅读[上游 README 的特别说明](https://github.com/RikkaApps/Shizuku#license)：其对启动图标素材、应用名称、原版 application ID 和若干权限声明有额外提醒。**不要把 Apache-2.0 的代码许可理解为自动获准使用上游品牌或图标制作独立官方风格的发行版。**

---

## English documentation

### Overview

This repository is an **independent, experimental fork of [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)**, based on the upstream **v13.6.0** codebase. Shizuku allows ordinary Android apps to communicate with system APIs via a Binder-based service started with ADB (`shell`) or root privileges. ADB mode does **not** grant root privileges.

This fork focuses on **per-app access restrictions to the Shizuku SDK, randomized release identities, OEM UserService startup recovery, connection diagnostics, and automated validation**. It does not replace Android package/process management, provide system-wide stealth, or alter the public Shizuku API contract.

### What is different from upstream?

| Feature | Behavior | Important limitation |
| --- | --- | --- |
| SDK blacklist | Block configured package names from Shizuku SDK Binder delivery, attachment and related transactions; stop newly blocked clients and their user services | Shared-UID packages are affected together. Does not conceal installed packages or processes |
| Randomized release identity | Each CI build generates a new application ID, used consistently across the manager and related service/starter components | A new build is generally a separate app rather than an in-place update. No automatic data migration |
| Hide launcher icon | Disable the Launcher activity alias through a settings switch | Does not hide the app from Android Settings or privileged scanners |
| OEM UserService recovery | Preserve upstream initialization where possible; on a known null-state failure temporarily supply and then restore framework binding state | ROM-specific recovery, not universal compatibility |
| Connection diagnostics | Manager-only collection of recent lifecycle events, Binder state, startup logs and bounded transaction/exception information | Logs may contain sensitive data; review before sharing |
| Legacy server replacement | Replace the current main server, original `shizuku_server`, and old main servers matching this fork's generated naming convention | Does not identify arbitrary custom names or target client user-service processes |
| CI and prereleases | Push to `master` or manual workflow dispatch builds, validates, device-tests and publishes prereleases in this fork | Emulator tests are not proof of compatibility with all physical devices |

See [FORK.md](FORK.md) and the [fork-only commit comparison](https://github.com/z2bguoguo/Shizuku/compare/b844bc491f1790c72328e1a8e5b2349f8978f0ea...master) for implementation details.

### Install and use

1. Download the signed APK from [Releases](https://github.com/z2bguoguo/Shizuku/releases). These are **prereleases**, not official Shizuku distributions.
2. Inspect `package-name.txt`, `SHA256SUMS` and `signing-certificate.txt` in the same release. The **actual package ID**, not the APK filename, is required for launcher recovery.
3. Start the service using a supported root or ADB method as described by the [official guide](https://shizuku.rikka.app/guide/setup/).
4. Open **Settings → SDK blacklist**, choose packages, and select **Save blacklist** to apply restrictions.
5. Use **Settings → Connection diagnostics** immediately after reproducing an IPC/UserService problem. The report is locally collected; it is not automatically uploaded.
6. Use **Settings → Hide launcher icon** to disable only the launcher entry.

To reopen a hidden manager, substitute its actual release package ID:

```bash
adb shell am start -n ACTUAL_PACKAGE/moe.shizuku.manager.MainActivity
adb shell pm enable ACTUAL_PACKAGE/moe.shizuku.manager.Launcher
```

**Blacklist scope:** Rules are persisted on the shell side, applied across users by package name, and shared-UID peers are handled together. Blocking Shizuku Binder delivery does **not** make a client incapable of detecting previously held handles, Android package records, permissions, process information, or other system-visible traces.

**Release identity and signing:** CI uses a randomly generated package ID for each run. With no repository-provided keystore, a temporary per-build signing key is generated. Consequently, do not expect ordinary APK upgrades, automatic migration, or trouble-free coexistence with official Shizuku installations that declare some of the same SDK permissions.

### Building from source

Prerequisites: **JDK 21**, Android SDK **API 36**, Build Tools **36.0.0**, NDK **29.0.14206865**, and CMake **3.31.x** (CI installs **3.31.6**). The project declares `minSdk 24` and `targetSdk 36`; the dedicated emulator test currently uses **Android 14 / API 34**.

```bash
git clone --recurse-submodules https://github.com/z2bguoguo/Shizuku.git
cd Shizuku
# When cloning without submodules:
git submodule update --init --recursive

./gradlew :manager:assembleDebug
./gradlew :manager:assembleRelease -PmanagerApplicationId=app.example.manager
./gradlew :manager:testReleaseUnitTest
```

Use `gradlew.bat` on Windows. Local builds do **not** generate a random package ID automatically; without `-PmanagerApplicationId`, the build uses the upstream default `moe.shizuku.privileged.api`. Release signing depends on local configuration. The Shizuku API submodule remains an upstream-pinned dependency and is not forked by this repository.

The [GitHub Actions workflow](.github/workflows/app.yml) builds and signs the release, checks the manifest package identity and signature, computes hashes, runs source/unit checks, and validates UserService/IPC behavior in an Android 14 emulator before publishing a prerelease. The Android test includes active-window and screenshot calls, exception capture, reconnect cycles, and main-server replacement. **CI success is not a guarantee of OEM compatibility.**

### Troubleshooting and security

- **SDK blacklist is not system-wide stealth.** It filters the Shizuku service’s SDK access, not Android’s global package or process APIs.
- **Updated manager, old running service:** Restart the Shizuku service if a diagnostic transaction is unsupported or stale behavior remains.
- **Cannot install alongside upstream:** Some API permission names are intentionally compatible with upstream; a conflicting permission owner can prevent installation. Back up important data and identify installed packages before uninstalling anything.
- **Different APK cannot update the previous one:** CI package IDs differ, and ephemeral signing certificates can also differ. A stable update channel requires a stable identity and signing/migration policy.
- **UserService problem on an OEM device:** Copy **Connection diagnostics** directly after reproducing the failure. Remove package/user identifiers, secrets and sensitive log content before publishing it.
- **Permissions:** An ADB-started service has shell permissions, not root privileges. Always limit Shizuku access to trusted client apps.

### Upstream, attribution and license

Upstream: **[RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)** · [Official setup guide](https://shizuku.rikka.app/guide/setup/) · [Developer API](https://github.com/RikkaApps/Shizuku-API) · [Fork implementation notes](FORK.md).

The repository declares **[Apache-2.0](LICENSE)** for its source code. Preserve copyright/license notices and review the [upstream README’s special notices](https://github.com/RikkaApps/Shizuku#license) concerning app identity, launcher artwork, branding, and permission names. This project is not affiliated with or endorsed by the upstream Shizuku maintainers.

### 多应用并发协调

此 fork 增加共享 UiAutomation 注册、客户端独立回调、手势编号映射，以及输入/窗口/任务与包管理写操作的分资源轮询队列。普通读取保持并行；用户服务最多四路并行启动，超时和断开的排队请求会被移除。诊断页面显示共享客户端数、手势状态和队列统计。

并发层覆盖普通 Shizuku Binder 转发和用户服务中支持的系统接口入口。原生代码、已缓存接口、独立 shell 子进程及跨多个调用的完整工作流仍需调用方配合；设备前台窗口和安全窗口限制保持不变。支持范围与双应用并发验证说明见 [FORK.md](FORK.md#concurrent-clients-and-resource-coordination)。

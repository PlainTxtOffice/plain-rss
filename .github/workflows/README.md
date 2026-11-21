---
last_updated: 2026-11-20
version: 0.4.0
description: Build workflows for the Windows, Android, and iOS editions of Plain RSS.
---

# GitHub Workflows

| Platform | Workflow | When it runs | Checks and artifacts |
| --- | --- | --- | --- |
| Windows | [CI](ci.yml) | Push to `main`, pull request, manual run, or Windows build | Ruff, mypy, pytest, and Markdown checks |
| Windows | [Build](windows-build.yml) | Manual run or `v*` tag | Standalone and onefile Nuitka builds after CI passes |
| Android | [Build](android-build.yml) | Platform or workflow changes on `main` or pull requests, `v*` tags, or manual runs | Android lint, unit tests, and debug APKs |
| iOS | [Build](ios-build.yml) | Platform or workflow changes on `main` or pull requests, `v*` tags, or manual runs | Unsigned Debug app for iOS Simulator |

Each build workflow retains its artifacts for 30 days.

## Windows

CI checks `platform/windows/` on Python 3.12 and 3.14. The build workflow uses
Python 3.14 and the
[Nuitka scripts](../../platform/windows/scripts/nuitka/README.md) to package
`Plain RSS.exe` as a standalone folder and a single executable.

## Android

The workflow installs Android SDK Platform 37.0 and uses the committed Gradle
wrapper to run `lintDebug`, `testDebugUnitTest`, and `assembleDebug` in
`platform/android/`.

## iOS

Open `platform/ios/plain RSS.xcodeproj` and select the shared `plain RSS`
scheme. CI uses Xcode on a macOS 26 runner to build for a generic iOS Simulator
with code signing disabled.

For a signed device build or archive, set `DEVELOPMENT_TEAM` to your Apple
Developer Team ID in `platform/ios/Signing.local.xcconfig`. The project loads
this optional, Git-ignored file through its
[signing configuration](../../platform/ios/Signing.xcconfig).

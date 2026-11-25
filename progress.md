# KoolKeys development progress

## Current status

This repository contains a Java/XML Android project with the main screens and core app flows implemented. The debug build succeeds and the app is installed on the Redmi Note 14.

## Completed

- Created an Android application module with Java 17, AndroidX, Material Components, and a dark theme.
- Added Keyboard, Editor, Insert, and Settings screens.
- Added an editable code editor view with a line-number gutter, basic syntax coloring, paste, clear, and preview controls.
- Added local draft persistence and a full-code preview/confirmation before insertion.
- Added a touchpad view, QWERTY/numeric/function-key controls, modifier keys, arrow keys, and mouse buttons.
- Implemented the Android Bluetooth HID Device profile registration flow, paired-host selection, keyboard/mouse HID reports, US QWERTY character mapping, and registration timeout/error status.
- Added Normal/Extended pacing, configurable durations, progress/current line/countdown, pause/resume/stop, and review interval scheduling.
- Added session foreground notification, completion/pause/review events, configurable notification sound/vibration, and notification enablement.
- Added saved default duration and US-layout-only disclosure in Settings.
- Added a conditional release-signing configuration that reads local-only `signing.properties`; signing files are excluded from source control.
- Parsed all 14 Android XML resources successfully and checked 49 Java `R.id` references against XML declarations.
- Ran a delimiter/string-literal scan across all 11 Java source files; it passed. This is not a substitute for Java/Android compilation.

## In progress

- Exercise navigation, editor persistence, preview/confirmation, and session flows on the Redmi.
- Verify Bluetooth HID registration and real keyboard/mouse input on the phone and Windows host.

## Known limitations

- Google Android Platform Tools 37.0.3 is installed at `C:\Android\Sdk\platform-tools`; `adb devices -l` detects the authorized Redmi Note 14 (`24117RN76G`, serial `ai4pm7tsfqaqn7kb`).
- Android command-line tools 23.0 and Java-based 20.0 are installed under `C:\Android\Sdk\cmdline-tools`; both archives were checksum-verified against Google repository metadata. Version 20.0 is used because the latest wrapper requires a separate Android CLI download that fails on this connection.
- Temurin JDK 17.0.21.1 is installed at `C:\Android\jdk-17`; `JAVA_HOME`, `ANDROID_HOME`, and `ANDROID_SDK_ROOT` are configured at user scope.
- Android platform 35, Build Tools 35.0.0, and Platform Tools are installed; the Android SDK license is accepted.
- Project requires AGP 8.7.3, Gradle 8.9, JDK 17, compileSdk/targetSdk 35, and minSdk 26. Gradle 8.9 was downloaded from the official Gradle host, checksum-verified, and used to generate the missing project wrapper.
- `gradlew.bat tasks` and `gradlew.bat assembleDebug` pass. The current debug APK is `app\build\outputs\apk\debug\app-debug.apk` (5,914,550 bytes).
- Corrected `BluetoothManager`'s `onAppStatusChanged` callback parameter to match the verified Android 35 API signature.
- Fixed a startup `InflateException` by adding missing width/height attributes to the keyboard-row buttons. Rebuilt and reinstalled; the Keyboard screen renders and no new crash was recorded on relaunch.
- APK signature verified with the Android debug key; installed on the authorized Redmi Note 14.
- ADB confirms the KoolKeys process remains alive after relaunch and the home keyboard controls render.
- Redmi denies `adb shell input` with `INJECT_EVENTS` permission; no connected phone-mirroring window is available for further UI navigation.
- Downloads through curl and PowerShell web requests have encountered unexpected EOF/network failures; bounded BITS transfers succeeded for the verified SDK tools and JDK archives.
- Bluetooth HID Device support varies by phone and OS build. API level alone does not prove support; profile registration and a Windows-host connection still require device verification.
- HID key mapping is currently US QWERTY only. The Windows host must use a matching layout; non-US/non-ASCII code characters are rejected before insertion.
- Android may unregister a Bluetooth HID app when it is no longer foreground. Background insertion therefore depends on the target device/OS keeping the HID profile active and must be tested; the session stops safely if connection is lost.
- Line endings/tabs are sent as keyboard actions, but Windows editor auto-indent and host keyboard behavior can affect the final buffer. Exact output in Notepad/VS Code remains unverified.
- Release signing is configured to read local-only `signing.properties`, but no private keystore has been generated and no signed release APK exists yet.
- Navigation/editor persistence, preview/confirmation, Bluetooth HID, session controls, and notifications remain unverified.

## Next steps

1. Exercise navigation/editor persistence and session flows with direct phone interaction available.
2. Physically test Bluetooth HID pairing and keyboard/mouse reports against Windows; report any remaining device or network blockers.

## Last update

2026-11-26 — Temurin 17.0.21.1, Android command-line tools, API 35, Build Tools 35.0.0, and the Gradle 8.9 wrapper are installed and verified. `tasks` and `assembleDebug` pass; the APK is installed, and the Keyboard screen renders after fixing the startup inflation crash.
jj
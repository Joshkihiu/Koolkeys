# KoolKeys development progress

## Current status

This repository contains a Java/XML Android Studio project with the main screens and core app flows implemented. It has not yet been compiled or run on a device.

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

- Set up the JDK, Android SDK, Gradle build, and ADB access to the connected phone.
- Compile the app, resolve any Android/Gradle errors, generate a signed APK, and install it on the phone.
- Verify Bluetooth HID registration and real keyboard/mouse input on the phone and Windows host.

## Known limitations

- This environment currently has no Java/JDK, Gradle, Android SDK, `adb`, or `sdkmanager` on PATH or in the checked Android locations.
- WinGet can locate the JDK/Android Studio packages, but package installation failed with a network connection error; an explicit-source JDK install also stalled and was stopped.
- Retried the Android Studio install after the phone was reconnected; it made no progress and was stopped. The local tool check still finds no Android Studio or build tools.
- The user identified the target phone as a Redmi Note 14. Windows shows Xiaomi-vendor USB entries (`VID_2717&PID_FF80` and `VID_2717&PID_FF48`) as unknown USB composite devices; no ADB interface/tool is available, so USB installation is not possible yet.
- A separate Samsung-vendor entry (`VID_04E8`) was mistakenly attributed to the target phone; it is unrelated and no Samsung software/driver was installed.
- Rechecked after the USB-debugging retry: the Redmi's Xiaomi USB entries remain unknown and `adb` is still unavailable. No Java/Gradle/Android SDK tools are installed.
- Bluetooth HID Device support varies by phone and OS build. API level alone does not prove support; profile registration and a Windows-host connection still require device verification.
- HID key mapping is currently US QWERTY only. The Windows host must use a matching layout; non-US/non-ASCII code characters are rejected before insertion.
- Android may unregister a Bluetooth HID app when it is no longer foreground. Background insertion therefore depends on the target device/OS keeping the HID profile active and must be tested; the session stops safely if connection is lost.
- Line endings/tabs are sent as keyboard actions, but Windows editor auto-indent and host keyboard behavior can affect the final buffer. Exact output in Notepad/VS Code remains unverified.
- Release signing is configured to read local-only `signing.properties`, but no private keystore has been generated and no signed release APK exists yet.
- No APK has been built or installed yet.

## Next steps

1. Restore network access or install a JDK, Gradle, Android SDK, and ADB locally.
2. Resolve the Redmi/Xiaomi USB ADB driver/interface, then verify the device with `adb devices`.
3. Build and validate the app; generate a private release keystore and local signing properties before creating a release-signed APK.
4. Install on the phone, test HID pairing and keyboard/mouse reports against Windows, then test insertion and notifications.

## Last update

2026-11-25 — User confirmed Redmi Note 14; Xiaomi-vendor USB entries remain unknown composites, with no ADB interface or build toolchain. Build/sign/install verification remains blocked by missing tools and package-network access.

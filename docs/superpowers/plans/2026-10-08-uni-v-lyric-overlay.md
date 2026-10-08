# UNI-V Lyric Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `subagent-driven-development` or `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one Android APK that reads NetEase Cloud Music playback on a phone, transfers timed lyrics to a paired Android 9 head unit over Bluetooth, and displays them in a floating overlay.

**Architecture:** A single Java Android app offers phone-sender and head-unit-receiver roles. The phone reads an active MediaSession through a NotificationListenerService, resolves and caches timed lyrics, then sends versioned messages over a secure Bluetooth RFCOMM socket. The car role maps playback position to LRC lines and renders them through a user-enabled application overlay.

**Tech Stack:** Java 17, Android SDK API 36 (`minSdk 28`, `targetSdk 35`), Android Gradle Plugin 9.4.0, Gradle 9.6.0, Android platform APIs, SQLite, JUnit 4.13.2.

---

## File Map

All paths below are new; the workspace has no existing Android project.

```text
settings.gradle                         Gradle plugin and module repositories
build.gradle                            Android Gradle Plugin version
gradle.properties                      AndroidX and JVM build settings
app/build.gradle                       app id, SDK levels, Java level, JUnit
app/src/main/AndroidManifest.xml        permissions, activities, services
app/src/main/java/com/univ/lyricsbridge/
  MainActivity.java                     choose/restore phone or head-unit role
  media/LyricsNotificationListenerService.java
                                        notification-listener entry point
  media/MediaSessionMonitor.java        track and playback-state observation
  model/TrackInfo.java                  normalized track metadata
  model/PlaybackSnapshot.java           progress anchor, status and speed
  model/LyricLine.java                  timestamp and line text
  lyric/LrcParser.java                  timestamped LRC parsing
  lyric/LyricProvider.java              provider interface
  lyric/LyricCandidate.java             song candidate and raw LRC result
  lyric/NetEaseLyricProvider.java       timed-lyric lookup for NetEase tracks
  lyric/LyricMatcher.java               candidate confidence scoring
  lyric/LyricCache.java                 SQLite cache of matched LRC
  sync/PlaybackSyncEngine.java          current-position and line calculation
  transport/BluetoothFrameCodec.java    length-prefixed stream framing
  transport/BluetoothMessageCodec.java JSON protocol conversion
  transport/PhoneBluetoothServer.java   phone RFCOMM/SPP server
  transport/CarBluetoothClient.java     paired-phone RFCOMM/SPP client
  service/PhoneSenderService.java       user-started foreground sender
  service/CarReceiverService.java       user-started receiver and overlay owner
  ui/PhoneModeActivity.java             notification access and sender controls
  ui/CarModeActivity.java               overlay permission and paired-device UI
  overlay/LyricsOverlayController.java  non-focusable draggable overlay window
  data/AppSettings.java                 role, paired device and display settings
app/src/main/res/layout/                role screens and lyrics overlay views
app/src/main/res/values/strings.xml     setup/status/error text
app/src/test/java/com/univ/lyricsbridge/
  lyric/LrcParserTest.java
  lyric/LyricMatcherTest.java
  sync/PlaybackSyncEngineTest.java
  transport/BluetoothFrameCodecTest.java
README.md                               build, permissions, pairing and two-phone test
dist/univ-lyrics-bridge-debug.apk       installable debug APK
```

## Build Toolchain

The host currently has no Java, Gradle, Android SDK, or ADB on PATH. Provision tools outside the project under `C:\Users\Administrator\.cache\univ-lyrics-toolchain`:

1. Download a Windows x64 JDK 17 archive from Eclipse Adoptium and place it at `jdk17` under that directory.
2. Download the official Android command-line tools archive `commandlinetools-win-15859902_latest.zip`; verify SHA-256 `90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a` before extraction.
3. Install SDK packages `platforms;android-36`, `build-tools;36.0.0`, and `platform-tools` with `sdkmanager`.
4. Download Gradle 9.6.0 and generate the checked-in Gradle Wrapper from the new project. AGP 9.4.0 requires Gradle 9.6.0 and JDK 17; these versions and the API 36 build tools are listed in Android's release documentation.

Set `JAVA_HOME` and `ANDROID_HOME` only for the build process; do not add machine-wide environment changes. The Android command-line tools are listed on [Android Developers](https://developer.android.com/studio/), and AGP compatibility is listed in the [AGP 9.4.0 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes).

## Tasks

### Task 1: Provision the Android build toolchain

**Files:** No project files; tools live in `C:\Users\Administrator\.cache\univ-lyrics-toolchain`.

- [x] Download JDK 17 and Android command-line tools into the toolchain directory; verify the Google archive checksum before extraction.
- [x] Use `sdkmanager` to install `platforms;android-36`, `build-tools;36.0.0`, and `platform-tools`; accept the Android SDK package licenses needed for the local build.
- [x] Download and extract Gradle 9.6.0; run `java -version`, `sdkmanager --list_installed`, and `gradle --version` with task-local `JAVA_HOME`/`ANDROID_HOME`.
- [x] Confirm output reports Java 17, API 36, Build Tools 36.0.0, and Gradle 9.6.0.

### Task 2: Create a minimal one-module Java APK project

**Files:** `settings.gradle`, `build.gradle`, `gradle.properties`, `app/build.gradle`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/univ/lyricsbridge/MainActivity.java`.

- [x] Create a single `:app` module with `com.android.application` 9.4.0, namespace/application id `com.univ.lyricsbridge`, `compileSdk 36`, `minSdk 28`, `targetSdk 35`, and Java 17 source/target compatibility.
- [x] Add JUnit 4.13.2 as the only test dependency; keep production dependencies on Android platform APIs.
- [x] Generate `gradlew.bat` and wrapper files for Gradle 9.6.0 using the downloaded Gradle distribution.
- [x] Create `MainActivity` with two role buttons; each opens an initial phone or car status screen that displays the selected role and setup instructions.
- [x] Run `gradlew.bat assembleDebug`; expected result is `BUILD SUCCESSFUL` and `app\build\outputs\apk\debug\app-debug.apk` exists.

### Task 3: Define shared playback models and parse LRC

**Files:** `model/TrackInfo.java`, `model/PlaybackSnapshot.java`, `model/LyricLine.java`, `lyric/LrcParser.java`, `sync/PlaybackSyncEngine.java`, the corresponding parser/sync test files.

- [x] Implement immutable value objects. `PlaybackSnapshot` carries `positionMs`, `sampledAtElapsedRealtimeMs`, `playing`, `speed`, and `durationMs`.
- [x] Implement `equals()` and `hashCode()` for `LyricLine` so parser output can be compared as a value in unit tests.
- [x] Implement `LrcParser.parse(String)`: strip UTF-8 BOM, parse `[mm:ss.fraction]`, expand multiple timestamps on one line, apply `[offset:+/-N]`, ignore metadata tags, discard blank/timestamp-free lines, and sort by timestamp while preserving input order for ties.
- [x] Write parser tests before implementation. Include these input cases:

```java
String lrc = "[ti:Song]\n[00:02.00]Second\n[00:01.250][00:04.00]First";
List<LyricLine> lines = LrcParser.parse(lrc);
assertEquals(Arrays.asList(
    new LyricLine(1250, "First"),
    new LyricLine(2000, "Second"),
    new LyricLine(4000, "First")
), lines);
```

- [x] Implement `PlaybackSyncEngine.positionAt(snapshot, nowElapsedRealtimeMs)` using the snapshot anchor and playback speed; clamp to `[0, durationMs]`. `lineIndexAt(lines, positionMs)` returns the last line whose timestamp is not greater than the position, or `-1` before the first line.
- [x] Add tests for playing interpolation, paused position, duration clamping, before-first-line, and exact line-boundary behavior; also test receiver clock-domain rebasing.
- [x] Run `gradlew.bat testDebugUnitTest`; parser and sync tests must pass.

### Task 4: Read NetEase media-session metadata on the phone

**Files:** `media/LyricsNotificationListenerService.java`, `media/MediaSessionMonitor.java`, `media/NetEasePackageFilter.java`, Android manifest, `ui/PhoneModeActivity.java`.

- [x] Declare `LyricsNotificationListenerService` with `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` and the system notification-listener intent action.
- [x] In `MediaSessionMonitor`, obtain active sessions using the enabled listener component, register a `MediaController.Callback`, and read title, artist, album, duration, playback state, position, speed, and update time.
- [x] Filter to the standard NetEase package `com.netease.cloudmusic`; if no matching session exists, publish a `NO_PLAYER` state rather than selecting another app silently.
- [x] Re-register callbacks when active sessions change and unregister them when the service disconnects/destroys.
- [x] Add the Phone screen action that opens `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` and a status label for access granted, no song, and progress unavailable.
- [ ] Build the app; on the sender phone, enable access and verify the screen shows NetEase title/artist/state while that sender phone plays a NetEase track. (Build is verified; device step awaits a phone visible to ADB.)

### Task 5: Add automatic lyric lookup and a local cache

**Files:** `lyric/LyricProvider.java`, `lyric/NetEaseLyricProvider.java`, `lyric/LyricMatcher.java`, `lyric/LyricCache.java`, `app/build.gradle`, manifest, and `LyricMatcherTest.java`.

- [x] Define `LyricProvider.search(TrackInfo)` returning candidate metadata plus raw timestamped LRC. Use LRCLIB's documented public HTTPS read API (anonymous access) with exact metadata lookup followed by search fallback; keep the provider replaceable and do not use account credentials or the archived 2018 demo host.
- [x] Implement HTTPS requests with 5-second connect and 7-second read timeouts on a worker executor; send only title, artist, and duration, never account cookies/passwords.
- [x] Implement title/artist normalization and deterministic confidence scoring: exact normalized title `0.60`, title containment `0.45`, exact artist `0.30`, artist containment `0.20`, duration difference at most 5 seconds `0.10`; accept only candidates scoring at least `0.85`.
- [x] Write matcher tests for exact title/artist/duration, wrong artist, wrong title, and candidates below threshold; then run `gradlew.bat testDebugUnitTest`.
- [x] Cache successful LRC by normalized title, artist, and duration in a SQLite table. A cache hit must avoid a network request; provider failures return a typed no-result/error state and keep the last successful cache entry.
- [x] Add `INTERNET` permission; network lookup is invoked on the service's single-thread worker.

### Task 6: Implement the Bluetooth message protocol

**Files:** `transport/BluetoothFrameCodec.java`, `transport/BluetoothMessageCodec.java`, `model/` message values, `transport/BluetoothFrameCodecTest.java`.

- [x] Encode each UTF-8 JSON message as a four-byte big-endian payload length followed by payload bytes; reject frames larger than 512 KiB and close on invalid length.
- [x] Use protocol version `1` and message types `HELLO`, `TRACK`, `LYRICS`, `PLAYBACK`, and `STATUS`. Every `PLAYBACK` message includes `positionMs`, `sampledAtElapsedRealtimeMs`, `playing`, `speed`, and `durationMs`; the receiving device reanchors to its local elapsed-realtime clock.
- [x] Write round-trip tests for an empty payload rejection, a normal frame, fragmented input, end-of-stream mid-frame, and an oversized length prefix.
- [x] Run `gradlew.bat testDebugUnitTest` and confirm all frame tests pass.

### Task 7: Add RFCOMM phone server and car client services

**Files:** `transport/PhoneBluetoothServer.java`, `transport/CarBluetoothClient.java`, `service/PhoneSenderService.java`, `service/CarReceiverService.java`, manifest, `data/AppSettings.java`.

- [x] Use one hard-coded app UUID on both sides. Phone calls `listenUsingRfcommWithServiceRecord`; car calls `createRfcommSocketToServiceRecord` for a selected paired phone. Keep accept/connect/read/write off the main thread.
- [x] Phone service sends `TRACK` and `LYRICS` when the song changes, then sends projected `PLAYBACK` every 500 ms while connected. The receiver reanchors the position to its own clock before updating the overlay.
- [x] On unexpected disconnect, close all sockets/streams and reconnect every 1, 2, 5, 10, then 30 seconds until stopped or connected.
- [x] Declare `BLUETOOTH`/`BLUETOOTH_ADMIN` for API 28–30, `BLUETOOTH_CONNECT` for API 31+, `FOREGROUND_SERVICE`, and `FOREGROUND_SERVICE_CONNECTED_DEVICE`; declare both role services with `foregroundServiceType="connectedDevice"`.
- [x] Request `BLUETOOTH_CONNECT` on API 31+; use only the bonded-device list, so the app does not request scan or location permission.
- [x] Add foreground notifications with a Stop action for both roles. Starting each service requires an explicit user tap in that role's screen.
- [ ] Build and manually connect the two test phones. Confirm the car-role phone receives track, LRC, pause/resume, seek, and disconnect status. (Build is verified; device step awaits connected phones.)

### Task 8: Build the head-unit overlay and role setup screens

**Files:** `MainActivity.java`, `ui/PhoneModeActivity.java`, `ui/CarModeActivity.java`, `overlay/LyricsOverlayController.java`, `app/src/main/res/layout/`, `app/src/main/res/values/strings.xml`, manifest.

- [x] Implement role selection and persist the selected role independently on each device using `AppSettings`.
- [x] Phone screen shows notification-access status, current track, lyric status, and Start/Stop Sending controls.
- [x] Car screen lists `BluetoothAdapter.getBondedDevices()`, selects a paired sender, opens overlay settings when `Settings.canDrawOverlays()` is false, and provides Connect/Disconnect/Show/Hide controls.
- [x] Add a compact landscape overlay with title/artist and current lyric line plus next line. Use `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE`, and `FLAG_LAYOUT_IN_SCREEN`; implement dragging with touch deltas, opacity and text size controls, and a hide button.
- [x] Update the current line on the main thread every 100 ms using `PlaybackSyncEngine`; hide prior-track lyrics immediately on `TRACK` change or disconnect.
- [x] Request `POST_NOTIFICATIONS` on API 33+ before starting a foreground service. Show explicit status for denied overlay, denied Bluetooth, no active player, no timed lyrics, and disconnected state.
- [ ] Build and manually verify the overlay appears above the second phone's launcher, can be dragged, changes font size/opacity, and stops cleanly. (Build is verified; device step awaits attached phones.)

### Task 9: Integrate, package, and document the two-phone workflow

**Files:** `README.md`, `.gitignore`, `dist/univ-lyrics-bridge-debug.apk`.

- [x] Run `gradlew.bat testDebugUnitTest` and `gradlew.bat assembleDebug` with JDK 17 and Android SDK 36; both commands must report `BUILD SUCCESSFUL`.
- [x] Verify the artifact with `apksigner.bat verify --verbose --print-certs` and `aapt2 dump badging`; confirm package `com.univ.lyricsbridge`, `minSdkVersion:'28'`, and a v2 debug signature.
- [x] Copy the APK to `dist\univ-lyrics-bridge-debug.apk`; source and delivery SHA-256 hashes match.
- [x] Document phone setup, notification access, NetEase playback, system Bluetooth pairing, car-role setup, overlay permission, known lyric-source limits, and uninstall steps in `README.md`.
- [ ] Complete the user-run two-phone checklist: install the same APK on both phones; set one to sender and play NetEase; set the other to car receiver; pair and connect; confirm correct lyrics across track change, pause/resume, seek, hide/show, and reconnect. (No Android devices currently appear in `adb devices -l`.)
- [x] Document that two-phone testing covers app RFCOMM and overlay behavior; final validation of A2DP/RFCOMM coexistence and OEM launcher behavior remains specific to the UNI-V head unit.

## Plan Review

- The spec's role split, media-session access, lyric matching, RFCOMM transport, overlay permission, reconnect behavior, privacy constraints, and debug APK delivery each map to Tasks 2–9.
- LRC parsing, matching, sync math, and stream framing have concrete unit-test cases in Tasks 3, 5, and 6.
- Device-specific A2DP/RFCOMM and launcher behavior are called out for actual UNI-V validation; the user-provided two-phone path covers the software transport before that check.
- Workspace is not a Git repository. The plan does not assume commits or initialize a repository without the user's request.

## Follow-up: 8821 handling and overlay styling

- [x] Map NetEase 8821 to a clear behavior-verification message in password login and SMS-code request paths. The app recommends SMS or official-app verification and does not bypass platform checks.
- [x] Add car-settings color choices for the previous sung line and next unsung line; preserve the active-line gold highlight and store choices in app settings.
- [x] Show title and artist at the lyric group's upper-left; include this header in the existing long-press drag surface and retain the settings-page hide action.
- [x] Add regression tests for 8821 handling, color palette defaults, and title/artist formatting; full build and tests pass.
- [ ] Validate color selection, header alignment, long-press movement, hide/show, and login behavior on the user's Android devices. No ADB device is currently attached.

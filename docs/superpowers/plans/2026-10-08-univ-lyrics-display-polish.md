# UNI-V 歌词显示与车机连接优化实施计划

> **For agentic workers:** Execute this plan inline, one task at a time, using the current workspace and the existing test-first workflow. Steps use checkbox (`- [x]`) syntax.

**Goal:** Improve lyric matching and deliver the user-approved 1280×720 car display: a road-and-lyrics launcher icon, one adaptive settings page, a transparent three-line rolling overlay, and remembered Bluetooth auto-connect on the car only.

**Architecture:** Keep the existing single Java Android application and its phone-sender/car-receiver split. Add title-only LRCLIB fallback and a pure lyric-window model; keep display preferences in `AppSettings`; let a boot receiver start only the configured car receiver foreground service; render the overlay as a touch-transparent, background-free text window.

**Tech Stack:** Java 17, Android API 36 compile / API 28 minimum / API 35 target, Android platform UI and Bluetooth APIs, JUnit 4.13.2.

---

## File Map

| File | Responsibility |
|---|---|
| `app/src/main/java/com/univ/lyricsbridge/lyric/LrclibLyricProvider.java` | Keep exact lookup, then request artist-scoped and title-only timed lyric candidates. |
| `app/src/main/java/com/univ/lyricsbridge/lyric/NetEaseWeApiCrypto.java` | Encrypt direct phone-side NetEase API requests without logging account secrets. |
| `app/src/main/java/com/univ/lyricsbridge/lyric/NetEaseApiClient.java` | Log in directly to NetEase over HTTPS, retain returned cookies, and search/fetch lyrics. |
| `app/src/main/java/com/univ/lyricsbridge/lyric/NetEaseSessionStore.java` | Keep only encrypted NetEase session cookies on the sender phone; clear on logout. |
| `app/src/main/java/com/univ/lyricsbridge/lyric/NetEaseLyricProvider.java` | Search NetEase candidates and return timestamped LRC candidates. |
| `app/src/main/java/com/univ/lyricsbridge/lyric/NetEaseFirstLyricProvider.java` | Try logged-in NetEase first, then LRCLIB on no match or API failure. |
| `app/src/main/java/com/univ/lyricsbridge/lyric/LyricWindow.java` | Return previous/current/next lyric lines for a current index. |
| `app/src/main/java/com/univ/lyricsbridge/data/AppSettings.java` | Persist boot-connect toggle, paired device, overlay font size, and lyric text opacity. |
| `app/src/main/java/com/univ/lyricsbridge/ui/CarLayoutPolicy.java` | Decide whether the current car screen can use a two-column layout. |
| `app/src/main/java/com/univ/lyricsbridge/ui/CarModeActivity.java` | Render the single responsive car settings page and bind controls to saved settings/services. |
| `app/src/main/java/com/univ/lyricsbridge/ui/PhoneModeActivity.java` | Add phone-only optional login/logout and show NetEase login status without retaining plaintext password. |
| `app/src/main/java/com/univ/lyricsbridge/overlay/LyricsOverlayController.java` | Render the three text rows with no panel, border, buttons, or touch interception. |
| `app/src/main/java/com/univ/lyricsbridge/service/CarBootStartPolicy.java` | Pure guard for role, user toggle, and remembered Bluetooth address. |
| `app/src/main/java/com/univ/lyricsbridge/service/CarBootReceiver.java` | Receive boot completion and start the car receiver only when policy permits. |
| `app/src/main/java/com/univ/lyricsbridge/service/CarReceiverService.java` | Preserve reconnect behavior and show lyrics after valid LRC arrives when started for auto-display. |
| `app/src/main/AndroidManifest.xml` | Declare boot permission/receiver and launcher icon. |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Vector road and lyric lines for the icon foreground. |
| `app/src/main/res/values/ic_launcher_background.xml` | Dark teal icon background color. |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive icon resource. |
| `app/src/test/java/com/univ/lyricsbridge/lyric/LrclibLyricProviderTest.java` | Verify search fallback construction and requested Chinese track candidate acceptance. |
| `app/src/test/java/com/univ/lyricsbridge/lyric/NetEaseWeApiCryptoTest.java` | Verify deterministic two-layer AES payload round-trip and RSA field shape. |
| `app/src/test/java/com/univ/lyricsbridge/lyric/NetEaseSessionCipherTest.java` | Verify session ciphertext round-trips and differs from plaintext using a test key. |
| `app/src/test/java/com/univ/lyricsbridge/lyric/NetEaseApiClientTest.java` | Verify account route selection and that login requests send encrypted fields only. |
| `app/src/test/java/com/univ/lyricsbridge/lyric/LyricWindowTest.java` | Verify three-line window at first, middle, and last lyric. |
| `app/src/test/java/com/univ/lyricsbridge/ui/CarLayoutPolicyTest.java` | Verify landscape width decisions for 1280×720 and narrow/portrait screens. |
| `app/src/test/java/com/univ/lyricsbridge/service/CarBootStartPolicyTest.java` | Verify only enabled car-role setup with a saved address starts on boot. |
| `README.md` | Update phone/car setup, auto-connect, overlay controls, and two-phone acceptance steps. |
| `dist/univ-lyrics-bridge-debug.apk` | Deliver the rebuilt Android 9 debug APK. |

## Task 1: Fix the LRCLIB artist mismatch fallback

**Files:** `LrclibLyricProvider.java`, `LyricMatcherTest.java`, new `LrclibLyricProviderTest.java`.

- [x] **Step 1: Add the regression test for the user-reported artist alias.** Add this test to `LyricMatcherTest.java`:

```java
@Test
public void acceptsDiaoLeByAmeiChineseArtistAliasAndDuration() {
    TrackInfo track = new TrackInfo("掉了", "张惠妹", "", "com.netease.cloudmusic", 238000);
    LyricCandidate candidate = candidate("掉了", "aMEI (張惠妹)", 238000);

    LyricMatcher.Match match = LyricMatcher.bestMatch(track, Arrays.asList(candidate));

    assertSame(candidate, match.getCandidate());
    assertTrue(match.getScore() >= LyricMatcher.ACCEPTANCE_THRESHOLD);
}
```

Import `assertTrue` and keep the existing wrong-title/wrong-artist threshold tests unchanged.

- [x] **Step 2: Add a test for the ordered fallback search paths.** Create `LrclibLyricProviderTest.java`:

```java
@Test
public void searchPathsTryArtistAndThenTitleOnly() {
    TrackInfo track = new TrackInfo("掉了", "张惠妹", "", "com.netease.cloudmusic", 238000);

    List<String> paths = LrclibLyricProvider.buildSearchPaths(track);

    assertEquals(2, paths.size());
    assertTrue(paths.get(0).contains("track_name=%E6%8E%89%E4%BA%86"));
    assertTrue(paths.get(0).contains("artist_name=%E5%BC%A0%E6%83%A0%E5%A6%B9"));
    assertEquals("/search?track_name=%E6%8E%89%E4%BA%86", paths.get(1));
}
```

- [x] **Step 3: Run only the new tests and confirm the expected RED.**

Run: `.\gradlew.bat --no-daemon --console=plain testDebugUnitTest --tests '*LyricMatcherTest' --tests '*LrclibLyricProviderTest'`

Expected: the provider path test fails because `buildSearchPaths` does not exist yet. The alias-scoring test may pass against existing scoring; it locks the reported Chinese artist alias while the missing artist/title query fallback is the behavior that must go RED.

- [x] **Step 4: Implement the smallest fallback.** Add package-visible static `buildSearchPaths(TrackInfo)` that URL-encodes title and artist, orders artist-scoped search before title-only search, and omits a duplicate artist path if artist is blank. In `search`, keep exact `/get` first; then request both search paths and aggregate all distinct candidates that contain timestamped LRC, so a title-only alias candidate remains available even if the artist-scoped search returns weak candidates. Keep HTTP/network errors distinct from no-match. Allow a nonempty title even when artist metadata is blank.

- [x] **Step 5: Run the focused lyric tests and confirm GREEN.** Use the same command from Step 3; expected result is `BUILD SUCCESSFUL` and all selected lyric tests pass.

## Task 2: Model the three visible lyric rows

**Files:** new `LyricWindow.java`, new `LyricWindowTest.java`.

- [x] **Step 1: Write first/middle/last index tests.** The test helper creates three `LyricLine` values at 0, 1000, and 2000 ms. Assert that `LyricWindow.at(lines, 0)` returns `previous=""`, `current="one"`, `next="two"`; index 1 returns `one/two/three`; index 2 returns `two/three/""`; a negative/out-of-range index returns three empty strings.
- [x] **Step 2: Run `.\gradlew.bat --no-daemon --console=plain testDebugUnitTest --tests '*LyricWindowTest'` and confirm it fails because the class is missing.**
- [x] **Step 3: Implement immutable `LyricWindow` with `at(List<LyricLine>, int)` and `getPrevious()`, `getCurrent()`, `getNext()` accessors.** Clamp only by returning empty fields for absent neighbors; do not wrap at either end.
- [x] **Step 4: Rerun the focused test and confirm all cases pass.**

## Task 3: Persist receiver boot and appearance settings

**Files:** `AppSettings.java`, new `CarBootStartPolicy.java`, new `CarBootStartPolicyTest.java`, new `CarLayoutPolicy.java`, new `CarLayoutPolicyTest.java`.

- [x] **Step 1: Write policy and layout tests first.** `CarBootStartPolicyTest` asserts start is true only for role `"car"`, enabled preference, and nonblank remembered address; phone role, disabled toggle, and blank address each return false. `CarLayoutPolicyTest` asserts landscape with available width `>=720 dp` uses two columns, portrait never does, and width below `720 dp` remains one column.
- [x] **Step 2: Run the two focused tests and confirm they fail because the policy classes are missing.**
- [x] **Step 3: Implement the pure policies and `AppSettings` accessors.** Add `isAutoConnectOnBoot` (default `true`), `setAutoConnectOnBoot`, `getOverlayTextOpacity` (default `0.90f`), and `setOverlayTextOpacity` clamped to `[0.40f, 1.0f]`. Keep the existing device address and font-size keys; do not change role storage. `CarBootStartPolicy.shouldStart(role, enabled, address)` must call `trim()` only for emptiness and preserve the Bluetooth address verbatim. `CarLayoutPolicy.useTwoColumns(widthDp, isLandscape)` returns `isLandscape && widthDp >= 720`.
- [x] **Step 4: Rerun the focused tests and confirm GREEN.**

## Task 4: Replace the car page with one adaptive settings screen

**Files:** `CarModeActivity.java`, `AppSettings.java`, `CarLayoutPolicy.java`.

- [x] **Step 1: Preserve the activity registration regression fix and all existing Bluetooth/runtime permission handling.** Keep `.ui.CarModeActivity` declared in the manifest and avoid launching system settings automatically on page entry.
- [x] **Step 2: Rebuild the page as a single scrollable screen.** Always read `BluetoothAdapter.getBondedDevices()` on resume; show every paired device as a selectable row, label the remembered one, persist selection immediately, and never silently replace a still-valid saved address with the first alphabetic device. Put the last connection status and current track/lyric status in this page.
- [x] **Step 3: Bind one boot-connect switch and two `SeekBar` controls.** The switch writes `AppSettings.setAutoConnectOnBoot`; seek bars write the font size (18–42 sp, default 28) and lyric text opacity (40–100%, default 90%). Initialize progress from saved values before attaching change listeners so opening settings does not overwrite preferences.
- [x] **Step 4: Use `CarLayoutPolicy` with current `screenWidthDp` and orientation.** At the real 1280×720 landscape display, place device/status choices and appearance/auto-connect controls in two weighted columns. In portrait or under 720 dp, use one column with vertical scrolling. Size all dimensions in dp/sp.
- [x] **Step 5: Keep actions simple.** Provide “连接并显示” and “隐藏歌词”; the connect action stores the selected address and starts the receiver with auto-show enabled. Keep the existing system Bluetooth settings route available only if no device is paired; permissions remain handled in context.
- [x] **Step 6: Build the app with `.\gradlew.bat --no-daemon --console=plain assembleDebug` and confirm the activity compiles.**

## Task 5: Render transparent rolling lyrics and move controls out of the overlay

**Files:** `LyricsOverlayController.java`, `CarReceiverService.java`, `AppSettings.java`, `LyricWindow.java`, `LyricWindowTest.java`.

- [x] **Step 1: Keep `LyricWindowTest` as the behavior contract and confirm it passes before view changes.**
- [x] **Step 2: Replace title/current/next/status/control children with exactly three centered lyric `TextView` rows.** Previous is dim gray, current is gold, next is white; apply the saved text opacity to text colors only. Remove the root background, stroke, padding, track title, status label, and `A−`/`A+`/opacity/hide buttons. Use `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_IN_SCREEN`, a transparent pixel format, and a width bounded by the available screen width.
- [x] **Step 3: Re-render only when playback crosses to a new line.** Use `LyricWindow.at(...)` and update the three strings. For a changed index, animate the lyric group with a short upward fade (about 180 ms) so each timestamp advances the visible three-line window. Paused playback must keep the current line stable; missing LRC or a new track must clear old lines.
- [x] **Step 4: Read size and opacity when constructing/updating the overlay.** No display adjustments or controls appear inside the overlay. Set default position to screen center and preserve only the user-facing preferences required by the settings screen.
- [x] **Step 5: Build the app and confirm it compiles for minSdk 28.**

## Task 6: Start only the car receiver after boot

**Files:** new `CarBootReceiver.java`, `CarBootStartPolicy.java`, `CarReceiverService.java`, `AndroidManifest.xml`, `CarModeActivity.java`.

- [x] **Step 1: Re-run `CarBootStartPolicyTest` to confirm the guard behavior is green.**
- [x] **Step 2: Add `RECEIVE_BOOT_COMPLETED` and a non-exported receiver for `android.intent.action.BOOT_COMPLETED`.** The receiver reads the saved role, toggle, and address, and returns without work unless `CarBootStartPolicy.shouldStart(...)` is true.
- [x] **Step 3: Start `CarReceiverService` as a foreground `connectedDevice` service with the saved address and an auto-show flag.** On Android O+, use `startForegroundService`; older supported Android paths use `startService`. Catch documented start/security exceptions so boot does not crash and leave a readable receiver status when the user next opens settings.
- [x] **Step 4: In `CarReceiverService`, preserve exponential/backoff Bluetooth reconnection.** Keep the service started while enabled; when a started-for-display session receives valid LRC, show the overlay if the permission is granted. Hiding lyrics clears auto-show for the current service run. Never start the phone sender from boot.
- [x] **Step 5: Build and inspect the merged manifest.** Confirm the boot receiver is present, non-exported, and filters only boot completion; confirm service type remains `connectedDevice`, minSdk is 28, and Bluetooth permissions remain declared.

## Task 7: Add the selected road-and-lyrics launcher icon

**Files:** `AndroidManifest.xml`, new `res/drawable/ic_launcher_foreground.xml`, new `res/values/ic_launcher_background.xml`, new `res/mipmap-anydpi-v26/ic_launcher.xml`.

- [x] **Step 1: Add a vector foreground with three short lyric strokes and a centered tapering road plus dashed center line.** Use the approved C direction and high-contrast teal/gold on a dark background; keep shapes inside the adaptive icon safe region.
- [x] **Step 2: Add the adaptive icon XML and point `android:icon` and `android:roundIcon` at `@mipmap/ic_launcher`.** No user photos or external images are needed.
- [x] **Step 3: Build and inspect APK badging/resource packaging.** Confirm launcher label remains `UNI-V 歌词桥` and icon resource is packaged.

## Task 8: Add optional direct NetEase login and timed lyric retrieval

**Files:** new `NetEaseWeApiCrypto.java`, `NetEaseApiClient.java`, `NetEaseSessionStore.java`, `NetEaseLyricProvider.java`, `NetEaseFirstLyricProvider.java`; `PhoneModeActivity.java`, `PhoneSenderService.java`, `LrclibLyricProvider.java`; new tests listed above.

- [x] **Step 1: Write credential-safe crypto/session tests before production code.** `NetEaseWeApiCryptoTest` uses a fixed 16-character test key and test JSON. The test decrypts the outer AES/CBC layer with the fixed key, decrypts the inner layer with the published WeAPI nonce, and asserts the original JSON; it also asserts `encSecKey` is exactly 256 hexadecimal characters. `NetEaseSessionCipherTest` encrypts/decrypts a dummy cookie using an in-memory AES test key and asserts stored text is not the cookie. `NetEaseApiClientTest` asserts a numeric account selects `/weapi/login/cellphone`, an email selects `/weapi/login`, and the HTTP form contains only `params` and `encSecKey`, never the plaintext account password.
- [x] **Step 2: Run the focused tests and confirm RED.** Run `.\gradlew.bat --no-daemon --console=plain testDebugUnitTest --tests '*NetEaseWeApiCryptoTest' --tests '*NetEaseSessionCipherTest' --tests '*NetEaseApiClientTest'`; expected failures name the missing new classes.
- [x] **Step 3: Implement request encryption using platform Java crypto.** Use AES-128-CBC with PKCS5 padding for the two WeAPI layers and RSA modular exponentiation with `BigInteger.modPow` for `encSecKey`. Generate a random 16-character ASCII secret key with `SecureRandom`; encode JSON as UTF-8. Submit form-encoded `params` and `encSecKey` to `https://music.163.com` only. Do not use a third-party proxy/service.
- [x] **Step 4: Implement login and cookie capture.** Accept a phone number or email, submit only on an explicit “登录网易云” tap, check the JSON response code, capture `Set-Cookie` values only after code 200, and expose concise login/verification/network states. Never include password or cookie in logs, exceptions shown to the user, or Bluetooth messages.
- [x] **Step 5: Encrypt the session cookie before persistence.** Generate/load an AES/GCM key from Android Keystore (`AndroidKeyStore`); store Base64 IV+ciphertext only in app-private preferences. Provide `getCookie()` and `clear()`; if decrypt fails, clear invalid stored data and treat as logged out. Store no password. Logout clears the encrypted cookie.
- [x] **Step 6: Fetch lyrics only on the sender phone.** With a saved session, search NetEase for `title + artist`, rank song metadata with `LyricMatcher`, fetch `/weapi/song/lyric` for the best candidates, and return only records whose `lrc.lyric` parses into timestamped lines. If no usable match or any private-API failure occurs, continue to LRCLIB. Do not block sending on NetEase login or service availability.
- [x] **Step 7: Add phone-side login controls.** `PhoneModeActivity` displays an account field (“手机号或邮箱”), password field, login status, “登录网易云”, and “退出登录”. Run login on a worker thread, clear the password field after the attempt, and keep the controls out of the car activity. Do not request or paste credentials in this conversation.
- [x] **Step 8: Add provider tests with fake transport.** A successful login response persists only response cookies; failed login does not create a session. A logged-in provider falls back to LRCLIB when NetEase search is empty, lyrics have no timestamps, or an HTTP/API error occurs. A no-session provider calls LRCLIB without asking for credentials.
- [x] **Step 9: Run all focused NetEase tests and confirm GREEN.** Real-account login cannot be exercised without the user's phone/account; note that a NetEase risk check or verification challenge may reject unofficial login requests.

## Task 9: Update setup instructions and deliver the APK

**Files:** `README.md`, `task_plan.md`, `progress.md`, `dist/univ-lyrics-bridge-debug.apk`.

- [x] **Step 1: Update README car instructions.** State that the car page lists paired devices, remembers the selection, provides font/text-opacity settings, and can reconnect after boot when enabled and permissions are granted. State phone sending still starts from the phone app.
- [x] **Step 2: Update README phone instructions.** Explain optional phone-only NetEase login, encrypted session storage, logout, direct HTTPS requests to NetEase, and LRCLIB fallback. State the login path is unofficial and may require an additional NetEase security verification.
- [x] **Step 3: Update the two-phone test steps.** Include one-time device selection, boot/autoconnect preference behavior, changing font/opacity from settings, verifying the three-color three-line transparent overlay, testing NetEase login on the sender phone, logout, LRCLIB fallback, track change/pause/reconnect.
- [x] **Step 4: Run the full requested verification command from the ASCII drive alias:** `.\gradlew.bat --no-daemon --console=plain testDebugUnitTest assembleDebug`. Expected: all JUnit tests pass and Gradle reports `BUILD SUCCESSFUL`.
- [x] **Step 5: Verify the APK.** Inspect minSdk/targetSdk, package name, manifest receiver/service type, debug signing, APK size, and SHA-256. Copy the built APK to `dist/univ-lyrics-bridge-debug.apk` and compare the two hashes.
- [x] **Step 6: Check connected devices.** Run `adb devices -l`. If no devices are listed, report software-test results and leave two-phone/head-unit behavior as a clearly stated device-validation item.

## Execution Notes

- Workspace is not a Git repository; do not attempt commits or initialize Git as part of this feature.
- User chose inline execution in the current conversation. Do not create a separate task or worktree.
- The user previously requested two-phone Bluetooth software testing. Run automated unit/build verification and perform physical checks only if Android devices are attached to ADB.
- Keep changes limited to the approved spec. Preserve the earlier `CarModeActivity` manifest registration and live receiver status fixes.

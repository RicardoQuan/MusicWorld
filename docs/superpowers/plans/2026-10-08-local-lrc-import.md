# Local LRC Import Implementation Plan

> **For agentic workers:** Implement inline in the current session. Steps use checkbox syntax for tracking.

**Goal:** Allow a sender to import an accessible LRC file, bind it to the current track, and send it to the car without NetEase third-party login.

**Architecture:** Add a small local lyric store and provider. The Activity uses Android's document picker to read one user-selected file, validates it with the existing parser, saves a private copy keyed by normalized title and artist, then asks the sender service to refresh. Provider priority is local import, NetEase, LRCLIB.

**Tech Stack:** Java 17, Android document picker (`ACTION_OPEN_DOCUMENT`), existing `LrcParser`, `LyricProvider`, and `SharedPreferences`.

---

### Task 1: Add the local lyric source and store

**Files:**
- Modify: `app/src/main/java/com/univ/lyricsbridge/lyric/LyricSource.java`
- Create: `app/src/main/java/com/univ/lyricsbridge/lyric/LocalLyricStore.java`
- Create: `app/src/main/java/com/univ/lyricsbridge/lyric/LocalLrcLyricProvider.java`

- [x] Add `LOCAL_LRC("本地 LRC")` as a source.
- [x] Store imported LRC text in app-private preferences by normalized title and artist; limit file text to 256 KB.
- [x] Return a candidate only for matching title and artist, with source `LOCAL_LRC`.

### Task 2: Add a sender import flow

**Files:**
- Modify: `app/src/main/java/com/univ/lyricsbridge/ui/PhoneModeActivity.java`
- Modify: `app/src/main/java/com/univ/lyricsbridge/service/PhoneSenderService.java`

- [x] Add a **导入本地 LRC** button and launch `ACTION_OPEN_DOCUMENT` with an openable text-file filter.
- [x] On selection, read the document, validate it with `LrcParser`, associate it with the current media-session track, save it, and refresh the active sender service.
- [x] Show actionable errors when no song is active, the file is unreadable, too large, or has no timestamped lines.
- [x] Resolve lyrics in the order local LRC, NetEase, LRCLIB.

### Task 3: Update user documentation

**Files:**
- Modify: `README.md`

- [x] Explain how to import a user-accessible LRC file and clarify that the app does not read NetEase private cache directories.

Tests were not added or run because the current task did not request testing; report the implementation as unverified until the user asks for verification.

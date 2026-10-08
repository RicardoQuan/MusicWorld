# Local LRC Import Design

## Goal

Let the phone sender use a user-accessible, timestamped `.lrc` file for the currently playing song when NetEase third-party login is unavailable.

## User flow

1. In phone sender mode, the user plays a song in NetEase Cloud Music and chooses **导入本地 LRC**.
2. Android's document picker opens. The user selects an accessible `.lrc` text file; the app does not browse or read NetEase's private app data.
3. The app validates that the file contains timestamped lyric lines and saves a private copy associated with the current song title and artist.
4. On this song now or on a later play, the lookup order is imported local LRC, NetEase, then LRCLIB. The sender reports **本地 LRC** as the source.

## Data and failure behavior

- Read only the selected document URI, then copy its text into the app's private storage. Do not retain the URI permission or request broad storage access.
- Reject empty files, oversized documents, unreadable encodings, and files without parseable timestamps with a clear message.
- Local matching is scoped to normalized title and artist. The selected file is not used for a different track.
- Existing Bluetooth message format remains unchanged; the source remains visible on the sender status.

## Scope boundary

This imports an exported or otherwise user-accessible LRC file. It does not scan, decrypt, or read NetEase's private cache. Whether NetEase exposes downloaded lyrics as standalone files depends on its app version and the phone's Android storage policy.

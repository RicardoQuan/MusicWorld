package com.univ.lyricsbridge.model;

import java.util.Objects;

/** A timed text segment from a word-level lyric format such as NetEase YRC. */
public final class LyricWord {
    private final String text;
    private final long startMs;
    private final long durationMs;

    public LyricWord(String text, long startMs, long durationMs) {
        this.text = text == null ? "" : text;
        this.startMs = Math.max(0, startMs);
        this.durationMs = Math.max(0, durationMs);
    }

    public String getText() { return text; }
    public long getStartMs() { return startMs; }
    public long getDurationMs() { return durationMs; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LyricWord)) return false;
        LyricWord that = (LyricWord) other;
        return startMs == that.startMs && durationMs == that.durationMs && text.equals(that.text);
    }

    @Override public int hashCode() { return Objects.hash(text, startMs, durationMs); }
}

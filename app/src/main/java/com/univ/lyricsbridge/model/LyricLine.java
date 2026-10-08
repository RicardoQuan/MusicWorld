package com.univ.lyricsbridge.model;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LyricLine {
    private final long timestampMs;
    private final String text;
    private final List<LyricWord> words;

    public LyricLine(long timestampMs, String text) {
        this(timestampMs, text, Collections.emptyList());
    }

    public LyricLine(long timestampMs, String text, List<LyricWord> words) {
        this.timestampMs = Math.max(0, timestampMs);
        this.text = text == null ? "" : text;
        this.words = Collections.unmodifiableList(words == null
                ? Collections.emptyList() : new ArrayList<>(words));
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public String getText() {
        return text;
    }

    public List<LyricWord> getWords() { return words; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LyricLine)) return false;
        LyricLine that = (LyricLine) other;
        return timestampMs == that.timestampMs && text.equals(that.text) && words.equals(that.words);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestampMs, text, words);
    }

    @Override
    public String toString() {
        return "LyricLine{" + "timestampMs=" + timestampMs + ", text='" + text + '\'' + '}';
    }
}

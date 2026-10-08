package com.univ.lyricsbridge.lyric;

import com.univ.lyricsbridge.model.LyricLine;
import java.util.Collections;
import java.util.List;

public final class LyricLookupResult {
    public enum Status { FOUND, NO_MATCH, ERROR }

    private final Status status;
    private final LyricCandidate candidate;
    private final String rawLrc;
    private final List<LyricLine> lines;
    private final String message;

    private LyricLookupResult(Status status, LyricCandidate candidate, String rawLrc,
                              List<LyricLine> lines, String message) {
        this.status = status;
        this.candidate = candidate;
        this.rawLrc = rawLrc;
        this.lines = lines == null ? Collections.emptyList() : Collections.unmodifiableList(lines);
        this.message = message == null ? "" : message;
    }

    public static LyricLookupResult found(LyricCandidate candidate, String rawLrc, List<LyricLine> lines) {
        return new LyricLookupResult(Status.FOUND, candidate, rawLrc, lines, "已匹配到带时间戳歌词");
    }

    public static LyricLookupResult noMatch() {
        return new LyricLookupResult(Status.NO_MATCH, null, "", Collections.emptyList(), "未匹配到带时间戳歌词");
    }

    public static LyricLookupResult error(String message) {
        return new LyricLookupResult(Status.ERROR, null, "", Collections.emptyList(), message);
    }

    public Status getStatus() {
        return status;
    }

    public LyricCandidate getCandidate() {
        return candidate;
    }

    public String getRawLrc() {
        return rawLrc;
    }

    public List<LyricLine> getLines() {
        return lines;
    }

    public String getMessage() {
        return message;
    }

    public LyricSource getSource() {
        return candidate == null ? LyricSource.UNKNOWN : candidate.getSource();
    }
}
